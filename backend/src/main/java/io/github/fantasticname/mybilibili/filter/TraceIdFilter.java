package io.github.fantasticname.mybilibili.filter;

import io.github.fantasticname.mybilibili.context.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * TraceId过滤器，负责在每个请求进来时生成TraceId，请求结束时清除TraceId
 *
 * <p>这个过滤器是整个请求处理链的第一环，它做两件事：</p>
 * <ol>
 *   <li>请求进来时：调用 TraceIdContext.setTraceId() 生成TraceId，
 *       并绑定到当前线程和MDC</li>
 *   <li>请求结束时（无论成功还是异常）：调用 TraceIdContext.clearTraceId() 清除TraceId，
 *       防止线程复用导致的TraceId混乱</li>
 * </ol>
 *
 * <p>为什么必须清除？因为Tomcat使用线程池，线程会被复用。
 * 如果不清除ThreadLocal，下一个请求复用这个线程时，
 * 会拿到上一个请求的TraceId，导致追踪混乱。</p>
 *
 * <p>在web.xml中配置时，此过滤器应该放在最前面（filter-mapping顺序最靠前），
 * 确保所有后续的Filter和Servlet都能在日志中输出正确的TraceId。</p>
 *
 * @author FantasticName
 */
public class TraceIdFilter implements Filter {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /**
     * 过滤器初始化方法，在Tomcat启动时调用
     *
     * @param filterConfig 过滤器配置
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("TraceIdFilter 初始化完成");
    }

    /**
     * 过滤器的核心方法，每个请求都会经过这里
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>请求进来，生成TraceId并绑定到当前线程</li>
     *   <li>调用 chain.doFilter()，让请求继续往下走</li>
     *   <li>无论后续处理成功还是异常，最终都要清除TraceId</li>
     * </ol>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param chain    过滤器链，调用 chain.doFilter() 让请求继续往下走
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 1. 获取HTTP请求对象，用于记录请求信息
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 2. 生成TraceId，绑定到当前线程和MDC
        //    这样后续所有代码的日志都会自动带上这个TraceId
        TraceIdContext.setTraceId();

        // 3. 记录请求开始日志
        log.info("请求开始: {} {}", method, requestURI);

        try {
            // 4. 调用 chain.doFilter()，让请求继续往下走
            //    后续的Filter和Servlet会处理这个请求
            chain.doFilter(request, response);
        } finally {
            // 5. 无论成功还是异常，都要清除TraceId
            //    使用 finally 块确保一定会执行
            //    这是防止ThreadLocal内存泄漏的关键步骤
            log.info("请求结束: {} {}", method, requestURI);
            TraceIdContext.clearTraceId();
        }
    }

    /**
     * 过滤器销毁方法，在Tomcat关闭时调用
     */
    @Override
    public void destroy() {
        log.info("TraceIdFilter 已销毁");
    }
}
