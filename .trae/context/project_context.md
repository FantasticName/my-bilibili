# MyBilibili 项目背景上下文

> 本文档为 AI 助手提供项目背景知识，帮助 AI 更准确地理解项目意图和架构决策。

---

## 项目定位

这是一个**模仿哔哩哔哩（Bilibili）的前后端分离全栈课设项目**。核心特色是**不使用任何 SSM/Spring Boot 框架**，而是从零手写了一个简易的 Java Web 框架。

## 核心架构决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 后端框架 | 手写 IoC + MVC | 学习 Spring 底层原理，不依赖 SSM |
| 数据库访问 | 纯 JDBC + 手写连接池 | 学习 JDBC 底层，不依赖 ORM |
| 缓存 | Redis + Jedis 原生客户端 | 不依赖 Spring Data Redis |
| 认证 | JWT + Redis 双重验证 | 无状态 + 可撤销 |
| 前端 | Vue3 + Vite + Pinia | 现代化前端技术栈 |
| 反向代理 | Nginx | 解决跨域、动静分离 |

## 手写框架层次

```
注解层 (annotation/)     → @Component, @Autowired, @RequestMapping 等 14 个注解
IoC 容器 (ioc/)          → SimpleContainer: 扫描→创建→注入
MVC 分发 (servlet/)      → DispatcherServlet: 路由匹配→参数解析→鉴权→反射调用
过滤器链 (filter/)       → 编码→TraceId→异常处理→上下文清理
拦截器 (interceptor/)    → AuthInterceptor: JWT 解析 + RBAC0 权限校验
数据访问 (dao/)          → BaseDao<T>: 泛型 CRUD + ResultSetMapper 自动映射
连接池 (util/)           → ConnectionPool: Semaphore + BlockingQueue
事务管理 (util/)         → TxManager: ThreadLocal 事务上下文
```

## 技术栈版本

| 组件 | 版本 |
|------|------|
| Java | 17 |
| Maven | WAR 打包 |
| Tomcat | 9.x (Servlet 4.0) |
| MySQL | 8.0 |
| Redis | 通过 Jedis 连接 |
| Node.js | 前端开发环境 |
| Nginx | 1.30.0 |

## 端口规划

| 端口 | 服务 | 说明 |
|------|------|------|
| Nginx | 80 | 统一入口 |
| Vite | 5173 | 前端开发服务器 |
| Tomcat | 8080 | 后端 API 服务 + WebSocket |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| RocketMQ NameServer | 9876 | 消息队列路由中心 |

## 新增模块（2026-05-12 扩展点落地）

| 模块 | 位置 | 说明 |
|------|------|------|
| 分布式锁 | config/RedissonConfig.java | Redisson RLock，看门狗自动续期 |
| 消息队列 | config/RocketMQConfig.java + mq/*Consumer.java | RocketMQ 5.1.4 事务消息/异步解耦。已实现消费者：CacheInvalidateConsumer（缓存失效）、NotificationConsumer（通知推送）、FeedPushConsumer（Feed推送+RateLimiter限流）、CouponGrabConsumer（优惠券码+通知） |
| 流量控制 | config/SentinelConfig.java + util/SentinelUtil.java | QPS限流 + 异常比例熔断 + 降级兜底 |
| 缓存防护 | util/BloomFilterUtil.java | 双布隆过滤器 + 原子切换 + 周期性24h重建，加载视频ID+动态ID+用户ID |
| 本地缓存 | Caffeine（FeedServiceImpl内） | 大V发件箱本地缓存，30s TTL，减少Redis请求 |
| 幂等性 | util/IdempotentUtil.java | Token-based 防重复提交，GET /api/user/token获取Token |
| 秒杀 | service/CouponServiceImpl.java | Lua脚本原子秒杀 + MQ异步落库 + 优惠券码生成 + 库存对账 + 前端页面(/coupon) |
| 通知推送 | entity/Notification.java + websocket/WebSocketServer.java | 持久化 + WebSocket实时推送 |
| 推荐系统 | entity/UserBehavior.java + service/RecommendServiceImpl.java | 行为埋点 + 内容推荐 + Redis推荐池离线计算 |
| Feed流升级 | mq/FeedPushConsumer.java + service/FeedServiceImpl.java | Push-Pull混合模式 + Caffeine本地缓存 + 两路召回 + hotFallback兜底 + 前端页面(/feed) |
| 三层缓存防护 | service/UserServiceImpl.java | 布隆过滤器→Redis缓存→分布式锁+双重检查 |
| 定时任务 | listener/ContainerInitializerListener.java | 布隆重建(24h) + 热榜刷新(5min) + 推荐计算(24h) + 库存对账(10min) |
| 优惠券管理 | controller/CouponController.java + views/CouponCenter.vue | 管理员创建+发布+用户秒杀全流程 |

## 项目所有者

- Java 小白，正在学习后端开发
- 需要详细的教程和注释来理解代码
