好的，同学！你已经搞懂了 IoC 容器，现在咱们来攻克第二个山头——**手写 Web 模块**。

目标：不用 Spring MVC，自己实现 `@RestController`、`@GetMapping`、`@PostMapping`、`@RequestBody`、`@PathVariable`、`@RequestParam` 这些注解，并且用**内嵌 Tomcat** 跑起来。

------

## 第一课：没有 Spring MVC 时，Java 怎么处理 HTTP 请求？

Java 原生处理 HTTP 请求的方式是 **Servlet**。你写过 `HttpServlet` 吗？大概长这样：

java

```
@WebServlet("/user")
public class UserServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String userId = req.getParameter("id");
        resp.getWriter().write("User id: " + userId);
    }
}
```



问题是：**每写一个接口就要新建一个 Servlet 类**，太麻烦了。

Spring MVC 做的事情就是：**只用一个 Servlet（叫 `DispatcherServlet`）拦截所有请求，然后根据 URL 和方法（GET/POST）找到对应的 Controller 方法，反射调用它，最后把返回值写回响应。**

我们要手写的，就是这个 `DispatcherServlet`。

------

## 第二课：自定义注解，定义“路由规则”

首先仿照 Spring 造一套注解。

java

```java
// 贴在类上，表示这是一个 Controller，同时可指定根路径
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyRestController {
    String value() default "";
}

// 请求方法映射注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyGetMapping {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyPostMapping {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyPutMapping {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyDeleteMapping {
    String value() default "";
}
```



参数绑定注解：

java

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MyPathVariable {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MyRequestParam {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MyRequestBody {
}
```



------

## 第三课：写一个 Controller 用用看

假设我们有一个用户模块：

java

```java
@MyRestController("/user")
public class UserController {

    // GET /user/1
    @MyGetMapping("/{id}")
    public User getUser(@MyPathVariable("id") Long id) {
        return new User(id, "张三", "zhangsan@example.com");
    }

    // POST /user
    @MyPostMapping
    public Result createUser(@MyRequestBody User user) {
        System.out.println("创建用户：" + user);
        return Result.success("用户创建成功");
    }

    // GET /user/search?name=xxx
    @MyGetMapping("/search")
    public List<User> search(@MyRequestParam("name") String name) {
        // 模拟查询
        return Arrays.asList(new User(1L, name, "test@test.com"));
    }
}
```



------

## 第四课：核心组件 DispatcherServlet 的实现逻辑

### ==4.1 初始化时建立“路由映射表”==

`DispatcherServlet` 启动时要扫描所有带 `@MyRestController` 的类，分析每个方法上的 `@MyGetMapping` 等注解，建立映射关系。

数据结构：

java

```java
// 保存一条路由信息
class HandlerMethod {
    Object controller;      // Controller 实例（从 IoC 容器拿）
    Method method;          // 要调用的方法
    String httpMethod;      // GET / POST / PUT / DELETE
    String fullPath;        // 完整路径，如 /user/{id}
    // 参数列表信息稍后补充
}

// 映射表：key = "GET:/user/{id}" , value = HandlerMethod
Map<String, HandlerMethod> handlerMap = new HashMap<>();
```



### ==4.2 请求到达时的处理流程==

1. 从 `HttpServletRequest` 获取请求方法（GET/POST）和请求 URI。
2. 拼接 key = `方法:URI`，去 `handlerMap` 找 `HandlerMethod`。
3. 如果找不到，返回 404。
4. 解析方法参数：
   - 遍历方法的 `Parameter`，看上面贴了什么注解。
   - 如果是 `@MyPathVariable`，从 URI 模板中提取对应变量。
   - 如果是 `@MyRequestParam`，从 `request.getParameter()` 获取。
   - 如果是 `@MyRequestBody`，读取请求体 JSON，用 Jackson 反序列化成对象。
5. 通过反射 `method.invoke(controller, args)` 调用方法，得到返回值。
6. 将返回值（对象）序列化成 JSON，写入 `HttpServletResponse`。

------

## 第五课：难点攻克——路径变量匹配

URL 模板如 `/user/{id}` 需要匹配实际请求 `/user/123`，并提取出 `id=123`。

手写一个简单的 `UriTemplateMatcher`：

java

```java
public class UriTemplateMatcher {
    
    public static boolean matches(String template, String actualPath, Map<String, String> variables) {
        String[] templateParts = template.split("/");
        String[] actualParts = actualPath.split("/");
        
        if (templateParts.length != actualParts.length) {
            return false;
        }
        
        for (int i = 0; i < templateParts.length; i++) {
            String tPart = templateParts[i];
            String aPart = actualParts[i];
            
            if (tPart.startsWith("{") && tPart.endsWith("}")) {
                // 变量，提取并存入 map
                String varName = tPart.substring(1, tPart.length() - 1);
                variables.put(varName, aPart);
            } else if (!tPart.equals(aPart)) {
                return false;
            }
        }
        return true;
    }
}
```



然后在 `DispatcherServlet` 中用循环匹配：

java

```java
// 遍历 handlerMap 的 entry，用模板去匹配实际路径
for (Map.Entry<String, HandlerMethod> entry : handlerMap.entrySet()) {
    String key = entry.getKey();
    String[] parts = key.split(":", 2);
    String httpMethod = parts[0];
    String template = parts[1];
    
    if (!httpMethod.equals(request.getMethod())) continue;
    
    Map<String, String> pathVars = new HashMap<>();
    if (UriTemplateMatcher.matches(template, actualPath, pathVars)) {
        // 找到！
        // 将 pathVars 存起来，供参数解析时使用
        request.setAttribute("pathVariables", pathVars);
        return entry.getValue();
    }
}
```



------

## 第六课：参数解析与类型转换

controller类的方法的参数,  可能带有不同的注解，我们需要逐个处理。

java

```java
private Object[] resolveArguments(HandlerMethod handler,  // 单条路由信息
                                  HttpServletRequest request, 
                                  HttpServletResponse response) throws Exception {
    Parameter[] parameters = handler.method.getParameters();
    Object[] args = new Object[parameters.length];
    
    // 获取路径变量
    Map<String, String> pathVariables = (Map<String, String>) request.getAttribute("pathVariables");
    
    for (int i = 0; i < parameters.length; i++) {
        Parameter param = parameters[i];
        Class<?> type = param.getType();
        
        // 特殊类型直接注入
        if (type == HttpServletRequest.class) {
            args[i] = request;
        } else if (type == HttpServletResponse.class) {
            args[i] = response;
        }
        // 处理 @MyPathVariable
        else if (param.isAnnotationPresent(MyPathVariable.class)) {
            MyPathVariable ann = param.getAnnotation(MyPathVariable.class);
            String varName = ann.value().isEmpty() ? param.getName() : ann.value();
            String value = pathVariables.get(varName);
            args[i] = convertValue(value, type);
        }
        // 处理 @MyRequestParam
        else if (param.isAnnotationPresent(MyRequestParam.class)) {
            MyRequestParam ann = param.getAnnotation(MyRequestParam.class);
            String paramName = ann.value().isEmpty() ? param.getName() : ann.value();
            String value = request.getParameter(paramName);
            args[i] = convertValue(value, type);
        }
        // 处理 @MyRequestBody
        else if (param.isAnnotationPresent(MyRequestBody.class)) {
            // 读取请求体 JSON，用 Jackson 转换
            String body = readRequestBody(request);
            ObjectMapper mapper = new ObjectMapper();
            args[i] = mapper.readValue(body, type);
        }
        else {
            // 没有注解，尝试按参数名匹配（简化，暂时忽略）
            args[i] = null;
        }
    }
    return args;
}
```



类型转换 `convertValue` 方法：

java

```java
private Object convertValue(String value, Class<?> targetType) {
    if (value == null) return null;
    if (targetType == String.class) return value;
    if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
    if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
    if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
    // 更多类型可继续添加
    throw new IllegalArgumentException("不支持的类型转换: " + targetType);
}
```



读取请求体：

java

```
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
```



------

## 第七课：返回结果处理与 JSON 序列化

Controller 方法返回的对象需要序列化成 JSON 字符串写回响应。

java

```
Object result = handler.method.invoke(handler.controller, args);

// 设置响应头
response.setContentType("application/json;charset=UTF-8");
PrintWriter writer = response.getWriter();

// 用 Jackson 把结果转成 JSON
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(result);
writer.write(json);
```



------

## 第八课：集成内嵌 Tomcat

不用外部 Tomcat，我们直接在 `main` 方法里启动一个 Tomcat 实例。

添加依赖（Maven）：

xml

```xml
<dependency>
    <groupId>org.apache.tomcat.embed</groupId>
    <artifactId>tomcat-embed-core</artifactId>
    <version>10.1.18</version>
</dependency>
<dependency>
    <groupId>org.apache.tomcat.embed</groupId>
    <artifactId>tomcat-embed-jasper</artifactId>
    <version>10.1.18</version>
</dependency>
<!-- Jackson 用于 JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.3</version>
</dependency>
```



启动类：

java

```java
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

public class MyApplication {
    public static void main(String[] args) throws Exception {
        // 1. 初始化 IoC 容器
        SimpleContainer container = new SimpleContainer();
        container.init("com.example");
        
        // 2. 创建 DispatcherServlet
        DispatcherServlet dispatcherServlet = new DispatcherServlet(container);
        dispatcherServlet.init();  // 扫描并建立路由映射
        
        // 3. 启动内嵌 Tomcat
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        
        // 设置上下文路径
        Context ctx = tomcat.addContext("", null);
        
        // 将 DispatcherServlet 注册到 Tomcat，拦截所有请求
        Tomcat.addServlet(ctx, "dispatcher", dispatcherServlet);
        ctx.addServletMappingDecoded("/*", "dispatcher");
        
        tomcat.start();
        System.out.println("Tomcat started on http://localhost:8080");
        tomcat.getServer().await(); // 保持运行
    }
}
```



------

## 第九课：DispatcherServlet 完整代码骨架

java

```java
public class DispatcherServlet extends HttpServlet {
    
    private SimpleContainer container;
    
    // 路由表 ( DispatcherServlet依赖一个单例的HandlerMap对象 ) 
    private Map<String, HandlerMethod> handlerMap = new HashMap<>();
    
    public DispatcherServlet(SimpleContainer container) {
        this.container = container;
    }
    
    // 初始化时扫描所有 Controller，建立路由表
    public void init() {
        // 从IoC容器中获取所有 Bean
        for (Object bean : container.getAllBeans()) {
            Class<?> clazz = bean.getClass();
            // 如果这个类上面没有RestController 注解，continue
            if (!clazz.isAnnotationPresent(MyRestController.class)) continue;
            
            // 现在拿到了一个有RestController注解的类，赋值给引用类型变量`bean`
            
            // 获取打在类上的RestController注解的 URL路径（basePath ）
            MyRestController restAnno = clazz.getAnnotation(MyRestController.class);
            String basePath = restAnno.value();
            
            // 遍历这个bean的所有方法
            for (Method method : clazz.getDeclaredMethods()) {
                String subPath = "";
                String httpMethod = null;
                
                if (method.isAnnotationPresent(MyGetMapping.class)) {
                    subPath = method.getAnnotation(MyGetMapping.class).value();
                    httpMethod = "GET";
                } else if (method.isAnnotationPresent(MyPostMapping.class)) {
                    subPath = method.getAnnotation(MyPostMapping.class).value();
                    httpMethod = "POST";
                } // ... 其他类似
                
                if (httpMethod != null) {
                    String fullPath = basePath + subPath;
                    
                    // 开始组装一条路由信息(前文提到了HandlerMethod类的一个对象存的就是一条路由信息)
                    HandlerMethod hm = new HandlerMethod();
                    hm.controller = bean;
                    hm.method = method;
                    hm.httpMethod = httpMethod;
                    hm.fullPath = fullPath;
                    
                    String key = httpMethod + ":" + fullPath;// 比如 get:/api/user/1
                    
                    // 存入路由表 (单例handlerMap对象)
                    handlerMap.put(key, hm);
                    
                    System.out.println("映射路由：" + key);
                }
            }
        }
    }
    
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        try {
            // 1. 匹配 Handler ,获取这个req对应的HandlerMethod(单条路由信息)对象
            HandlerMethod handler = findHandler(req);
            if (handler == null) {
                resp.sendError(404, "Not Found");
                return;
            }
            
            // 2. 解析参数
            Object[] args = resolveArguments(handler, req, resp);
            
            // 3. 通过反射调用方法
            Object result = handler.method.invoke(handler.controller, args);
            
            // 4. 写响应
            resp.setContentType("application/json;charset=UTF-8");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(resp.getWriter(), result);
            
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, e.getMessage());
        }
    }
    
    private HandlerMethod findHandler(HttpServletRequest req) {
        String method = req.getMethod();
        String path = req.getRequestURI();
        String contextPath = req.getContextPath();
        String actualPath = path.substring(contextPath.length());
        
        // 遍历映射表，匹配路径模板
        for (Map.Entry<String, HandlerMethod> entry : handlerMap.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":", 2);
            String httpMethod = parts[0];
            String template = parts[1];
            
            if (!httpMethod.equals(method)) continue;
            
            Map<String, String> vars = new HashMap<>();
            if (UriTemplateMatcher.matches(template, actualPath, vars)) {
                req.setAttribute("pathVariables", vars);
                return entry.getValue();
            }
        }
        return null;
    }
    
    // resolveArguments 和 convertValue 方法如前所述
}
```



------

## 第十课：测试一下

用 Postman 或浏览器测试：

- `GET http://localhost:8080/user/1` → 返回 JSON `{"id":1,"name":"张三","email":"..."}`
- `POST http://localhost:8080/user` + JSON body → 返回 `{"code":200,"message":"用户创建成功"}`
- `GET http://localhost:8080/user/search?name=李四` → 返回数组

------

## 总结：Spring MVC 的核心你已掌握

| Spring 功能       | 手写实现的核心                         |
| :---------------- | :------------------------------------- |
| `@RestController` | 类上注解 + 扫描时识别并注册到容器      |
| `@GetMapping` 等  | 方法上注解 + 建立 `方法:路径` 映射表   |
| `@PathVariable`   | URI 模板匹配 + 提取变量 + 类型转换     |
| `@RequestParam`   | `request.getParameter()` + 类型转换    |
| `@RequestBody`    | 读取请求体 + Jackson 反序列化          |
| 返回 JSON         | Jackson 序列化 + 写入响应              |
| 内嵌 Tomcat       | `Tomcat` 类 + `addServlet` + `start()` |

你现在已经用纯 Java 手写了一个微型 Spring MVC 框架。