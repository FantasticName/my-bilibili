package io.github.fantasticname.mybilibili.util;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.github.fantasticname.mybilibili.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Sentinel工具类，封装Sentinel的SphU.entry()模板代码
 *
 * <p>Sentinel的使用需要固定的模板代码：Entry entry = null; try { entry = SphU.entry(...); ... }
 * catch (BlockException) { ... } finally { if (entry != null) entry.exit(); }。
 * 如果每个Controller方法都写一遍，代码会非常冗余。</p>
 *
 * <p>本工具类使用<b>函数式编程（Lambda表达式）</b>封装了这个模板：
 * 调用方只需要传入"资源名"和一个Lambda（包含正常业务逻辑），
 * 限流/熔断时的降级逻辑由工具类统一处理。</p>
 *
 * <p>降级策略：限流/熔断时返回热榜兜底数据，而不是429错误。
 * 这样用户体验更好——至少能看到内容，而不是白屏。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 在FeedController中
 * public Result<List<PostVO>> getFeed(Long userId, String cursor) {
 *     return SentinelUtil.executeWithProtection("feed-get", () -> {
 *         List<PostVO> feed = feedService.getFeed(userId, cursor, 20);
 *         return feed;
 *     });
 * }
 * }</pre>
 *
 * @author FantasticName
 */
public class SentinelUtil {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SentinelUtil.class);

    /**
     * 带Sentinel保护的执行方法
     *
     * <p>核心流程：</p>
     * <ol>
     *   <li>SphU.entry(resourceName)：向Sentinel申请通过，如果超过QPS阈值则抛BlockException</li>
     *   <li>执行Lambda中的正常业务逻辑（Supplier.get()）</li>
     *   <li>捕获BlockException：限流/熔断时，返回预生成的降级数据</li>
     *   <li>finally中必须调用entry.exit()：告诉Sentinel本次请求结束，释放资源</li>
     * </ol>
     *
     * <p>为什么finally中必须调用entry.exit()？
     * Sentinel内部使用时间窗口统计QPS/异常比例，entry.exit()标记本次统计结束。
     * 如果不调用，Sentinel会认为请求一直没结束，导致统计不准确。</p>
     *
     * @param resourceName Sentinel资源名称（需与SentinelConfig中配置一致）
     * @param action       正常业务逻辑（Lambda表达式/方法引用）
     * @param fallback     限流/熔断时的降级数据（Supplier）
     * @param <T>          返回值类型
     * @return Result包装的业务结果或降级数据
     */
    public static <T> Result<T> executeWithProtection(String resourceName, Supplier<T> action, Supplier<T> fallback) {
        // Sentinel的Entry相当于"进入受保护资源的许可"
        Entry entry = null;
        try {
            // 【第1步】向Sentinel申请进入资源
            // 如果当前QPS超过阈值，SphU.entry()会抛BlockException
            entry = SphU.entry(resourceName);

            // 【第2步】执行正常业务逻辑
            T result = action.get();
            return Result.success(result);

        } catch (BlockException e) {
            // 【第3步】限流/熔断降级处理
            // 不返回429错误码，而是返回降级数据（如热榜兜底）
            log.warn("Sentinel限流/熔断触发: 资源={}, 规则={}", resourceName, e.getRule().getResource());

            // 如果调用方提供了降级数据（fallback），就返回降级数据
            if (fallback != null) {
                T fallbackData = fallback.get();
                log.info("返回降级数据: 资源={}", resourceName);
                return Result.success(fallbackData);
            }
            // 没有降级数据，返回提示信息
            return Result.error(429, "系统繁忙，请稍后再试");

        } catch (Exception e) {
            // 【第4步】业务异常——不是Sentinel限流，而是业务代码抛出的异常
            log.error("受保护资源执行异常: resource={}, error={}", resourceName, e.getMessage());
            throw new RuntimeException("业务执行异常", e);

        } finally {
            // 【第5步】【重要】必须调用entry.exit()释放资源
            // 如果不调用，Sentinel认为请求一直未结束，统计结果不准
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /**
     * 带Sentinel保护的执行方法（无降级数据版本）
     *
     * <p>当不需要降级数据时使用此重载方法。</p>
     *
     * @param resourceName Sentinel资源名称
     * @param action       正常业务逻辑
     * @param <T>          返回值类型
     * @return Result包装的结果
     */
    public static <T> Result<T> executeWithProtection(String resourceName, Supplier<T> action) {
        return executeWithProtection(resourceName, action, null);
    }
}