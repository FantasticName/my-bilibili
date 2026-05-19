package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserBehaviorDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserBehavior;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务实现类（基于内容推荐+热度排序的混合推荐）
 *
 * <p>推荐系统的核心思想：根据用户过去的"点赞+评论+观看"行为，
 * 找到和已交互内容"相似"的候选内容，按相似度排序。</p>
 *
 * <p>推荐流程：</p>
 * <ol>
 *   <li>查询用户最近N条行为记录</li>
 *   <li>从热榜中拉取候选内容（最近7天热门动态）</li>
 *   <li>基于行为数据进行协同过滤排序</li>
 *   <li>混合热度得分和行为匹配得分，最终排序</li>
 * </ol>
 *
 * <p>排序公式：
 * Score = heatScore * 0.3 + behaviorScore * 0.7
 * - heatScore：归一化的热度分数（点赞+评论）
 * - behaviorScore：目标是否与用户历史行为匹配（0或1）
 * - 行为匹配权重（0.7）高于热度权重（0.3），个性化优先</p>
 *
 * <p>冷启动处理（新用户无行为数据）：直接返回热榜内容</p>
 *
 * @author FantasticName
 */
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

    @Autowired
    private UserBehaviorDao userBehaviorDao;

    @Autowired
    private PostDao postDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private LikeService likeService;

    public RecommendServiceImpl() {
    }

    @Override
    public List<PostVO> getRecommendedFeed(Long userId, int limit) {
        log.info("推荐Feed流计算开始: userId={}, limit={}", userId, limit);

        // ==================== 优先从Redis推荐池获取（离线计算结果） ====================
        try {
            String poolKey = "recommend:pool:" + userId;
            List<String> cachedIds = io.github.fantasticname.mybilibili.util.RedisUtil.lrange(poolKey, 0, limit - 1);
            if (!cachedIds.isEmpty()) {
                log.info("Redis推荐池命中: userId={}, {}条", userId, cachedIds.size());
                List<Long> postIds = cachedIds.stream().map(Long::parseLong).collect(java.util.stream.Collectors.toList());
                List<Post> posts = postDao.findByIds(postIds);
                // 按Redis中的顺序排列
                java.util.Map<Long, Post> postMap = posts.stream().collect(java.util.stream.Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
                List<PostVO> result = new ArrayList<>();
                for (Long id : postIds) {
                    Post p = postMap.get(id);
                    if (p != null) {
                        result.add(convertToVO(p, userId));
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis推荐池读取失败，降级为实时计算: userId={}, error={}", userId, e.getMessage());
        }

        // ==================== 第1步：查询用户最近30天行为 ====================
        List<UserBehavior> behaviors = userBehaviorDao.listByUser(userId, 100);
        log.info("用户行为数据: userId={}, 共{}条", userId, behaviors.size());

        // ==================== 第2步：拉取候选内容（热榜作为候选池） ====================
        List<Post> candidatePosts = postDao.listHotPosts(limit * 3);
        log.info("候选内容池: {}条", candidatePosts.size());

        if (candidatePosts.isEmpty()) {
            return Collections.emptyList();
        }

        // ==================== 第3步：过滤掉用户已经交互过的内容 ====================
        // 已经点赞/评论/观看过的内容不重复推荐
        Set<Long> interactedPostIds = behaviors.stream()
                .filter(b -> b.getTargetType() != null && b.getTargetType() == 0) // 只关注动态类型
                .map(UserBehavior::getTargetId)
                .collect(Collectors.toSet());

        List<Post> filteredPosts = candidatePosts.stream()
                .filter(p -> !interactedPostIds.contains(p.getId()))
                .collect(Collectors.toList());

        log.info("过滤后候选内容: {}条 (去重{}条)", filteredPosts.size(),
                candidatePosts.size() - filteredPosts.size());

        // ==================== 第4步：计算每个候选内容的得分 ====================
        // 计算每个候选的得分
        List<PostScore> scoredPosts = new ArrayList<>();
        for (Post post : filteredPosts) {
            double score = calculateScore(post, behaviors);
            scoredPosts.add(new PostScore(post, score));
        }

        // 按得分降序排列
        scoredPosts.sort((a, b) -> Double.compare(b.score, a.score));

        // ==================== 第5步：取Top N并转换为VO ====================
        List<PostVO> result = new ArrayList<>();
        int count = Math.min(limit, scoredPosts.size());
        for (int i = 0; i < count; i++) {
            Post post = scoredPosts.get(i).post;
            result.add(convertToVO(post, userId));
        }

        log.info("推荐Feed流计算完成: userId={}, 返回{}条", userId, result.size());
        return result;
    }

    /**
     * 离线计算用户推荐池并存入Redis（由定时任务调用）
     *
     * <p>每天凌晨执行一次，计算用户兴趣标签，查询热门内容，
     * 存入Redis recommend:pool:{userId}，TTL=24小时。</p>
     *
     * @param userId 用户ID
     */
    public void computeAndCacheRecommendPool(Long userId) {
        try {
            List<PostVO> recommended = getRecommendedFeed(userId, 100);
            if (!recommended.isEmpty()) {
                String poolKey = "recommend:pool:" + userId;
                // 先删除旧数据
                io.github.fantasticname.mybilibili.util.RedisUtil.del(poolKey);
                // 写入新数据
                String[] ids = recommended.stream()
                        .map(vo -> String.valueOf(vo.getId()))
                        .toArray(String[]::new);
                io.github.fantasticname.mybilibili.util.RedisUtil.lpush(poolKey, ids);
                // 设置24小时过期
                io.github.fantasticname.mybilibili.util.RedisUtil.expireKey(poolKey, 86400);
                log.info("推荐池计算完成: userId={}, {}条", userId, recommended.size());
            }
        } catch (Exception e) {
            log.error("推荐池计算失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 计算单个候选内容的推荐得分
     *
     * <p>得分 = 热度分 * 0.3 + 行为匹配分 * 0.7</p>
     *
     * <p>热度分归一化：
     * rawHeat = likeCount + commentCount * 2
     * heatScore = Math.min(rawHeat / 100.0, 1.0) → 0~1之间</p>
     *
     * <p>行为匹配分：
     * 如果用户的历史行为中包含了和候选内容发布者相同的用户ID → 1.0（完全匹配）
     * 否则 → 0.0</p>
     *
     * @param post      候选内容
     * @param behaviors 用户历史行为
     * @return 推荐得分
     */
    private double calculateScore(Post post, List<UserBehavior> behaviors) {
        // 热度分（归一化到0~1）
        long rawHeat = (post.getLikeCount() != null ? post.getLikeCount() : 0) +
                       (post.getCommentCount() != null ? post.getCommentCount() : 0) * 2;
        double heatScore = Math.min(rawHeat / 100.0, 1.0);

        // 行为匹配分：检查候选内容的发布者是否在用户历史行为中（表示对该发布者有兴趣）
        double behaviorScore = 0.0;
        for (UserBehavior beh : behaviors) {
            // 如果用户之前点赞/评论/观看了同一个发布者的内容 → 匹配
            if (beh.getTargetType() != null && beh.getTargetType() == 0) {
                // 这里简化处理：如果候选的发布者在行为记录中，给匹配分
                Long behTargetId = beh.getTargetId();
                if (behTargetId != null && behTargetId.equals(post.getUserId())) {
                    behaviorScore = 1.0;
                    break;
                }
            }
        }

        // 加权平均
        return heatScore * 0.3 + behaviorScore * 0.7;
    }

    /**
     * 内部类：临时存储Post和得分（用于排序）
     */
    private static class PostScore {
        final Post post;
        final double score;

        PostScore(Post post, double score) {
            this.post = post;
            this.score = score;
        }
    }

    /**
     * 将Post转换为PostVO
     */
    private PostVO convertToVO(Post post, Long currentUserId) {
        if (post == null) return null;
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setContent(post.getContent());
        vo.setImages(parseImages(post.getImages()));
        vo.setUserId(post.getUserId());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setCreatedAt(post.getCreatedAt());

        User user = userDao.findById(post.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        }

        if (currentUserId != null) {
            vo.setIsLiked(likeService.isLiked(currentUserId, 3, post.getId()));
        }

        return vo;
    }

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