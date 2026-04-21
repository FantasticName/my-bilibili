package io.github.fantasticname.mybilibili.interceptor;

import io.github.fantasticname.mybilibili.annotation.RequireAuth;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.ioc.SimpleContainer;
import io.github.fantasticname.mybilibili.service.UserService;
import io.github.fantasticname.mybilibili.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 鉴权拦截器，在Controller方法执行前进行登录验证和角色鉴权
 *
 * <p>AuthInterceptor 实现了三个核心功能：</p>
 * <ol>
 *   <li><b>令牌解析</b>：从请求头中取出JWT令牌并解析，验证用户身份</li>
 *   <li><b>用户绑定</b>：从JWT解析出userId后，查数据库获得User实体类实例，
 *       通过 UserContext 把它与请求上下文绑定起来</li>
 *   <li><b>角色鉴权</b>：查看当前用户的角色是否有权限访问Controller接口</li>
 * </ol>
 *
 * <p>鉴权规则（基于 @RequireAuth 注解）：</p>
 * <ul>
 *   <li>没有 @RequireAuth 注解：不需要登录，任何人都可以访问（如登录、注册接口）</li>
 *   <li>@RequireAuth 或 @RequireAuth("user")：需要登录，任何登录用户都可以访问</li>
 *   <li>@RequireAuth("admin")：需要登录且角色为admin，只有管理员可以访问</li>
 * </ul>
 *
 * <p>优先级：方法上的注解优先于类上的注解。如果方法上没有注解，再看类上有没有。</p>
 *
 * <p>在 DispatcherServlet 的 service 方法中，在通过反射调用controller方法前，
 * 一行调用这个鉴权拦截器的方法：</p>
 * <pre>
 *   AuthInterceptor.preHandle(handler.getMethod(), req);
 *   Object result = handler.getMethod().invoke(handler.getController(), args);
 * </pre>
 *
 * @author FantasticName
 */
public class AuthInterceptor {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /**
     * IoC容器引用，用于获取UserService和JwtUtil等Bean
     *
     * <p>AuthInterceptor本身不是由IoC容器管理的（它是静态调用的），
     * 所以需要通过 init 方法注入容器引用。</p>
     */
    private static SimpleContainer container;

    /**
     * 初始化AuthInterceptor，注入IoC容器引用
     *
     * <p>在 DispatcherServlet 的 init 方法中调用，
     * 传入IoC容器实例，这样AuthInterceptor就能从容器中获取需要的Bean。</p>
     *
     * @param container IoC容器实例
     */
    public static void init(SimpleContainer container) {
        AuthInterceptor.container = container;
        log.info("AuthInterceptor 初始化完成，已注入IoC容器");
    }

    /**
     * 鉴权前置处理，在Controller方法执行前调用
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查方法上是否有 @RequireAuth 注解</li>
     *   <li>如果没有，再看类上是否有 @RequireAuth 注解</li>
     *   <li>如果都没有，说明不需要鉴权，直接放行</li>
     *   <li>如果需要鉴权，从请求头中取出JWT令牌并解析</li>
     *   <li>根据userId查数据库获取User对象，绑定到UserContext</li>
     *   <li>检查用户角色是否满足要求</li>
     * </ol>
     *
     * @param method  即将被调用的Controller方法
     * @param request HTTP请求对象
     */
    public static void preHandle(Method method, HttpServletRequest request) {
        // 1. 获取方法上的 @RequireAuth 注解
        RequireAuth requireAuth = method.getAnnotation(RequireAuth.class);

        // 2. 如果方法上没有注解，看看类上面有没有
        if (requireAuth == null) {
            requireAuth = method.getDeclaringClass().getAnnotation(RequireAuth.class);
        }

        // 3. 注意：如果这个接口确实没有RequireAuth注解，Interceptor是不会拦截的
        //    直接return，放行请求
        if (requireAuth == null) {
            log.debug("接口无需鉴权: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
            return;
        }

        // 4. 需要鉴权，获取当前登录用户
        //    getLoginUser方法做了下面这些事情：
        //    从request对象中取出token，验证token并解析出userId，查数据库返回User实体类对象
        User loginUser = getLoginUser(request);

        // 5. 将登录用户绑定到UserContext，供业务代码使用
        UserContext.set(loginUser);

        // 6. 角色鉴权：检查当前用户角色是否满足要求
        String requiredRole = requireAuth.value();
        log.debug("角色鉴权: 要求角色={}, 用户角色={}", requiredRole, loginUser.getRole());

        // 6.1 如果要求admin角色，但用户不是admin，抛出无权限异常
        if ("admin".equals(requiredRole) && !"admin".equals(loginUser.getRole())) {
            log.warn("权限不足: 用户userId={}, 角色={}, 要求角色={}",
                    loginUser.getId(), loginUser.getRole(), requiredRole);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 6.2 如果要求user角色（默认），只要登录了就行，不需要额外检查
        log.debug("鉴权通过: userId={}, 角色={}", loginUser.getId(), loginUser.getRole());
    }

    /**
     * 获取当前登录用户
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>先检查UserContext中是否已有用户（避免重复查询数据库）</li>
     *   <li>从请求头的Authorization字段获取JWT令牌</li>
     *   <li>解析JWT令牌，提取userId</li>
     *   <li>根据userId查询数据库，获取完整的User对象</li>
     * </ol>
     *
     * @param request HTTP请求对象
     * @return 当前登录用户
     * @throws BusinessException 如果未登录或令牌无效
     */
    private static User getLoginUser(HttpServletRequest request) {
        // 1. 如果上下文已经有用户，直接返回（优化，避免重复查询数据库）
        User contextUser = UserContext.get();
        if (contextUser != null) {
            log.debug("从UserContext获取已缓存的用户: userId={}", contextUser.getId());
            return contextUser;
        }

        // 2. 从请求头中获取Authorization字段
        String token = request.getHeader("Authorization");

        // 3. 检查令牌是否为空
        if (token == null || token.trim().isEmpty()) {
            log.debug("获取当前登录用户失败: Token为空");
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 4. 如果令牌以 "Bearer " 开头，去掉前缀
        //    前端通常会在JWT令牌前加上 "Bearer " 前缀，这是OAuth2.0的规范
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            // 5. 解析JWT令牌，获取声明信息（claims）
            Map<String, Object> claims = JwtUtil.parseToken(token);

            // 6. 从claims中提取userId
            Long userId = Long.valueOf(claims.get("userId").toString());

            // 7. 从IoC容器中获取UserService，查询用户信息
            UserService userService = container.getBean(UserService.class);
            if (userService == null) {
                log.error("未找到UserService实现，请确保已注册到IoC容器");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统配置错误");
            }

            // 8. 根据userId查询数据库
            User user = userService.getById(userId);

            // 9. 检查用户是否存在
            if (user == null) {
                log.warn("获取当前登录用户失败: 用户不存在, userId={}", userId);
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }

            log.debug("获取当前登录用户成功: userId={}, role={}", userId, user.getRole());
            return user;
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // 解析Token失败，记录错误日志
            log.error("解析Token失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }
}
