package io.github.fantasticname.mybilibili.config;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocketMQ消息队列配置类
 *
 * <p>RocketMQ是阿里巴巴开源的分布式消息中间件，提供低延迟、高可靠的消息发布与订阅服务。
 * 本项目使用RocketMQ的核心场景：</p>
 * <ul>
 *   <li><b>事务消息（Transaction Message）</b>：保证"写数据库"和"发消息"的原子性，
 *       用于缓存一致性（先写DB，再发消息删缓存）</li>
 *   <li><b>异步解耦</b>：主流程完成后发消息，由消费者异步处理（如推送Feed、发通知、落库）</li>
 *   <li><b>削峰填谷</b>：秒杀场景中，Redis扣库存后发消息，消费者慢慢写DB</li>
 *   <li><b>可靠投递</b>：消息持久化到磁盘，服务重启不丢失；失败自动重试，最终进死信队列</li>
 * </ul>
 *
 * <p>Producer vs Consumer：</p>
 * <ul>
 *   <li>Producer（生产者）：业务代码调用，发消息到MQ</li>
 *   <li>Consumer（消费者）：独立的线程，监听MQ消息并处理</li>
 * </ul>
 *
 * <p>Topic设计（消息的分类标签）：</p>
 * <ul>
 *   <li>POST_PUBLISH - 动态发布 → FeedPushConsumer展开推送</li>
 *   <li>VIDEO_PUBLISH - 视频发布 → FeedPushConsumer展开推送</li>
 *   <li>COUPON_GRAB - 优惠券抢购 → CouponGrabConsumer异步落库</li>
 *   <li>CACHE_INVALIDATE - 缓存失效 → CacheInvalidateConsumer删除缓存</li>
 *   <li>USER_FOLLOW / COMMENT_CREATE / LIKE_TOGGLE - 社交事件 → NotificationConsumer发通知</li>
 * </ul>
 *
 * <p>本项目没有Spring，手动创建Producer和Consumer实例。在ContainerInitializerListener中启动Consumer线程。</p>
 *
 * @author FantasticName
 */
public class RocketMQConfig {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

    /**
     * RocketMQ NameServer地址（默认本地9876端口）
     */
    private static final String NAME_SRV_ADDR = "localhost:9876";

    /**
     * 普通消息生产者组名
     *
     * <p>注意：普通消息用DefaultMQProducer，事务消息用TransactionMQProducer。
     * ProducerGroup用于标识同一组的生产者，RocketMQ会根据组名做负载均衡和故障转移。</p>
     */
    private static final String PRODUCER_GROUP = "my-bilibili-producer-group";

    /**
     * 事务消息生产者组名
     *
     * <p>事务消息需要单独的Producer，因为需要注册TransactionListener
     * （用于执行本地事务和回查本地事务状态）。</p>
     */
    private static final String TRANS_PRODUCER_GROUP = "my-bilibili-trans-producer-group";

    /**
     * 普通消息生产者（单例）
     *
     * <p>volatile + DCL保证线程安全的单例。用于发送普通消息（不涉及事务）。</p>
     */
    private static volatile DefaultMQProducer producer;

    /**
     * 事务消息生产者（单例）
     *
     * <p>用于发送事务消息。事务消息的三步流程：</p>
     * <ol>
     *   <li>发半消息（Half Message）→ MQ回复"半消息已保存"</li>
     *   <li>执行本地事务（executeLocalTransaction）→ 成功则COMMIT，失败则ROLLBACK</li>
     *   <li>MQ回查（checkLocalTransaction）→ 半消息状态未知时查询本地事务结果</li>
     * </ol>
     */
    private static volatile TransactionMQProducer transProducer;

    /**
     * 启动LitePullConsumer（拉模式消费者）的线程
     *
     * <p>LitePullConsumer是RocketMQ 4.6+引入的轻量级拉模式消费者，
     * 相比PushConsumer更灵活——消费者自己控制拉取速度和时机。</p>
     */
    public static final String CONSUMER_GROUP = "my-bilibili-consumer-group";

    /**
     * NameServer地址公共常量（供MQ消费者直接引用）
     */
    public static final String NAMESRV_ADDR = NAME_SRV_ADDR;

    /**
     * 缓存失效消费者组名
     */
    public static final String CACHE_INVALIDATE_CONSUMER_GROUP = "my-bilibili-cache-invalidate-consumer-group";

    /**
     * 缓存失效Topic
     */
    public static final String CACHE_INVALIDATE_TOPIC = "CACHE_INVALIDATE";

    /**
     * 通知消费者组名
     */
    public static final String NOTIFICATION_CONSUMER_GROUP = "my-bilibili-notification-consumer-group";

    /**
     * 用户关注事件Topic
     */
    public static final String USER_FOLLOW_TOPIC = "USER_FOLLOW";

    /**
     * 评论创建事件Topic
     */
    public static final String COMMENT_CREATE_TOPIC = "COMMENT_CREATE";

    /**
     * 点赞切换事件Topic
     */
    public static final String LIKE_TOGGLE_TOPIC = "LIKE_TOGGLE";

    /**
     * 获取普通消息生产者实例（双重检查锁，线程安全）
     *
     * <p>DefaultMQProducer是RocketMQ提供的默认生产者实现，
     * 支持同步发送、异步发送、单向发送三种模式。</p>
     *
     * @return DefaultMQProducer实例
     * @throws RuntimeException 如果连接NameServer失败
     */
    public static DefaultMQProducer getProducer() {
        // 第1次检查（不加锁）
        if (producer == null) {
            // 第2次检查（加锁）
            synchronized (RocketMQConfig.class) {
                if (producer == null) {
                    log.info("========================================");
                    log.info("开始初始化RocketMQ Producer...");
                    log.info("NameServer地址: {}", NAME_SRV_ADDR);
                    log.info("========================================");

                    // 创建生产者实例，指定生产者组名
                    producer = new DefaultMQProducer(PRODUCER_GROUP);
                    // 设置NameServer地址（RocketMQ的路由中心）
                    producer.setNamesrvAddr(NAME_SRV_ADDR);
                    // 发送失败时的重试次数（默认2次）
                    producer.setRetryTimesWhenSendFailed(3);
                    // 发送超时时间（毫秒）
                    producer.setSendMsgTimeout(5000);

                    try {
                        // 启动生产者（连接NameServer，注册自己）
                        producer.start();
                        log.info("RocketMQ Producer启动成功: group={}", PRODUCER_GROUP);
                    } catch (MQClientException e) {
                        log.error("RocketMQ Producer启动失败！请确认NameServer已启动: {}", NAME_SRV_ADDR);
                        producer = null;
                        // 注意：这里不抛异常，因为项目在没有NameServer的情况下也能运行
                        // 只是消息队列功能不可用，降级为线程池异步模式
                    }

                    log.info("========================================");
                }
            }
        }
        return producer;
    }

    /**
     * 获取事务消息生产者实例
     *
     * <p>事务消息是RocketMQ的核心特性之一，用于解决分布式事务问题。
     * 在"先写DB，再发消息"的场景中，事务消息保证两者原子性。</p>
     *
     * <p>使用前必须先调用{@link #setTransactionListener(TransactionListener)}注册监听器，
     * 否则事务消息无法正常工作。</p>
     *
     * @param listener 事务监听器（包含executeLocalTransaction和checkLocalTransaction）
     * @return TransactionMQProducer实例
     */
    public static TransactionMQProducer getTransProducer(TransactionListener listener) {
        if (transProducer == null) {
            synchronized (RocketMQConfig.class) {
                if (transProducer == null) {
                    log.info("开始初始化RocketMQ事务消息Producer...");

                    transProducer = new TransactionMQProducer(TRANS_PRODUCER_GROUP);
                    transProducer.setNamesrvAddr(NAME_SRV_ADDR);
                    transProducer.setRetryTimesWhenSendFailed(3);
                    transProducer.setSendMsgTimeout(5000);

                    // 【关键】注册事务监听器
                    // TransactionListener有两个方法：
                    // 1. executeLocalTransaction: 执行本地事务（如写DB）
                    // 2. checkLocalTransaction: 本地事务状态未知时，回查（如查DB确认写入成功）
                    transProducer.setTransactionListener(listener);

                    try {
                        transProducer.start();
                        log.info("RocketMQ事务消息Producer启动成功: group={}", TRANS_PRODUCER_GROUP);
                    } catch (MQClientException e) {
                        log.error("RocketMQ事务消息Producer启动失败！", e);
                        transProducer = null;
                    }
                }
            }
        }
        return transProducer;
    }

    /**
     * 获取NameServer地址
     */
    public static String getNameSrvAddr() {
        return NAME_SRV_ADDR;
    }

    /**
     * 获取消费者组名
     */
    public static String getConsumerGroup() {
        return CONSUMER_GROUP;
    }

    /**
     * 关闭RocketMQ连接（应用关闭时调用）
     *
     * <p>优雅关闭，释放网络连接等资源。</p>
     */
    public static void shutdown() {
        log.info("正在关闭RocketMQ Producer...");
        if (producer != null) {
            producer.shutdown();
            producer = null;
        }
        if (transProducer != null) {
            transProducer.shutdown();
            transProducer = null;
        }
        log.info("RocketMQ已关闭");
    }
}