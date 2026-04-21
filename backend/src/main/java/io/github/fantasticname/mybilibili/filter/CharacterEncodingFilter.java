package io.github.fantasticname.mybilibili.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * 字符编码过滤器，统一设置请求和响应的字符编码为UTF-8
 *
 * <p>为什么需要这个过滤器？</p>
 * <ul>
 *   <li>HTTP请求默认的编码可能不是UTF-8，导致中文参数乱码</li>
 *   <li>HTTP响应默认的编码也可能不是UTF-8，导致中文内容乱码</li>
 *   <li>统一设置UTF-8编码，确保中文在请求和响应中正确传输</li>
 * </ul>
 *
 * <p>在web.xml中配置时，此过滤器应该放在最前面（在TraceIdFilter之前），
 * 确保所有后续的Filter和Servlet收到的请求都是UTF-8编码的。</p>
 *
 * @author FantasticName
 */
public class CharacterEncodingFilter implements Filter {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(CharacterEncodingFilter.class);

    /**
     * 统一使用的字符编码
     */
    private static final String ENCODING = "UTF-8";

    /**
     * 过滤器初始化方法
     *
     * @param filterConfig 过滤器配置
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("CharacterEncodingFilter 初始化完成，编码: {}", ENCODING);
    }

    /**
     * 过滤器的核心方法，设置请求和响应的字符编码
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>设置请求的字符编码为UTF-8，防止中文参数乱码</li>
     *   <li>设置响应的字符编码为UTF-8，防止中文内容乱码</li>
     *   <li>设置响应的Content-Type，指定字符集</li>
     *   <li>调用 chain.doFilter()，让请求继续往下走</li>
     * </ol>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param chain    过滤器链
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 1. 设置请求的字符编码为UTF-8
        //    这样 request.getParameter() 获取的中文参数就不会乱码
        request.setCharacterEncoding(ENCODING);

        // 2. 设置响应的字符编码为UTF-8
        //    这样响应输出流写入的中文就不会乱码
        response.setCharacterEncoding(ENCODING);

        // 3. 设置响应的Content-Type，指定字符集
        //    告诉浏览器：我返回的内容是UTF-8编码的
        response.setContentType("text/html;charset=UTF-8");

        // 4. 让请求继续往下走
        chain.doFilter(request, response);
    }

    /**
     * 过滤器销毁方法
     */
    @Override
    public void destroy() {
        log.info("CharacterEncodingFilter 已销毁");
    }
}
