package io.github.fantasticname.mybilibili.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fantasticname.mybilibili.config.RocketMQConfig;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.dao.NotificationDao;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.websocket.WebSocketServer;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 通知消费者（异步通知系统的核心组件）
 *
 * <p>各业务Service在完成操作后，发送MQ消息到社交事件Topic，
 * 本Consumer统一消费这些消息，执行"写DB + WebSocket推送"。</p>
 *
 * <p>监听的Topic：</p>
 * <ul>
 *   <li>USER_FOLLOW - 关注事件</li>
 *   <li>COMMENT_CREATE - 评论事件</li>
 *   <li>LIKE_TOGGLE - 点赞事件</li>
 * </ul>
 *
 * <p>消息体格式（JSON）：</p>
 * <pre>{"userId":接收者ID, "fromUserId":触发者ID, "notifyType":"FOLLOW", "targetId":123, "targetType":3, "content":"XXX关注了你"}</pre>
 *
 * <p>为什么用MQ异步通知？</p>
 * <ul>
 *   <li>解耦：业务Service不需要依赖NotificationService</li>
 *   <li>可靠：MQ保证消息不丢失，通知一定会发出</li>
 *   <li>异步：发通知不会阻塞业务主流程</li>
 * </ul>
 *
 * @author FantasticName
 */
public class NotificationConsumer {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    /**
     * JSON序列化/反序列化工具
     *
     * <p>ObjectMapper是Jackson库的核心类，用于将Java对象和JSON字符串互相转换。</p>
     * <p>这里声明为static final，因为ObjectMapper是线程安全的，全局只需要一个实例。</p>
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 单例实例
     *
     * <p>使用volatile关键字保证多线程环境下的可见性：
     * 当一个线程修改了INSTANCE引用时，其他线程能立即看到最新值。</p>
     */
    private static volatile NotificationConsumer INSTANCE;

    /**
     * 通知数据访问对象，用于将通知持久化到数据库
     *
     * <p>NotificationDao通过构造方法注入，而不是通过@Autowired自动注入，
     * 因为Consumer不是由IoC容器管理的Bean，而是手动创建的单例对象。</p>
     */
    private final NotificationDao notificationDao;

    /**
     * RocketMQ拉取模式消费者
     *
     * <p>DefaultLitePullConsumer 是RocketMQ提供的拉取模式消费者，
     * 与推送模式（DefaultMQPushConsumer）不同，拉取模式由消费者主动从Broker拉取消息，
     * 可以更灵活地控制消费速率。</p>
     */
    private DefaultLitePullConsumer consumer;

    /**
     * 消费者工作线程
     *
     * <p>使用守护线程（Daemon Thread）运行消费者循环，
     * 当JVM中只剩下守护线程时，JVM会自动退出，不会因为消费者线程而阻塞关闭。</p>
     */
    private Thread workerThread;

    /**
     * 运行标志位，控制消费者循环是否继续
     *
     * <p>使用volatile保证多线程可见性，当调用stop()时设为false，消费者循环会退出。</p>
     */
    private volatile boolean running = false;

    /**
     * 构造方法，注入NotificationDao依赖
     *
     * <p>为什么不用单例的无参构造？因为NotificationConsumer依赖NotificationDao，
     * 而NotificationDao是由IoC容器管理的Bean。所以需要在创建Consumer时，
     * 从容器中获取NotificationDao并传入。</p>
     *
     * @param notificationDao 通知数据访问对象，由调用方从IoC容器中获取后传入
     */
    public NotificationConsumer(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    /**
     * 获取单例实例（双重检查锁）
     *
     * <p>双重检查锁（Double-Checked Locking）是一种高效的单例实现方式：</p>
     * <ol>
     *   <li>第一次检查：如果实例已存在，直接返回，避免不必要的加锁</li>
     *   <li>加锁：确保只有一个线程能创建实例</li>
     *   <li>第二次检查：加锁后再检查一次，防止多个线程同时通过第一次检查</li>
     * </ol>
     *
     * <p>注意：此单例需要传入NotificationDao参数，因此首次调用时必须通过
     * getInstance(NotificationDao)创建实例，后续调用无参getInstance()即可。</p>
     *
     * @param notificationDao 通知数据访问对象
     * @return NotificationConsumer的单例实例
     */
    public static NotificationConsumer getInstance(NotificationDao notificationDao) {
        // 第一次检查：快速路径，实例已存在时直接返回
        if (INSTANCE == null) {
            // 加锁，保证只有一个线程能创建实例
            synchronized (NotificationConsumer.class) {
                // 第二次检查：防止多个线程同时通过第一次检查后重复创建
                if (INSTANCE == null) {
                    INSTANCE = new NotificationConsumer(notificationDao);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 获取已创建的单例实例
     *
     * <p>在实例已经创建过的情况下使用此方法，无需再传入NotificationDao。</p>
     *
     * @return NotificationConsumer的单例实例，如果未初始化则返回null
     */
    public static NotificationConsumer getInstance() {
        return INSTANCE;
    }

    /**
     * 启动消费者
     *
     * <p>启动流程：</p>
     * <ol>
     *   <li>创建DefaultLitePullConsumer实例，设置消费者组</li>
     *   <li>订阅USER_FOLLOW、COMMENT_CREATE、LIKE_TOGGLE三个Topic</li>
     *   <li>启动消费者连接RocketMQ Broker</li>
     *   <li>创建并启动守护线程，开始拉取消息循环</li>
     * </ol>
     */
    public void start() {
        try {
            // 1. 创建拉取模式消费者
            //    消费者组名从RocketMQConfig中获取，保证同一组内的消费者共同分担消息
            consumer = new DefaultLitePullConsumer(RocketMQConfig.NOTIFICATION_CONSUMER_GROUP);

            // 2. 设置NameServer地址
            //    NameServer是RocketMQ的路由注册中心，类似于注册中心
            //    消费者通过NameServer找到Topic对应的Broker地址
            consumer.setNamesrvAddr(RocketMQConfig.NAMESRV_ADDR);

            // 3. 订阅三个社交事件Topic
            //    每个Topic对应一种社交事件类型：
            //    - USER_FOLLOW：用户关注事件
            //    - COMMENT_CREATE：评论创建事件
            //    - LIKE_TOGGLE：点赞/取消点赞事件
            //    第二个参数 "*" 表示订阅该Topic下所有Tag的消息
            consumer.subscribe(RocketMQConfig.USER_FOLLOW_TOPIC, "*");
            consumer.subscribe(RocketMQConfig.COMMENT_CREATE_TOPIC, "*");
            consumer.subscribe(RocketMQConfig.LIKE_TOGGLE_TOPIC, "*");

            // 4. 启动消费者，建立与RocketMQ Broker的连接
            consumer.start();
            log.info("[NotificationConsumer] 消费者启动成功，已订阅Topic: {}, {}, {}",
                    RocketMQConfig.USER_FOLLOW_TOPIC,
                    RocketMQConfig.COMMENT_CREATE_TOPIC,
                    RocketMQConfig.LIKE_TOGGLE_TOPIC);

            // 5. 设置运行标志位为true
            running = true;

            // 6. 创建守护线程，执行消息拉取循环
            //    守护线程的特点：当所有非守护线程结束时，JVM会自动退出
            //    这样Web应用关闭时，消费者线程不会阻止JVM退出
            workerThread = new Thread(this::pollLoop, "notification-consumer");
            workerThread.setDaemon(true);
            workerThread.start();
            log.info("[NotificationConsumer] 工作线程已启动");

        } catch (Exception e) {
            // 启动失败，记录错误日志
            log.error("[NotificationConsumer] 消费者启动失败", e);
        }
    }

    /**
     * 消息拉取循环
     *
     * <p>这是消费者的核心方法，在一个无限循环中不断从RocketMQ拉取消息。</p>
     *
     * <p>拉取模式的工作原理：</p>
     * <ol>
     *   <li>调用 consumer.poll() 主动从Broker拉取一批消息</li>
     *   <li>遍历每条消息，调用 handleMessage() 处理</li>
     *   <li>处理完成后调用 consumer.commitSync() 提交消费进度</li>
     *   <li>如果拉取失败或处理异常，等待一小段时间后重试</li>
     * </ol>
     */
    private void pollLoop() {
        // 循环拉取消息，直到running被设为false
        while (running) {
            try {
                // 1. 从RocketMQ拉取一批消息
                //    poll()方法会阻塞等待，直到拉到消息或超时
                List<MessageExt> messages = consumer.poll();

                if (messages != null && !messages.isEmpty()) {
                    // 2. 遍历处理每条消息
                    for (MessageExt msg : messages) {
                        handleMessage(msg);
                    }

                    // 3. 所有消息处理完成后，同步提交消费进度（offset）
                    //    commitSync() 保证消费进度持久化到Broker
                    //    即使消费者重启，也不会重复消费已提交的消息
                    consumer.commitSync();
                }
            } catch (Exception e) {
                // 拉取或处理消息时发生异常，记录错误日志
                log.error("[NotificationConsumer] 消息拉取/处理异常", e);

                // 短暂休眠，避免异常时疯狂重试导致CPU飙升
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    // 线程被中断，退出循环
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 循环结束，关闭消费者
        if (consumer != null) {
            consumer.shutdown();
            log.info("[NotificationConsumer] 消费者已关闭");
        }
    }

    /**
     * 处理单条通知消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>从消息体中解析出JSON格式的通知数据</li>
     *   <li>设置TraceId，用于全链路追踪</li>
     *   <li>根据JSON数据构建Notification实体对象</li>
     *   <li>调用NotificationDao.insert()将通知持久化到数据库</li>
     *   <li>通过WebSocketServer向接收者实时推送通知</li>
     *   <li>清除TraceId，避免线程复用导致的TraceId污染</li>
     * </ol>
     *
     * @param msg RocketMQ消息对象
     */
    private void handleMessage(MessageExt msg) {
        // 1. 从消息体中解析JSON字符串
        //    消息体是字节数组，需要先转为字符串
        String json = new String(msg.getBody(), StandardCharsets.UTF_8);

        // 2. 设置TraceId，用于全链路追踪
        //    消息的keys属性中存储了生产者设置的TraceId
        //    在消费者线程中恢复TraceId，这样日志中就能串联起整条调用链
        String traceId = msg.getKeys();
        if (traceId != null && !traceId.isEmpty()) {
            TraceIdContext.setTraceId(traceId);
        } else {
            // 如果消息中没有TraceId，生成一个新的
            TraceIdContext.setTraceId();
        }

        try {
            // 3. 解析JSON数据
            //    使用Jackson的ObjectMapper将JSON字符串解析为JsonNode树
            //    JsonNode是Jackson提供的JSON节点对象，可以按字段名取值
            JsonNode node = MAPPER.readTree(json);

            // 3.1 从JSON中提取各字段值
            //     asLong() / asInt() / asText() 将JSON值转为Java类型
            //     如果字段不存在，会返回默认值（0 / null）
            long userId = node.path("userId").asLong();
            long fromUserId = node.path("fromUserId").asLong();
            String notifyType = node.path("notifyType").asText();
            long targetId = node.path("targetId").asLong();
            int targetType = node.path("targetType").asInt();
            String content = node.path("content").asText();

            // 3.2 获取消息来源的Topic，用于日志记录
            String topic = msg.getTopic();
            log.info("[NotificationConsumer] 收到通知消息, topic={}, userId={}, fromUserId={}, notifyType={}",
                    topic, userId, fromUserId, notifyType);

            // 4. 构建Notification实体对象
            //    Notification实体对应数据库中的notification表
            //    将JSON中的字段映射到实体属性
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setFromUserId(fromUserId);
            notification.setNotifyType(notifyType);
            notification.setTargetId(targetId);
            notification.setTargetType(targetType);
            notification.setContent(content);

            // 5. 将通知持久化到数据库
            //    调用NotificationDao的insert方法，将Notification对象写入DB
            //    持久化后通知就有了记录，用户即使不在线也能在下次登录时看到
            notificationDao.insert(notification);
            log.info("[NotificationConsumer] 通知已持久化到数据库, userId={}, notifyType={}", userId, notifyType);

            // 6. 通过WebSocket实时推送给接收者
            //    WebSocketServer.sendToUser() 会检查用户是否在线
            //    如果在线，就通过WebSocket连接推送通知JSON
            //    如果不在线，推送会静默失败（通知已持久化，用户下次登录能看到）
            WebSocketServer.sendToUser(userId, json);
            log.info("[NotificationConsumer] 通知已通过WebSocket推送, userId={}", userId);

        } catch (Exception e) {
            // 处理通知消息时发生异常，记录错误日志
            //    注意：这里不抛出异常，避免影响其他消息的处理
            //    MQ会在下次拉取时重新投递这条消息（因为还没commit）
            log.error("[NotificationConsumer] 处理通知消息异常, json={}", json, e);
        } finally {
            // 7. 清除TraceId
            //    消费者线程是长驻线程，处理完一条消息后必须清除TraceId
            //    否则下一条消息会沿用上一条的TraceId，导致追踪混乱
            TraceIdContext.clearTraceId();
        }
    }

    /**
     * 停止消费者
     *
     * <p>停止流程：</p>
     * <ol>
     *   <li>设置running标志位为false，通知拉取循环退出</li>
     *   <li>中断工作线程，唤醒可能在poll()中阻塞的线程</li>
     *   <li>等待工作线程结束（最多3秒）</li>
     * </ol>
     */
    public void stop() {
        // 1. 设置运行标志位为false
        running = false;

        // 2. 中断工作线程
        //    如果线程正在poll()中阻塞，interrupt()会使其抛出InterruptedException
        if (workerThread != null) {
            workerThread.interrupt();

            try {
                // 3. 等待工作线程结束，最多等3秒
                workerThread.join(3000);
            } catch (InterruptedException e) {
                // 等待被中断，记录日志
                log.warn("[NotificationConsumer] 等待工作线程结束时被中断");
                Thread.currentThread().interrupt();
            }
        }

        log.info("[NotificationConsumer] 消费者已停止");
    }
}
