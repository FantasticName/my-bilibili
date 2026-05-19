package io.github.fantasticname.mybilibili.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Redis工具类，封装Redis的常用操作
 *
 * <p>使用Jedis连接池管理Redis连接，提供字符串类型的get/set/del/expire等操作。
 * 主要用于缓存用户登录状态和JWT Token。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>连接池管理：JedisPool统一管理Redis连接，避免频繁创建/销毁</li>
 *   <li>Token缓存：key为 jwttoken:{token}，value为User对象的JSON字符串</li>
 *   <li>TTL刷新：每次访问Token时刷新过期时间，实现"活跃用户自动续期"</li>
 * </ul>
 *
 * <p>配置从 env.properties 读取，包括Redis地址、端口、密码等。</p>
 *
 * @author FantasticName
 */
public class RedisUtil {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    /**
     * Jedis连接池，线程安全，全局只需一个实例
     */
    private static JedisPool jedisPool;

    /**
     * Token在Redis中的TTL（秒），默认30分钟
     */
    private static int tokenTtl = 1800;

    /**
     * Token在Redis中的key前缀
     */
    private static final String TOKEN_KEY_PREFIX = "jwttoken:";

    /**
     * Jackson ObjectMapper，用于对象与JSON之间的序列化/反序列化
     *
     * <p>注册了JavaTimeModule以支持LocalDateTime等Java8时间类型的序列化。
     * 这是全局唯一的ObjectMapper实例，线程安全。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 静态初始化块，在类加载时创建连接池
     *
     * <p>从env.properties读取Redis配置，创建JedisPool。</p>
     */
    static {
        Properties props = new Properties();
        try (InputStream is = RedisUtil.class.getClassLoader().getResourceAsStream("env.properties")) {
            if (is == null) {
                throw new RuntimeException("未找到环境变量配置文件: env.properties");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("读取环境变量配置文件失败", e);
        }

        // 优先从环境变量读取，回退到配置文件
        // 这样在生产环境可以通过环境变量注入敏感信息，避免明文存储在配置文件中
        String host = getEnvOrProp("REDIS_HOST", props, "redis.host", "localhost");
        int port = Integer.parseInt(getEnvOrProp("REDIS_PORT", props, "redis.port", "6379"));
        String password = getEnvOrProp("REDIS_PASSWORD", props, "redis.password", "");
        int database = Integer.parseInt(getEnvOrProp("REDIS_DATABASE", props, "redis.database", "0"));
        tokenTtl = Integer.parseInt(getEnvOrProp("JWT_TOKEN_TTL", props, "jwt.token.ttl", "1800"));

        // 配置连接池
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setMaxWaitMillis(3000);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        // 创建连接池
        if (password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, null, database);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database);
        }

        log.info("Redis连接池初始化成功: {}:{}", host, port);
    }

    /**
     * 设置字符串值（带TTL）
     *
     * @param key     Redis键
     * @param value   Redis值
     * @param ttlSeconds 过期时间（秒）
     */
    public static void set(String key, String value, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            // ttlSeconds=0 表示永不过期，用普通set；否则用setex带TTL
            if (ttlSeconds <= 0) {
                jedis.set(key, value);
                log.debug("Redis SET: key={}, ttl=永不过期", key);
            } else {
                jedis.setex(key, ttlSeconds, value);
                log.debug("Redis SET: key={}, ttl={}", key, ttlSeconds);
            }
        } catch (Exception e) {
            log.error("Redis SET失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 获取字符串值
     *
     * @param key Redis键
     * @return Redis值，不存在返回null
     */
    public static String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key);
            log.debug("Redis GET: key={}, found={}", key, value != null);
            return value;
        } catch (Exception e) {
            log.error("Redis GET失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 删除键
     *
     * <p>返回值为jedis.del的原始返回值：
     * 1=成功删除（key存在），0=key不存在（删除失败）。
     * 这个返回值对于幂等性校验非常重要——通过del的返回值判断是否为重复请求。</p>
     *
     * @param key Redis键
     * @return 1=成功删除，0=key不存在
     */
    public static long del(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.del(key);
            log.debug("Redis DEL: key={}, result={}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis DEL失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 刷新键的TTL（重新设置过期时间）
     *
     * @param key Redis键
     * @param ttlSeconds 新的过期时间（秒）
     * @return true表示刷新成功，false表示键不存在
     */
    public static boolean expire(String key, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean result = jedis.expire(key, ttlSeconds) == 1;
            log.debug("Redis EXPIRE: key={}, ttl={}, result={}", key, ttlSeconds, result);
            return result;
        } catch (Exception e) {
            log.error("Redis EXPIRE失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 检查键是否存在
     *
     * @param key Redis键
     * @return true表示存在，false表示不存在
     */
    public static boolean exists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean result = jedis.exists(key);
            log.debug("Redis EXISTS: key={}, result={}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis EXISTS失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 存储用户Token到Redis
     *
     * <p>key格式：jwttoken:{token}</p>
     * <p>value：User对象的JSON字符串</p>
     * <p>TTL：tokenTtl秒（默认30分钟）</p>
     *
     * @param token    JWT令牌
     * @param userJson User对象的JSON字符串
     */
    public static void saveToken(String token, String userJson) {
        String key = TOKEN_KEY_PREFIX + token;
        set(key, userJson, tokenTtl);
        log.info("Token已存入Redis: token前缀={}", token.substring(0, Math.min(10, token.length())));
    }

    /**
     * 从Redis获取用户Token对应的User信息
     *
     * @param token JWT令牌
     * @return User对象的JSON字符串，不存在返回null
     */
    public static String getUserByToken(String token) {
        String key = TOKEN_KEY_PREFIX + token;
        return get(key);
    }

    /**
     * 刷新Token的TTL（用户活跃时自动续期）
     *
     * @param token JWT令牌
     * @return true表示刷新成功，false表示Token已过期
     */
    public static boolean refreshTokenTtl(String token) {
        String key = TOKEN_KEY_PREFIX + token;
        return expire(key, tokenTtl);
    }

    /**
     * 从Redis删除用户Token（登出时使用）
     *
     * @param token JWT令牌
     */
    public static void removeToken(String token) {
        String key = TOKEN_KEY_PREFIX + token;
        del(key);
        log.info("Token已从Redis删除: token前缀={}", token.substring(0, Math.min(10, token.length())));
    }

    /**
     * 获取Token TTL配置值
     *
     * @return TTL秒数
     */
    public static int getTokenTtl() {
        return tokenTtl;
    }

    /**
     * 优先从环境变量获取配置值，如果环境变量不存在则回退到Properties文件，最后使用默认值
     *
     * <p>这种模式称为"环境变量优先"策略，是12-Factor App推荐的做法：
     * 敏感信息（如密码）通过环境变量注入，避免明文存储在配置文件中。</p>
     *
     * @param envKey       环境变量名
     * @param props        Properties配置对象
     * @param propKey      Properties中的键名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String getEnvOrProp(String envKey, Properties props, String propKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            log.debug("从环境变量读取配置: {}", envKey);
            return envValue;
        }
        return props.getProperty(propKey, defaultValue);
    }

    // ======================== 对象缓存方法 ========================

    /**
     * 将Java对象序列化为JSON字符串并存入Redis
     *
     * <p>使用Jackson的ObjectMapper将对象转换为JSON字符串。
     * 这实现了"对象级缓存"——不存二进制、不存单字段，而是将整个对象以JSON格式存入。
     * 反序列化时用{@link #getObject(String, Class)}取出并转回对象。</p>
     *
     * <p>使用场景：视频详情缓存、用户信息缓存、活动详情缓存</p>
     *
     * @param key       Redis键名
     * @param value     要缓存的Java对象
     * @param ttlSeconds 过期时间（秒）
     */
    public static void setObject(String key, Object value, long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 第1步：将Java对象序列化为JSON字符串
            String json = OBJECT_MAPPER.writeValueAsString(value);
            // 第2步：将JSON字符串存入Redis，设置TTL
            jedis.setex(key, ttlSeconds, json);
            log.debug("Redis SET OBJECT: key={}, ttl={}s", key, ttlSeconds);
        } catch (JsonProcessingException e) {
            log.error("对象序列化失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis对象缓存失败（序列化错误）", e);
        } catch (Exception e) {
            log.error("Redis SET OBJECT失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 从Redis获取JSON字符串并反序列化为Java对象
     *
     * <p>从Redis取出的是JSON字符串，通过Jackson反序列化为指定类型的Java对象。
     * 如果key不存在（redis返回null），此方法也返回null。</p>
     *
     * @param key   Redis键名
     * @param clazz 目标对象的Class类型
     * @param <T>   泛型类型
     * @return 反序列化后的对象，key不存在则返回null
     */
    public static <T> T getObject(String key, Class<T> clazz) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 第1步：从Redis取出JSON字符串
            String json = jedis.get(key);
            if (json == null) {
                log.debug("Redis GET OBJECT: key={}, 缓存未命中", key);
                return null;
            }
            // 第2步：将JSON字符串反序列化为Java对象
            T result = OBJECT_MAPPER.readValue(json, clazz);
            log.debug("Redis GET OBJECT: key={}, 缓存命中", key);
            return result;
        } catch (JsonProcessingException e) {
            log.error("对象反序列化失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis对象缓存失败（反序列化错误）", e);
        } catch (Exception e) {
            log.error("Redis GET OBJECT失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    // ======================== Sorted Set（有序集合）操作方法 ========================

    /**
     * 向Sorted Set中添加一个成员
     *
     * <p>Sorted Set（有序集合）是Redis的一种数据类型，每个成员关联一个score（分数），
     * 所有成员按score排序。适合"按时间排序的时间线/排行榜"等场景。</p>
     *
     * <p>在本项目中的典型用途：</p>
     * <ul>
     *   <li>Feed收件箱：score=发布时间戳，member=动态ID</li>
     *   <li>大V发件箱：score=发布时间戳，member=动态ID</li>
     * </ul>
     *
     * @param key    Redis键名
     * @param score  分数（如时间戳，越大越靠前）
     * @param member 成员值（如动态ID的字符串形式）
     * @return 添加成功的成员数（新成员返回1，已存在返回0）
     */
    public static long zadd(String key, double score, String member) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.zadd(key, score, member);
            log.debug("Redis ZADD: key={}, score={}, member={}", key, score, member);
            return result;
        } catch (Exception e) {
            log.error("Redis ZADD失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 从Sorted Set中按score倒序获取指定范围的成员
     *
     * <p>ZREVRANGEBYSCORE命令：按score从大到小排列，获取指定score区间内的成员。
     * 这是Feed流查询的核心操作——从收件箱中按时间倒序拉取动态ID列表。</p>
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>max="+inf"：最大score为无穷大（包含所有最新数据）</li>
     *   <li>min=cursor：最小score为游标值（上一页最后一条的时间戳）</li>
     *   <li>offset=0, count=limit：从第0条开始，取limit条</li>
     * </ul>
     *
     * @param key    Redis键名
     * @param max    最大score（"+inf"表示不限）
     * @param min    最小score（游标值，如"1700000000"）
     * @param offset 偏移量（通常为0）
     * @param count  取的数量（如20条）
     * @return score从大到小排列的成员列表（String形式）
     */
    public static List<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
        try (Jedis jedis = jedisPool.getResource()) {
            // NOTE: Jedis 4.x中zrevrangeByScore返回List<String>，不是Set<String>
            List<String> members = jedis.zrevrangeByScore(key, max, min, offset, count);
            log.debug("Redis ZREVRANGEBYSCORE: key={}, 返回{}条", key, members.size());
            return members;
        } catch (Exception e) {
            log.error("Redis ZREVRANGEBYSCORE失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 从Sorted Set中按score倒序获取指定范围的成员（简化版）
     *
     * <p>与上面的方法功能相同，但max固定为"+inf"（查询最新的数据），
     * 减少调用方传参复杂度。</p>
     *
     * @param key    Redis键名
     * @param min    最小score（游标值）
     * @param offset 偏移量
     * @param count  取的数量
     * @return 成员列表
     */
    public static List<String> zrevrangeByScore(String key, double min, int offset, int count) {
        try (Jedis jedis = jedisPool.getResource()) {
            // NOTE: Jedis 4.x中zrevrangeByScore返回List<String>，不是Set<String>
            List<String> members = jedis.zrevrangeByScore(key, Double.MAX_VALUE, min, offset, count);
            log.debug("Redis ZREVRANGEBYSCORE: key={}, 返回{}条", key, members.size());
            return members;
        } catch (Exception e) {
            log.error("Redis ZREVRANGEBYSCORE失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 从Sorted Set中按score正序获取指定范围的成员
     *
     * <p>ZRANGEBYSCORE命令：按score从小到大排列，获取指定区间内的成员。
     * 用于批量导出、离线分析等场景。</p>
     *
     * @param key    Redis键名
     * @param min    最小score
     * @param max    最大score
     * @param offset 偏移量
     * @param count  取的数量
     * @return 成员列表
     */
    public static List<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        try (Jedis jedis = jedisPool.getResource()) {
            // NOTE: Jedis 4.x中zrangeByScore返回List<String>，不是Set<String>
            List<String> members = jedis.zrangeByScore(key, min, max, offset, count);
            log.debug("Redis ZRANGEBYSCORE: key={}, 返回{}条", key, members.size());
            return members;
        } catch (Exception e) {
            log.error("Redis ZRANGEBYSCORE失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 删除Sorted Set中排名在指定范围内的成员
     *
     * <p>ZREMRANGEBYRANK命令：按排名（从低到高第0位开始）删除成员。
     * 用于控制收件箱大小——每个用户最多保留1000条Feed，超出部分删除。</p>
     *
     * @param key   Redis键名
     * @param start 起始排名（从0开始，-N表示倒数第N个）
     * @param stop  结束排名（-1表示最后一个）
     * @return 删除的成员数
     */
    public static long zremrangeByRank(String key, long start, long stop) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.zremrangeByRank(key, start, stop);
            log.debug("Redis ZREMRANGEBYRANK: key={}, 删除{}条", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis ZREMRANGEBYRANK失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 获取Sorted Set的成员数量
     *
     * @param key Redis键名
     * @return 成员数量
     */
    public static long zcard(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.zcard(key);
            log.debug("Redis ZCARD: key={}, count={}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis ZCARD失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 获取成员的score值
     *
     * <p>用于查询某个动态是否已经推送到用户的收件箱中。</p>
     *
     * @param key    Redis键名
     * @param member 成员值
     * @return score值，成员不存在返回null
     */
    public static Double zscore(String key, String member) {
        try (Jedis jedis = jedisPool.getResource()) {
            Double result = jedis.zscore(key, member);
            log.debug("Redis ZSCORE: key={}, member={}, score={}", key, member, result);
            return result;
        } catch (Exception e) {
            log.error("Redis ZSCORE失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    // ======================== Lua脚本执行方法 ========================

    /**
     * 执行Lua脚本（传入键和参数列表）
     *
     * <p>Lua脚本在Redis中原子执行，不会被其他命令打断。这保证了复杂操作
     * （如"先检查库存→再扣库存→最后加购买记录"）的原子性。</p>
     *
     * <p>在本项目中的典型用途：</p>
     * <ul>
     *   <li>优惠券秒杀：原子执行"检查库存+检查是否已抢+扣库存+加购买记录"</li>
     *   <li>库存对账：原子执行"检查Redis库存+DB库存+修正"</li>
     * </ul>
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>script：Lua脚本内容字符串</li>
     *   <li>keys：Redis键名列表，在Lua中通过KEYS[1]、KEYS[2]访问</li>
     *   <li>args：参数列表，在Lua中通过ARGV[1]、ARGV[2]访问</li>
     * </ul>
     *
     * @param script Lua脚本内容
     * @param keys   键名列表
     * @param args   参数列表
     * @return Lua脚本的返回值（Long/String/List等）
     */
    public static Object eval(String script, List<String> keys, List<String> args) {
        try (Jedis jedis = jedisPool.getResource()) {
            // jedis.eval(script, keyCount, ...keys, ...args)
            // keyCount告诉Redis前几个参数是键名
            Object result = jedis.eval(
                    script,
                    keys,
                    args
            );
            log.debug("Redis EVAL: keys={}, args={}, result={}", keys, args, result);
            return result;
        } catch (Exception e) {
            log.error("Redis EVAL失败: keys={}, error={}", keys, e.getMessage());
            throw new RuntimeException("Redis Lua脚本执行失败", e);
        }
    }

    /**
     * 执行Lua脚本（传入键和参数列表，当结果是Long类型时使用）
     *
     * @param script Lua脚本内容
     * @param keys   键名列表
     * @param args   参数列表
     * @return Lua脚本的返回值（Long类型）
     */
    public static Long evalLong(String script, List<String> keys, List<String> args) {
        Object result = eval(script, keys, args);
        if (result instanceof Long) {
            return (Long) result;
        }
        if (result instanceof Integer) {
            return ((Integer) result).longValue();
        }
        return null;
    }

    // ======================== List（列表）操作方法 ========================

    /**
     * 向List的头部添加元素（左插入）
     *
     * <p>LPUSH命令：将元素插入列表的左侧（头部）。
     * 配合{@link #lrange(String, long, long)}从右侧读取，可以实现队列。</p>
     *
     * @param key    Redis键名
     * @param values 要添加的值列表
     * @return 插入后列表的长度
     */
    public static long lpush(String key, String... values) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.lpush(key, values);
            log.debug("Redis LPUSH: key={}, 插入后长度={}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis LPUSH失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 获取List中指定范围的元素
     *
     * <p>LRANGE命令：start=0表示第一个元素，end=-1表示最后一个元素。
     * 用于读取推荐池、消息队列等List类型数据。</p>
     *
     * @param key   Redis键名
     * @param start 起始索引（从0开始）
     * @param stop  结束索引（-1表示到末尾）
     * @return 元素列表
     */
    public static List<String> lrange(String key, long start, long stop) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> result = jedis.lrange(key, start, stop);
            log.debug("Redis LRANGE: key={}, 返回{}条", key, result.size());
            return result;
        } catch (Exception e) {
            log.error("Redis LRANGE失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    // ======================== Set（集合）操作方法 ========================

    /**
     * 向Set中添加元素
     *
     * <p>SADD命令：Set中的元素是无序且唯一的。
     * 在本项目中用于存储"已抢购用户集合"、"已点赞用户集合"等。</p>
     *
     * @param key     Redis键名
     * @param members 要添加的成员
     * @return 添加成功的成员数
     */
    public static long sadd(String key, String... members) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.sadd(key, members);
            log.debug("Redis SADD: key={}, 新增{}个", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis SADD失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 判断一个成员是否在Set中
     *
     * <p>SISMEMBER命令：O(1)时间复杂度。
     * 在本项目中用于判断"用户是否已抢过"、"用户是否已点赞"等。</p>
     *
     * @param key    Redis键名
     * @param member 成员值
     * @return true=存在，false=不存在
     */
    public static boolean sismember(String key, String member) {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean result = jedis.sismember(key, member);
            log.debug("Redis SISMEMBER: key={}, member={}, result={}", key, member, result);
            return result;
        } catch (Exception e) {
            log.error("Redis SISMEMBER失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 获取Set中的所有成员
     *
     * @param key Redis键名
     * @return 成员集合
     */
    public static Set<String> smembers(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> result = jedis.smembers(key);
            log.debug("Redis SMEMBERS: key={}, 共{}个", key, result.size());
            return result;
        } catch (Exception e) {
            log.error("Redis SMEMBERS失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 从Set中移除指定成员
     *
     * @param key     Redis键名
     * @param members 要移除的成员
     * @return 移除的成员数
     */
    public static long srem(String key, String... members) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.srem(key, members);
            log.debug("Redis SREM: key={}, 移除{}个", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis SREM失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    // ======================== 数值操作方法 ========================

    /**
     * 将key的值自增1（原子操作）
     *
     * <p>INCR命令：如果key不存在，会先将值初始化为0再执行INCR。
     * 这是一个原子操作，不会被其他命令打断。</p>
     *
     * @param key Redis键名
     * @return 自增后的值
     */
    public static long incr(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.incr(key);
            log.debug("Redis INCR: key={}, result={}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis INCR失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 将key的值自减1（原子操作）
     *
     * @param key Redis键名
     * @return 自减后的值
     */
    public static long decr(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.decr(key);
            log.debug("Redis DECR: key={}, result={}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis DECR失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 设置key的过期时间
     *
     * @param key        Redis键名
     * @param ttlSeconds 过期时间（秒）
     * @return 1=成功设置，0=key不存在
     */
    public static long expireKey(String key, long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.expire(key, (int) ttlSeconds);
            log.debug("Redis ExpireKey: key={}, ttl={}s, result={}", key, ttlSeconds, result);
            return result;
        } catch (Exception e) {
            log.error("Redis ExpireKey失败: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 获取所有匹配指定模式的key
     *
     * <p>KEYS命令会扫描整个Redis库，生产环境慎用（可能阻塞Redis）。
     * 本项目数据库小，可以放心使用。</p>
     *
     * @param pattern 匹配模式（如 "feed:*" 匹配所有feed相关的key）
     * @return 匹配的key集合
     */
    public static Set<String> keys(String pattern) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> result = jedis.keys(pattern);
            log.debug("Redis KEYS: pattern={}, 匹配{}个", pattern, result.size());
            return result;
        } catch (Exception e) {
            log.error("Redis KEYS失败: pattern={}, error={}", pattern, e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }
}
