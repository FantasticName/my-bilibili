package io.github.fantasticname.mybilibili.listener;

import io.github.fantasticname.mybilibili.config.RedissonConfig;
import io.github.fantasticname.mybilibili.config.RocketMQConfig;
import io.github.fantasticname.mybilibili.config.SentinelConfig;
import io.github.fantasticname.mybilibili.dao.*;
import io.github.fantasticname.mybilibili.entity.CouponActivity;
import io.github.fantasticname.mybilibili.ioc.SimpleContainer;
import io.github.fantasticname.mybilibili.mq.CacheInvalidateConsumer;
import io.github.fantasticname.mybilibili.mq.CouponGrabConsumer;
import io.github.fantasticname.mybilibili.mq.FeedPushConsumer;
import io.github.fantasticname.mybilibili.mq.NotificationConsumer;
import io.github.fantasticname.mybilibili.service.CouponServiceImpl;
import io.github.fantasticname.mybilibili.service.FeedServiceImpl;
import io.github.fantasticname.mybilibili.service.RecommendServiceImpl;
import io.github.fantasticname.mybilibili.util.BloomFilterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.List;

/**
 * IoC容器初始化监听器，在Web应用启动时初始化IoC容器及各类基础设施组件
 *
 * <p>本监听器负责项目启动时的全面初始化工作：</p>
 * <ol>
 *   <li>初始化IoC容器（扫描Bean、注入依赖）</li>
 *   <li>初始化Sentinel流量控制规则</li>
 *   <li>重建布隆过滤器（从DB加载全量ID）</li>
 *   <li>启动RocketMQ消费者线程（FeedPushConsumer、CouponGrabConsumer）</li>
 *   <li>应用关闭时清理资源（Redisson、RocketMQ、消费者）</li>
 * </ol>
 *
 * <p>注意：RocketMQ消费者的启动是"尽力而为"的——
 * 如果NameServer没有启动，消费者启动失败不影响主流程，
 * 业务功能仍可通过降级逻辑正常运行。</p>
 *
 * @author FantasticName
 */
public class ContainerInitializerListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(ContainerInitializerListener.class);

    /**
     * ServletContext中存储IoC容器的属性key
     */
    public static final String CONTAINER_ATTR = "simpleContainer";

    /**
     * 要扫描的基础包名
     */
    private static final String BASE_PACKAGE = "io.github.fantasticname.mybilibili";

    /**
     * RocketMQ消费者线程
     */
    private Thread feedPushConsumerThread;
    private Thread couponGrabConsumerThread;
    private Thread cacheInvalidateConsumerThread;
    private Thread notificationConsumerThread;
    private Thread bloomRebuildThread;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("========================================");
        log.info("开始初始化IoC容器...");
        log.info("========================================");

        ServletContext servletContext = sce.getServletContext();
        String serverInfo = servletContext.getServerInfo();
        log.info("服务器信息: {}", serverInfo);
        log.info("扫描基础包: {}", BASE_PACKAGE);

        // ==================== 第1步：初始化IoC容器 ====================
        SimpleContainer container = new SimpleContainer();
        container.init(BASE_PACKAGE);
        servletContext.setAttribute(CONTAINER_ATTR, container);

        log.info("IoC容器初始化完成，开始初始化基础设施组件...");

        // ==================== 第2步：初始化Sentinel规则 ====================
        try {
            SentinelConfig.init();
            log.info("Sentinel规则初始化完成");
        } catch (Exception e) {
            log.error("Sentinel初始化失败（不影响主流程）: {}", e.getMessage());
        }

        // ==================== 第3步：重建布隆过滤器 ====================
        // 从DB加载全量ID，写入布隆过滤器，后续可用防缓存穿透
        // 改为周期性重建：每24小时重建一次，保证新增数据能被识别
        bloomRebuildThread = new Thread(() -> {
            while (true) {
                try {
                    log.info("开始重建布隆过滤器...");

                    com.google.common.hash.BloomFilter<Integer> fromFilter = BloomFilterUtil.beginRebuild();

                    VideoDao videoDao = container.getBean(VideoDao.class);
                    if (videoDao != null) {
                        List<Long> videoIds = videoDao.findAllIds();
                        for (Long id : videoIds) {
                            fromFilter.put(id.intValue());
                        }
                        log.info("布隆过滤器已加载视频ID: {}个", videoIds.size());
                    }

                    PostDao postDao = container.getBean(PostDao.class);
                    if (postDao != null) {
                        List<Long> postIds = postDao.findAllIds();
                        for (Long id : postIds) {
                            fromFilter.put(id.intValue());
                        }
                        log.info("布隆过滤器已加载动态ID: {}个", postIds.size());
                    }

                    UserDao userDao = container.getBean(UserDao.class);
                    if (userDao != null) {
                        List<Long> userIds = userDao.findAllIds();
                        for (Long id : userIds) {
                            fromFilter.put(id.intValue());
                        }
                        log.info("布隆过滤器已加载用户ID: {}个", userIds.size());
                    }

                    BloomFilterUtil.endRebuild();
                    log.info("布隆过滤器重建完成，主布隆大小: {}", BloomFilterUtil.getMainSize());
                    // 每24小时重建一次
                    Thread.sleep(24 * 60 * 60 * 1000);
                } catch (InterruptedException e) {
                    log.info("布隆过滤器重建线程被中断，退出");
                    break;
                } catch (Exception e) {
                    log.error("布隆过滤器重建失败: {}", e.getMessage());
                    try {
                        Thread.sleep(60000); // 失败后1分钟重试
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "BloomFilterRebuild-Thread");
        bloomRebuildThread.setDaemon(true);
        bloomRebuildThread.start();

        // ==================== 第4步：启动RocketMQ消费者（Daemon线程） ====================
        // Feed流推送消费者
        FollowDao followDao = container.getBean(FollowDao.class);
        if (followDao != null) {
            feedPushConsumerThread = new Thread(() -> {
                try {
                    FeedPushConsumer consumer = FeedPushConsumer.getInstance(followDao);
                    consumer.start();
                } catch (Exception e) {
                    log.error("FeedPushConsumer启动失败（RocketMQ可能未启动）: {}", e.getMessage());
                }
            }, "FeedPushConsumer-Thread");
            feedPushConsumerThread.setDaemon(true);
            feedPushConsumerThread.start();
        }

        // 优惠券抢购落库消费者
        CouponRecordDao couponRecordDao = container.getBean(CouponRecordDao.class);
        CouponActivityDao couponActivityDao = container.getBean(CouponActivityDao.class);
        NotificationDao notificationDao = container.getBean(NotificationDao.class);
        if (couponRecordDao != null && couponActivityDao != null && notificationDao != null) {
            couponGrabConsumerThread = new Thread(() -> {
                try {
                    CouponGrabConsumer consumer = CouponGrabConsumer.getInstance(couponRecordDao, couponActivityDao, notificationDao);
                    consumer.start();
                } catch (Exception e) {
                    log.error("CouponGrabConsumer启动失败（RocketMQ可能未启动）: {}", e.getMessage());
                }
            }, "CouponGrabConsumer-Thread");
            couponGrabConsumerThread.setDaemon(true);
            couponGrabConsumerThread.start();
        }

        // 缓存失效消费者（监听CACHE_INVALIDATE Topic，删除对应Redis缓存Key）
        cacheInvalidateConsumerThread = new Thread(() -> {
            try {
                CacheInvalidateConsumer consumer = CacheInvalidateConsumer.getInstance();
                consumer.start();
            } catch (Exception e) {
                log.error("CacheInvalidateConsumer启动失败（RocketMQ可能未启动）: {}", e.getMessage());
            }
        }, "CacheInvalidateConsumer-Thread");
        cacheInvalidateConsumerThread.setDaemon(true);
        cacheInvalidateConsumerThread.start();

        // 通知消费者（监听USER_FOLLOW/COMMENT_CREATE/LIKE_TOGGLE Topic，写DB+WebSocket推送）
        NotificationDao notificationDaoForConsumer = container.getBean(NotificationDao.class);
        if (notificationDaoForConsumer != null) {
            notificationConsumerThread = new Thread(() -> {
                try {
                    NotificationConsumer consumer = NotificationConsumer.getInstance(notificationDaoForConsumer);
                    consumer.start();
                } catch (Exception e) {
                    log.error("NotificationConsumer启动失败（RocketMQ可能未启动）: {}", e.getMessage());
                }
            }, "NotificationConsumer-Thread");
            notificationConsumerThread.setDaemon(true);
            notificationConsumerThread.start();
        }

        // ==================== 热榜兜底数据定时刷新（每5分钟） ====================
        Thread hotFallbackThread = new Thread(() -> {
            try {
                Thread.sleep(30000); // 等30秒让应用完全启动
            } catch (InterruptedException e) {
                return;
            }
            FeedServiceImpl feedService = container.getBean(FeedServiceImpl.class);
            if (feedService != null) {
                while (true) {
                    try {
                        feedService.refreshHotFallback();
                        Thread.sleep(5 * 60 * 1000); // 每5分钟刷新
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        log.error("热榜兜底数据刷新失败: {}", e.getMessage());
                        try { Thread.sleep(60000); } catch (InterruptedException ie) { break; }
                    }
                }
            }
        }, "HotFallback-Thread");
        hotFallbackThread.setDaemon(true);
        hotFallbackThread.start();

        // ==================== 推荐池离线计算（每天凌晨） ====================
        Thread recommendComputeThread = new Thread(() -> {
            try {
                Thread.sleep(60000); // 等1分钟让应用完全启动
            } catch (InterruptedException e) {
                return;
            }
            RecommendServiceImpl recommendService = container.getBean(RecommendServiceImpl.class);
            UserDao userDaoForRecommend = container.getBean(UserDao.class);
            if (recommendService != null && userDaoForRecommend != null) {
                while (true) {
                    try {
                        // 计算所有用户的推荐池
                        java.util.List<Long> allUserIds = userDaoForRecommend.findAllIds();
                        for (Long userId : allUserIds) {
                            try {
                                recommendService.computeAndCacheRecommendPool(userId);
                            } catch (Exception e) {
                                log.warn("推荐池计算失败: userId={}, error={}", userId, e.getMessage());
                            }
                        }
                        log.info("推荐池离线计算完成，共处理{}个用户", allUserIds.size());
                        Thread.sleep(24 * 60 * 60 * 1000); // 每24小时计算一次
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        log.error("推荐池离线计算异常: {}", e.getMessage());
                        try { Thread.sleep(60000); } catch (InterruptedException ie) { break; }
                    }
                }
            }
        }, "RecommendCompute-Thread");
        recommendComputeThread.setDaemon(true);
        recommendComputeThread.start();

        // ==================== 优惠券定时对账（每10分钟） ====================
        Thread couponReconcileThread = new Thread(() -> {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                return;
            }
            CouponServiceImpl couponService = container.getBean(CouponServiceImpl.class);
            CouponActivityDao couponActivityDaoForReconcile = container.getBean(CouponActivityDao.class);
            if (couponService != null && couponActivityDaoForReconcile != null) {
                while (true) {
                    try {
                        // 遍历所有活动，对已结束的活动进行对账
                        java.util.List<CouponActivity> allActivities = couponActivityDaoForReconcile.listAll();
                        for (CouponActivity activity : allActivities) {
                            // 只对已结束的活动（status=2）进行对账
                            if (activity.getStatus() != null && activity.getStatus() == 2) {
                                couponService.reconcileStock(activity.getId());
                            }
                        }
                        Thread.sleep(10 * 60 * 1000); // 每10分钟对账一次
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        log.error("优惠券对账异常: {}", e.getMessage());
                        try { Thread.sleep(60000); } catch (InterruptedException ie) { break; }
                    }
                }
            }
        }, "CouponReconcile-Thread");
        couponReconcileThread.setDaemon(true);
        couponReconcileThread.start();

        log.info("========================================");
        log.info("应用启动完成：IoC容器、Sentinel、BloomFilter、MQ消费者已就绪");
        log.info("========================================");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("========================================");
        log.info("应用正在关闭，清理资源...");
        log.info("========================================");

        // ===== 关闭RocketMQ消费者 =====
        if (feedPushConsumerThread != null) {
            feedPushConsumerThread.interrupt();
        }
        if (couponGrabConsumerThread != null) {
            couponGrabConsumerThread.interrupt();
        }
        if (cacheInvalidateConsumerThread != null) {
            cacheInvalidateConsumerThread.interrupt();
            CacheInvalidateConsumer.getInstance().stop();
        }
        if (notificationConsumerThread != null) {
            notificationConsumerThread.interrupt();
            NotificationConsumer consumer = NotificationConsumer.getInstance();
            if (consumer != null) {
                consumer.stop();
            }
        }

        // ===== 关闭RocketMQ Producer =====
        RocketMQConfig.shutdown();

        // ===== 关闭Redisson客户端 =====
        RedissonConfig.shutdown();

        // ===== 清理ServletContext =====
        sce.getServletContext().removeAttribute(CONTAINER_ATTR);

        log.info("应用已关闭，资源已清理");
    }
}