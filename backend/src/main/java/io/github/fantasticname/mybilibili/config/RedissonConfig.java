package io.github.fantasticname.mybilibili.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Redisson客户端配置类
 *
 * <p>Redisson是一个基于Redis的Java驻留内存数据网格（In-Memory Data Grid），
 * 提供了分布式锁（RLock）、分布式集合、分布式对象等高级功能。</p>
 *
 * <p>本项目使用Redisson的核心场景：</p>
 * <ul>
 *   <li><b>分布式锁（RLock）</b>：缓存击穿防护时，多个线程同时查同一个key，
 *       只有拿到锁的线程去查数据库，其他线程等待或重试</li>
 *   <li><b>看门狗（Watch Dog）</b>：Redisson自动为锁续期，防止业务执行时间过长导致锁提前释放</li>
 *   <li><b>可重入锁</b>：同一个线程可以多次获取同一把锁，不会死锁</li>
 * </ul>
 *
 * <p>手动创建RedissonClient单例（本项目没有Spring，不使用@Bean机制）：</p>
 * <ul>
 *   <li>使用双重检查锁（DCL）保证单例的线程安全</li>
 *   <li>从env.properties读取Redis连接信息</li>
 *   <li>配置单节点模式（SingleServer），未来可改为集群/哨兵模式</li>
 * </ul>
 *
 * <p>配置读取策略（环境变量优先）：</p>
 * <ul>
 *   <li>先读环境变量 REDIS_HOST/REDIS_PORT/REDIS_PASSWORD</li>
 *   <li>环境变量不存在时回退到 env.properties 配置文件</li>
 *   <li>再不存在则使用默认值 localhost:6379</li>
 * </ul>
 *
 * @author FantasticName
 */
public class RedissonConfig {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    /**
     * 【核心】RedissonClient单例
     *
     * <p>volatile关键字保证多线程之间的可见性：
     * 当一个线程修改了redissonClient的值，其他线程能立即看到最新值。</p>
     *
     * <p>为什么用volatile？
     * 双重检查锁（DCL）中，如果没有volatile，可能出现"半初始化对象"问题——
     * 线程A在synchronized块内创建对象（分配内存→初始化→赋值引用），
     * 线程B在第一个if判断时看到引用非空但对象尚未完全初始化。</p>
     */
    private static volatile RedissonClient redissonClient;

    /**
     * Redis连接信息（缓存配置，避免重复读取文件）
     */
    private static String redisHost;
    private static int redisPort;
    private static String redisPassword;

    static {
        // 从配置文件读取Redis连接信息
        Properties props = new Properties();
        try (InputStream is = RedissonConfig.class.getClassLoader().getResourceAsStream("env.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            log.warn("读取env.properties失败，使用默认配置", e);
        }

        // 优先从环境变量读取，回退到配置文件，最后使用默认值
        redisHost = getConfig("REDIS_HOST", props, "redis.host", "localhost");
        redisPort = Integer.parseInt(getConfig("REDIS_PORT", props, "redis.port", "6379"));
        redisPassword = getConfig("REDIS_PASSWORD", props, "redis.password", "LiuRRredis1224");
    }

    /**
     * 获取RedissonClient单例（双重检查锁，线程安全）
     *
     * <p>双重检查锁（Double-Checked Locking）工作原理：</p>
     * <ol>
     *   <li>第一次检查（不加锁）：如果实例已存在，直接返回，避免每次都加锁</li>
     *   <li>加锁（synchronized）：保证同一时刻只有一个线程进入创建代码块</li>
     *   <li>第二次检查（加锁内）：防止两个线程同时通过第一次检查后，
     *       第一个线程创建了实例，第二个线程进入后再次创建</li>
     * </ol>
     *
     * @return RedissonClient实例
     */
    public static RedissonClient getRedissonClient() {
        // 第1次检查（不加锁）：高频调用时的快速路径
        if (redissonClient == null) {
            // 第2次检查（加锁）：并发创建时的安全路径
            synchronized (RedissonConfig.class) {
                if (redissonClient == null) {
                    log.info("========================================");
                    log.info("开始初始化Redisson客户端...");
                    log.info("Redis地址: {}:{}", redisHost, redisPort);
                    log.info("========================================");

                    // 创建Redisson配置
                    Config config = new Config();

                    // 配置单节点模式（SingleServer）
                    // 本项目是课设项目，单机部署，使用单节点模式即可
                    // 生产环境可以改为集群模式：config.useClusterServers()
                    String address = "redis://" + redisHost + ":" + redisPort;
                    config.useSingleServer()
                            .setAddress(address)
                            .setPassword(redisPassword)
                            .setConnectionPoolSize(32)        // 连接池大小
                            .setConnectionMinimumIdleSize(8);  // 最小空闲连接数

                    // 创建Redisson客户端
                    redissonClient = Redisson.create(config);

                    log.info("Redisson客户端初始化成功");
                    log.info("========================================");
                }
            }
        }
        return redissonClient;
    }

    /**
     * 关闭Redisson客户端（应用关闭时调用）
     *
     * <p>释放连接池资源，优雅关闭。</p>
     */
    public static void shutdown() {
        if (redissonClient != null) {
            log.info("正在关闭Redisson客户端...");
            redissonClient.shutdown();
            redissonClient = null;
            log.info("Redisson客户端已关闭");
        }
    }

    /**
     * 从环境变量或配置文件获取配置值
     *
     * @param envKey       环境变量名
     * @param props        配置对象
     * @param propKey      配置文件键名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String getConfig(String envKey, Properties props, String propKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            log.debug("从环境变量读取配置: {}", envKey);
            return envValue;
        }
        return props.getProperty(propKey, defaultValue);
    }
}