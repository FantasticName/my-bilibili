package io.github.fantasticname.mybilibili.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.annotation.RequireAuth;
import io.github.fantasticname.mybilibili.annotation.RequirePermission;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dao.PermissionDao;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.util.JwtUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Component
public class AuthInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private PermissionDao permissionDao;

    public void preHandle(Method method, HttpServletRequest request) {
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            requirePermission = method.getDeclaringClass().getAnnotation(RequirePermission.class);
        }

        RequireAuth requireAuth = method.getAnnotation(RequireAuth.class);
        if (requireAuth == null) {
            requireAuth = method.getDeclaringClass().getAnnotation(RequireAuth.class);
        }

        if (requirePermission == null && requireAuth == null) {
            log.debug("接口无需鉴权: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
            trySetOptionalLoginUser(request);
            return;
        }

        User loginUser = getLoginUser(request);
        UserContext.set(loginUser);

        if (requirePermission != null) {
            String requiredPermission = requirePermission.value();
            List<String> userPermissions = permissionDao.findPermissionCodesByUserId(loginUser.getId());
            log.debug("RBAC权限鉴权: 要求权限={}, 用户权限列表={}", requiredPermission, userPermissions);

            if (!userPermissions.contains(requiredPermission)) {
                log.warn("RBAC权限不足: 用户userId={}, 权限列表={}, 要求权限={}",
                        loginUser.getId(), userPermissions, requiredPermission);
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            log.debug("RBAC权限鉴权通过: userId={}, 权限={}", loginUser.getId(), requiredPermission);
            return;
        }

        String requiredRole = requireAuth.value();
        Integer userRole = loginUser.getRole();
        log.debug("角色鉴权: 要求角色={}, 用户角色={}", requiredRole, userRole);

        if ("admin".equals(requiredRole) && (userRole == null || userRole != 2)) {
            log.warn("权限不足: 用户userId={}, 角色={}, 要求角色={}",
                    loginUser.getId(), userRole, requiredRole);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        log.debug("鉴权通过: userId={}, 角色={}", loginUser.getId(), userRole);
    }

    /**
     * 尝试为公开接口设置可选的登录用户信息
     *
     * <p>公开接口不需要登录也能访问，但如果用户已登录（携带了有效Token），
     * 则解析Token并设置UserContext，以便公开接口能获取登录用户的个性化数据（如是否已点赞）。</p>
     *
     * @param request HTTP请求
     */
    private void trySetOptionalLoginUser(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            Map<String, Object> claims = JwtUtil.parseToken(token);
            String userJson = RedisUtil.getUserByToken(token);
            if (userJson != null) {
                User user = OBJECT_MAPPER.readValue(userJson, User.class);
                UserContext.set(user);
                RedisUtil.refreshTokenTtl(token);
                log.debug("公开接口: 已设置可选登录用户, userId={}", user.getId());
            }
        } catch (Exception e) {
            log.debug("公开接口: Token解析失败，视为未登录, reason={}", e.getMessage());
        }
    }

    private User getLoginUser(HttpServletRequest request) {
        User contextUser = UserContext.get();
        if (contextUser != null) {
            log.debug("从UserContext获取已缓存的用户: userId={}", contextUser.getId());
            return contextUser;
        }

        String token = request.getHeader("Authorization");

        if (token == null || token.trim().isEmpty()) {
            log.debug("获取当前登录用户失败: Token为空");
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            Map<String, Object> claims = JwtUtil.parseToken(token);

            String userJson = RedisUtil.getUserByToken(token);
            if (userJson == null) {
                log.warn("获取当前登录用户失败: Token在Redis中不存在（已过期）, token前缀={}",
                        token.substring(0, Math.min(10, token.length())));
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已过期，请重新登录");
            }

            User user = OBJECT_MAPPER.readValue(userJson, User.class);

            RedisUtil.refreshTokenTtl(token);
            log.debug("Token TTL已刷新: userId={}", user.getId());

            log.debug("获取当前登录用户成功: userId={}, role={}", user.getId(), user.getRole());
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Token失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }
}
