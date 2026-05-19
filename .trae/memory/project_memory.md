# MyBilibili 项目记忆

> AI 助手会在对话中自动更新此文件，记录ai助手觉得重要的信息,以实现即使新建会话窗口导致上下文丢失,ai助手也可以读取这个文件以获取记忆信息,包括但不限于重要的项目信息和用户偏好。
> 你也可以手动编辑此文件。

***

## 项目路径

- 项目根目录: `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili`
- 后端源码: `backend/src/main/java/io/github/fantasticname/mybilibili/`
- 前端源码: `frontend/src/`
- AI 学习笔记: `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\ai\`
- Nginx 目录: `D:\Nginx\nginx-1.30.0\nginx-1.30.0\`

## 用户偏好

- 根据阿里巴巴编码规范进行编码，其中，需要添加javadoc注释，author写FantasticName
- 代码中**需要添加详细注释**以辅助用户学习和理解源码，不仅要写javadoc，还要写详细的单行注释，解释某一块或者某一行代码干了什么。用户是个Java小白。注意：注释要中文，单行注释不要和代码写同一行。
- 使用**纯 JDBC** 或者**手写ORM框架**操作数据库，项目中有已经写好的简单的ORM框架功能，不使用 MyBatis/JPA
- 数据库操作遵循 `纯 JDBC 数据库操作规范.md` 中的规范
- 修改代码后需要同步更新文档（见 `rules/project_rules.md`）
- 涉及数据库变更需要提供 SQL 语句
- 新增业务功能需要写教程和单元测试
- 修改完成后需要重启服务

## 当前服务状态

- 后端: Tomcat (端口 8080)
- 前端: Vite (端口 5173/5174，如果5173 被占用时自动切换到5174)
- 反向代理: Nginx (端口 80)
- 数据库: MySQL 8.0 (端口 3306)
- 缓存: Redis (端口 6379)

## 已实现功能

### 用户模块

- 用户注册/登录（JWT + BCrypt）
- 个人信息查看/修改（昵称、手机号、密码）
- 头像上传
- 退出登录 + Token 自动续期
- 查看其他用户公开信息
- 查看用户视频列表
- 查看用户动态列表

### 视频模块

- 视频列表（分页浏览）
- 视频详情（自动递增播放量）
- 发布视频（multipart 上传视频文件和封面）
- 删除视频（仅作者可删除）

### 评论模块

- 评论列表（按视频查看）
- 发表评论（支持楼中楼回复，通过 parentId）
- 删除评论（仅作者可删除）

### 关注模块

- 关注/取关（Toggle 模式）
- 查询关注状态
- 关注列表 / 粉丝列表
- 视频详情页博主信息旁可直接关注（无需跳转个人主页）

### 点赞模块

- 点赞/取消（Toggle 模式，支持视频、评论、动态三种目标，targetType: 1=视频, 2=评论, 3=动态）
- 查询点赞状态
- 获取点赞数
- 一键二连（单向操作：点赞视频 + 收藏进默认收藏夹，只添加不取消）

### 收藏模块

- 收藏/取消（支持多收藏夹）
- 收藏夹管理（创建、删除、查看内容）
- 默认收藏夹（注册时自动创建，不可删除）

### Feed 流模块

- 关注动态流（游标分页，按时间倒序）

### 动态模块

- 发布动态（支持纯文字、纯图片、文字+图片，最多9张图，multipart/form-data）
- 编辑动态（PUT /post/{postId}，支持修改文字和增删图片，权限post:update）
- 删除动态（仅作者，软删除，权限post:delete）
- 动态详情（GET /post/{postId}，含点赞状态）
- 用户动态列表（游标分页，瀑布流）
- 动态点赞（targetType=3）
- 动态评论（复用comment表，targetType=2）
- Feed流包含自己的动态（FeedServiceImpl将userId加入followeeIds）

### 搜索模块

- 综合搜索（同时搜索视频标题、动态内容、用户昵称）
- 视频搜索（按标题模糊搜索，分页）
- 动态搜索（按内容模糊搜索，分页）
- 用户搜索（按昵称模糊搜索，分页）
- 搜索结果点击可跳转详情页

### 基础设施

- 全链路追踪（TraceId）
- RBAC0 权限控制（用户-角色-权限三表关联，共22个权限码）
- 全局异常处理
- 手写 IoC 容器 + DispatcherServlet 路由分发
- 手写 JDBC 连接池 + 事务管理
- Redis 缓存（JWT Token 存储，自动续期）
- Caffeine 本地缓存（大V发件箱缓存，30s TTL，最多1000 key）
- Sentinel 流量控制（QPS限流 + 异常比例熔断 + 降级兜底）
- 幂等性Token机制（防重复提交，Redis del原子校验）
- 布隆过滤器防缓存穿透（双布隆+原子切换，周期性24h重建，加载视频ID+动态ID+用户ID）
- 三层缓存防护（布隆过滤器→Redis缓存→分布式锁+双重检查，同时保护视频查询和用户查询）

### MQ消费者模块

- CacheInvalidateConsumer：监听CACHE_INVALIDATE Topic，删除对应Redis缓存Key（Cache Aside模式）
- NotificationConsumer：监听USER_FOLLOW、COMMENT_CREATE、LIKE_TOGGLE Topic，写DB + WebSocket推送
- FeedPushConsumer：监听POST_CREATE、VIDEO_PUBLISH Topic，Push到粉丝Redis收件箱 + 大V发件箱，RateLimiter限流500QPS
- CouponGrabConsumer：监听COUPON_GRAB Topic，异步落库+扣DB库存+生成优惠券码+发送通知
- 消费者均为单例模式，使用DefaultLitePullConsumer（拉取模式），运行在守护线程中
- ContainerInitializerListener负责在IoC容器初始化后启动消费者和定时任务，Web关闭时停止

### 定时任务模块

- BloomFilterRebuild-Thread：每24小时重建布隆过滤器（加载视频ID+动态ID+用户ID），失败1分钟重试
- HotFallback-Thread：每5分钟刷新热榜兜底数据
- RecommendCompute-Thread：每24小时计算所有用户推荐池
- CouponReconcile-Thread：每10分钟对已结束活动进行库存对账

### 消息通知模块

- FollowServiceImpl：关注成功后自动发送FOLLOW类型通知给被关注者
- LikeServiceImpl：点赞成功后自动发送LIKE类型通知给内容拥有者（自己赞自己不发通知）
- CommentServiceImpl：评论成功后自动发送COMMENT类型通知给内容拥有者（自己评论自己不发通知）
- 通知发送用try-catch包裹，失败不影响主业务流程
- 通知类型：FOLLOW / LIKE / COMMENT，便于前端分类展示

### RBAC权限码清单

| ID | 权限码 | 说明 |
|----|--------|------|
| 1 | user:profile:view | 查看个人信息 |
| 2 | user:profile:update | 修改个人信息 |
| 3 | user:avatar:upload | 上传头像 |
| 4 | user:logout | 退出登录 |
| 5 | user:ban | 封禁用户（管理员） |
| 6 | video:delete | 删除视频（管理员） |
| 7 | comment:delete | 删除评论（管理员） |
| 8 | video:publish | 发布视频 |
| 9 | comment:create | 发表评论 |
| 10 | follow:manage | 关注管理 |
| 11 | like:toggle | 点赞操作 |
| 12 | favorite:manage | 收藏管理 |
| 13 | post:create | 发布动态 |
| 14 | post:delete | 删除动态 |
| 15 | file:upload | 文件上传 |
| 16 | feed:view | 查看Feed流 |

### 所有权校验清单

- 删除视频：服务层校验 video.getUserId().equals(userId)
- 删除评论：服务层校验 comment.getUserId().equals(userId)
- 删除动态：服务层校验 post.getUserId().equals(userId)
- 删除收藏夹：服务层校验 folder.getUserId().equals(userId)
- 重命名收藏夹：服务层校验 folder.getUserId().equals(userId)
- 查看收藏夹视频：服务层校验 folder.getUserId().equals(userId)

## 数据库连接信息

### MySQL

- 主机: localhost
- 端口: 3306
- 数据库名: my\_bilibili
- 用户名: root
- 密码: 123456
- 连接命令: `mysql -u root -p"123456" my_bilibili`

### Redis

- 主机: localhost
- 端口: 6379
- 密码: LiuRRredis1224
- 连接命令: `redis-cli -h localhost -p 6379 -a LiuRRredis1224`

## 敏感信息配置机制

- `env.properties` 和 `db.properties` 已被 `.gitignore` 排除，不会被提交到版本库
- 模板文件 `env.properties.template` 和 `db.properties.template` 提交到版本库，只含键名不含真实值
- 代码采用"环境变量优先"策略：优先读取 `System.getenv()`，回退到 `.properties` 文件
- 环境变量对照表：

| 配置文件键名 | 环境变量名 | 说明 |
|---|---|---|
| jdbc.url | JDBC_URL | 数据库连接URL |
| jdbc.username | JDBC_USERNAME | 数据库用户名 |
| jdbc.password | JDBC_PASSWORD | 数据库密码 |
| jdbc.poolSize | JDBC_POOL_SIZE | 连接池大小 |
| redis.host | REDIS_HOST | Redis主机 |
| redis.port | REDIS_PORT | Redis端口 |
| redis.password | REDIS_PASSWORD | Redis密码 |
| redis.database | REDIS_DATABASE | Redis数据库编号 |
| jwt.secret | JWT_SECRET | JWT签名密钥 |
| jwt.token.ttl | JWT_TOKEN_TTL | Token过期时间(秒) |
| admin.invite.code | ADMIN_INVITE_CODE | 管理员邀请码 |

<br />

<br />

## 启动命令

前端: (在/my-bilibili/frontend目录下) npm run dev 

后端: (在/my-bilibili/backend目录下) mvn tomcat7:run -DskipTests
