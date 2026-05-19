package io.github.fantasticname.mybilibili.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fantasticname.mybilibili.config.RocketMQConfig;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.dao.CouponActivityDao;
import io.github.fantasticname.mybilibili.dao.CouponRecordDao;
import io.github.fantasticname.mybilibili.dao.NotificationDao;
import io.github.fantasticname.mybilibili.entity.CouponRecord;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.websocket.WebSocketServer;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * 优惠券抢购异步落库消费者（削峰填谷）
 *
 * <p>秒杀的高QPS（如500QPS）冲击DB，因此抢购成功后不直接写DB，
 * 而是发送RocketMQ消息，由本Consumer慢慢写入。</p>
 *
 * <p>为什么要异步落库？</p>
 * <ul>
 *   <li>Redis的QPS上限约10万/秒，可以轻松支撑秒杀并发</li>
 *   <li>但MySQL的QPS上限约3000/秒，如果同步写库，抢购响应变慢且DB压力大</li>
 *   <li>通过MQ将"扣库存+写DB"异步化，抢购响应快速返回，落库由Consumer慢消</li>
 * </ul>
 *
 * <p>Consumer做的事情：</p>
 * <ol>
 *   <li>从RocketMQ拉取COUPON_GRAB Topic的消息</li>
 *   <li>解析消息：{"userId":1001,"activityId":1}</li>
 *   <li>插入一条coupon_record记录</li>
 *   <li>扣减coupon_activity的remain_stock</li>
 * </ol>
 *
 * <p>Consumer运行方式：</p>
 * <ul>
 *   <li>独立Daemon线程，在ContainerInitializerListener中启动</li>
 *   <li>如果NameServer不可用，启动失败但降级逻辑保证数据不丢</li>
 * </ul>
 *
 * @author FantasticName
 */
public class CouponGrabConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponGrabConsumer.class);

    /**
     * 单例实例
     */
    private static volatile CouponGrabConsumer instance;

    /**
     * 消费者是否正在运行
     */
    private volatile boolean running = false;

    /**
     * LitePullConsumer
     */
    private DefaultLitePullConsumer consumer;

    /**
     * DAO依赖（通过构造器注入）
     */
    private final CouponRecordDao couponRecordDao;
    private final CouponActivityDao couponActivityDao;
    private final NotificationDao notificationDao;

    /**
     * JSON解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 私有构造器
     */
    private CouponGrabConsumer(CouponRecordDao couponRecordDao, CouponActivityDao couponActivityDao, NotificationDao notificationDao) {
        this.couponRecordDao = couponRecordDao;
        this.couponActivityDao = couponActivityDao;
        this.notificationDao = notificationDao;
    }

    /**
     * 获取单例
     */
    public static CouponGrabConsumer getInstance(CouponRecordDao couponRecordDao, CouponActivityDao couponActivityDao, NotificationDao notificationDao) {
        if (instance == null) {
            synchronized (CouponGrabConsumer.class) {
                if (instance == null) {
                    instance = new CouponGrabConsumer(couponRecordDao, couponActivityDao, notificationDao);
                }
            }
        }
        return instance;
    }

    /**
     * 启动消费者
     */
    public void start() {
        try {
            consumer = new DefaultLitePullConsumer(RocketMQConfig.getConsumerGroup());
            consumer.setNamesrvAddr(RocketMQConfig.getNameSrvAddr());
            // 订阅优惠券抢购Topic
            consumer.subscribe("COUPON_GRAB", "*");
            consumer.start();
            running = true;

            log.info("CouponGrabConsumer启动成功，监听Topic: COUPON_GRAB");

            // 循环拉取消息
            while (running) {
                try {
                    List<MessageExt> messages = consumer.poll();
                    if (messages != null && !messages.isEmpty()) {
                        for (MessageExt msg : messages) {
                            handleMessage(msg);
                        }
                        consumer.commitSync();
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("CouponGrabConsumer拉取消息异常: {}", e.getMessage());
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
            log.error("CouponGrabConsumer启动失败！: {}", e.getMessage());
            running = false;
        }
    }

    /**
     * 处理单条消息——写入抢购记录并扣减库存
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
            log.info("收到抢购落库消息: msgId={}, traceId={}", msg.getMsgId(), parentTraceId);
        } else {
            log.info("收到抢购落库消息: msgId={}", msg.getMsgId());
        }

        String body = new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8);

        try {
            com.fasterxml.jackson.databind.JsonNode json = MAPPER.readTree(body);
            long userId = json.get("userId").asLong();
            long activityId = json.get("activityId").asLong();

            // ===== 异步落库 =====
            // 1. 插入抢购记录
            CouponRecord record = new CouponRecord();
            record.setUserId(userId);
            record.setActivityId(activityId);
            // 生成优惠券码（UUID格式，去掉横线，取前16位）
            record.setCouponCode(generateCouponCode());
            couponRecordDao.insert(record);

            // 2. 扣减DB库存
            int affected = couponActivityDao.decrementStock(activityId, 1);
            log.info("抢购记录落库成功: userId={}, activityId={}, 优惠券码={}, 扣库存结果={}",
                    userId, activityId, record.getCouponCode(), affected);

            // 3. 发送抢购成功通知
            try {
                Notification notification = new Notification();
                notification.setUserId(userId);
                notification.setFromUserId(0L);
                notification.setNotifyType("SYSTEM");
                notification.setTargetId(activityId);
                notification.setTargetType(1);
                notification.setContent("恭喜！你成功抢到了优惠券，优惠券码：" + record.getCouponCode());
                notification.setIsRead(0);
                notification.setStatus(0);
                notificationDao.insert(notification);
                WebSocketServer.sendToUser(userId,
                    "{\"type\":\"COUPON_GRAB\",\"content\":\"恭喜！你成功抢到了优惠券\"}");
            } catch (Exception ex) {
                log.warn("抢购通知发送失败: userId={}, activityId={}", userId, activityId);
            }

        } catch (Exception e) {
            log.error("抢购落库消息处理失败: msgId={}, error={}", msg.getMsgId(), e.getMessage());
            // TODO: 失败时写入死信表，后续人工处理
        } finally {
            // 3. 处理完毕后清除TraceId，防止线程复用导致混乱
            if (parentTraceId != null) {
                TraceIdContext.clearTraceId();
            }
        }
    }

    /**
     * 停止消费者
     */
    public void shutdown() {
        running = false;
        if (consumer != null) {
            consumer.shutdown();
            log.info("CouponGrabConsumer已停止");
        }
    }

    /**
     * 生成优惠券码（UUID格式，去掉横线，取前16位）
     *
     * @return 优惠券码字符串
     */
    private String generateCouponCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}