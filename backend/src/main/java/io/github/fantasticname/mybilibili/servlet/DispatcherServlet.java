package io.github.fantasticname.mybilibili.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.interceptor.AuthInterceptor;
import io.github.fantasticname.mybilibili.ioc.HandlerMethod;
import io.github.fantasticname.mybilibili.ioc.SimpleContainer;
import io.github.fantasticname.mybilibili.ioc.UriTemplateMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 核心分发器，所有HTTP请求的统一入口
 *
 * <p>DispatcherServlet 是手写 Spring MVC 框架的核心组件，它的作用是：</p>
 * <ol>
 *   <li><b>初始化时</b>：扫描所有 @RestController 的Bean，分析方法上的映射注解，建立路由映射表</li>
 *   <li><b>请求到达时</b>：根据HTTP方法和请求URI查找对应的HandlerMethod，
 *       解析参数，执行鉴权拦截，通过反射调用Controller方法，将返回值序列化为JSON写回响应</li>
 * </ol>
 *
 * <p>这就是 Spring MVC 的 DispatcherServlet 的最核心原理。
 * Spring 只用一个Servlet拦截所有请求，然后根据URL和HTTP方法找到对应的Controller方法，
 * 反射调用它，最后把返回值写回响应。我们要手写的，就是这个DispatcherServlet。</p>
 *
 * @author FantasticName
 */
public class DispatcherServlet extends HttpServlet {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);

    /**
     * Jackson的ObjectMapper，用于JSON序列化/反序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * IoC容器引用，用于获取Controller Bean实例
     */
    private SimpleContainer container;

    /**
     * 路由映射表
     *
     * <p>key = "HTTP方法:路径模板"，如 "GET:/user/{id}"</p>
     * <p>value = HandlerMethod对象，包含Controller实例、方法、路径等信息</p>
     *
     * <p>DispatcherServlet依赖一个单例的handlerMap对象，
     * 初始化时填充，请求处理时查询。</p>
     */
    private final Map<String, HandlerMethod> handlerMap = new HashMap<>();

    /**
     * Servlet初始化方法，在Tomcat启动时由容器调用
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>从ServletContext获取IoC容器实例</li>
     *   <li>初始化AuthInterceptor（传入容器引用）</li>
     *   <li>扫描所有 @RestController Bean，建立路由映射表</li>
     * </ol>
     *
     * @param config Servlet配置
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        log.info("DispatcherServlet 开始初始化...");

        // 1. 从ServletContext获取IoC容器
        //    容器在 ContainerInitializerListener 中初始化并存入ServletContext
        ServletContext servletContext = config.getServletContext();
        this.container = (SimpleContainer) servletContext.getAttribute("simpleContainer");

        if (this.container == null) {
            log.error("未找到IoC容器，请确保ContainerInitializerListener已配置");
            throw new ServletException("IoC容器未初始化");
        }

        // 2. 初始化AuthInterceptor，传入容器引用
        //    这样AuthInterceptor就能从容器中获取JwtUtil和UserService
        AuthInterceptor.init(container);
        log.info("AuthInterceptor 初始化完成");

        // 3. 扫描所有Controller，建立路由映射表
        initHandlerMappings();

        log.info("DispatcherServlet 初始化完成，共注册 {} 条路由", handlerMap.size());
    }

    /**
     * 扫描所有 @RestController 的Bean，建立路由映射表
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>从IoC容器获取所有Bean</li>
     *   <li>过滤出带有 @RestController 注解的Bean</li>
     *   <li>获取类上的 @RequestMapping 作为basePath</li>
     *   <li>遍历Bean的所有方法，检查方法上的映射注解</li>
     *   <li>拼接完整路径，创建HandlerMethod，存入路由映射表</li>
     * </ol>
     */
    private void initHandlerMappings() {
        log.info("开始建立路由映射表...");

        // 1. 从IoC容器获取所有Bean
        Map<String, Object> allBeans = container.getAllBeans();

        // 2. 遍历所有Bean
        for (Map.Entry<String, Object> entry : allBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> clazz = bean.getClass();

            // 2.1 只处理带有 @RestController 注解的类
            if (!clazz.isAnnotationPresent(RestController.class)) {
                continue;
            }

            log.debug("扫描Controller: {}", clazz.getSimpleName());

            // 2.2 获取类上的 @RequestMapping 注解的value作为basePath
            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
                log.debug("Controller根路径: {}", basePath);
            }

            // 2.3 遍历这个Bean的所有方法
            for (Method method : clazz.getDeclaredMethods()) {
                String subPath = "";
                String httpMethod = null;

                // 2.3.1 检查方法上的各种映射注解
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
                    // 方法上的 @RequestMapping 匹配所有HTTP方法
                    subPath = method.getAnnotation(RequestMapping.class).value();
                    httpMethod = "*";
                }

                // 2.3.2 如果方法上有映射注解，注册路由
                if (httpMethod != null) {
                    // 拼接完整路径：basePath + subPath
                    String fullPath = basePath + subPath;

                    // 创建HandlerMethod对象，保存这条路由的所有元数据
                    HandlerMethod hm = new HandlerMethod();
                    hm.setController(bean);
                    hm.setMethod(method);
                    hm.setHttpMethod(httpMethod);
                    hm.setFullPath(fullPath);

                    // 生成路由key：HTTP方法:完整路径
                    String key = httpMethod + ":" + fullPath;

                    // 存入路由映射表
                    handlerMap.put(key, hm);

                    log.info("映射路由: {} -> {}.{}()", key, clazz.getSimpleName(), method.getName());
                }
            }
        }
    }

    /**
     * 请求处理的核心方法，所有HTTP请求都经过这里
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>根据请求的HTTP方法和URI，在路由映射表中查找HandlerMethod</li>
     *   <li>如果找不到，返回404错误</li>
     *   <li>解析方法参数（@PathVariable、@RequestParam、@RequestBody等）</li>
     *   <li>执行鉴权拦截（AuthInterceptor.preHandle）</li>
     *   <li>通过反射调用Controller方法</li>
     *   <li>将返回值序列化为JSON写回响应</li>
     * </ol>
     *
     * @param req  HTTP请求
     * @param resp HTTP响应
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1. 获取请求的HTTP方法和URI
        String method = req.getMethod();
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();

        // 1.1 去掉contextPath前缀，得到实际的请求路径
        //     比如应用部署在 /api 下，请求 /api/user/1 → 实际路径 /user/1
        String actualPath = requestURI.substring(contextPath.length());

        log.info("收到请求: {} {}", method, actualPath);

        try {
            // 2. 在路由映射表中查找HandlerMethod
            HandlerMethod handler = findHandler(method, actualPath, req);

            if (handler == null) {
                // 2.1 路由未命中，返回404
                log.warn("路由未命中: {} {}", method, actualPath);
                writeErrorResponse(resp, Result.error(ErrorCode.NOT_FOUND_ERROR, "接口不存在"));
                return;
            }

            log.debug("路由命中: {} -> {}.{}()", method, handler.getController().getClass().getSimpleName(),
                    handler.getMethod().getName());

            // 3. 解析方法参数
            //    根据方法参数上的注解，从请求中提取对应的值
            Object[] args = resolveArguments(handler, req, resp);

            // 4. 执行鉴权拦截
            //    在通过反射调用controller方法前，先检查用户是否有权限访问
            //    如果没有权限，AuthInterceptor会抛出BusinessException
            AuthInterceptor.preHandle(handler.getMethod(), req);

            // 5. 通过反射调用Controller方法
            //    handler.method.invoke(handler.controller, args) 相当于 controller.method(args)
            Object result = handler.getMethod().invoke(handler.getController(), args);

            log.debug("Controller方法执行完成，返回值类型: {}",
                    result != null ? result.getClass().getSimpleName() : "null");

            // 6. 将返回值写回响应
            writeResponse(resp, result);

        } catch (BusinessException e) {
            // 业务异常，已经被AuthInterceptor或业务代码抛出
            // 返回统一的错误格式
            log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
            writeErrorResponse(resp, Result.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            // 反射调用可能抛出InvocationTargetException，需要获取目标异常
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException) {
                BusinessException be = (BusinessException) cause;
                log.warn("业务异常: code={}, message={}", be.getCode(), be.getMessage());
                writeErrorResponse(resp, Result.error(be.getCode(), be.getMessage()));
            } else {
                // 未知异常，返回系统错误
                log.error("请求处理异常: {} {}", method, actualPath, e);
                writeErrorResponse(resp, Result.error(ErrorCode.SYSTEM_ERROR));
            }
        }
    }

    /**
     * 在路由映射表中查找匹配的HandlerMethod
     *
     * <p>查找逻辑：</p>
     * <ol>
     *   <li>遍历handlerMap的所有entry</li>
     *   <li>比较HTTP方法是否匹配</li>
     *   <li>使用UriTemplateMatcher匹配路径模板</li>
     *   <li>如果匹配成功，将路径变量存入request属性，返回HandlerMethod</li>
     * </ol>
     *
     * @param httpMethod HTTP方法
     * @param actualPath 实际请求路径
     * @param request    HTTP请求对象
     * @return 匹配的HandlerMethod，未找到返回null
     */
    private HandlerMethod findHandler(String httpMethod, String actualPath, HttpServletRequest request) {
        // 1. 遍历路由映射表
        for (Map.Entry<String, HandlerMethod> entry : handlerMap.entrySet()) {
            String key = entry.getKey();

            // 2. 解析路由key，格式为 "HTTP方法:路径模板"
            String[] parts = key.split(":", 2);
            String routeHttpMethod = parts[0];
            String template = parts[1];

            // 3. 比较HTTP方法
            //    "*" 表示匹配所有HTTP方法（方法上的@RequestMapping）
            if (!routeHttpMethod.equals(httpMethod) && !"*".equals(routeHttpMethod)) {
                continue;
            }

            // 4. 使用UriTemplateMatcher匹配路径模板
            //    如果匹配成功，路径变量会被填充到vars中
            Map<String, String> vars = new HashMap<>();
            if (UriTemplateMatcher.matches(template, actualPath, vars)) {
                // 5. 将路径变量存入request属性
                //    后续参数解析时可以从request中取出
                request.setAttribute("pathVariables", vars);
                return entry.getValue();
            }
        }

        // 6. 遍历完也没找到匹配的路由
        return null;
    }

    /**
     * 解析Controller方法的参数
     *
     * <p>遍历方法的每个参数，根据参数上的注解从请求中提取对应的值：</p>
     * <ul>
     *   <li>HttpServletRequest / HttpServletResponse：直接注入</li>
     *   <li>@PathVariable：从URI模板中提取变量值</li>
     *   <li>@RequestParam：从URL查询字符串获取</li>
     *   <li>@RequestBody：读取请求体JSON，用Jackson反序列化</li>
     * </ul>
     *
     * @param handler HandlerMethod对象
     * @param request HTTP请求
     * @param response HTTP响应
     * @return 参数值数组
     */
    private Object[] resolveArguments(HandlerMethod handler, HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        // 1. 获取方法的所有参数
        Parameter[] parameters = handler.getMethod().getParameters();
        Object[] args = new Object[parameters.length];

        // 2. 获取路径变量（由findHandler方法存入request属性）
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute("pathVariables");

        // 3. 逐个解析参数
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> type = param.getType();

            // 3.1 特殊类型直接注入
            if (type == HttpServletRequest.class) {
                args[i] = request;
                log.debug("注入参数: HttpServletRequest");
            } else if (type == HttpServletResponse.class) {
                args[i] = response;
                log.debug("注入参数: HttpServletResponse");
            }
            // 3.2 处理 @PathVariable：从URI模板中提取变量值
            else if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable ann = param.getAnnotation(PathVariable.class);
                // 获取变量名：如果注解指定了value则用指定的，否则用参数名
                String varName = ann.value().isEmpty() ? param.getName() : ann.value();
                String value = pathVariables != null ? pathVariables.get(varName) : null;
                args[i] = convertValue(value, type);
                log.debug("注入参数: @PathVariable {}={}", varName, value);
            }
            // 3.3 处理 @RequestParam：从URL查询字符串获取
            else if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam ann = param.getAnnotation(RequestParam.class);
                String paramName = ann.value().isEmpty() ? param.getName() : ann.value();
                String value = request.getParameter(paramName);
                args[i] = convertValue(value, type);
                log.debug("注入参数: @RequestParam {}={}", paramName, value);
            }
            // 3.4 处理 @RequestBody：读取请求体JSON，用Jackson反序列化
            else if (param.isAnnotationPresent(RequestBody.class)) {
                String body = readRequestBody(request);
                if (body != null && !body.isEmpty()) {
                    args[i] = objectMapper.readValue(body, type);
                    log.debug("注入参数: @RequestBody, 类型={}", type.getSimpleName());
                }
            }
            else {
                // 没有注解的参数，暂时设为null
                args[i] = null;
                log.debug("未识别的参数: {}, 类型={}", param.getName(), type.getSimpleName());
            }
        }

        return args;
    }

    /**
     * 类型转换，将字符串值转换为目标类型
     *
     * <p>从URL中获取的参数值都是String类型，需要转换成方法参数的实际类型。
     * 比如 @PathVariable("id") Long id，需要把 "123" 转成 123L。</p>
     *
     * @param value      字符串值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    private Object convertValue(String value, Class<?> targetType) {
        // 1. null值直接返回
        if (value == null) {
            return null;
        }

        // 2. 根据目标类型进行转换
        if (targetType == String.class) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        }

        // 3. 不支持的类型，抛出异常
        throw new IllegalArgumentException("不支持的类型转换: " + targetType.getName());
    }

    /**
     * 读取HTTP请求体内容
     *
     * <p>用于 @RequestBody 注解的参数解析。
     * 从request的输入流中逐行读取，拼接成完整的JSON字符串。</p>
     *
     * @param request HTTP请求
     * @return 请求体的字符串内容
     */
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

    /**
     * 将结果对象序列化为JSON并写入HTTP响应
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>设置响应类型为JSON，字符集UTF-8</li>
     *   <li>如果结果是Result类型，设置TraceId</li>
     *   <li>使用Jackson将对象序列化为JSON</li>
     *   <li>写入响应输出流</li>
     * </ol>
     *
     * @param resp   HTTP响应
     * @param result 结果对象
     */
    private void writeResponse(HttpServletResponse resp, Object result) throws IOException {
        // 1. 设置响应类型为JSON，字符集UTF-8
        resp.setContentType("application/json;charset=UTF-8");

        // 2. 如果结果是Result类型，设置TraceId
        if (result instanceof Result) {
            ((Result<?>) result).setTraceId(TraceIdContext.getCurrentTraceId());
        }

        // 3. 使用Jackson将对象序列化为JSON并写入响应
        objectMapper.writeValue(resp.getWriter(), result);
    }

    /**
     * 将错误结果写入HTTP响应
     *
     * <p>与writeResponse类似，但专门处理错误场景，
     * 确保错误响应也包含TraceId。</p>
     *
     * @param resp   HTTP响应
     * @param result 错误结果
     */
    private void writeErrorResponse(HttpServletResponse resp, Result<?> result) throws IOException {
        // 1. 设置HTTP状态码为200
        //    业务错误用Result的code字段表示，HTTP层面保持成功
        resp.setStatus(HttpServletResponse.SC_OK);

        // 2. 设置响应类型为JSON，字符集UTF-8
        resp.setContentType("application/json;charset=UTF-8");

        // 3. 设置TraceId
        result.setTraceId(TraceIdContext.getCurrentTraceId());

        // 4. 序列化并写入响应
        objectMapper.writeValue(resp.getWriter(), result);
    }
}
