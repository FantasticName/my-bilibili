# MyBilibili 后端项目概览

## 项目简介

模仿哔哩哔哩的前后端分离网站课设项目。后端纯 JavaWeb 开发，依赖 Tomcat 容器，不使用任何 SSM 框架，手写简易 Spring IoC 容器和 Spring MVC 框架，实现控制反转、依赖注入、路由分发和 JWT 鉴权。

## 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 开发语言 |
| Maven | 依赖管理和项目构建 |
| Tomcat | Web 容器（Servlet 4.0） |
| Lombok | 简化 JavaBean 开发 |
| SLF4J + Logback | 日志框架 |
| Jackson | JSON 序列化/反序列化 |
| JJWT | JWT 令牌生成与解析 |
| Nginx | 前端部署、跨域解决、动静分离 |

## 项目目录结构

```
backend/
├── pom.xml                                         # Maven配置文件
├── logs/                                           # 日志文件目录（运行时生成）
├── uploads/                                        # 用户上传文件目录
└── src/
    └── main/
        ├── java/
        │   └── io/github/fantasticname/mybilibili/
        │       ├── annotation/                     # 自定义注解
        │       │   ├── Autowired.java              # 自动注入注解
        │       │   ├── Component.java              # 组件注解
        │       │   ├── DeleteMapping.java          # DELETE请求映射注解
        │       │   ├── GetMapping.java             # GET请求映射注解
        │       │   ├── PathVariable.java           # 路径变量注解
        │       │   ├── PostMapping.java            # POST请求映射注解
        │       │   ├── PutMapping.java             # PUT请求映射注解
        │       │   ├── RequestBody.java            # 请求体注解
        │       │   ├── RequestMapping.java         # 请求映射注解
        │       │   ├── RequestParam.java           # 请求参数注解
        │       │   ├── RequireAuth.java            # 鉴权注解
        │       │   ├── RestController.java         # REST控制器注解
        │       │   └── Service.java                # 服务层注解
        │       ├── common/                         # 通用类
        │       │   ├── BusinessException.java      # 自定义业务异常类
        │       │   ├── ErrorCode.java              # 错误码枚举类
        │       │   └── Result.java                 # 统一响应结果类
        │       ├── context/                        # 上下文工具
        │       │   ├── TraceIdContext.java         # TraceId上下文工具类
        │       │   └── UserContext.java            # 用户上下文工具类
        │       ├── entity/                         # 实体类
        │       │   └── User.java                   # 用户实体类
        │       ├── filter/                         # 过滤器
        │       │   ├── CharacterEncodingFilter.java  # 字符编码过滤器
        │       │   ├── GlobalExceptionFilter.java    # 全局异常处理过滤器
        │       │   └── TraceIdFilter.java            # TraceId过滤器
        │       ├── interceptor/                    # 拦截器
        │       │   └── AuthInterceptor.java        # 鉴权拦截器
        │       ├── ioc/                            # IoC容器 & MVC
        │       │   ├── HandlerMethod.java          # 路由处理器方法
        │       │   ├── PackageScanner.java         # 包扫描器
        │       │   ├── SimpleContainer.java        # 简易IoC容器
        │       │   └── UriTemplateMatcher.java     # URI模板匹配器
        │       ├── listener/                       # 监听器
        │       │   └── ContainerInitializerListener.java  # IoC容器初始化监听器
        │       ├── servlet/                        # Servlet
        │       │   └── DispatcherServlet.java      # 核心分发器
        │       ├── service/                        # 服务接口
        │       │   └── UserService.java            # 用户服务接口
        │       └── util/                           # 工具类
        │           └── JwtUtil.java                # JWT工具类
        ├── resources/
        │   └── logback.xml                         # 日志配置文件
        └── webapp/
            └── WEB-INF/
                └── web.xml                         # Web应用配置文件
```

## 核心模块说明

### 1. 手写 IoC 容器

参考 Spring IoC 容器的核心原理，手写实现了控制反转和依赖注入功能。

**核心流程：**

1. **包扫描**（`PackageScanner`）：将包名转为路径，通过 ClassLoader 定位资源，递归遍历目录找到所有 `.class` 文件，用 `Class.forName()` 加载
2. **实例化**（`SimpleContainer`）：扫描到带有 `@Component` / `@Service` / `@RestController` 注解的类，通过反射 `newInstance()` 创建实例，存入 `beanMap`
3. **依赖注入**：遍历所有 Bean 的字段，发现 `@Autowired` 注解后，根据字段类型从容器中查找匹配的 Bean，通过 `field.set()` 注入

**支持的注解：**

| 注解 | 作用域 | 说明 |
|------|--------|------|
| `@Component` | 类 | 通用组件注解，标记类交给容器管理 |
| `@Service` | 类 | 业务服务层注解，语义更明确 |
| `@RestController` | 类 | REST控制器注解，标记控制层 |
| `@Autowired` | 字段 | 自动注入注解，按类型注入依赖 |

### 2. 手写 Spring MVC 框架

参考 Spring MVC 的核心原理，手写实现了路由分发和参数绑定功能。

**核心组件：**

- **DispatcherServlet**：核心分发器，拦截所有 `/api/*` 请求，根据 URL 和 HTTP 方法找到对应的 Controller 方法
- **HandlerMethod**：路由信息类，保存一条路由的所有元数据（Controller实例、方法、HTTP方法、路径模板）
- **UriTemplateMatcher**：URI模板匹配器，将 `/user/{id}` 这样的模板与 `/user/123` 匹配，并提取路径变量

**请求处理流程：**

```
请求 → Filter链 → DispatcherServlet.service()
  → findHandler() 查找路由
  → resolveArguments() 解析参数
  → AuthInterceptor.preHandle() 鉴权拦截
  → method.invoke() 反射调用Controller方法
  → writeResponse() 序列化JSON写回响应
```

**支持的 MVC 注解：**

| 注解 | 作用域 | 说明 |
|------|--------|------|
| `@RequestMapping` | 类/方法 | 请求映射，可指定路径，方法上匹配所有HTTP方法 |
| `@GetMapping` | 方法 | GET 请求映射 |
| `@PostMapping` | 方法 | POST 请求映射 |
| `@PutMapping` | 方法 | PUT 请求映射 |
| `@DeleteMapping` | 方法 | DELETE 请求映射 |
| `@PathVariable` | 参数 | 从 URI 模板中提取变量值 |
| `@RequestParam` | 参数 | 从 URL 查询字符串获取参数 |
| `@RequestBody` | 参数 | 从请求体 JSON 反序列化为对象 |

### 3. JWT 登录鉴权

使用 JWT 令牌实现用户登录鉴权和角色权限控制。

**核心组件：**

- **JwtUtil**：JWT 工具类，提供令牌生成（`generateToken`）和解析（`parseToken`）功能
- **AuthInterceptor**：鉴权拦截器，在 Controller 方法执行前进行登录验证和角色鉴权
- **UserContext**：用户上下文，通过 ThreadLocal 将 User 对象与当前请求线程绑定
- **@RequireAuth**：鉴权注解，标记接口需要的角色权限

**鉴权流程：**

```
请求到达 → DispatcherServlet
  → AuthInterceptor.preHandle(method, request)
    → 检查方法/类上的 @RequireAuth 注解
    → 如果没有注解，放行
    → 如果有注解，从请求头取 JWT 令牌
    → 解析令牌，获取 userId
    → 查数据库获取 User 对象
    → 绑定到 UserContext
    → 检查用户角色是否满足要求
  → 调用 Controller 方法
```

**鉴权注解使用：**

| 写法 | 含义 |
|------|------|
| 不加注解 | 不需要登录，任何人可访问（如登录、注册） |
| `@RequireAuth` | 需要登录，任何登录用户可访问 |
| `@RequireAuth("admin")` | 需要管理员角色才能访问 |

### 4. 统一响应格式

所有接口返回统一的 JSON 格式：

```json
{
  "code": 0,
  "data": {},
  "message": "ok",
  "traceId": "550e8400e29b41d4a716446655440000"
}
```

| 字段 | 说明 |
|------|------|
| `code` | 状态码，0 表示成功，其他表示错误 |
| `data` | 业务数据，成功时携带，失败时为 null |
| `message` | 描述信息 |
| `traceId` | 全链路追踪 ID |

**错误码规范：**

| 错误码 | 含义 |
|--------|------|
| 0 | 成功 |
| 40000 | 请求参数错误 |
| 40100 | 未登录 |
| 40101 | 无权限 |
| 40300 | 禁止访问 |
| 40400 | 请求数据不存在 |
| 50000 | 系统内部异常 |
| 50001 | 操作失败 |

### 5. 全局异常处理

通过 `GlobalExceptionFilter` 统一捕获异常，返回前端友好的响应格式：

- `BusinessException`：提取业务异常的 code 和 message 返回
- 其他异常：只返回"系统内部异常"，不暴露技术细节

### 6. TraceId 全链路追踪

每个请求生成唯一的 TraceId，贯穿整个请求处理过程：

- `TraceIdFilter`：请求进来时生成 TraceId，请求结束时清除
- `TraceIdContext`：同时使用 ThreadLocal 和 MDC 存储 TraceId
- 日志格式中自动输出 TraceId：`[%X{traceId}]`
- 响应 Result 对象中携带 TraceId，方便前端反馈问题

### 7. 日志配置

使用 SLF4J + Logback，配置如下：

- 同时输出到控制台 + 文件
- 日志文件放在 `backend/logs` 目录
- 按天自动生成新文件，保留 30 天
- 全局默认 INFO 级别
- `io.github.fantasticname.mybilibili` 包下开启 DEBUG 级别
- ERROR 级别日志单独输出到 `my-bilibili-error.log`

### 8. Filter 链执行顺序

```
请求 → CharacterEncodingFilter → TraceIdFilter → GlobalExceptionFilter → DispatcherServlet → 响应
```

| 顺序 | 过滤器 | 职责 |
|------|--------|------|
| 1 | CharacterEncodingFilter | 统一 UTF-8 编码 |
| 2 | TraceIdFilter | 生成/清除 TraceId |
| 3 | GlobalExceptionFilter | 捕获异常，返回统一格式 |

### 9. 框架层契约接口

`UserService` 是框架层的契约接口，AuthInterceptor 在鉴权时依赖此接口查询用户信息。业务实现类需要实现此接口并使用 `@Service` 注解注册到 IoC 容器。

## 编码规范

- 使用阿里巴巴编码规范编写 Javadoc 注释，author 为 FantasticName
- 方法内使用单行注释解释代码运行逻辑，注释不与代码同行
- 每个类都必须打上详细的 SLF4J 日志
- 后端错误的技术细节不暴露给前端
