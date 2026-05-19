# MyBilibili 项目概览

> 模仿哔哩哔哩（Bilibili）的前后端分离全栈课设项目，后端手写 Spring + SpringMVC 框架，前端使用 Vue3 + Bilibili 风格 UI，Nginx 实现反向代理与动静分离。

---

## 一、项目架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                        浏览器 (Client)                        │
│                  http://localhost:80                          │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                    Nginx (端口 80)                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ /            │  │ /api/        │  │ /upload/     │       │
│  │ → Vite:5173  │  │ → Tomcat:8080│  │ → 静态文件    │       │
│  │ (前端开发服务) │  │ (后端API)    │  │ (上传文件)    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└──────────────────────┬───────────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Vite Dev     │ │ Tomcat       │ │ uploads/     │
│ Server       │ │ (后端)       │ │ 目录         │
│ :5173        │ │ :8080        │ │              │
└──────────────┘ └──────┬───────┘ └──────────────┘
                        │
              ┌─────────┼─────────┐
              ▼         ▼         ▼
        ┌─────────┐ ┌────────┐ ┌────────┐
        │ MySQL   │ │ Redis  │ │ 文件   │
        │ :3306   │ │ :6379  │ │ 系统   │
        └─────────┘ └────────┘ └────────┘
```

### 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue3 + Vite | 组合式 API、响应式系统 |
| 状态管理 | Pinia | 轻量级状态管理，替代 Vuex |
| 路由 | Vue Router 4 | SPA 路由，支持路由守卫 |
| HTTP 客户端 | Axios | 请求/响应拦截器，统一错误处理 |
| 反向代理 | Nginx 1.30 | 跨域解决、动静分离、Gzip 压缩 |
| 后端框架 | 手写 IoC + MVC | 自研注解 + 反射，无 SSM 依赖 |
| 数据库 | MySQL 8.0 | utf8mb4 字符集，InnoDB 引擎 |
| 连接池 | 自研 ConnectionPool | Semaphore + BlockingQueue |
| 缓存 | Redis + Jedis + Caffeine | Token存储、自动续期、Sorted Set收件箱、Lua脚本、本地缓存 |
| 分布式锁 | Redisson 3.25 | RLock可重入锁、看门狗自动续期、防缓存击穿 |
| 消息队列 | RocketMQ 5.1.4 | 事务消息、异步解耦、削峰填谷 |
| 流量控制 | Sentinel 1.8.6 | QPS限流、异常比例熔断、系统保护、降级兜底 |
| 布隆过滤器 | Guava 32.1.3 | 双布隆+原子切换，防缓存穿透，周期性重建 |
| 本地缓存 | Caffeine 3.1.8 | 大V发件箱本地缓存，30s TTL，减少Redis请求 |
| 实时推送 | WebSocket | javax.websocket，通知实时推送 |
| 认证 | JWT (JJWT) | 无状态令牌 + Redis 双重验证 |
| 密码加密 | BCrypt | 自带盐值，无需单独存储 |
| 序列化 | Jackson + JavaTimeModule | JSON 序列化，支持 LocalDateTime |
| 日志 | SLF4J + Logback | MDC 链路追踪 |
| 构建 | Maven | WAR 包部署到 Tomcat |

---

## 二、项目目录结构

```
my-bilibili/
├── backend/                          # 后端项目
│   ├── src/main/java/io/github/fantasticname/mybilibili/
│   │   ├── annotation/               # 自定义注解
│   │   │   ├── Autowired.java        #   依赖注入注解
│   │   │   ├── Component.java        #   组件注解
│   │   │   ├── Service.java          #   服务层注解
│   │   │   ├── RestController.java   #   控制器注解
│   │   │   ├── RequestMapping.java   #   路径映射注解
│   │   │   ├── GetMapping.java       #   GET 请求映射
│   │   │   ├── PostMapping.java      #   POST 请求映射
│   │   │   ├── PutMapping.java       #   PUT 请求映射
│   │   │   ├── DeleteMapping.java    #   DELETE 请求映射
│   │   │   ├── RequestBody.java      #   请求体注解
│   │   │   ├── RequestParam.java     #   查询参数注解
│   │   │   ├── PathVariable.java     #   路径变量注解
│   │   │   ├── RequireAuth.java      #   鉴权注解（兼容保留）
│   │   │   └── RequirePermission.java #  RBAC0权限注解
│   │   ├── common/                   # 通用类
│   │   │   ├── Result.java           #   统一响应封装
│   │   │   ├── ErrorCode.java        #   错误码枚举
│   │   │   └── BusinessException.java#   业务异常
│   │   ├── context/                  # 线程上下文
│   │   │   ├── UserContext.java      #   用户上下文 (ThreadLocal)
│   │   │   └── TraceIdContext.java   #   链路追踪上下文
│   │   ├── controller/               # 控制器层
│   │   │   ├── UserController.java   #   用户接口控制器
│   │   │   ├── VideoController.java  #   视频接口控制器
│   │   │   ├── CommentController.java#   评论接口控制器
│   │   │   ├── FollowController.java #   关注接口控制器
│   │   │   ├── LikeController.java   #   点赞接口控制器
│   │   │   ├── FavoriteController.java#  收藏接口控制器
│   │   │   ├── PostController.java   #   动态接口控制器
│   │   │   └── FeedController.java   #   Feed流接口控制器
│   │   │   └── SearchController.java  #   搜索接口控制器
│   │   │   └── FileController.java    #   文件上传控制器
│   │   ├── dao/                      # 数据访问层
│   │   │   ├── base/
│   │   │   │   ├── BaseDao.java      #   泛型 DAO 基类
│   │   │   │   ├── ConnectionHolder.java # 连接持有者
│   │   │   │   └── ResultSetMapper.java  # 结果集映射器
│   │   │   ├── UserDao.java          #   用户 DAO
│   │   │   ├── VideoDao.java         #   视频 DAO
│   │   │   ├── CommentDao.java       #   评论 DAO
│   │   │   ├── FollowDao.java        #   关注 DAO
│   │   │   ├── LikeRecordDao.java    #   点赞 DAO
│   │   │   ├── FavoriteFolderDao.java#   收藏夹 DAO
│   │   │   ├── FavoriteRecordDao.java#   收藏记录 DAO
│   │   │   ├── PostDao.java          #   动态 DAO
│   │   │   ├── RoleDao.java          #   角色 DAO
│   │   │   ├── PermissionDao.java    #   权限 DAO
│   │   │   ├── UserRoleDao.java      #   用户角色关联 DAO
│   │   │   └── RolePermissionDao.java#   角色权限关联 DAO
│   │   ├── dto/                      # 数据传输对象
│   │   │   ├── RegisterDTO.java      #   注册请求 DTO
│   │   │   ├── LoginDTO.java         #   登录请求 DTO
│   │   │   ├── UpdateProfileDTO.java #   修改信息 DTO
│   │   │   ├── PublishVideoDTO.java  #   发布视频 DTO
│   │   │   ├── CreateCommentDTO.java #   创建评论 DTO
│   │   │   ├── LikeRequestDTO.java   #   点赞请求 DTO
│   │   │   ├── FavoriteRequestDTO.java#  收藏请求 DTO
│   │   │   └── CreatePostDTO.java    #   创建动态 DTO
│   │   ├── entity/                   # 实体类
│   │   │   ├── User.java             #   用户实体
│   │   │   ├── Video.java            #   视频实体
│   │   │   ├── Comment.java          #   评论实体
│   │   │   ├── Follow.java           #   关注实体
│   │   │   ├── LikeRecord.java       #   点赞实体
│   │   │   ├── FavoriteFolder.java   #   收藏夹实体
│   │   │   ├── FavoriteRecord.java   #   收藏记录实体
│   │   │   ├── Post.java             #   动态实体
│   │   │   ├── Role.java             #   角色实体
│   │   │   ├── Permission.java       #   权限实体
│   │   │   ├── UserRole.java         #   用户角色关联实体
│   │   │   └── RolePermission.java   #   角色权限关联实体
│   │   ├── filter/                   # Servlet 过滤器
│   │   │   ├── CharacterEncodingFilter.java # 编码过滤器
│   │   │   ├── TraceIdFilter.java    #   链路追踪过滤器
│   │   │   ├── GlobalExceptionFilter.java   # 全局异常过滤器
│   │   │   └── AuthFilter.java       #   用户上下文清理过滤器
│   │   ├── interceptor/              # 拦截器
│   │   │   └── AuthInterceptor.java  #   鉴权拦截器
│   │   ├── ioc/                      # IoC 容器
│   │   │   ├── SimpleContainer.java  #   简易 IoC 容器
│   │   │   ├── PackageScanner.java   #   包扫描器
│   │   │   ├── HandlerMethod.java    #   处理方法封装
│   │   │   ├── UriTemplateMatcher.java #  URI 模板匹配器
│   │   │   ├── TypeConverter.java    #   类型转换器接口
│   │   │   └── TypeConverterRegistry.java # 类型转换器注册表
│   │   ├── listener/                 # 监听器
│   │   │   └── ContainerInitializerListener.java # IoC 容器初始化监听器
│   │   ├── service/                  # 服务层
│   │   │   ├── UserService.java      #   用户服务接口
│   │   │   ├── UserServiceImpl.java  #   用户服务实现
│   │   │   ├── VideoService.java     #   视频服务接口
│   │   │   ├── VideoServiceImpl.java #   视频服务实现
│   │   │   ├── CommentService.java   #   评论服务接口
│   │   │   ├── CommentServiceImpl.java#  评论服务实现
│   │   │   ├── FollowService.java    #   关注服务接口
│   │   │   ├── FollowServiceImpl.java#   关注服务实现
│   │   │   ├── LikeService.java      #   点赞服务接口
│   │   │   ├── LikeServiceImpl.java  #   点赞服务实现
│   │   │   ├── FavoriteService.java  #   收藏服务接口
│   │   │   ├── FavoriteServiceImpl.java# 收藏服务实现
│   │   │   ├── PostService.java      #   动态服务接口
│   │   │   ├── PostServiceImpl.java  #   动态服务实现
│   │   │   ├── FeedService.java      #   Feed服务接口
│   │   │   ├── FeedServiceImpl.java  #   Feed服务实现
│   │   │   ├── SearchService.java     #   搜索服务接口
│   │   │   └── SearchServiceImpl.java #   搜索服务实现
│   │   │   ├── FileService.java      #    文件上传服务接口
│   │   │   └── FileServiceImpl.java  #    文件上传服务实现
│   │   ├── servlet/                  # Servlet
│   │   │   └── DispatcherServlet.java#   核心分发器
│   │   ├── util/                     # 工具类
│   │   │   ├── ConnectionPool.java   #   数据库连接池
│   │   │   ├── TxManager.java        #   事务管理器
│   │   │   ├── TxSupplier.java       #   事务函数式接口
│   │   │   ├── JwtUtil.java          #   JWT 工具类
│   │   │   ├── RedisUtil.java        #   Redis 工具类
│   │   │   ├── FileUtil.java         #   文件上传工具
│   │   │   └── RegexUtil.java        #   正则校验工具
│   │   └── vo/                       # 视图对象
│   │       ├── UserVO.java           #   用户信息 VO
│   │       ├── LoginVO.java          #   登录响应 VO
│   │       ├── ProfileVO.java        #   个人信息 VO（含统计）
│   │       ├── PublicUserVO.java     #   公开用户信息 VO
│   │       ├── VideoVO.java          #   视频 VO
│   │       ├── CommentVO.java        #   评论 VO
│   │       ├── PostVO.java           #   动态 VO
│   │       └── SearchResultVO.java    #   搜索结果 VO
│   ├── src/main/resources/
│   │   ├── db.properties             # 数据库配置
│   │   ├── env.properties            # 环境变量配置
│   │   └── logback.xml               # 日志配置
│   ├── src/main/webapp/WEB-INF/
│   │   └── web.xml                   # Web 应用部署描述符
│   ├── src/test/java/                # 单元测试
│   ├── uploads/                      # 上传文件存储目录
│   ├── init.sql                      # 数据库初始化脚本
│   └── pom.xml                       # Maven 配置
├── frontend/                         # 前端项目
│   ├── src/
│   │   ├── api/
│   │   │   ├── request.js            # Axios 实例与拦截器
│   │   │   ├── user.js               # 用户相关 API
│   │   │   ├── video.js              # 视频相关 API
│   │   │   ├── comment.js            # 评论相关 API
│   │   │   ├── follow.js             # 关注相关 API
│   │   │   ├── like.js               # 点赞相关 API
│   │   │   ├── favorite.js           # 收藏相关 API
│   │   │   ├── post.js               # 动态相关 API
│   │   │   └── feed.js               # Feed流相关 API
│   │   ├── components/
│   │   │   ├── NavBar.vue            # 导航栏组件
│   │   │   └── Message.vue           # 消息提示组件
│   │   ├── composables/
│   │   │   └── useMessage.js         # 消息提示组合式函数
│   │   ├── router/
│   │   │   └── index.js              # 路由配置
│   │   ├── stores/
│   │   │   └── user.js               # 用户状态管理
│   │   ├── views/
│   │   │   ├── Home.vue              # 首页
│   │   │   ├── Login.vue             # 登录页
│   │   │   ├── Register.vue          # 注册页
│   │   │   ├── Profile.vue           # 个人中心页
│   │   │   ├── VideoList.vue         # 视频列表页
│   │   │   ├── VideoDetail.vue       # 视频详情页
│   │   │   ├── VideoPublish.vue      # 视频发布页
│   │   │   ├── PostPublish.vue        # 发布动态页面
│   │   │   ├── PostDetail.vue         # 动态详情页面
│   │   │   ├── Feed.vue              # Feed流页面
│   │   │   └── UserCenter.vue        # 用户中心页面
│   │   ├── App.vue                   # 根组件
│   │   ├── main.js                   # 应用入口
│   │   └── style.css                 # 全局样式
│   ├── index.html                    # HTML 模板
│   ├── vite.config.js                # Vite 配置
│   └── package.json                  # 依赖配置
├── 更新日志.md                        # 代码修改记录
└── PROJECT_OVERVIEW.md               # 本文件
```

---

## 三、功能模块

### 3.1 已实现功能

| 功能 | 描述 | 状态 |
|------|------|------|
| 用户注册 | 手机号 + 密码 + 昵称注册，管理员注册需邀请码 | ✅ 已完成 |
| 用户登录 | 手机号 + 密码登录，返回 JWT Token | ✅ 已完成 |
| 个人信息查看 | 查看昵称、手机号、头像、角色、注册时间、关注数、粉丝数、获赞数 | ✅ 已完成 |
| 修改昵称 | 修改用户昵称，1-20位中英文/数字/下划线 | ✅ 已完成 |
| 修改手机号 | 需验证旧密码，新手机号唯一性校验 | ✅ 已完成 |
| 修改密码 | 需验证旧密码，两次新密码一致性校验 | ✅ 已完成 |
| 头像上传 | multipart/form-data 上传，白名单校验，UUID 重命名 | ✅ 已完成 |
| 退出登录 | 删除 Redis 中的 Token，前端清除本地存储 | ✅ 已完成 |
| Token 自动续期 | 每次请求刷新 Redis 中 Token 的 TTL | ✅ 已完成 |
| 全链路追踪 | 每个请求生成唯一 TraceId，贯穿日志与响应 | ✅ 已完成 |
| 全局异常处理 | 统一捕获异常，返回标准错误响应格式 | ✅ 已完成 |
| 视频发布 | 博主上传视频，含标题、简介、封面 | ✅ 已完成 |
| 视频列表 | 分页浏览所有视频，按发布时间倒序 | ✅ 已完成 |
| 视频详情 | 视频播放，播放量自动递增 | ✅ 已完成 |
| 视频删除 | 作者或管理员可删除视频 | ✅ 已完成 |
| 评论发表 | 对视频或动态发表评论，支持无限层级嵌套回复（parent_id） | ✅ 已完成 |
| 评论列表 | 热门排序（按点赞数降序）+ 游标分页 + O(n)树形组装，每个顶层评论最多3条子回复 | ✅ 已完成 |
| 评论删除 | 普通用户删除自己的评论，管理员可删除任何评论（软删除） | ✅ 已完成 |
| 关注/取关 | 用户关注/取关其他用户 | ✅ 已完成 |
| 关注列表 | 查看用户的关注列表 | ✅ 已完成 |
| 粉丝列表 | 查看用户的粉丝列表 | ✅ 已完成 |
| 点赞/取消 | 对视频或评论点赞/取消 | ✅ 已完成 |
| 点赞状态 | 查询当前用户对目标的点赞状态 | ✅ 已完成 |
| 一键二连 | 点赞视频 + 收藏进默认收藏夹（单向操作，只添加不取消） | ✅ 已完成 |
| 收藏/取消 | 将视频收藏到指定收藏夹/取消收藏 | ✅ 已完成 |
| 收藏夹管理 | 创建、删除收藏夹，查看收藏夹内容 | ✅ 已完成 |
| 发布动态 | 用户发布文字动态 | ✅ 已完成 |
| 动态发布 | 发布动态（支持纯文字/纯图片/文字+图片，最多9张图） | ✅ 已完成 |
| 动态详情 | 查看动态详情（内容、图片、点赞数、评论数） | ✅ 已完成 |
| 动态点赞 | 对动态点赞/取消点赞（targetType=3） | ✅ 已完成 |
| 动态评论 | 对动态发表评论（复用comment表，targetType=2） | ✅ 已完成 |
| 动态列表 | 查看用户的动态列表（游标分页，瀑布流） | ✅ 已完成 |
| 关注Feed流 | 查看关注用户的动态（游标分页） | ✅ 已完成 |
| 综合搜索 | 同时搜索视频标题、动态内容、用户昵称 | ✅ 已完成 |
| 视频搜索 | 按标题模糊搜索视频，分页 | ✅ 已完成 |
| 动态搜索 | 按内容模糊搜索动态，分页 | ✅ 已完成 |
| 用户搜索 | 按昵称模糊搜索用户，分页 | ✅ 已完成 |
| RBAC0 权限 | 用户-角色-权限三表关联，@RequirePermission注解鉴权 | ✅ 已完成 |

### 3.2 预留功能

| 功能 | 描述 | 状态 |
|------|------|------|
| 弹幕系统 | 视频弹幕发送与显示 | 🔲 预留 |
| 消息通知 | 点赞、评论、关注等实时通知 | 🔲 预留 |
| 数据统计 | 视频播放量、点赞数等数据看板 | 🔲 预留 |

---

## 四、后端框架核心实现

### 4.1 IoC 容器（SimpleContainer）

**实现原理**：通过自定义注解 + Java 反射，实现类似 Spring IoC 的控制反转和依赖注入。
**工作流程**：

```
ContainerInitializerListener.contextInitialized()
    │
    ▼
SimpleContainer.init("io.github.fantasticname.mybilibili")
    │
    ├── 1. PackageScanner.scan() → 扫描包下所有 .class 文件
    │
    ├── 2. 过滤出带 @Component / @Service / @RestController 注解的类
    │
    ├── 3. 反射创建实例 (clazz.getDeclaredConstructor().newInstance())
    │       → 存入 beanMap (ConcurrentHashMap)
    │
    └── 4. 遍历所有 Bean，检查字段上的 @Autowired 注解
            → 从 beanMap 中找到类型匹配的 Bean
            → field.setAccessible(true) + field.set() 注入
```

**Bean 名称生成规则**：
- 注解指定了 value（如 `@Service("myService")`）→ 使用指定名称
- 否则使用类名首字母小写（如 `UserServiceImpl` → `userServiceImpl`）

**支持的注解**：

| 注解 | 作用 | 等价 Spring 注解 |
|------|------|------------------|
| `@Component` | 通用组件标记 | `@Component` |
| `@Service` | 服务层标记 | `@Service` |
| `@RestController` | 控制器标记 | `@RestController` |
| `@Autowired` | 字段依赖注入 | `@Autowired` |

### 4.2 MVC 框架（DispatcherServlet）

**实现原理**：手写 Spring MVC 核心分发逻辑，通过注解驱动路由映射和参数解析。

**请求处理流程**：

```
HTTP 请求 → Tomcat
    │
    ▼
Filter 链（按 web.xml 配置顺序）：
    ├── CharacterEncodingFilter    → 设置 UTF-8 编码
    ├── TraceIdFilter              → 生成 TraceId，绑定 ThreadLocal + MDC
    ├── GlobalExceptionFilter      → try-catch 包裹，统一异常处理
    └── AuthFilter                 → finally 中清理 UserContext
    │
    ▼
DispatcherServlet.service()
    │
    ├── 1. findHandler()           → 根据 HTTP 方法 + URL 查找 HandlerMethod
    │       ├── 静态路由：Map<"GET:/user/profile", HandlerMethod>
    │       └── 动态路由：UriTemplateMatcher 匹配 {id} 等路径变量
    │
    ├── 2. resolveArguments()      → 解析方法参数
    │       ├── HttpServletRequest / HttpServletResponse → 直接注入
    │       ├── @PathVariable      → 从路径变量中提取并类型转换
    │       ├── @RequestParam      → 从查询参数中提取并类型转换
    │       └── @RequestBody       → 读取请求体，Jackson 反序列化
    │
    ├── 3. AuthInterceptor.preHandle() → 鉴权检查（支持RBAC0）
    │       ├── 检查方法/类上是否有 @RequirePermission 或 @RequireAuth
    │       ├── 从 Authorization 头提取 Token
    │       ├── JwtUtil.parseToken() 解析 Token
    │       ├── RedisUtil.getUserByToken() 从 Redis 获取用户
    │       ├── UserContext.set() 绑定用户到当前线程
    │       ├── RBAC0权限鉴权：@RequirePermission → 查询用户权限列表，校验权限码
    │       └── 角色鉴权（兼容）：@RequireAuth("admin") → 检查 role == 2
    │
    ├── 4. method.invoke()         → 反射调用 Controller 方法
    │
    └── 5. writeResponse()         → Jackson 序列化结果，写入响应
            → Result 对象自动设置 traceId
```

**路由映射注解**：

| 注解 | HTTP 方法 | 示例 |
|------|-----------|------|
| `@RequestMapping("/user")` | 类级别基础路径 | 控制器根路径 |
| `@GetMapping("/profile")` | GET | 查询操作 |
| `@PostMapping("/login")` | POST | 创建/提交操作 |
| `@PutMapping("/profile")` | PUT | 更新操作 |
| `@DeleteMapping("/{id}")` | DELETE | 删除操作 |

**参数解析注解**：

| 注解 | 参数来源 | 示例 |
|------|----------|------|
| `@RequestBody` | 请求体 JSON | `@RequestBody RegisterDTO dto` |
| `@RequestParam` | URL 查询参数 | `@RequestParam("page") int page` |
| `@PathVariable` | URL 路径变量 | `@PathVariable("id") Long id` |

### 4.3 数据库访问层（BaseDao + ResultSetMapper）

**实现原理**：泛型 DAO 基类 + 反射结果集映射，消除重复 JDBC 模板代码。

**BaseDao 核心方法**：

| 方法 | 功能 | 返回值 |
|------|------|--------|
| `queryOne(sql, params)` | 查询单条记录 | T 或 null |
| `queryList(sql, params)` | 查询多条记录 | List\<T\> |
| `executeUpdate(sql, params)` | 执行更新/删除 | void |
| `executeInsert(sql, params)` | 执行插入并返回自增主键 | long |

**连接获取策略（borrow 方法）**：

```
borrow()
    │
    ├── TxManager.currentConnection() 非空？
    │       ├── 是 → 复用事务连接（borrowed=false，不归还）
    │       └── 否 → 从连接池新借一个（borrowed=true，finally 归还）
    │
    └── 返回 ConnectionHolder(connection, borrowed)
```

**ResultSetMapper 映射规则**：
- 数据库列名（下划线）→ Java 字段名（驼峰）自动映射
- 例如：`password_hash` → `passwordHash`，`created_at` → `createdAt`
- 特殊类型处理：`Timestamp` → `LocalDateTime`
- 映射缓存：`ConcurrentHashMap<列名, Field>`，只解析一次

### 4.4 连接池（ConnectionPool）

**实现原理**：Semaphore 限流 + BlockingQueue 空闲连接 + 动态代理归还连接。

```
ConnectionPool (implements DataSource)
    │
    ├── Semaphore(maxPoolSize)     → 控制最大并发连接数
    ├── BlockingQueue<Connection>  → 空闲连接队列
    ├── AtomicInteger              → 当前总连接数
    │
    ├── getConnection()
    │       ├── semaphore.tryAcquire(30s)  → 等待许可
    │       ├── idleConnections.poll()     → 尝试获取空闲连接
    │       │       ├── 连接有效 → wrapWithProxy() 返回代理连接
    │       │       └── 连接失效 → 物理关闭，重试
    │       └── DriverManager.getConnection() → 创建新连接
    │
    └── wrapWithProxy()  → JDK 动态代理
            ├── close() → 归还到空闲队列 + semaphore.release()
            └── 其他方法 → 委托给物理连接
```

**配置参数**（从 `db.properties` 读取）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `jdbc.url` | - | JDBC 连接 URL |
| `jdbc.username` | - | 数据库用户名 |
| `jdbc.password` | - | 数据库密码 |
| `jdbc.poolSize` | 10 | 最大连接数 |

### 4.5 事务管理器（TxManager）

**实现原理**：ThreadLocal 绑定事务连接，保证同一线程内多个 DAO 操作使用同一连接。

```java
TxManager.executeInTransaction(dataSource, () -> {
    userDao.insert(user);           // 复用事务连接
    userProfileDao.insert(profile); // 复用事务连接
    return null;
});
// 全部成功 → commit
// 任何异常 → rollback
```

**执行流程**：

```
executeInTransaction(dataSource, block)
    │
    ├── 1. 检查是否已存在事务（不支持嵌套）
    ├── 2. dataSource.getConnection() → 获取连接
    ├── 3. connection.setAutoCommit(false) → 关闭自动提交
    ├── 4. CURRENT_CONNECTION.set(connection) → 绑定到 ThreadLocal
    ├── 5. block.execute() → 执行业务逻辑
    │       └── DAO 中 borrow() 检测到事务连接 → 复用
    ├── 6. connection.commit() → 提交
    ├── 7. 异常时 → connection.rollback() → 回滚
    └── 8. finally → CURRENT_CONNECTION.remove() + 恢复 autoCommit
```

### 4.6 认证与鉴权

**JWT + Redis 双重验证机制**：

```
登录流程：
    用户提交 phone + password
        │
        ├── BCrypt.checkpw() 校验密码
        ├── JwtUtil.generateTokenWithoutExp({userId, role}) → 生成 Token
        ├── ObjectMapper.writeValueAsString(user) → 序列化 User
        ├── RedisUtil.saveToken(token, userJson) → 存入 Redis，TTL=30min
        └── 返回 LoginVO{token, expiresIn, user}

鉴权流程（AuthInterceptor.preHandle，支持RBAC0）：
    请求到达
        │
        ├── 检查方法/类上是否有 @RequirePermission 或 @RequireAuth
        │       └── 都无 → 直接放行
        ├── 从 Authorization 头提取 Token
        ├── JwtUtil.parseToken(token) → 解析 JWT
        ├── RedisUtil.getUserByToken(token) → 从 Redis 获取用户 JSON
        │       └── null → Token 已过期，抛出 NOT_LOGIN_ERROR
        ├── ObjectMapper.readValue(userJson, User.class) → 反序列化
        ├── RedisUtil.refreshTokenTtl(token) → 刷新 TTL（活跃用户自动续期）
        ├── UserContext.set(user) → 绑定到 ThreadLocal
        ├── RBAC0权限鉴权（@RequirePermission 优先）：
        │       └── PermissionDao.findPermissionCodesByUserId() → 查询用户权限列表
        │       └── 权限列表不包含要求权限码 → 抛出 NO_AUTH_ERROR
        └── 角色鉴权（兼容 @RequireAuth）：@RequireAuth("admin") → 检查 role == 2
```

**RBAC0 权限模型**：

```
用户(user) ──多对多── 用户角色(user_role) ──多对多── 角色(role)
                                                          │
                                                    角色权限(role_permission)
                                                          │
                                                    权限(permission)
```

**当前权限码定义**：

| 权限码 | 名称 | 分配给 |
|--------|------|--------|
| `user:profile:view` | 查看个人信息 | user、admin |
| `user:profile:update` | 修改个人信息 | user、admin |
| `user:avatar:upload` | 上传头像 | user、admin |
| `user:logout` | 退出登录 | user、admin |
| `user:ban` | 封禁用户 | admin |
| `video:delete` | 删除视频 | admin |
| `comment:delete` | 删除评论 | admin |
| `video:publish` | 发布视频 | user、admin |
| `comment:create` | 发表评论 | user、admin |
| `follow:manage` | 关注管理 | user、admin |
| `like:toggle` | 点赞操作 | user、admin |
| `favorite:manage` | 收藏管理 | user、admin |
| `post:create` | 发布动态 | user、admin |
| `post:delete` | 删除动态 | user、admin |
| `file:upload` | 文件上传 | user、admin |
| `feed:view` | 查看Feed流 | user、admin |

**注册时自动分配角色**：用户注册时，根据 `role` 字段自动在 `user_role` 表中插入对应的 RBAC 角色关联（role=0 → user 角色，role=2 → admin 角色）。

**Token 存储格式**：
- Redis Key：`jwttoken:{token字符串}`
- Redis Value：User 对象的 JSON 字符串
- TTL：默认 1800 秒（30 分钟），每次请求自动续期

### 4.7 过滤器链

按 `web.xml` 配置顺序执行：

| 顺序 | 过滤器 | URL 匹配 | 职责 |
|------|--------|----------|------|
| 1 | CharacterEncodingFilter | `/*` | 设置请求/响应编码为 UTF-8 |
| 2 | TraceIdFilter | `/*` | 生成 TraceId，绑定 ThreadLocal + MDC |
| 3 | GlobalExceptionFilter | `/*` | 捕获所有异常，返回统一错误响应 |
| 4 | AuthFilter | `/api/*` | finally 中清理 UserContext，防止 ThreadLocal 泄漏 |

**GlobalExceptionFilter 异常处理策略**：

| 异常类型 | 处理方式 | 响应码 |
|----------|----------|--------|
| BusinessException | 直接返回业务错误码和消息 | 业务错误码 |
| ServletException 包含 BusinessException | 解包后返回业务错误 | 业务错误码 |
| InvocationTargetException 包含 BusinessException | 反射调用异常，解包返回 | 业务错误码 |
| 其他 RuntimeException | 返回系统内部异常 | 50000 |
| 其他 Exception | 返回系统内部异常 | 50000 |

### 4.8 统一响应格式

所有接口统一返回 `Result<T>` 结构：

```json
{
    "code": 0,
    "data": { ... },
    "message": "ok",
    "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**错误码编码规则**：

| 范围 | 含义 | 示例 |
|------|------|------|
| 0 | 成功 | SUCCESS |
| 40xxx | 客户端错误 | 40000 参数错误，40100 未登录，40101 无权限 |
| 50xxx | 服务端错误 | 50000 系统异常，50001 操作失败，50002 上传失败 |

**完整错误码列表**：

| 错误码 | 常量名 | 含义 |
|--------|--------|------|
| 0 | SUCCESS | 成功 |
| 40000 | PARAMS_ERROR | 请求参数错误 |
| 40001 | PHONE_ALREADY_REGISTERED | 手机号已注册 |
| 40002 | PASSWORD_NOT_MATCH | 两次输入的密码不一致 |
| 40003 | INVITE_CODE_ERROR | 邀请码错误 |
| 40004 | OLD_PASSWORD_ERROR | 旧密码错误 |
| 40100 | NOT_LOGIN_ERROR | 未登录 |
| 40101 | NO_AUTH_ERROR | 无权限 |
| 40102 | PHONE_OR_PASSWORD_ERROR | 手机号或密码错误 |
| 40300 | FORBIDDEN_ERROR | 禁止访问 |
| 40301 | ACCOUNT_BANNED | 账号已被封禁 |
| 40400 | NOT_FOUND_ERROR | 请求数据不存在 |
| 50000 | SYSTEM_ERROR | 系统内部异常 |
| 50001 | OPERATION_ERROR | 操作失败 |
| 50002 | UPLOAD_ERROR | 文件上传失败 |

---

## 五、API 接口文档

### 5.1 用户注册

- **URL**：`POST /api/user/register`
- **是否需要登录**：否
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "phone": "13800138000",
    "password": "123456",
    "confirmPassword": "123456",
    "nickname": "测试用户",
    "role": 0,
    "inviteCode": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | String | 是 | 手机号，1开头11位 |
| password | String | 是 | 密码，6-12位 |
| confirmPassword | String | 是 | 确认密码，必须与 password 一致 |
| nickname | String | 是 | 昵称，1-20位 |
| role | Integer | 否 | 角色：0-普通用户（默认），2-管理员 |
| inviteCode | String | 管理员必填 | 管理员邀请码 |

**响应体**：

```json
{
    "code": 0,
    "data": {
        "id": 1,
        "phone": "13800138000",
        "nickname": "测试用户",
        "avatar": null,
        "role": 0,
        "createdAt": "2026-05-05T12:00:00"
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.2 用户登录

- **URL**：`POST /api/user/login`
- **是否需要登录**：否
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "phone": "13800138000",
    "password": "123456"
}
```

**响应体**：

```json
{
    "code": 0,
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "expiresIn": 1800,
        "user": {
            "id": 1,
            "phone": "13800138000",
            "nickname": "测试用户",
            "avatar": "abc123.jpg",
            "role": 0,
            "createdAt": "2026-05-05T12:00:00"
        }
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.3 查看个人信息

- **URL**：`POST /api/user/profile`
- **是否需要登录**：是
- **请求头**：`Authorization: Bearer {token}`

**响应体**：

```json
{
    "code": 0,
    "data": {
        "id": 1,
        "phone": "13800138000",
        "nickname": "测试用户",
        "avatar": "abc123.jpg",
        "role": 0,
        "createdAt": "2026-05-05T12:00:00"
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.4 修改个人信息

- **URL**：`PUT /api/user/profile`
- **是否需要登录**：是
- **Content-Type**：`application/json`
- **请求头**：`Authorization: Bearer {token}`

**请求体**（所有字段可选，只传需要修改的）：

```json
{
    "nickname": "新昵称",
    "newPhone": "13900139000",
    "oldPassword": "123456",
    "newPassword": "654321",
    "confirmNewPassword": "654321",
    "newAvatar": "new_avatar.jpg"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nickname | String | 否 | 新昵称，1-20位 |
| newPhone | String | 否 | 新手机号，需同时提供 oldPassword |
| oldPassword | String | 修改手机号/密码时必填 | 旧密码验证 |
| newPassword | String | 否 | 新密码，6-12位 |
| confirmNewPassword | String | 修改密码时必填 | 确认新密码 |
| newAvatar | String | 否 | 新头像文件名（上传头像后获得） |

**响应体**：同查看个人信息

### 5.5 上传头像

- **URL**：`POST /api/user/avatar`
- **是否需要登录**：是
- **Content-Type**：`multipart/form-data`
- **请求头**：`Authorization: Bearer {token}`

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 图片文件（jpg/png/gif/webp），最大 10MB |

**响应体**：

```json
{
    "code": 0,
    "data": {
        "avatar": "a3fd6080ba084eb58ab385083c48e7fd.png"
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.6 退出登录

- **URL**：`POST /api/user/logout`
- **是否需要登录**：是
- **请求头**：`Authorization: Bearer {token}`

**响应体**：

```json
{
    "code": 0,
    "data": null,
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.7 视频列表

- **URL**：`GET /api/video/list`
- **是否需要登录**：否
- **查询参数**：`?page=1&size=10`

**响应体**：

```json
{
    "code": 0,
    "data": {
        "list": [
            {
                "id": 1,
                "title": "视频标题",
                "coverUrl": "cover.jpg",
                "userId": 1,
                "userNickname": "发布者",
                "userAvatar": "avatar.jpg",
                "viewCount": 100,
                "likeCount": 10,
                "createdAt": "2026-05-06T12:00:00"
            }
        ],
        "total": 50,
        "page": 1,
        "size": 10
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.8 视频详情

- **URL**：`GET /api/video/detail/{id}`
- **是否需要登录**：否
- **说明**：每次请求自动递增播放量

**响应体**：

```json
{
    "code": 0,
    "data": {
        "id": 1,
        "title": "视频标题",
        "description": "视频简介",
        "coverUrl": "cover.jpg",
        "videoUrl": "video.mp4",
        "userId": 1,
        "userNickname": "发布者",
        "userAvatar": "avatar.jpg",
        "viewCount": 101,
        "likeCount": 10,
        "createdAt": "2026-05-06T12:00:00"
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.9 发布视频

- **URL**：`POST /api/video/publish`
- **是否需要登录**：是
- **Content-Type**：`multipart/form-data`

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 视频标题 |
| description | String | 否 | 视频简介 |
| cover | File | 否 | 封面图片 |
| video | File | 是 | 视频文件 |

### 5.10 删除视频

- **URL**：`DELETE /api/video/{id}`
- **是否需要登录**：是
- **说明**：仅作者可删除自己的视频

### 5.11 评论列表

- **URL**：`GET /api/comment/list?targetType=1&targetId=1&sort=hot&size=10`
- **是否需要登录**：否（公开接口）

**请求参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | int | 是 | 目标类型：1-视频，2-动态 |
| targetId | long | 是 | 目标ID |
| sort | string | 否 | 排序方式，默认hot（热门） |
| cursor | int | 否 | 游标（上一页最后一条评论的likeCount） |
| cursorId | long | 否 | 游标对应的评论ID |
| size | int | 否 | 每页数量，默认10 |

**响应体**：

```json
{
    "code": 0,
    "data": {
        "list": [
            {
                "id": 1,
                "content": "评论内容",
                "userId": 1,
                "nickname": "评论者",
                "avatar": "/upload/avatar.jpg",
                "targetType": 1,
                "targetId": 1,
                "parentId": null,
                "likeCount": 5,
                "hasMoreReplies": true,
                "replies": [...],
                "createdAt": "2026-05-06T12:00:00"
            }
        ],
        "nextCursor": 5,
        "nextCursorId": 1
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.12 发表评论

- **URL**：`POST /api/comment/create`
- **是否需要登录**：是
- **所需权限**：`comment:create`
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "targetType": 1,
    "targetId": 1,
    "content": "评论内容",
    "parentId": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | int | 是 | 目标类型：1-视频，2-动态 |
| targetId | long | 是 | 目标ID |
| content | String | 是 | 评论内容 |
| parentId | Long | 否 | 父评论ID（回复时使用） |

### 5.13 删除评论

- **URL**：`DELETE /api/comment/{id}`
- **是否需要登录**：是
- **所需权限**：`comment:delete`
- **说明**：普通用户只能删除自己的评论，管理员可删除任何评论

### 5.14 关注/取关

- **URL**：`POST /api/follow/toggle`
- **是否需要登录**：是
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "followeeId": 2
}
```

**响应体**：

```json
{
    "code": 0,
    "data": {
        "followed": true
    },
    "message": "ok",
    "traceId": "xxx"
}
```

### 5.15 查询关注状态

- **URL**：`GET /api/follow/status?followeeId=2`
- **是否需要登录**：是

### 5.16 关注列表 / 粉丝列表

- **URL**：`GET /api/follow/following/{userId}` / `GET /api/follow/followers/{userId}`
- **是否需要登录**：否

### 5.17 点赞/取消

- **URL**：`POST /api/like/toggle`
- **是否需要登录**：是
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "targetType": 1,
    "targetId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | Integer | 是 | 目标类型：1-视频，2-评论 |
| targetId | Long | 是 | 目标ID |

### 5.18 查询点赞状态 / 点赞数

- **URL**：`GET /api/like/status?targetType=1&targetId=1` / `GET /api/like/count?targetType=1&targetId=1`
- **是否需要登录**：状态查询需登录，计数查询不需要

### 5.18.1 一键二连

- **URL**：`POST /api/like/double-tap/{videoId}`
- **是否需要登录**：是
- **所需权限**：`like:toggle`
- **说明**：一键二连是单向操作，只添加不取消。如果未点赞则点赞，如果未收藏进默认收藏夹则收藏。已点赞/已收藏则保持不变。

**响应体**：

```json
{
    "code": 0,
    "data": {
        "liked": true,
        "favorited": true,
        "message": "已点赞，已收藏"
    },
    "message": "ok",
    "traceId": "xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| liked | boolean | 是否已点赞（操作后状态） |
| favorited | boolean | 是否已收藏（操作后状态） |
| message | string | 操作描述信息 |

### 5.19 收藏/取消

- **URL**：`POST /api/favorite/toggle`
- **是否需要登录**：是
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "videoId": 1,
    "folderId": 1
}
```

### 5.20 收藏夹管理

- **我的收藏夹**：`GET /api/favorite/folders`（需登录）
- **创建收藏夹**：`POST /api/favorite/folder`（需登录），请求体：`{"name": "收藏夹名称"}`
- **删除收藏夹**：`DELETE /api/favorite/folder/{id}`（需登录，仅作者）
- **收藏夹内容**：`GET /api/favorite/list/{folderId}`（需登录）

### 5.21 发布动态

- **URL**：`POST /api/post/create`
- **是否需要登录**：是
- **Content-Type**：`application/json`

**请求体**：

```json
{
    "content": "动态内容"
}
```

### 5.22 删除动态

- **URL**：`DELETE /api/post/{id}`
- **是否需要登录**：是
- **说明**：仅作者可删除自己的动态

### 5.23 用户动态列表（游标分页）

- **URL**：`GET /api/post/user/{userId}?cursor=xxx&size=10`
- **是否需要登录**：否
- **说明**：游标分页，返回 `{list, nextCursor}` 格式

### 5.24 动态详情

- **URL**：`GET /api/post/{postId}`
- **是否需要登录**：否（登录后返回 isLiked 字段）

### 5.25 关注Feed流

- **URL**：`GET /api/feed?cursor=2026-05-06T12:00:00&limit=10`
- **是否需要登录**：是
- **说明**：游标分页，返回关注用户的动态，按时间倒序

### 5.25 查看其他用户公开信息

- **URL**：`GET /api/user/public/{id}`
- **是否需要登录**：否

### 5.26 查看用户视频列表

- **URL**：`GET /api/user/videos/{id}`
- **是否需要登录**：否

### 5.27 查看用户动态列表

- **URL**：`GET /api/user/posts/{id}`
- **是否需要登录**：否

---

## 六、前端实现

### 6.1 技术架构

```
Vue3 App
    │
    ├── Vue Router（路由管理）
    │       ├── /            → Home.vue（首页）
    │       ├── /login       → Login.vue（登录）
    │       ├── /register    → Register.vue（注册）
    │       ├── /profile     → Profile.vue（个人中心，需登录）
    │       ├── /videos      → VideoList.vue（视频列表）
    │       ├── /video/:id   → VideoDetail.vue（视频详情）
    │       ├── /publish     → VideoPublish.vue（视频发布，需登录）
    │       ├── /feed        → Feed.vue（关注动态，需登录）
    │       └── /user/:id    → UserCenter.vue（用户中心）
    │
    ├── Pinia（状态管理）
    │       └── useUserStore
    │               ├── token（localStorage 持久化）
    │               ├── userInfo
    │               ├── isLoggedIn（计算属性）
    │               ├── nickname（计算属性）
    │               ├── avatar（计算属性，自动拼接 /upload/ 前缀）
    │               ├── login()
    │               ├── fetchProfile()
    │               ├── logout()
    │               └── setUserInfo()
    │
    ├── Axios（HTTP 客户端）
    │       ├── 请求拦截器 → 自动附加 Authorization 头
    │       └── 响应拦截器 → 统一错误处理，401 自动跳转登录
    │
    └── 组件
            ├── NavBar.vue（导航栏，登录/未登录状态切换）
            └── Message.vue（消息提示组件）
```

### 6.2 前端页面说明

| 页面 | 路径 | 功能 |
|------|------|------|
| 首页 | `/` | 项目介绍、特色展示、CTA 按钮 |
| 登录 | `/login` | 手机号 + 密码登录，密码显示切换 |
| 注册 | `/register` | 手机号 + 密码 + 昵称注册，管理员邀请码 |
| 个人中心 | `/profile` | 查看信息、修改昵称/手机号/密码、上传头像 |
| 视频列表 | `/videos` | 分页浏览所有视频，卡片式布局 |
| 视频详情 | `/video/:id` | 视频播放、博主信息旁关注按钮、评论列表、点赞收藏一键二连 |
| 视频发布 | `/publish` | 发布视频（标题、简介、封面、视频文件） |
| 关注动态 | `/feed` | 查看关注用户的动态（游标分页） |
| 用户中心 | `/user/:id` | 查看用户公开信息、视频、动态 |

### 6.3 路由守卫

```javascript
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.meta.requiresAuth && !token) {
        next({ name: 'Login', query: { redirect: to.fullPath } })
    } else {
        next()
    }
})
```

标记 `meta: { requiresAuth: true }` 的路由需要登录才能访问，未登录自动跳转到登录页，登录后跳回原页面。

### 6.4 API 请求封装

**request.js**（Axios 实例）：
- baseURL：`/api`（由 Nginx 代理到后端）
- 超时：10 秒
- 请求拦截器：自动附加 `Authorization: Bearer {token}`
- 响应拦截器：`code !== 0` 时 reject，401 状态码自动登出

**user.js**（用户 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `register(data)` | POST | `/user/register` | 注册 |
| `login(data)` | POST | `/user/login` | 登录 |
| `getProfile()` | POST | `/user/profile` | 获取个人信息 |
| `updateProfile(data)` | PUT | `/user/profile` | 修改个人信息 |
| `uploadAvatar(file)` | POST | `/user/avatar` | 上传头像（multipart） |
| `logout()` | POST | `/user/logout` | 退出登录 |
| `getPublicUser(id)` | GET | `/user/public/{id}` | 获取公开用户信息 |
| `getUserVideos(id)` | GET | `/user/videos/{id}` | 获取用户视频列表 |
| `getUserPosts(id)` | GET | `/user/posts/{id}` | 获取用户动态列表 |

**video.js**（视频 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `listVideos(page, size)` | GET | `/video/list` | 视频列表（分页） |
| `getVideoDetail(id)` | GET | `/video/detail/{id}` | 视频详情 |
| `publishVideo(data)` | POST | `/video/publish` | 发布视频（multipart） |
| `deleteVideo(id)` | DELETE | `/video/{id}` | 删除视频 |

**comment.js**（评论 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `getCommentList(params)` | GET | `/comment/list` | 评论列表（热门排序+游标分页+树形） |
| `createComment(data)` | POST | `/comment/create` | 发表评论 |
| `getCommentReplies(parentId)` | GET | `/comment/replies/{parentId}` | 展开更多回复 |
| `deleteComment(commentId)` | DELETE | `/comment/{commentId}` | 删除评论 |

**follow.js**（关注 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `toggleFollow(followeeId)` | POST | `/follow/toggle` | 关注/取关 |
| `getFollowStatus(followeeId)` | GET | `/follow/status` | 查询关注状态 |
| `getFollowing(userId)` | GET | `/follow/following/{userId}` | 关注列表 |
| `getFollowers(userId)` | GET | `/follow/followers/{userId}` | 粉丝列表 |

**like.js**（点赞 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `toggleLike(data)` | POST | `/like/toggle` | 点赞/取消 |
| `getLikeStatus(targetType, targetId)` | GET | `/like/status` | 查询点赞状态 |
| `getLikeCount(targetType, targetId)` | GET | `/like/count` | 获取点赞数 |
| `doubleTap(videoId)` | POST | `/like/double-tap/{videoId}` | 一键二连（点赞+收藏） |

**favorite.js**（收藏 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `toggleFavorite(data)` | POST | `/favorite/toggle` | 收藏/取消 |
| `getFavoriteStatus(videoId)` | GET | `/favorite/status` | 查询收藏状态 |
| `getFolders()` | GET | `/favorite/folders` | 我的收藏夹 |
| `createFolder(name)` | POST | `/favorite/folder` | 创建收藏夹 |
| `deleteFolder(id)` | DELETE | `/favorite/folder/{id}` | 删除收藏夹 |
| `getFolderContent(folderId)` | GET | `/favorite/list/{folderId}` | 收藏夹内容 |

**post.js**（动态 API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `createPost(data)` | POST | `/post/create` | 发布动态 |
| `deletePost(id)` | DELETE | `/post/{id}` | 删除动态 |
| `listPosts(userId)` | GET | `/post/list/{userId}` | 用户动态列表 |

**feed.js**（Feed API）：

| 函数 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `getFeed(cursor, limit)` | GET | `/feed` | 关注Feed流（游标分页） |

---

## 七、Nginx 配置

### 7.1 路由规则

| URL 路径 | 代理目标 | 说明 |
|----------|----------|------|
| `/` | `http://[::1]:5173/` | 前端开发服务器（支持热更新） |
| `/api/` | `http://127.0.0.1:8080` | 后端 API（Tomcat） |
| `/upload/` | 静态文件目录 | 上传文件访问（动静分离） |

### 7.2 关键配置

```nginx
server {
    listen 80;
    server_name localhost;
    client_max_body_size 500M;

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
    gzip_min_length 1024;
    gzip_comp_level 5;

    # 前端开发服务器（支持 WebSocket 热更新）
    location / {
        proxy_pass http://[::1]:5173/;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 上传文件静态托管（动静分离）
    location /upload/ {
        alias "D:/Program Files (x86)/Backend-Study/project-study/MyBilibili/my-bilibili/backend/uploads/";
        expires 7d;
    }
}
```

### 7.3 生产环境切换

将前端 `location /` 从代理 Vite 开发服务器切换为静态文件托管：

```nginx
location / {
    root "D:/Program Files (x86)/Backend-Study/project-study/MyBilibili/my-bilibili/frontend/dist";
    index index.html;
    try_files $uri $uri/ /index.html;
}
```

---

## 八、数据库设计

### 8.1 ER 关系图

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│   User   │     │   Video  │     │  Comment │
│──────────│     │──────────│     │──────────│
│ id (PK)  │◄──┐ │ id (PK)  │◄──┐ │ id (PK)  │
│ phone    │   │ │ user_id  │───┘ │ user_id  │───┐
│ password │   │ │ title    │     │ video_id │───┤
│ nickname │   │ │ video_url│     │ parent_id│───┤(自关联)
│ avatar   │   │ │ status   │     │ content  │   │
│ role     │   └──────────┘     │ status   │   │
│ status   │                     └──────────┘   │
└────┬─────┘                                    │
     │                                          │
     │    ┌──────────┐     ┌──────────────┐     │
     │    │  Follow  │     │ LikeRecord   │     │
     │    │──────────│     │──────────────│     │
     ├───►│follower_id│     │ user_id      │◄────┘
     │    │followee_id│     │ target_type  │
     │    └──────────┘     │ target_id    │
     │                      └──────────────┘
     │
     │    ┌──────────┐     ┌──────────────┐     ┌──────────────┐
     │    │   Role   │     │RolePermission│     │ Permission   │
     │    │──────────│     │──────────────│     │──────────────│
     └───►│ id (PK)  │◄────│ role_id (FK) │────►│ id (PK)      │
          │ code     │     │permission_id │     │ code         │
          │ name     │     └──────────────┘     │ name         │
          └──────────┘                           └──────────────┘
```

### 8.2 表结构详情

#### user 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 用户ID |
| phone | VARCHAR(20) | NOT NULL, UNIQUE | 手机号 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 密码哈希 |
| nickname | VARCHAR(50) | - | 昵称 |
| avatar | VARCHAR(255) | - | 头像文件名 |
| role | TINYINT | NOT NULL, DEFAULT 0 | 角色：0-普通用户，1-博主，2-管理员 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 状态：0-正常，1-封禁 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 注册时间 |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

#### video 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 视频ID |
| title | VARCHAR(255) | NOT NULL | 标题 |
| description | TEXT | - | 简介 |
| cover_url | VARCHAR(255) | - | 封面图URL |
| video_url | VARCHAR(255) | NOT NULL | 视频文件URL |
| user_id | BIGINT UNSIGNED | NOT NULL, FK | 发布者ID |
| status | TINYINT | NOT NULL, DEFAULT 0 | 状态：0-正常，1-下架 |
| view_count | BIGINT UNSIGNED | DEFAULT 0 | 播放量 |
| like_count | BIGINT UNSIGNED | DEFAULT 0 | 点赞数 |

#### comment 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 评论ID |
| content | TEXT | NOT NULL | 评论内容 |
| user_id | BIGINT UNSIGNED | NOT NULL, FK | 评论用户ID |
| video_id | BIGINT UNSIGNED | NOT NULL, FK | 所属视频ID |
| parent_id | BIGINT UNSIGNED | FK (自关联) | 父评论ID（回复） |
| like_count | INT UNSIGNED | DEFAULT 0 | 点赞数 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 状态：0-正常，1-删除 |

#### follow 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 关注关系ID |
| follower_id | BIGINT UNSIGNED | NOT NULL, FK | 关注者ID |
| followee_id | BIGINT UNSIGNED | NOT NULL, FK | 被关注者ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 关注时间 |

> UNIQUE KEY `(follower_id, followee_id)` 防止重复关注

#### like_record 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 点赞记录ID |
| user_id | BIGINT UNSIGNED | NOT NULL, FK | 点赞用户ID |
| target_type | TINYINT | NOT NULL | 目标类型：1-视频，2-评论 |
| target_id | BIGINT UNSIGNED | NOT NULL | 目标ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 点赞时间 |

> UNIQUE KEY `(user_id, target_type, target_id)` 防止重复点赞

#### RBAC0 相关表（role, permission, role_permission, user_role）

采用标准 RBAC0（Role-Based Access Control）模型，实现"用户→角色→权限"三表关联的细粒度权限控制。

**role 表**：

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INT UNSIGNED | PK, AUTO_INCREMENT | 角色ID |
| code | VARCHAR(32) | UNIQUE, NOT NULL | 角色代码（如 user、admin） |
| name | VARCHAR(32) | NOT NULL | 角色名称 |

**初始数据**：id=1 (user, 普通用户)，id=2 (admin, 管理员)

**permission 表**：

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INT UNSIGNED | PK, AUTO_INCREMENT | 权限ID |
| code | VARCHAR(64) | UNIQUE, NOT NULL | 权限代码（如 user:profile:view） |
| name | VARCHAR(64) | NOT NULL | 权限名称 |
| description | VARCHAR(255) | | 描述 |

**role_permission 表**：角色与权限的多对多关联

**user_role 表**：用户与角色的多对多关联

**权限查询SQL**：

```sql
SELECT DISTINCT p.code FROM permission p
    JOIN role_permission rp ON p.id = rp.permission_id
    JOIN user_role ur ON rp.role_id = ur.role_id
    WHERE ur.user_id = ?
```

---

## 九、请求完整生命周期

以"上传头像"为例，展示一个完整请求从浏览器到后端的处理过程：

```
1. 浏览器
   ├── 用户选择图片文件
   ├── FormData.append('file', file)
   └── POST /api/user/avatar
       Headers: Authorization: Bearer eyJ...
       Body: multipart/form-data

2. Nginx (端口 80)
   ├── 匹配 /api/ 前缀
   └── proxy_pass → http://127.0.0.1:8080/api/user/avatar

3. Tomcat (端口 8080)
   ├── CharacterEncodingFilter → 设置 UTF-8
   ├── TraceIdFilter → 生成 TraceId，绑定 ThreadLocal + MDC
   ├── GlobalExceptionFilter → try { chain.doFilter() } catch { 统一异常处理 }
   ├── AuthFilter → try { chain.doFilter() } finally { UserContext.clear() }
   └── DispatcherServlet.service()
       ├── findHandler("POST", "/user/avatar") → UserController.uploadAvatar()
       ├── resolveArguments() → 注入 HttpServletRequest
       ├── AuthInterceptor.preHandle()
       │       ├── 检测到 @RequirePermission("user:avatar:upload") → 需要RBAC0权限鉴权
       │       ├── 从 Authorization 头提取 Token
       │       ├── JwtUtil.parseToken() → 解析 JWT 获取 claims
       │       ├── RedisUtil.getUserByToken() → 从 Redis 获取 User JSON
       │       ├── ObjectMapper.readValue() → 反序列化为 User 对象
       │       ├── RedisUtil.refreshTokenTtl() → 刷新 Token 过期时间
       │       ├── UserContext.set(user) → 绑定到 ThreadLocal
       │       └── RBAC0权限鉴权通过（用户拥有 user:avatar:upload 权限）
       ├── method.invoke() → UserController.uploadAvatar(request)
       │       ├── request.getPart("file") → 获取上传文件
       │       ├── FileUtil.isImage() → 校验文件类型
       │       ├── FileUtil.saveFile() → UUID 重命名并保存到 uploads/
       │       └── 返回 Result.success(Map.of("avatar", newFilename))
       └── writeResponse() → Jackson 序列化，设置 traceId

4. 响应回传
   ├── DispatcherServlet → writeResponse()
   ├── AuthFilter.finally() → UserContext.clear()
   ├── GlobalExceptionFilter → 无异常，正常返回
   ├── TraceIdFilter.finally() → TraceIdContext.clearTraceId()
   ├── Tomcat → Nginx → 浏览器
   └── 响应体：
       {
           "code": 0,
           "data": {"avatar": "a3fd6080ba084eb58ab385083c48e7fd.png"},
           "message": "ok",
           "traceId": "8edb6be8..."
       }

5. 前端处理
   ├── Axios 响应拦截器 → code === 0，正常返回
   ├── Profile.vue → res.data.avatar 获取文件名
   ├── userStore.setUserInfo() → 更新用户信息
   └── avatar 计算属性 → 拼接 /upload/ 前缀
       → 浏览器请求 /upload/a3fd6080ba084eb58ab385083c48e7fd.png
       → Nginx 匹配 /upload/ → alias 到 backend/uploads/ 目录
       → 返回图片文件
```

---

## 十、安全设计

| 安全措施 | 实现方式 | 说明 |
|----------|----------|------|
| 密码加密 | BCrypt | 自带盐值，每次加密结果不同，防彩虹表 |
| 并发注册防护 | `synchronized(phone.intern())` | 相同手机号串行化，防重复注册 |
| Token 验证 | JWT 签名 + Redis 存储 | 双重验证，JWT 防篡改，Redis 防伪造 |
| Token 续期 | Redis TTL 刷新 | 活跃用户自动续期，不活跃自动过期 |
| ThreadLocal 清理 | AuthFilter.finally() | 防止线程池复用导致用户信息泄漏 |
| 文件上传白名单 | FileUtil.isImage() | 只允许 jpg/png/gif/webp/mp4/webm |
| 文件重命名 | UUID | 防止文件名冲突和路径穿越 |
| 输入校验 | RegexUtil | 手机号、密码、昵称格式校验 |
| 唯一性校验 | 业务层查表 | 手机号唯一性，不依赖数据库约束 |
| 全局异常处理 | GlobalExceptionFilter | 避免堆栈信息泄漏到前端 |

---

## 十一、配置文件说明

### 11.1 后端配置

**db.properties**（数据库配置）：

| 配置项 | 说明 |
|--------|------|
| `jdbc.url` | JDBC 连接 URL（含时区、编码、SSL 设置） |
| `jdbc.username` | 数据库用户名 |
| `jdbc.password` | 数据库密码 |
| `jdbc.poolSize` | 连接池最大连接数（默认 10） |

**env.properties**（环境变量配置）：

| 配置项 | 说明 |
|--------|------|
| `admin.invite.code` | 管理员注册邀请码 |
| `redis.host` | Redis 地址 |
| `redis.port` | Redis 端口 |
| `redis.password` | Redis 密码 |
| `redis.database` | Redis 数据库编号 |
| `jwt.secret` | JWT 签名密钥 |
| `jwt.token.ttl` | Token 过期时间（秒） |

### 11.2 前端配置

**vite.config.js**：

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | 5173 | 开发服务器端口 |
| `server.host` | `::` | 监听所有网络接口 |

### 11.3 Nginx 配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `listen` | 80 | 监听端口 |
| `client_max_body_size` | 500M | 最大上传大小 |
| `gzip` | on | 启用 Gzip 压缩 |

---

## 十二、启动指南

### 12.1 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Node.js 16+
- Nginx 1.20+
- Tomcat 9.0+

### 12.2 启动步骤

1. **初始化数据库**：
   ```bash
   mysql -u root -p < backend/init.sql
   ```

2. **修改配置**：
   - 编辑 `backend/src/main/resources/db.properties` 设置数据库连接
   - 编辑 `backend/src/main/resources/env.properties` 设置 Redis 和 JWT 密钥

3. **启动 Redis**：
   ```bash
   redis-server
   ```

4. **构建并启动后端**：
   ```bash
   cd backend
   mvn clean package
   # 将 WAR 部署到 Tomcat，或使用 IDE 直接运行
   ```

5. **安装前端依赖并启动**：
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

6. **启动 Nginx**：
   ```bash
   nginx
   ```

7. **访问**：浏览器打开 `http://localhost`
