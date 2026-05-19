package io.github.fantasticname.mybilibili.mq;

import io.github.fantasticname.mybilibili.config.RocketMQConfig;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.dao.FollowDao;
import io.github.fantasticname.mybilibili.entity.Follow;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Feed流推送消费者（Push模式的核心实现）
 *
 * <p>当用户发布动态时，RocketMQ会发送一条消息到这个Topic。
 * 本Consumer收到消息后，执行"展开推送"逻辑：</p>
 *
 * <p>推送流程（以Post发布为例）：</p>
 * <ol>
 *   <li>从消息中提取发布者ID(postUserId)和动态ID(postId)</li>
 *   <li>查询发布者的所有粉丝列表（FollowDao.listFollowers）</li>
 *   <li>遍历每个粉丝，将动态ID推入其Redis收件箱（Sorted Set）</li>
 *   <li>收件箱Key格式：feed:inbox:{userId}</li>
 *   <li>Score = 动态发布时间戳，member = 动态ID字符串</li>
 *   <li>推送完成后，修剪收件箱：每个用户最多保留1000条</li>
 * </ol>
 *
 * <p>为什么要异步推送？</p>
 * <ul>
 *   <li>发布动态时，如果同步推送，大V有百万粉丝，主线程会阻塞很久</li>
 *   <li>异步推送到MQ，发布者立即看到"发布成功"，推送在后台慢慢进行</li>
 *   <li>即使用户暂时不在线，动态也在收件箱里等着他下次打开</li>
 * </ul>
 *
 * <p>大V粉丝数量的处理（粉丝数 > 1000时）：</p>
 * <ul>
 *   <li>粉丝数 <= 1000：全部Push到收件箱</li>
 *   <li>粉丝数 > 1000：Push到前1000个活跃粉丝，其他粉丝Pull时从大V发件箱拉取</li>
 *   <li>大V发件箱Key：feed:outbox:{userId}</li>
 * </ul>
 *
 * <p>Consumer运行方式：</p>
 * <ul>
 *   <li>在ContainerInitializerListener中启动一个Daemon线程</li>
 *   <li>使用LitePullConsumer（拉模式），手动控制拉取时机</li>
 *   <li>如果NameServer不可用，Consumer启动失败但不影响主流程</li>
 * </ul>
 *
 * @author FantasticName
 */
public class FeedPushConsumer {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(FeedPushConsumer.class);

    /**
     * 普通用户粉丝阈值（超过此值视为大V）
     */
    private static final int BIG_V_THRESHOLD = 1000;

    /**
     * 写扩散限速器（每秒最多写500个收件箱，平抑写尖峰）
     *
     * <p>普通用户发帖时，Push模式会向所有粉丝的Redis收件箱写入数据。
     * 如果粉丝数很多，瞬间写入压力巨大。RateLimiter通过令牌桶算法限速，
     * 每秒最多写500个收件箱，平滑写入曲线。</p>
     */
    private final RateLimiter rateLimiter = RateLimiter.create(500.0);

    /**
     * 每个用户收件箱最多保留的动态数
     */
    private static final int MAX_INBOX_SIZE = 1000;

    /**
     * Feed流收件箱Redis Key前缀
     *
     * <p>格式：feed:inbox:{userId}
     * 数据结构：Sorted Set，score=时间戳，member=动态ID</p>
     */
    public static final String FEED_INBOX_PREFIX = "feed:inbox:";

    /**
     * 大V发件箱Redis Key前缀
     *
     * <p>格式：feed:outbox:{userId}
     * 大V（粉丝>1000）的动态也会写入发件箱，Pull时从这里补充拉取</p>
     */
    public static final String FEED_OUTBOX_PREFIX = "feed:outbox:";

    /**
     * FeedPushConsumer的单例
     */
    private static volatile FeedPushConsumer instance;

    /**
     * 消费者是否正在运行
     */
    private volatile boolean running = false;

    /**
     * LitePullConsumer实例
     */
    private DefaultLitePullConsumer consumer;

    /**
     * FollowDao实例（通过构造器注入，不走IoC容器的@Autowired）
     *
     * <p>因为本类不是IoC容器管理的Bean（不用@Autowired注解），
     * 而是手动new出来的，所以通过构造器/setter注入依赖。</p>
     */
    private FollowDao followDao;

    /**
     * 私有构造器（单例模式）
     *
     * @param followDao FollowDao实例
     */
    private FeedPushConsumer(FollowDao followDao) {
        this.followDao = followDao;
    }

    /**
     * 获取单例
     *
     * @param followDao FollowDao（首次创建时需要）
     * @return FeedPushConsumer实例
     */
    public static FeedPushConsumer getInstance(FollowDao followDao) {
        if (instance == null) {
            synchronized (FeedPushConsumer.class) {
                if (instance == null) {
                    instance = new FeedPushConsumer(followDao);
                }
            }
        }
        return instance;
    }

    /**
     * 启动消费者（在Daemon线程中循环拉取消息）
     *
     * <p>使用LitePullConsumer拉模式：
     * - assign(String topic, String tag)：订阅指定的Topic
     * - poll()：拉取一批消息（阻塞等待）
     * - 处理完一批后继续poll()</p>
     */
    public void start() {
        try {
            // 创建LitePullConsumer
            consumer = new DefaultLitePullConsumer(RocketMQConfig.getConsumerGroup());
            consumer.setNamesrvAddr(RocketMQConfig.getNameSrvAddr());
            // 订阅动态发布Topic（*表示所有tag）
            consumer.subscribe("POST_PUBLISH", "*");
            // 订阅视频发布Topic（视频发布也触发Feed推送）
            consumer.subscribe("VIDEO_PUBLISH", "*");
            // 启动消费者
            consumer.start();
            running = true;

            log.info("FeedPushConsumer启动成功，监听Topic: POST_PUBLISH, VIDEO_PUBLISH");

            // 循环拉取消息
            while (running) {
                try {
                    // poll()拉取一批消息（默认拉取32条）
                    List<MessageExt> messages = consumer.poll();
                    if (messages != null && !messages.isEmpty()) {
                        for (MessageExt msg : messages) {
                            try {
                                // 处理每条消息
                                handleMessage(msg);
                            } catch (Exception e) {
                                log.error("处理Feed推送消息失败: msgId={}, error={}", msg.getMsgId(), e.getMessage());
                                // 单条消息处理失败不影响其他消息
                            }
                        }
                        // 确认消费（ack）
                        consumer.commitSync();
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("FeedPushConsumer拉取消息异常: {}", e.getMessage());
                        // 短暂休眠后继续（防止异常循环）
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("FeedPushConsumer启动失败！请确认RocketMQ NameServer已启动: {}", e.getMessage());
            running = false;
        }
    }

    /**
     * 处理单条消息——展开推送到粉丝收件箱
     *
     * <p>消息体格式（JSON）：{"postUserId":1001,"postId":202}</p>
     *
     * <p>TraceId链路追踪：从消息属性中取出生产者传递的TraceId，
     * 设置到当前线程的MDC中，使消费者日志与HTTP请求日志关联。</p>
     *
     * @param msg RocketMQ消息
     */
    private void handleMessage(MessageExt msg) {
        // 1. 从消息属性中取出TraceId，恢复链路追踪上下文
        String parentTraceId = msg.getUserProperty("TRACE_ID");
        if (parentTraceId != null) {
            // 将TraceId设置到当前线程的ThreadLocal和MDC中
            TraceIdContext.setTraceId(parentTraceId);
            log.info("收到Feed推送消息: msgId={}, traceId={}", msg.getMsgId(), parentTraceId);
        } else {
            log.info("收到Feed推送消息: msgId={}", msg.getMsgId());
        }

        try {
            // 解析JSON消息体
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(msg.getBody());

            long postUserId = json.get("postUserId").asLong();
            long postId = json.get("postId").asLong();
            long timestamp = json.has("timestamp") ? json.get("timestamp").asLong()
                    : LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));

            log.info("开始推送Feed: postUserId={}, postId={}", postUserId, postId);

            // ==================== 推送到粉丝收件箱 ====================
            List<Follow> followers = followDao.listFollowers(postUserId, 0, 10000);
            int followerCount = followers.size();

            log.info("粉丝数: {} (userId={})", followerCount, postUserId);

            if (followerCount <= BIG_V_THRESHOLD) {
                // 普通用户：所有粉丝都Push
                for (Follow f : followers) {
                    rateLimiter.acquire(); // 限速：拿令牌，没令牌就阻塞等待
                    String inboxKey = FEED_INBOX_PREFIX + f.getFollowerId();
                    RedisUtil.zadd(inboxKey, timestamp, String.valueOf(postId));
                    // 修剪收件箱（保留前1000条）
                    RedisUtil.zremrangeByRank(inboxKey, 0, -(MAX_INBOX_SIZE + 1));
                }
                log.info("Push完成（普通用户模式）: postUserId={}, 推送{}人", postUserId, followerCount);
            } else {
                // 大V：只Push前1000个活跃粉丝，其他粉丝Pull
                int pushCount = 0;
                for (Follow f : followers) {
                    if (pushCount >= BIG_V_THRESHOLD) break;
                    rateLimiter.acquire(); // 限速：拿令牌，没令牌就阻塞等待
                    String inboxKey = FEED_INBOX_PREFIX + f.getFollowerId();
                    RedisUtil.zadd(inboxKey, timestamp, String.valueOf(postId));
                    RedisUtil.zremrangeByRank(inboxKey, 0, -(MAX_INBOX_SIZE + 1));
                    pushCount++;
                }

                // ==================== 写入大V发件箱 ====================
                // 未被Push的粉丝在Pull时从发件箱拉取
                String outboxKey = FEED_OUTBOX_PREFIX + postUserId;
                RedisUtil.zadd(outboxKey, timestamp, String.valueOf(postId));
                // 修剪发件箱（保留最近1000条）
                RedisUtil.zremrangeByRank(outboxKey, 0, -(MAX_INBOX_SIZE + 1));

                log.info("Push完成（大V模式）: postUserId={}, Push了{}人(共{}粉丝), 写入发件箱",
                        postUserId, pushCount, followerCount);
            }

        } catch (Exception e) {
            log.error("Feed推送消息处理失败: msgId={}, error={}", msg.getMsgId(), e.getMessage());
            throw new RuntimeException("Feed推送失败", e);
        } finally {
            // 2. 处理完毕后清除TraceId，防止线程复用导致混乱
            if (parentTraceId != null) {
                TraceIdContext.clearTraceId();
            }
        }
    }

    /**
     * 停止消费者
     *
     * <p>应用关闭时调用，graceful shutdown。</p>
     */
    public void shutdown() {
        running = false;
        if (consumer != null) {
            consumer.shutdown();
            log.info("FeedPushConsumer已停止");
        }
    }
}