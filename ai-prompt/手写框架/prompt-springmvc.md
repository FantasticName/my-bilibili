# 手写springmvc框架



- 我现在正在给一个项目搭建底层框架 . 这个项目要求不允许使用任何SSM框架, 所以请你帮我手写一个简单的springmvc框架, 实现下面的功能: 

  > - RESTful API 开发 ：使用 @RestController 、 @RequestMapping 、 @GetMapping 、 @PostMapping 、 @PutMapping 、 @DeleteMapping 等注解构建 REST API
  > - 请求参数绑定 ： @RequestBody 、 @PathVariable 、 @RequestParam 自动绑定请求参数

- 我会发一篇我在学习手写springmvc框架时的笔记给你, 里面详细描述了如何一步一步搭建一个简单的springmvc框架, 你要参考这篇笔记 , 必须尽可能多的使用笔记里的方法或者复用笔记里的代码 . 
- 笔记里的代码是没有记录日志的 , 请你使用 SLF4J + logback 记录 详细日志
- 笔记里的代码都打上了非常详细的注释 . 你必须模仿笔记 , 给你的代码同样打上非常详细的注释, 以方便我学习 . 注释只可以比笔记代码里的多, 不可以少
- 笔记里的注解大多有`My`做前缀(比如`MyPostMapping `) , 你在写框架的时候, 不要写My前缀 , 直接使用springmvc框架原原本本的注释名字即可 ( 比如 `PostMapping`)

- 笔记里的代码有try-catch, 如果catch到了异常, 会返回一个结果 (比如路由未命中返回400的result) , 但是这些结果格式不统一 . 我要求返回统一的错误格式Result类



# 额外要求

- 此外 ! ! 这个项目还要求使用jwt令牌进行登录鉴权 , 还要求对`controller`的接口进行角色鉴权 (有一些接口只允许admin访问 , 不允许user访问)
- 我期望实现的效果
  - 自定义一个注解@RequireAuth 默认值为 "user" , 可以手动填入"admin" , 可以打在类上或者方法上
  - 如果一个controller接口要求用户登录了才可以访问, 那么就打上@RequireAuth注解, 如果这个接口要求管理员角色才能访问 , 那么就打上@RequireAuth("admin") . 如果这个接口不需要鉴权 (比如 登录 , 注册接口 )那就不打注解
- 要手写一个`UserContext.java`类, 通过TreadLocal把实体类User的对象和当前请求上下文绑定起来

- 手写一个`AuthInterceptor`, 它实现了下面三个功能

  - 从请求头中取出jwt 令牌并解析鉴权
  - 从jwt解析出userId 后 , 查数据库 , 获得user实体类实例 , 通过 UserContext 把它与请求上下文绑定起来
  - 接口鉴权 , 查看当前用户的角色是否有权限访问controller接口

  参考代码如下: **(你如果要复用我的参考代码, 必须像我的参考代码一样打上详细的注释 , 注释只可以多 , 不可以少!!)**

  ```java
  public class AuthInterceptor {
      public static void preHandle(Method method, HttpServletRequest request) {
          RequireAuth requireAuth = method.getAnnotation(RequireAuth.class);
          if (requireAuth == null) {
              // 如果方法上没有注解 , 看看类上面有没有
              requireAuth = method.getDeclaringClass().getAnnotation(RequireAuth.class);
          }
          // 注意:如果这个接口确实没有RequireAuth注解, Interceptor是不会拦截的
          if (requireAuth != null) {
              // 注意 ! 这个getLoginUser方法做了下面这些事情:
              // 从request对象中取出token , 验证token并解析出userId , 查数据库返回User实体类对象
              User loginUser = getLoginUser(request); // 静态调用或从容器获取
              UserContext.set(loginUser);
              String requiredRole = requireAuth.value();
              if ("admin".equals(requiredRole) && !"admin".equals(loginUser.getRole())) {
                  throw new BusinessException("无权限");
              }
          }
      }
  }
  ```

  ```java
  // 参考的 getLoginUser
  private User getLoginUser(HttpServletRequest request) {
          // 如果上下文已经有用户，直接返回 (优化)
          User contextUser = UserContext.get();
          if (contextUser != null) {
              return contextUser;
          }
          
          String token = request.getHeader("Authorization");
          if (StringUtils.isBlank(token)) {
              log.debug("获取当前登录用户失败: Token 为空");
              throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
          }
          try {
              Map<String, Object> claims = jwtUtil.parseToken(token);
              Long userId = Long.valueOf(claims.get("userId").toString());
              User user = this.getById(userId);
              if (user == null) {
                  log.warn("获取当前登录用户失败: 用户不存在, userId={}", userId);
                  throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
              }
              log.debug("获取当前登录用户成功: userId={}", userId);
              return user;
          } catch (Exception e) {
              log.error("解析 Token 失败: {}", e.getMessage());
              throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
          }
      }
  ```

  然后 , **重点来了 ! ! **

  在 `DispatcherServlet` 的`service`方法中 , 在通过反射调用controller方法前 ,

  

  一行调用这个鉴权拦截器的方法：

  java

  ```java
  AuthInterceptor.preHandle(hm.getMethod(), req);
  // 3. 通过反射调用方法
  Object result = handler.method.invoke(handler.controller, args);
  ```