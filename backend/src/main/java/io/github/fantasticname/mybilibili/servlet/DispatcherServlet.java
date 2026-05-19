package io.github.fantasticname.mybilibili.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.interceptor.AuthInterceptor;
import io.github.fantasticname.mybilibili.ioc.HandlerMethod;
import io.github.fantasticname.mybilibili.ioc.SimpleContainer;
import io.github.fantasticname.mybilibili.ioc.TypeConverterRegistry;
import io.github.fantasticname.mybilibili.ioc.UriTemplateMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MultipartConfig(
        maxFileSize = 500 * 1024 * 1024,
        maxRequestSize = 500 * 1024 * 1024,
        fileSizeThreshold = 0
)
public class DispatcherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024;
    private static final int FILE_SIZE_THRESHOLD = 0;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SimpleContainer container;

    private final Map<String, HandlerMethod> staticRoutes = new HashMap<>();

    private final List<RouteEntry> dynamicRoutes = new ArrayList<>();

    private final TypeConverterRegistry typeConverterRegistry = TypeConverterRegistry.createDefault();

    private AuthInterceptor authInterceptor;

    static class RouteEntry {
        final String httpMethod;
        final String template;
        final HandlerMethod handlerMethod;

        RouteEntry(String httpMethod, String template, HandlerMethod handlerMethod) {
            this.httpMethod = httpMethod;
            this.template = template;
            this.handlerMethod = handlerMethod;
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        log.info("DispatcherServlet 开始初始化...");

        MultipartConfigElement multipartConfig = new MultipartConfigElement(
                "", MAX_FILE_SIZE, MAX_REQUEST_SIZE, FILE_SIZE_THRESHOLD);
        config.getServletContext().setInitParameter("multipartConfig", "true");

        ServletContext servletContext = config.getServletContext();
        this.container = (SimpleContainer) servletContext.getAttribute("simpleContainer");

        if (this.container == null) {
            log.error("未找到IoC容器，请确保ContainerInitializerListener已配置");
            throw new ServletException("IoC容器未初始化");
        }

        this.authInterceptor = container.getBean(AuthInterceptor.class);
        if (this.authInterceptor == null) {
            log.error("未找到AuthInterceptor Bean，请确保已添加@Component注解");
            throw new ServletException("AuthInterceptor未注册到IoC容器");
        }
        log.info("AuthInterceptor 从IoC容器获取成功");

        initHandlerMappings();

        log.info("DispatcherServlet 初始化完成，静态路由: {}，动态路由: {}",
                staticRoutes.size(), dynamicRoutes.size());
    }

    private void initHandlerMappings() {
        log.info("开始建立路由映射表...");

        Map<String, Object> allBeans = container.getAllBeans();

        for (Map.Entry<String, Object> entry : allBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> clazz = bean.getClass();

            if (!clazz.isAnnotationPresent(RestController.class)) {
                continue;
            }

            log.debug("扫描Controller: {}", clazz.getSimpleName());

            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
                log.debug("Controller根路径: {}", basePath);
            }

            for (Method method : clazz.getDeclaredMethods()) {
                String subPath = "";
                String httpMethod = null;

                if (method.isAnnotationPresent(GetMapping.class)) {
                    subPath = method.getAnnotation(GetMapping.class).value();
                    httpMethod = "GET";
                } else if (method.isAnnotationPresent(PostMapping.class)) {
                    subPath = method.getAnnotation(PostMapping.class).value();
                    httpMethod = "POST";
                } else if (method.isAnnotationPresent(PutMapping.class)) {
                    subPath = method.getAnnotation(PutMapping.class).value();
                    httpMethod = "PUT";
                } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                    subPath = method.getAnnotation(DeleteMapping.class).value();
                    httpMethod = "DELETE";
                } else if (method.isAnnotationPresent(RequestMapping.class)) {
                    subPath = method.getAnnotation(RequestMapping.class).value();
                    httpMethod = "*";
                }

                if (httpMethod != null) {
                    String fullPath = basePath + subPath;

                    HandlerMethod hm = new HandlerMethod();
                    hm.setController(bean);
                    hm.setMethod(method);
                    hm.setHttpMethod(httpMethod);
                    hm.setFullPath(fullPath);

                    boolean isDynamic = fullPath.contains("{");

                    if (isDynamic) {
                        dynamicRoutes.add(new RouteEntry(httpMethod, fullPath, hm));
                        log.info("映射动态路由: {}:{} -> {}.{}()", httpMethod, fullPath,
                                clazz.getSimpleName(), method.getName());
                    } else {
                        String key = httpMethod + ":" + fullPath;
                        staticRoutes.put(key, hm);
                        log.info("映射静态路由: {} -> {}.{}()", key,
                                clazz.getSimpleName(), method.getName());
                    }
                }
            }
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String method = req.getMethod();
        String actualPath = req.getPathInfo();
        if (actualPath == null || actualPath.isEmpty()) {
            actualPath = "/";
        }

        log.info("收到请求: {} {}", method, actualPath);

        HandlerMethod handler = findHandler(method, actualPath, req);

        if (handler == null) {
            log.warn("路由未命中: {} {}", method, actualPath);
            writeResponse(resp, io.github.fantasticname.mybilibili.common.Result.error(
                    io.github.fantasticname.mybilibili.common.ErrorCode.NOT_FOUND_ERROR, "接口不存在"));
            return;
        }

        log.debug("路由命中: {} -> {}.{}()", method, handler.getController().getClass().getSimpleName(),
                handler.getMethod().getName());

        Object[] args = resolveArguments(handler, req, resp);

        authInterceptor.preHandle(handler.getMethod(), req);

        Object result;
        try {
            result = handler.getMethod().invoke(handler.getController(), args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Exception) {
                throw new ServletException(cause);
            }
            throw new ServletException(e);
        }

        log.debug("Controller方法执行完成，返回值类型: {}",
                result != null ? result.getClass().getSimpleName() : "null");

        writeResponse(resp, result);
    }

    private HandlerMethod findHandler(String httpMethod, String actualPath, HttpServletRequest request) {
        String staticKey = httpMethod + ":" + actualPath;
        HandlerMethod handler = staticRoutes.get(staticKey);
        if (handler != null) {
            request.setAttribute("pathVariables", new HashMap<String, String>());
            return handler;
        }

        for (RouteEntry entry : dynamicRoutes) {
            if (!entry.httpMethod.equals(httpMethod) && !"*".equals(entry.httpMethod)) {
                continue;
            }

            Map<String, String> vars = new HashMap<>();
            if (UriTemplateMatcher.matches(entry.template, actualPath, vars)) {
                request.setAttribute("pathVariables", vars);
                return entry.handlerMethod;
            }
        }

        return null;
    }

    private Object[] resolveArguments(HandlerMethod handler, HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        Parameter[] parameters = handler.getMethod().getParameters();
        Object[] args = new Object[parameters.length];

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute("pathVariables");

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> type = param.getType();

            if (type == HttpServletRequest.class) {
                args[i] = request;
                log.debug("注入参数: HttpServletRequest");
            } else if (type == HttpServletResponse.class) {
                args[i] = response;
                log.debug("注入参数: HttpServletResponse");
            } else if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable ann = param.getAnnotation(PathVariable.class);
                String varName = ann.value().isEmpty() ? param.getName() : ann.value();
                String value = pathVariables != null ? pathVariables.get(varName) : null;
                args[i] = typeConverterRegistry.convert(value, type);
                log.debug("注入参数: @PathVariable {}={}", varName, value);
            } else if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam ann = param.getAnnotation(RequestParam.class);
                String paramName = ann.value().isEmpty() ? param.getName() : ann.value();
                String value = request.getParameter(paramName);
                if (value == null || value.isEmpty()) {
                    String defaultValue = ann.defaultValue();
                    if (defaultValue != null && !defaultValue.isEmpty()) {
                        value = defaultValue;
                    } else if (ann.required()) {
                        throw new IllegalArgumentException("缺少必需参数: " + paramName);
                    }
                }
                args[i] = typeConverterRegistry.convert(value, type);
                log.debug("注入参数: @RequestParam {}={}", paramName, value);
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                String body = readRequestBody(request);
                if (body != null && !body.isEmpty()) {
                    args[i] = objectMapper.readValue(body, type);
                    log.debug("注入参数: @RequestBody, 类型={}", type.getSimpleName());
                }
            } else {
                args[i] = null;
                log.debug("未识别的参数: {}, 类型={}", param.getName(), type.getSimpleName());
            }
        }

        return args;
    }

    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void writeResponse(HttpServletResponse resp, Object result) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        if (result instanceof io.github.fantasticname.mybilibili.common.Result) {
            ((io.github.fantasticname.mybilibili.common.Result<?>) result).setTraceId(TraceIdContext.getCurrentTraceId());
        }

        objectMapper.writeValue(resp.getWriter(), result);
    }
}
