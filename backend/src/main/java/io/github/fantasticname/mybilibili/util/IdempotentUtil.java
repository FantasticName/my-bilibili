package io.github.fantasticname.mybilibili.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/**
 * 幂等性校验工具类（防止重复提交）
 *
 * <p>幂等性（Idempotent）是指：同一个操作执行一次和执行多次产生的结果完全相同。
 * 比如扣款100元，扣了两次就变成200元了，这是不幂等的。</p>
 *
 * <p>典型的不幂等场景：</p>
 * <ul>
 *   <li>用户狂点"提交下单"按钮 → 创建了两个订单</li>
 *   <li>网络超时，微信回调重试了3次 → 用户余额被扣了3次</li>
 *   <li>用户F5刷新支付结果页 → 重复发起支付请求</li>
 * </ul>
 *
 * <p>本工具类使用<b>Token机制</b>实现幂等性：</p>
 * <ol>
 *   <li>进入表单页时，前端调用 /api/idempotent/token 获取一个唯一的Token</li>
 *   <li>Token存入Redis，同时返回给前端</li>
 *   <li>提交表单时，前端在Header中带上 X-Idempotent-Token</li>
 *   <li>后端用"从Redis中删除Token并判断删除结果"的方式做校验：
 *     <ul>
 *       <li>删除成功 → 本次是第一次请求，执行正常业务流程</li>
 *       <li>删除失败 → Token已被删除过，是重复请求，直接返回"请勿重复操作"</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>为什么用"删除+判断"而不是"查询+删除"？</p>
 * <ul>
 *   <li>"查询+删除"是两步操作，中间可以被其他线程插入 → 不是原子操作</li>
 *   <li>"删除+判断"是Redis的SET NX变种实现，Redis的del命令是原子的</li>
 *   <li>del命令返回1=成功删除，返回0=key不存在（已被删过）=重复请求</li>
 * </ul>
 *
 * <p>Token的Redis Key设计：
 * idempotent:token:{UUID} → "1"，TTL=5分钟（超时自动失效）</p>
 *
 * @author FantasticName
 */
public class IdempotentUtil {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(IdempotentUtil.class);

    /**
     * Redis中幂等性Token的Key前缀
     */
    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:token:";

    /**
     * Token的过期时间（秒）
     *
     * <p>5分钟后自动失效，防止Redis中堆积大量废弃Token。
     * 同时限制了用户必须在5分钟内提交，减少Token泄露风险。</p>
     */
    private static final int TOKEN_TTL_SECONDS = 300; // 5分钟（int类型，匹配RedisUtil.set的TTL参数）

    /**
     * 生成并存储一个幂等性Token
     *
     * <p>由Controller接口调用，返回给前端。
     * Token使用UUID生成，保证全局唯一。</p>
     *
     * <p>UUID（Universally Unique Identifier）的标准形式：
     * 550e8400-e29b-41d4-a716-446655440000
     * 全局唯一，碰撞概率极低（2^122分之一），可以安全使用。</p>
     *
     * @return 生成的Token字符串
     */
    public static String generateToken() {
        // 生成UUID作为Token
        String token = UUID.randomUUID().toString();
        // 存入Redis，值为"1"，设置TTL
        RedisUtil.set(IDEMPOTENT_KEY_PREFIX + token, "1", TOKEN_TTL_SECONDS);
        log.info("幂等性Token已生成: token={}", token.substring(0, 8) + "...");
        return token;
    }

    /**
     * 校验并消费Token（原子操作）
     *
     * <p>这是幂等性校验的核心方法。原理：
     * 用Redis的del命令删除Token，根据返回值判断是否为重复请求。</p>
     *
     * <p>del命令的返回值：
     * 1 → 成功删除（Token存在，本次是第一次请求，合法）
     * 0 → key不存在（Token已被之前的请求消费了，这是重复请求）</p>
     *
     * @param token 待校验的Token（从请求Header中获取）
     * @return true=Token有效（首次请求），false=Token无效（重复请求或Token已过期）
     */
    public static boolean consumeToken(String token) {
        if (token == null || token.isEmpty()) {
            log.warn("幂等性校验: Token为空");
            return false;
        }

        // 拼接Redis Key
        String redisKey = IDEMPOTENT_KEY_PREFIX + token;

        // 【核心】使用Redis的del命令原子删除并判断
        // del返回1=成功删除（首次请求），返回0=key不存在（重复请求或已过期）
        long result = RedisUtil.del(redisKey);

        if (result == 1) {
            // Token存在且成功删除 → 首次请求，合法
            log.info("幂等性校验通过: token={}...", token.substring(0, Math.min(8, token.length())));
            return true;
        } else {
            // Token不存在 → 重复请求或已过期
            log.warn("幂等性校验失败：重复请求或Token已过期! token={}...",
                    token.substring(0, Math.min(8, token.length())));
            return false;
        }
    }

    /**
     * 获取Token的完整Redis Key（供单元测试使用）
     */
    public static String getTokenKey(String token) {
        return IDEMPOTENT_KEY_PREFIX + token;
    }
}