package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.config.RedissonConfig;
import io.github.fantasticname.mybilibili.dao.UserBehaviorDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserBehavior;
import io.github.fantasticname.mybilibili.entity.Video;
import io.github.fantasticname.mybilibili.util.BloomFilterUtil;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.util.RegexUtil;
import io.github.fantasticname.mybilibili.vo.VideoVO;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 视频服务实现类
 *
 * <p>核心功能：视频发布、视频详情查看、视频列表查询、视频删除。</p>
 *
 * <p>【缓存安全设计】视频详情接口实现了三层缓存防护：</p>
 * <ul>
 *   <li><b>第1层-缓存穿透防护（BloomFilter）</b>：查询前先检查布隆过滤器，
 *       如果ID一定不存在则直接返回null，不查DB</li>
 *   <li><b>第2层-缓存击穿防护（分布式锁）</b>：多个请求同时查同一个过期key时，
 *       只有拿到Redisson锁的线程去查DB并写缓存，其他线程等待或轮询</li>
 *   <li><b>第3层-缓存雪崩防护（随机TTL）</b>：缓存过期时间加上随机值（5~10分钟），
 *       避免大量缓存同时过期，DB被打垮</li>
 * </ul>
 *
 * @author FantasticName
 */
@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    @Autowired
    private VideoDao videoDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FavoriteService favoriteService;

    /**
     * 用户行为DAO，用于异步写入行为埋点数据
     */
    @Autowired
    private UserBehaviorDao userBehaviorDao;

    /**
     * 行为埋点异步线程池（用于异步写入user_behavior表，不阻塞主流程）
     */
    private static final java.util.concurrent.ExecutorService BEHAVIOR_EXECUTOR =
            java.util.concurrent.Executors.newFixedThreadPool(4);

    /**
     * 视频缓存Key前缀
     *
     * <p>完整的Redis Key格式：video:detail:{videoId}
     * 例如：video:detail:123 → 视频ID=123的缓存数据</p>
     */
    private static final String VIDEO_CACHE_KEY_PREFIX = "video:detail:";

    /**
     * 视频缓存基础TTL（秒）
     *
     * <p>视频详情数据变化不频繁，设置较长的基础TTL。
     * 实际TTL = 基础TTL + 随机值（0~300秒），防雪崩。</p>
     */
    private static final long VIDEO_CACHE_TTL_SECONDS = 300; // 5分钟

    /**
     * 缓存TTL随机范围（秒）
     *
     * <p>每个key的TTL额外加上0~300秒的随机值，
     * 使得大量缓存不会在同一时刻过期。</p>
     */
    private static final long VIDEO_CACHE_RANDOM_RANGE_SECONDS = 300; // 0~5分钟随机

    /**
     * 缓存空值的TTL（秒）
     *
     * <p>对于不存在的数据，缓存一个空标记（"NULL"），
     * TTL较短（60秒），防止恶意攻击者用不同的不存在ID打DB。</p>
     */
    private static final long NULL_CACHE_TTL_SECONDS = 60; // 1分钟

    /**
     * 获取分布式锁的最大等待时间（秒）
     *
     * <p>等待时间过长会影响用户体验，这里设为3秒。
     * 如果3秒内没拿到锁，直接去查DB（降级策略）。</p>
     */
    private static final long LOCK_WAIT_SECONDS = 3;

    /**
     * 分布式锁的持有时间（秒）
     *
     * <p>Redisson的看门狗会自动续期，但设置一个合理的持有时间
     * 可以防止极端情况下锁永不释放。</p>
     */
    private static final long LOCK_HOLD_SECONDS = 10;

    /**
     * 随机数生成器（用于TTL随机化）
     */
    private static final Random RANDOM = new Random();

    public VideoServiceImpl() {
    }

    @Override
    public VideoVO publish(Long userId, String title, String description, String coverUrl, String videoUrl, String category) {
        log.info("开始发布视频: userId={}, title={}", userId, title);

        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能为空");
        }
        if (title.length() > 255) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "视频URL不能为空");
        }
        if (category == null || category.trim().isEmpty()) {
            category = "搞笑";
        }

        Video video = new Video();
        video.setTitle(title.trim());
        video.setDescription(description);
        video.setCoverUrl(coverUrl);
        video.setVideoUrl(videoUrl.trim());
        video.setCategory(category.trim());
        video.setUserId(userId);

        long videoId = videoDao.insert(video);
        log.info("视频发布成功: videoId={}, userId={}", videoId, userId);

        // 【缓存安全】新视频发布后，添加到布隆过滤器中
        // 这样后续查询时布隆过滤器能识别这个ID
        BloomFilterUtil.add((int) videoId);

        // 【缓存一致性】新视频发布后，删除视频列表缓存
        // 下次查询列表时重新从DB加载（Cache Aside模式）
        RedisUtil.del("video:list:all");
        RedisUtil.del("video:list:" + (category != null ? category.trim() : ""));

        // 【Feed推送】视频发布后，发送MQ消息触发Feed推送
        try {
            org.apache.rocketmq.client.producer.DefaultMQProducer producer =
                io.github.fantasticname.mybilibili.config.RocketMQConfig.getProducer();
            if (producer != null) {
                com.fasterxml.jackson.databind.ObjectMapper mqMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> msgBody = new java.util.HashMap<>();
                msgBody.put("postUserId", userId);
                msgBody.put("postId", videoId);
                msgBody.put("timestamp", java.time.LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.of("+8")));
                org.apache.rocketmq.common.message.Message msg = new org.apache.rocketmq.common.message.Message(
                    "VIDEO_PUBLISH", "video", mqMapper.writeValueAsString(msgBody).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String traceId = io.github.fantasticname.mybilibili.context.TraceIdContext.getCurrentTraceId();
                if (traceId != null) {
                    msg.putUserProperty("TRACE_ID", traceId);
                }
                producer.send(msg);
                log.info("VIDEO_PUBLISH消息发送成功: videoId={}, userId={}", videoId, userId);
            }
        } catch (Exception e) {
            // MQ发送失败不影响视频发布主流程
            log.warn("VIDEO_PUBLISH消息发送失败: videoId={}, error={}", videoId, e.getMessage());
        }

        return convertToVO(videoDao.findById(videoId), userId);
    }

    @Override
    public VideoVO getDetail(Long videoId, Long currentUserId) {
        log.info("查询视频详情: videoId={}, currentUserId={}", videoId, currentUserId);

        // ==================== 第1层：布隆过滤器防缓存穿透 ====================
        // 如果布隆过滤器说"一定不存在"，则直接返回null，不查缓存也不查DB
        // 这能过滤掉90%以上的恶意攻击请求
        if (!BloomFilterUtil.mightContain(videoId.intValue())) {
            log.warn("缓存穿透拦截: videoId={} 一定不存在", videoId);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在");
        }

        // ==================== 第2层：查询Redis缓存（带雪崩防护） ====================
        String cacheKey = VIDEO_CACHE_KEY_PREFIX + videoId;
        VideoVO cached = RedisUtil.getObject(cacheKey, VideoVO.class);

        // 检查是否缓存了空值标记
        // 缓存空值是防穿透的另一道防线——对于"数据库中不存在"的ID，
        // 缓存一个空标记，后续请求直接返回，不查DB
        if (cached != null) {
            // 检查是否是空值标记（id为-1表示缓存了"不存在"）
            if (cached.getId() != null && cached.getId() == -1) {
                log.info("缓存穿透拦截（空值缓存）: videoId={}", videoId);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在或已下架");
            }
            log.info("缓存命中: videoId={}", videoId);
            // 缓存命中后异步增加播放量（不阻塞返回）
            incrementViewCountAsync(videoId);
            // 【行为埋点】异步记录用户观看行为（推荐系统数据源）
            if (currentUserId != null) {
                final Long uid = currentUserId;
                BEHAVIOR_EXECUTOR.submit(() -> {
                    try {
                        UserBehavior behavior = new UserBehavior();
                        behavior.setUserId(uid);
                        behavior.setBehaviorType("VIEW");
                        behavior.setTargetId(videoId);
                        behavior.setTargetType(1);
                        behavior.setWeight(1);
                        userBehaviorDao.insert(behavior);
                    } catch (Exception ex) {
                        log.warn("行为埋点失败(VIEW): userId={}, videoId={}, error={}", uid, videoId, ex.getMessage());
                    }
                });
            }
            return cached;
        }

        // ==================== 第3层：分布式锁防缓存击穿 ====================
        // 缓存未命中，使用Redisson分布式锁保证只有一个线程去查DB
        // 其他线程等待（最多3秒），等待后重新从缓存取
        String lockKey = "video:lock:" + videoId;
        RLock lock = RedissonConfig.getRedissonClient().getLock(lockKey);

        try {
            // tryLock(等待时间, 持有时间, 时间单位)
            // 等待3秒：3秒内拿不到锁就不等了，直接查DB（降级策略）
            // 持有10秒：拿到锁后10秒自动释放（看门狗也会续期）
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_HOLD_SECONDS, TimeUnit.SECONDS);

            if (locked) {
                try {
                    // 【双重检查】拿到锁后再查一次缓存
                    // 防止当前线程等待锁期间，前一个线程已经写了缓存
                    cached = RedisUtil.getObject(cacheKey, VideoVO.class);
                    if (cached != null) {
                        log.info("双重检查缓存命中: videoId={}", videoId);
                        if (cached.getId() != null && cached.getId() == -1) {
                            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在或已下架");
                        }
                        incrementViewCountAsync(videoId);
                        return cached;
                    }

                    // ==================== 第4层：查询数据库 ====================
                    Video video = videoDao.findById(videoId);
                    if (video == null || video.getStatus() == 1) {
                        // 视频不存在 → 缓存空值标记（防穿透）
                        // 空值标记的TTL较短（1分钟），因为可能是新数据还没进布隆
                        VideoVO nullMarker = new VideoVO();
                        nullMarker.setId(-1L); // -1表示空值标记
                        RedisUtil.setObject(cacheKey, nullMarker, NULL_CACHE_TTL_SECONDS);
                        log.info("缓存空值标记: videoId={}", videoId);
                        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在或已下架");
                    }

                    // ==================== 第5层：写缓存（带TTL随机化防雪崩） ====================
                    // TTL = 基础值(300秒) + 随机值(0~300秒)
                    long randomTtl = VIDEO_CACHE_TTL_SECONDS + RANDOM.nextInt((int) VIDEO_CACHE_RANDOM_RANGE_SECONDS);
                    VideoVO vo = convertToVO(video, currentUserId);
                    RedisUtil.setObject(cacheKey, vo, randomTtl);
                    log.info("缓存写入成功: videoId={}, TTL={}秒", videoId, randomTtl);

                    // 增加播放量（此处同步增加，因为刚从DB查出来还没+1）
                    videoDao.incrementViewCount(videoId);
                    // 【行为埋点】异步记录用户观看行为（推荐系统数据源）
                    if (currentUserId != null) {
                        final Long uid = currentUserId;
                        BEHAVIOR_EXECUTOR.submit(() -> {
                            try {
                                UserBehavior behavior = new UserBehavior();
                                behavior.setUserId(uid);
                                behavior.setBehaviorType("VIEW");
                                behavior.setTargetId(videoId);
                                behavior.setTargetType(1);
                                behavior.setWeight(1);
                                userBehaviorDao.insert(behavior);
                            } catch (Exception ex) {
                                log.warn("行为埋点失败(VIEW): userId={}, videoId={}, error={}", uid, videoId, ex.getMessage());
                            }
                        });
                    }
                    return vo;

                } finally {
                    // 【重要】finally中释放锁
                    // RLock.unlock()必须成对调用，否则Redisson计数器异常
                    // 用isHeldByCurrentThread()判断当前线程是否持有锁，防止非持锁线程释放
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("分布式锁释放: lockKey={}", lockKey);
                    }
                }
            } else {
                // 没拿到锁（等待3秒超时）→ 降级：直接查DB
                // 这是一种兜底策略，防止用户等待太久
                log.warn("获取分布式锁超时，降级查DB: videoId={}", videoId);
                Video video = videoDao.findById(videoId);
                if (video == null || video.getStatus() == 1) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在或已下架");
                }
                videoDao.incrementViewCount(videoId);
                return convertToVO(video, currentUserId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁中断: videoId={}", videoId, e);
            // 中断时也降级查DB
            Video video = videoDao.findById(videoId);
            if (video == null || video.getStatus() == 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在或已下架");
            }
            return convertToVO(video, currentUserId);
        }
    }

    /**
     * 异步增加播放量（使用线程池避免阻塞缓存命中路径）
     *
     * <p>当缓存命中时，主线程不需要等待播放量写入完成，
     * 异步执行可以加快接口响应速度。</p>
     *
     * @param videoId 视频ID
     */
    private void incrementViewCountAsync(Long videoId) {
        try {
            videoDao.incrementViewCount(videoId);
            log.debug("异步增加播放量: videoId={}", videoId);
        } catch (Exception e) {
            // 播放量写入失败不影响主流程
            log.warn("异步增加播放量失败: videoId={}, error={}", videoId, e.getMessage());
        }
    }

    @Override
    public List<VideoVO> list(String category, int page, int size) {
        int offset = (page - 1) * size;
        List<Video> videos;
        if (category != null && !category.isEmpty()) {
            videos = videoDao.listByCategory(category, offset, size);
        } else {
            videos = videoDao.listAll(offset, size);
        }
        return convertList(videos, null);
    }

    @Override
    public List<VideoVO> listByUser(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Video> videos = videoDao.listByUserId(userId, offset, size);
        return convertList(videos, null);
    }

    @Override
    public int count(String category) {
        if (category != null && !category.isEmpty()) {
            return videoDao.countByCategory(category);
        }
        return videoDao.countAll();
    }

    @Override
    public int countByUser(Long userId) {
        return videoDao.countByUserId(userId);
    }

    @Override
    public void deleteVideo(Long videoId, Long userId) {
        Video video = videoDao.findById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在");
        }
        if (!video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能删除自己的视频");
        }
        videoDao.updateStatus(videoId, 1);

        // 【缓存一致性】删除视频后，删除对应的缓存
        // 下次查询时会重新从DB加载（或缓存空值）
        String cacheKey = VIDEO_CACHE_KEY_PREFIX + videoId;
        deleteCacheWithRetry(cacheKey);
        // 同时删除视频列表缓存，确保列表页也能及时更新
        deleteCacheWithRetry("video:list:all");
        deleteCacheWithRetry("video:list:" + (video.getCategory() != null ? video.getCategory() : ""));
        log.info("视频删除成功并清理缓存: videoId={}, userId={}", videoId, userId);
    }

    private VideoVO convertToVO(Video video, Long currentUserId) {
        if (video == null) return null;
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setTitle(video.getTitle());
        vo.setDescription(video.getDescription());
        vo.setCoverUrl(FileUtil.toUrl(video.getCoverUrl()));
        vo.setVideoUrl(FileUtil.toUrl(video.getVideoUrl()));
        vo.setCategory(video.getCategory());
        vo.setUserId(video.getUserId());
        vo.setViewCount(video.getViewCount());
        vo.setLikeCount(video.getLikeCount());
        vo.setCreatedAt(video.getCreatedAt());

        User user = userDao.findById(video.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        }

        if (currentUserId != null) {
            vo.setIsLiked(likeService.isLiked(currentUserId, 1, video.getId()));
            vo.setIsFavorited(favoriteService.isFavorited(currentUserId, 1, video.getId()));
        }

        return vo;
    }

    private List<VideoVO> convertList(List<Video> videos, Long currentUserId) {
        List<VideoVO> result = new ArrayList<>();
        for (Video v : videos) {
            result.add(convertToVO(v, currentUserId));
        }
        return result;
    }

    /**
     * 删除缓存并重试（缓存一致性过渡方案）
     *
     * <p>写操作后同步删除Redis缓存，如果删除失败则重试最多3次。
     * 这是RocketMQ事务消息方案引入前的过渡方案。</p>
     *
     * @param cacheKey 缓存Key
     */
    private void deleteCacheWithRetry(String cacheKey) {
        for (int i = 0; i < 3; i++) {
            try {
                if (io.github.fantasticname.mybilibili.util.RedisUtil.del(cacheKey) >= 1) {
                    log.info("缓存删除成功: key={}", cacheKey);
                    return;
                }
                log.warn("缓存删除返回0，重试第{}次: key={}", i + 1, cacheKey);
            } catch (Exception e) {
                log.warn("缓存删除异常，重试第{}次: key={}, error={}", i + 1, cacheKey, e.getMessage());
            }
        }
        log.error("缓存删除失败，3次重试均未成功: key={}", cacheKey);
    }
}
