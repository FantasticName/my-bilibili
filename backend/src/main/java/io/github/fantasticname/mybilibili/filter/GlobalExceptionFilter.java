package io.github.fantasticname.mybilibili.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.lang.reflect.InvocationTargetException;

public class GlobalExceptionFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionFilter.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("GlobalExceptionFilter 初始化完成");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            chain.doFilter(request, response);
        } catch (BusinessException e) {
            log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
            handleError(httpResponse, Result.error(e.getCode(), e.getMessage()));
        } catch (ServletException e) {
            Throwable rootCause = getRootCause(e);
            if (rootCause instanceof BusinessException) {
                BusinessException be = (BusinessException) rootCause;
                log.warn("业务异常(Servlet): code={}, message={}", be.getCode(), be.getMessage());
                handleError(httpResponse, Result.error(be.getCode(), be.getMessage()));
            } else if (rootCause instanceof InvocationTargetException) {
                Throwable target = ((InvocationTargetException) rootCause).getTargetException();
                if (target instanceof BusinessException) {
                    BusinessException be = (BusinessException) target;
                    log.warn("业务异常(反射): code={}, message={}", be.getCode(), be.getMessage());
                    handleError(httpResponse, Result.error(be.getCode(), be.getMessage()));
                } else {
                    log.error("反射调用异常: {}", target.getMessage(), target);
                    handleError(httpResponse, Result.error(ErrorCode.SYSTEM_ERROR));
                }
            } else {
                log.error("Servlet异常: {}", e.getMessage(), e);
                handleError(httpResponse, Result.error(ErrorCode.SYSTEM_ERROR));
            }
        } catch (RuntimeException e) {
            log.error("运行时异常: {}", e.getMessage(), e);
            handleError(httpResponse, Result.error(ErrorCode.SYSTEM_ERROR));
        } catch (Exception e) {
            log.error("系统异常: {}", e.getMessage(), e);
            handleError(httpResponse, Result.error(ErrorCode.SYSTEM_ERROR));
        }
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        int depth = 0;
        while (cause.getCause() != null && depth < 10) {
            cause = cause.getCause();
            depth++;
        }
        return cause;
    }

    private void handleError(HttpServletResponse httpResponse, Result<?> result) {
        try {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            httpResponse.setContentType("application/json;charset=UTF-8");
            result.setTraceId(TraceIdContext.getCurrentTraceId());
            String json = objectMapper.writeValueAsString(result);
            httpResponse.getWriter().write(json);
            httpResponse.getWriter().flush();

            log.debug("错误响应已写入: code={}, message={}", result.getCode(), result.getMessage());
        } catch (IOException e) {
            log.error("写入错误响应失败", e);
        }
    }

    @Override
    public void destroy() {
        log.info("GlobalExceptionFilter 已销毁");
    }
}
