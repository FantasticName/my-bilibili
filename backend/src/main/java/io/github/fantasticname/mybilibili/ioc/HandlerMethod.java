package io.github.fantasticname.mybilibili.ioc;

import java.lang.reflect.Method;

/**
 * 路由处理器方法，保存一条路由信息的所有元数据
 *
 * <p>DispatcherServlet初始化时，会扫描所有 @RestController 类的方法，
 * 为每个带有请求映射注解的方法创建一个 HandlerMethod 对象，
 * 存入路由映射表（handlerMap）。</p>
 *
 * <p>当请求到达时，DispatcherServlet 根据HTTP方法和请求URI
 * 在路由映射表中查找匹配的 HandlerMethod，然后通过反射调用其中的方法。</p>
 *
 * <p>一条路由信息包含：</p>
 * <ul>
 *   <li>controller：Controller实例（从IoC容器获取）</li>
 *   <li>method：要调用的方法（通过反射执行）</li>
 *   <li>httpMethod：HTTP方法（GET/POST/PUT/DELETE）</li>
 *   <li>fullPath：完整路径模板（如 /user/{id}）</li>
 * </ul>
 *
 * @author FantasticName
 */
public class HandlerMethod {

    /**
     * Controller实例，从IoC容器中获取
     *
     * <p>通过 method.invoke(controller, args) 反射调用方法时，
     * 需要传入对象实例。这个实例就是IoC容器创建并管理的Bean。</p>
     */
    private Object controller;

    /**
     * 要调用的方法
     *
     * <p>通过反射 Method 对象，可以在运行时动态调用任意方法。
     * method.invoke(controller, args) 就相当于 controller.method(args)。</p>
     */
    private Method method;

    /**
     * HTTP方法，如 "GET"、"POST"、"PUT"、"DELETE"
     */
    private String httpMethod;

    /**
     * 完整路径模板，如 "/user/{id}"
     *
     * <p>由类上的 @RequestMapping 的value + 方法上的映射注解的value 拼接而成。
     * 例如：类上 @RequestMapping("/user") + 方法上 @GetMapping("/{id}")
     * = fullPath = "/user/{id}"</p>
     */
    private String fullPath;

    /**
     * 获取Controller实例
     *
     * @return Controller实例
     */
    public Object getController() {
        return controller;
    }

    /**
     * 设置Controller实例
     *
     * @param controller Controller实例
     */
    public void setController(Object controller) {
        this.controller = controller;
    }

    /**
     * 获取要调用的方法
     *
     * @return Method对象
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 设置要调用的方法
     *
     * @param method Method对象
     */
    public void setMethod(Method method) {
        this.method = method;
    }

    /**
     * 获取HTTP方法
     *
     * @return HTTP方法字符串
     */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * 设置HTTP方法
     *
     * @param httpMethod HTTP方法字符串
     */
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    /**
     * 获取完整路径模板
     *
     * @return 完整路径模板
     */
    public String getFullPath() {
        return fullPath;
    }

    /**
     * 设置完整路径模板
     *
     * @param fullPath 完整路径模板
     */
    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    /**
     * 重写toString方法，方便调试和日志输出
     *
     * @return 路由信息的字符串表示
     */
    @Override
    public String toString() {
        return httpMethod + ":" + fullPath + " -> " +
                controller.getClass().getSimpleName() + "." + method.getName() + "()";
    }
}
