package io.github.fantasticname.mybilibili.filter;

import io.github.fantasticname.mybilibili.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 用户上下文清理过滤器
 *
 * <p>负责在请求处理完成后（finally块）清理UserContext，
 * 避免ThreadLocal污染线程池中的线程，防止内存泄漏。</p>
 *
 * <p>为什么需要这个Filter？</p>
 * <ul>
 *   <li>Tomcat使用线程池处理请求，线程会被复用</li>
 *   <li>AuthInterceptor在preHandle中将User存入ThreadLocal</li>
 *   <li>如果请求处理过程中抛出异常，afterCompletion可能不会执行</li>
 *   <li>ThreadLocal中的数据会残留在复用的线程中，导致安全问题</li>
 * </ul>
 *
 * <p>因此，在Filter的finally块中统一清理ThreadLocal，确保万无一失。</p>
 *
 * @author FantasticName
 */
public class AuthFilter implements Filter {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /**
     * 过滤器初始化方法
     *
     * @param filterConfig 过滤器配置
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("AuthFilter 初始化完成");
    }

    /**
     * 过滤请求，在finally块中清理UserContext
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param chain    过滤器链
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // 继续执行后续过滤器和Servlet
            chain.doFilter(request, response);
        } finally {
            // 无论请求成功还是失败，都清理UserContext
            UserContext.clear();
            log.debug("AuthFilter: UserContext已清理");
        }
    }

    /**
     * 过滤器销毁方法
     */
    @Override
    public void destroy() {
        log.info("AuthFilter 已销毁");
    }
}
