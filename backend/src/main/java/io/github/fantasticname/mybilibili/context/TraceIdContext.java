package io.github.fantasticname.mybilibili.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * TraceId上下文工具类，用于把TraceId和当前线程绑定在一起
 *
 * <p>TraceId（全链路追踪ID）的作用：</p>
 * <ul>
 *   <li>每个HTTP请求进来时，生成一个唯一的TraceId</li>
 *   <li>这个TraceId会贯穿整个请求的处理过程</li>
 *   <li>在日志中打印TraceId，就能通过它追踪一个请求的完整调用链</li>
 *   <li>前端收到响应后，如果出问题了，可以把TraceId反馈给后端，方便排查</li>
 * </ul>
 *
 * <p>本类同时使用了两种方式存储TraceId：</p>
 * <ol>
 *   <li><b>ThreadLocal</b>：手动维护，将TraceId和当前线程绑定。
 *       好处是代码中可以随时通过 TraceIdContext.getCurrentTraceId() 获取</li>
 *   <li><b>MDC</b>（Mapped Diagnostic Context）：SLF4J提供的日志上下文。
 *       好处是logback可以在日志格式中自动输出MDC中的值，不需要手动拼接</li>
 * </ol>
 *
 * <p>两种方式同时使用，既能在日志中自动打印TraceId，又能在代码中随时获取TraceId。</p>
 *
 * @author FantasticName
 */
public class TraceIdContext {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(TraceIdContext.class);

    /**
     * MDC中TraceId的key名称
     *
     * <p>在logback.xml的日志格式配置中，使用 %X{traceId} 就能自动输出这个值</p>
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * ThreadLocal，用于将TraceId和当前线程绑定
     *
     * <p>ThreadLocal的原理：每个线程都有一个独立的存储空间，
     * 在线程A中set的值，只有线程A能get到，线程B拿不到。
     * 这样就实现了"线程隔离"，每个请求的TraceId互不干扰。</p>
     *
     * <p>为什么需要ThreadLocal？因为Web服务器（Tomcat）使用线程池处理请求，
     * 多个请求可能同时被处理，如果不做线程隔离，TraceId就会混乱。</p>
     */
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    /**
     * 生成并设置当前线程的TraceId
     *
     * <p>当请求进来的时候调用此方法，生成一个唯一的TraceId，
     * 同时存入ThreadLocal和MDC。</p>
     *
     * <p>TraceId的生成方式：使用UUID去掉横线，得到32位的唯一字符串。
     * UUID几乎不可能重复，适合作为TraceId。</p>
     */
    public static void setTraceId() {
        // 1. 生成UUID，去掉横线，得到32位唯一字符串
        //    例如：550e8400e29b41d4a716446655440000
        String traceId = UUID.randomUUID().toString().replace("-", "");

        // 2. 存入ThreadLocal，绑定到当前线程
        TRACE_ID_HOLDER.set(traceId);

        // 3. 存入MDC，logback会自动在日志中输出
        MDC.put(TRACE_ID_KEY, traceId);

        log.debug("生成TraceId: {}", traceId);
    }

    /**
     * 设置指定的TraceId到当前线程
     *
     * <p>当需要沿用已有的TraceId时使用（比如异步线程中传递TraceId）。</p>
     *
     * @param traceId 要设置的TraceId
     */
    public static void setTraceId(String traceId) {
        // 1. 存入ThreadLocal
        TRACE_ID_HOLDER.set(traceId);

        // 2. 存入MDC
        MDC.put(TRACE_ID_KEY, traceId);

        log.debug("设置TraceId: {}", traceId);
    }

    /**
     * 获取当前线程的TraceId
     *
     * <p>在业务代码中随时调用此方法获取当前请求的TraceId，
     * 比如在构造返回给前端的Result对象时，需要把TraceId塞进去。</p>
     *
     * @return 当前线程的TraceId，如果没有设置则返回null
     */
    public static String getCurrentTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    /**
     * 清除当前线程的TraceId
     *
     * <p>请求处理完成后必须调用此方法！</p>
     *
     * <p>为什么必须清除？因为Tomcat使用线程池，线程会被复用。
     * 如果不清除ThreadLocal，下一个请求复用这个线程时，
     * 会拿到上一个请求的TraceId，导致追踪混乱。</p>
     *
     * <p>同时也要清除MDC，避免日志输出错误的TraceId。</p>
     */
    public static void clearTraceId() {
        // 1. 清除ThreadLocal
        TRACE_ID_HOLDER.remove();

        // 2. 清除MDC
        MDC.remove(TRACE_ID_KEY);

        log.debug("清除TraceId");
    }
}
