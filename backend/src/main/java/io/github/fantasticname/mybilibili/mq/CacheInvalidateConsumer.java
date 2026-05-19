package io.github.fantasticname.mybilibili.mq;

import io.github.fantasticname.mybilibili.config.RocketMQConfig;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 缓存失效消费者（缓存一致性方案的核心组件）
 *
 * <p>当业务数据被修改（更新/删除）后，需要删除对应的Redis缓存，
 * 保证下次查询时从数据库加载最新数据（Cache Aside模式）。</p>
 *
 * <p>为什么用MQ异步删缓存而不是同步删？</p>
 * <ul>
 *   <li>解耦：业务Service只负责写DB+发消息，不关心删缓存是否成功</li>
 *   <li>可靠：MQ自动重试，删缓存失败不会丢失，最终会成功</li>
 *   <li>削峰：高并发写时，删缓存请求被MQ缓冲，不会打爆Redis</li>
 * </ul>
 *
 * <p>消息体格式：缓存Key字符串（如 "video:detail:123" 或 "user:profile:456"）</p>
 *
 * <p>删缓存是幂等操作：删1次和删10次效果一样（key不存在时del返回0，不会报错）</p>
 *
 * @author FantasticName
 */
public class CacheInvalidateConsumer {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidateConsumer.class);

    /**
     * 单例实例
     *
     * <p>使用volatile关键字保证多线程环境下的可见性：
     * 当一个线程修改了INSTANCE引用时，其他线程能立即看到最新值。</p>
     */
    private static volatile CacheInvalidateConsumer INSTANCE;

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
     * 私有构造方法，防止外部直接new
     *
     * <p>单例模式的核心：把构造方法设为private，
     * 外部只能通过 getInstance() 获取唯一实例。</p>
     */
    private CacheInvalidateConsumer() {
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
     * @return CacheInvalidateConsumer的单例实例
     */
    public static CacheInvalidateConsumer getInstance() {
        // 第一次检查：快速路径，实例已存在时直接返回
        if (INSTANCE == null) {
            // 加锁，保证只有一个线程能创建实例
            synchronized (CacheInvalidateConsumer.class) {
                // 第二次检查：防止多个线程同时通过第一次检查后重复创建
                if (INSTANCE == null) {
                    INSTANCE = new CacheInvalidateConsumer();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 启动消费者
     *
     * <p>启动流程：</p>
     * <ol>
     *   <li>创建DefaultLitePullConsumer实例，设置消费者组</li>
     *   <li>订阅CACHE_INVALIDATE Topic</li>
     *   <li>启动消费者连接RocketMQ Broker</li>
     *   <li>创建并启动守护线程，开始拉取消息循环</li>
     * </ol>
     */
    public void start() {
        try {
            // 1. 创建拉取模式消费者
            //    消费者组名从RocketMQConfig中获取，保证同一组内的消费者共同分担消息
            consumer = new DefaultLitePullConsumer(RocketMQConfig.CACHE_INVALIDATE_CONSUMER_GROUP);

            // 2. 设置NameServer地址
            //    NameServer是RocketMQ的路由注册中心，类似于注册中心
            //    消费者通过NameServer找到Topic对应的Broker地址
            consumer.setNamesrvAddr(RocketMQConfig.NAMESRV_ADDR);

            // 3. 订阅CACHE_INVALIDATE Topic
            //    第二个参数 "*" 表示订阅该Topic下所有Tag的消息
            consumer.subscribe(RocketMQConfig.CACHE_INVALIDATE_TOPIC, "*");

            // 4. 启动消费者，建立与RocketMQ Broker的连接
            consumer.start();
            log.info("[CacheInvalidateConsumer] 消费者启动成功，已订阅Topic: {}", RocketMQConfig.CACHE_INVALIDATE_TOPIC);

            // 5. 设置运行标志位为true
            running = true;

            // 6. 创建守护线程，执行消息拉取循环
            //    守护线程的特点：当所有非守护线程结束时，JVM会自动退出
            //    这样Web应用关闭时，消费者线程不会阻止JVM退出
            workerThread = new Thread(this::pollLoop, "cache-invalidate-consumer");
            workerThread.setDaemon(true);
            workerThread.start();
            log.info("[CacheInvalidateConsumer] 工作线程已启动");

        } catch (Exception e) {
            // 启动失败，记录错误日志
            log.error("[CacheInvalidateConsumer] 消费者启动失败", e);
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
                log.error("[CacheInvalidateConsumer] 消息拉取/处理异常", e);

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
            log.info("[CacheInvalidateConsumer] 消费者已关闭");
        }
    }

    /**
     * 处理单条缓存失效消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>从消息体中解析出缓存Key</li>
     *   <li>设置TraceId，用于全链路追踪</li>
     *   <li>调用RedisUtil.del()删除对应的Redis缓存Key</li>
     *   <li>记录删除结果日志</li>
     *   <li>清除TraceId，避免线程复用导致的TraceId污染</li>
     * </ol>
     *
     * @param msg RocketMQ消息对象
     */
    private void handleMessage(MessageExt msg) {
        // 1. 从消息体中解析缓存Key
        //    消息体是字节数组，需要转为字符串
        String cacheKey = new String(msg.getBody(), StandardCharsets.UTF_8);

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
            // 3. 调用RedisUtil.del()删除对应的Redis缓存Key
            //    删缓存是幂等操作：key不存在时del返回0，不会报错
            //    所以即使重复消费也不会有问题
            //    注意：RedisUtil.del()返回long类型（被删除的key数量），1表示成功删除，0表示key不存在
            long deleted = RedisUtil.del(cacheKey);

            // 4. 记录删除结果日志
            if (deleted > 0) {
                log.info("[CacheInvalidateConsumer] 缓存删除成功, cacheKey={}", cacheKey);
            } else {
                // key不存在，del返回false，这也是正常情况
                // 比如缓存已经过期了，或者还没来得及写入
                log.warn("[CacheInvalidateConsumer] 缓存Key不存在或删除失败, cacheKey={}", cacheKey);
            }

        } catch (Exception e) {
            // 删缓存异常，记录错误日志
            //    注意：这里不抛出异常，避免影响其他消息的处理
            //    MQ会在下次拉取时重新投递这条消息（因为还没commit）
            log.error("[CacheInvalidateConsumer] 缓存删除异常, cacheKey={}", cacheKey, e);
        } finally {
            // 5. 清除TraceId
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
                log.warn("[CacheInvalidateConsumer] 等待工作线程结束时被中断");
                Thread.currentThread().interrupt();
            }
        }

        log.info("[CacheInvalidateConsumer] 消费者已停止");
    }
}
