package io.github.fantasticname.mybilibili.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.common.Result;
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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 全局异常处理过滤器，捕获所有未处理的异常并返回前端友好的统一响应格式
 *
 * <p>这个过滤器是整个请求处理链的第二环（在TraceIdFilter之后），它做两件事：</p>
 * <ol>
 *   <li>将 chain.doFilter() 包裹在 try-catch 中，捕获所有未处理的异常</li>
 *   <li>根据异常类型，构造对应的 Result 对象，以JSON格式返回给前端</li>
 * </ol>
 *
 * <p>异常处理策略：</p>
 * <ul>
 *   <li>{@link BusinessException}：业务异常，提取其中的code和message返回给前端</li>
 *   <li>其他 {@link RuntimeException}：未知运行时异常，返回系统错误，不暴露技术细节</li>
 *   <li>其他 {@link Exception}：受检异常，返回系统错误，不暴露技术细节</li>
 * </ul>
 *
 * <p>重要原则：后端错误的技术细节不要暴露给前端！
 * 前端只需要知道"出错了"和"错误码"，不需要知道是NullPointerException还是SQLException。</p>
 *
 * @author FantasticName
 */
public class GlobalExceptionFilter implements Filter {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionFilter.class);

    /**
     * Jackson的ObjectMapper，用于将Result对象序列化为JSON字符串
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 过滤器初始化方法
     *
     * @param filterConfig 过滤器配置
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("GlobalExceptionFilter 初始化完成");
    }

    /**
     * 过滤器的核心方法，捕获所有异常并返回统一格式的错误响应
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>用 try-catch 包裹 chain.doFilter()</li>
     *   <li>如果正常执行，什么都不做</li>
     *   <li>如果抛出异常，根据异常类型构造Result对象，写入响应</li>
     * </ol>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param chain    过滤器链
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 1. 获取HTTP请求和响应对象
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 2. 正常执行过滤器链，让请求继续往下走
            //    如果后续处理正常完成，这里不会有任何问题
            chain.doFilter(request, response);
        } catch (BusinessException e) {
            // 3. 捕获业务异常
            //    BusinessException 是我们自己定义的异常，包含code和message
            //    可以安全地返回给前端，因为信息是业务层面的，不涉及技术细节
            log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
            handleError(httpResponse, Result.error(e.getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            // 4. 捕获未知运行时异常
            //    这类异常可能包含技术细节（如NullPointerException的堆栈信息）
            //    不能暴露给前端，只记录日志，返回通用的系统错误
            log.error("运行时异常: {}", e.getMessage(), e);
            handleError(httpResponse, Result.error(ErrorCode.SYSTEM_ERROR));
        } catch (Exception e) {
            // 5. 捕获受检异常
            //    同样不暴露技术细节
            log.error("系统异常: {}", e.getMessage(), e);
            handleError(httpResponse, Result.error(ErrorCode.SYSTEM_ERROR));
        }
    }

    /**
     * 将错误信息写入HTTP响应
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>设置响应状态码为200（业务层面的错误用Result的code字段表示，HTTP状态码保持200）</li>
     *   <li>设置响应类型为JSON，字符集为UTF-8</li>
     *   <li>将当前线程的TraceId塞进Result对象</li>
     *   <li>使用Jackson将Result对象序列化为JSON字符串</li>
     *   <li>将JSON字符串写入响应输出流</li>
     * </ol>
     *
     * @param httpResponse HTTP响应对象
     * @param result       统一响应结果
     */
    private void handleError(HttpServletResponse httpResponse, Result<?> result) {
        try {
            // 1. 设置HTTP状态码为200
            //    业务错误用Result的code字段表示，HTTP层面保持成功
            //    这样前端只需要解析JSON即可，不需要根据HTTP状态码判断
            httpResponse.setStatus(HttpServletResponse.SC_OK);

            // 2. 设置响应类型为JSON，字符集UTF-8，防止中文乱码
            httpResponse.setContentType("application/json;charset=UTF-8");

            // 3. 将当前线程的TraceId塞进Result对象
            //    前端收到响应后，如果出问题了，可以把TraceId反馈给后端
            result.setTraceId(TraceIdContext.getCurrentTraceId());

            // 4. 使用Jackson将Result对象序列化为JSON字符串
            String json = objectMapper.writeValueAsString(result);

            // 5. 将JSON字符串写入响应输出流
            httpResponse.getWriter().write(json);
            httpResponse.getWriter().flush();

            log.debug("错误响应已写入: code={}, message={}", result.getCode(), result.getMessage());
        } catch (IOException e) {
            // 写入响应失败，记录错误日志
            log.error("写入错误响应失败", e);
        }
    }

    /**
     * 过滤器销毁方法
     */
    @Override
    public void destroy() {
        log.info("GlobalExceptionFilter 已销毁");
    }
}
