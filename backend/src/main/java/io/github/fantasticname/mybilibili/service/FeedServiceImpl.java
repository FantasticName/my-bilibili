package io.github.fantasticname.mybilibili.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.dao.FollowDao;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Follow;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.mq.FeedPushConsumer;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
/**
 * Feed流服务实现类（Push-Pull混合模式）
 *
 * <p>Feed流是社交平台的核心功能——用户看到自己关注的人发布的内容。</p>
 *
 * <p>【Push-Pull混合模式设计原理】</p>
 *
 * <p>纯Push模式的缺点：
 * 大V有百万粉丝，发布一条动态需要Push给百万人，推送压力巨大，且大多数粉丝不在线。</p>
 *
 * <p>纯Pull模式的缺点：
 * 每次打开App都要拉取所有关注用户的最新动态，DB压力大，响应慢。</p>
 *
 * <p>Push-Pull混合模式：</p>
 * <ul>
 *   <li><b>普通用户（粉丝≤1000）</b>：发布动态时Push到所有粉丝的Redis收件箱</li>
 *   <li><b>大V（粉丝>1000）</b>：Push给前1000个活跃粉丝，其余粉丝Pull时从大V发件箱拉取</li>
 *   <li><b>收件箱（inbox）</b>：每个用户一个Redis Sorted Set，score=时间戳，member=动态ID</li>
 *   <li><b>发件箱（outbox）</b>：大V的Redis Sorted Set，用于粉丝Pull补充</li>
 *   <li><b>Pull降级</b>：Redis数据丢失时，降级查DB（拉取所有关注用户的动态）</li>
 * </ul>
 *
 * <p>查询流程（用户打开App -> 请求Feed流）：</p>
 * <ol>
 *   <li>先查Redis收件箱（push来的数据，速度快）</li>
 *   <li>如果收件箱数据不足20条，再从热榜补充</li>
 *   <li>如果用户没有任何关注 → 返回热榜（hot posts）</li>
 *   <li>如果Redis挂了 → 降级查DB</li>
 * </ol>
 *
 * @author FantasticName
 */
@Service
public class FeedServiceImpl implements FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedServiceImpl.class);

    /**
     * Feed流每页默认数量
     */
    private static final int DEFAULT_FEED_LIMIT = 20;

    /**
     * 普通用户粉丝阈值（超过此值视为大V，与FeedPushConsumer保持一致）
     */
    private static final int BIG_V_THRESHOLD = 1000;

    /**
     * Caffeine本地缓存：缓存大V发件箱数据，30秒过期，最多1000个key
     *
     * <p>相同大V的发件箱数据在30秒内可被多个粉丝"复用"，
     * 不需要每次都查Redis，大幅减少Redis请求次数。</p>
     */
    private static final Cache<Long, List<String>> BIG_V_OUTBOX_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    /**
     * 热榜兜底数据（熔断降级时直接返回，不碰Redis和MySQL）
     *
     * <p>当Redis不可用、或Sentinel触发限流/熔断时，
     * 直接返回这份预生成的静态热榜数据，避免DB被打垮。</p>
     */
    private volatile List<PostVO> hotFallback = new ArrayList<>();

    @Autowired
    private PostDao postDao;

    @Autowired
    private FollowDao followDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private LikeService likeService;

    @Autowired
    private RecommendServiceImpl recommendService;

    public FeedServiceImpl() {
    }

    /**
     * 获取用户的Feed流（Push-Pull混合模式的核心实现）
     *
     * <p>查询策略（按优先级）：</p>
     * <ol>
     *   <li><b>Redis收件箱</b>：查询用户自己的 feed:inbox:{userId}，获取Push来的动态ID</li>
     *   <li><b>大V发件箱</b>：如果关注了大V但没收到Push，从 feed:outbox:{bigVId} 拉取</li>
     *   <li><b>DB降级</b>：Redis挂了或数据丢失时，直接查DB</li>
     *   <li><b>热榜兜底</b>：没有任何关注时，返回热榜内容</li>
     * </ol>
     *
     * @param userId 当前用户ID
     * @param cursor 游标（时间戳字符串，首次请求传null或空串）
     * @param limit  每页数量
     * @return 动态VO列表
     */
    @Override
    public List<PostVO> getFeed(Long userId, String cursor, int limit) {
        if (limit <= 0) limit = DEFAULT_FEED_LIMIT;

        log.info("Feed流查询开始: userId={}, cursor={}, limit={}", userId, cursor, limit);

        try {
            // ==================== 步骤1：查询Redis收件箱（Push数据） ====================
            String inboxKey = FeedPushConsumer.FEED_INBOX_PREFIX + userId;
            double minScore = 0; // 默认从最早开始
            if (cursor != null && !cursor.isEmpty()) {
                try {
                    // cursor是上一页最后一条的时间戳
                    minScore = Double.parseDouble(cursor);
                } catch (NumberFormatException e) {
                    // 如果cursor解析失败（可能是日期格式），使用0
                    log.warn("游标解析失败，从0开始: cursor={}", cursor);
                }
            }

            // 从收件箱按时间倒序拉取动态ID
            List<String> postIdStrs = RedisUtil.zrevrangeByScore(inboxKey, minScore, 0, limit * 2);
            log.info("Redis收件箱查询成功: userId={}, 获取{}条ID", userId, postIdStrs.size());

            // ==================== 大V Outbox拉取归并 ====================
            // 如果关注了大V（粉丝>1000），大V的动态可能没有Push到收件箱
            // 需要从大V的发件箱(feed:outbox:{bigVId})补充拉取
            // 为lambda表达式创建final副本
            final double finalMinScore = minScore;
            final int finalLimit = limit;
            List<Follow> follows = followDao.listFollowing(userId, 0, 10000);

            // 查找大V：粉丝数超过阈值的关注对象
            for (Follow f : follows) {
                int fansCount = followDao.countFollowers(f.getFolloweeId());
                if (fansCount > BIG_V_THRESHOLD) {
                    // 从大V发件箱拉取（先查Caffeine本地缓存，再查Redis）
                    List<String> outboxIds = BIG_V_OUTBOX_CACHE.get(f.getFolloweeId(), key -> {
                        String outboxKey = FeedPushConsumer.FEED_OUTBOX_PREFIX + key;
                        return RedisUtil.zrevrangeByScore(outboxKey, finalMinScore, 0, finalLimit);
                    });
                    if (!outboxIds.isEmpty()) {
                        // 归并：将大V发件箱的ID合并到收件箱ID列表（去重）
                        Set<String> existingIds = new HashSet<>(postIdStrs);
                        for (String id : outboxIds) {
                            if (!existingIds.contains(id)) {
                                postIdStrs.add(id);
                                existingIds.add(id);
                            }
                        }
                    }
                }
            }

            // 如果收件箱+发件箱合并后仍为空，走Pull降级
            if (postIdStrs.isEmpty()) {
                return pullFallback(userId, cursor, limit);
            }

            // ==================== 步骤2：根据ID批量查询Post实体 ====================
            List<Long> postIds = postIdStrs.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            List<Post> posts = postDao.findByIds(postIds);
            log.info("批量查询Post: 请求{}条ID, 查得{}条", postIds.size(), posts.size());

            // 按收件箱的顺序排列Post（因为IN查询打乱了顺序）
            Map<Long, Post> postMap = posts.stream()
                    .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

            List<Post> orderedPosts = new ArrayList<>();
            for (Long id : postIds) {
                Post p = postMap.get(id);
                if (p != null) {
                    orderedPosts.add(p);
                }
            }

            // ==================== 步骤3：转换为VO ====================
            List<PostVO> result = new ArrayList<>();
            for (Post p : orderedPosts) {
                result.add(convertToVO(p, userId));
            }

            // ==================== 两路召回：融合推荐结果 ====================
            // 第1路：Feed收件箱（权重0.7）——已在上面的result中
            // 第2路：个人推荐池（权重0.3）——从RecommendService获取
            try {
                List<PostVO> recommended = recommendService.getRecommendedFeed(userId, limit);
                if (!recommended.isEmpty() && result.size() < limit) {
                    // 按权重混合：收件箱70% + 推荐池30%
                    int inboxCount = (int) Math.ceil(result.size() * 0.7);
                    int recommendCount = Math.min((int) Math.ceil(limit * 0.3), recommended.size());
                    // 保留收件箱前inboxCount条
                    List<PostVO> mixed = new ArrayList<>(result.subList(0, Math.min(inboxCount, result.size())));
                    // 补充推荐池前recommendCount条（去重）
                    Set<Long> existingIds = mixed.stream().map(PostVO::getId).collect(Collectors.toSet());
                    for (PostVO rec : recommended) {
                        if (mixed.size() >= limit) break;
                        if (!existingIds.contains(rec.getId())) {
                            mixed.add(rec);
                            existingIds.add(rec.getId());
                        }
                    }
                    result = mixed;
                    log.info("两路召回混合完成: 收件箱{}条 + 推荐池补充后共{}条", inboxCount, result.size());
                }
            } catch (Exception e) {
                // 推荐服务异常不影响Feed流主流程
                log.warn("推荐服务调用失败，回退纯收件箱模式: userId={}, error={}", userId, e.getMessage());
            }

            log.info("Feed流查询完成: userId={}, 返回{}条", userId, result.size());
            return result;

        } catch (Exception e) {
            // Redis挂了或出错了 → 返回热榜兜底数据（不碰DB，防止雪崩）
            log.error("Feed流Redis查询异常，返回热榜兜底: userId={}, error={}", userId, e.getMessage());
            if (!hotFallback.isEmpty()) {
                return hotFallback;
            }
            return pullFallback(userId, cursor, limit);
        }
    }

    /**
     * 刷新热榜兜底数据（由定时线程调用）
     *
     * <p>每5分钟刷新一次，保证兜底数据不会太旧。</p>
     */
    public void refreshHotFallback() {
        try {
            List<Post> hotPosts = postDao.listHotPosts(20);
            List<PostVO> newData = new ArrayList<>();
            for (Post p : hotPosts) {
                newData.add(convertToVO(p, null));
            }
            this.hotFallback = newData;
            log.info("热榜兜底数据刷新完成: {}条", newData.size());
        } catch (Exception e) {
            log.error("热榜兜底数据刷新失败: {}", e.getMessage());
        }
    }

    /**
     * 获取热榜兜底数据（供Sentinel降级和FeedController使用）
     *
     * @return 热榜动态VO列表
     */
    public List<PostVO> getHotFallback() {
        return hotFallback;
    }

    /**
     * Pull降级策略——直接从数据库拉取Feed
     *
     * <p>当Redis不可用或收件箱为空时调用。
     * 如果用户有关注对象，拉取关注者的动态；如果没有关注，返回热榜。</p>
     *
     * @param userId 用户ID
     * @param cursor 游标
     * @param limit  每页数量
     * @return 动态VO列表
     */
    private List<PostVO> pullFallback(Long userId, String cursor, int limit) {
        log.info("执行Pull降级: userId={}", userId);

        // 查询用户的关注列表
        List<Follow> follows = followDao.listFollowing(userId, 0, 10000);

        if (follows.isEmpty()) {
            // 没有关注任何人 → 返回热榜
            log.info("用户无关注，返回热榜: userId={}", userId);
            List<Post> hotPosts = postDao.listHotPosts(limit);
            return hotPosts.stream()
                    .map(p -> convertToVO(p, userId))
                    .collect(Collectors.toList());
        }

        // 有关注 → 直接查DB拉取关注者的动态
        List<Long> followeeIds = follows.stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toList());
        followeeIds.add(userId); // 也包含自己的动态

        List<Post> posts = postDao.listFeedByCursor(followeeIds, cursor, limit);
        List<PostVO> result = new ArrayList<>();
        for (Post p : posts) {
            result.add(convertToVO(p, userId));
        }
        log.info("Pull降级完成: userId={}, 返回{}条", userId, result.size());
        return result;
    }

    /**
     * 将Post实体转换为PostVO
     *
     * @param post           动态实体
     * @param currentUserId  当前用户ID
     * @return PostVO
     */
    private PostVO convertToVO(Post post, Long currentUserId) {
        if (post == null) return null;
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setContent(post.getContent());

        // 将逗号分隔的图片字符串转换为URL列表
        vo.setImages(parseImages(post.getImages()));

        vo.setUserId(post.getUserId());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setCreatedAt(post.getCreatedAt());

        // 查询发布者信息
        User user = userDao.findById(post.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        }

        // 查询是否已点赞（targetType=3表示动态点赞）
        if (currentUserId != null) {
            vo.setIsLiked(likeService.isLiked(currentUserId, 3, post.getId()));
        }

        return vo;
    }

    /**
     * 将逗号分隔的图片字符串转换为URL列表
     *
     * @param imagesStr 逗号分隔的图片文件名字符串
     * @return 图片URL列表
     */
    private List<String> parseImages(String imagesStr) {
        if (imagesStr == null || imagesStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(imagesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(FileUtil::toUrl)
                .collect(Collectors.toList());
    }
}