package io.github.fantasticname.mybilibili.context;

import io.github.fantasticname.mybilibili.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户上下文工具类，通过ThreadLocal把User实体类对象和当前请求上下文绑定起来
 *
 * <p>工作原理：</p>
 * <ul>
 *   <li>AuthInterceptor在鉴权成功后，将User对象存入ThreadLocal</li>
 *   <li>业务代码中通过 UserContext.get() 获取当前登录用户</li>
 *   <li>请求结束后，AuthInterceptor清除ThreadLocal，防止内存泄漏</li>
 * </ul>
 *
 * <p>为什么用ThreadLocal？</p>
 * <ul>
 *   <li>Web服务器（Tomcat）使用线程池处理请求，多个请求可能同时被处理</li>
 *   <li>ThreadLocal为每个线程提供独立的存储空间，线程之间互不干扰</li>
 *   <li>这样每个请求的用户信息都是隔离的，不会串</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 *   // 在业务代码中获取当前登录用户
 *   User currentUser = UserContext.get();
 *   Long userId = currentUser.getId();
 * </pre>
 *
 * @author FantasticName
 */
public class UserContext {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(UserContext.class);

    /**
     * ThreadLocal，用于将User对象和当前线程绑定
     *
     * <p>每个线程都有独立的存储空间，线程A中set的User对象，
     * 只有线程A能get到，线程B拿不到。</p>
     *
     * <p>这样在多线程环境下，每个请求的用户信息互不干扰。</p>
     */
    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    /**
     * 将User对象绑定到当前线程
     *
     * <p>AuthInterceptor在鉴权成功后调用此方法，
     * 将从数据库查询到的User对象存入ThreadLocal。</p>
     *
     * @param user 当前登录用户
     */
    public static void set(User user) {
        USER_HOLDER.set(user);
        log.debug("UserContext设置用户: userId={}, role={}",
                user != null ? user.getId() : null,
                user != null ? user.getRole() : null);
    }

    /**
     * 获取当前线程绑定的User对象
     *
     * <p>业务代码中调用此方法获取当前登录用户的信息，
     * 不需要每次都从数据库查询。</p>
     *
     * @return 当前登录用户，如果未登录则返回null
     */
    public static User get() {
        return USER_HOLDER.get();
    }

    /**
     * 清除当前线程绑定的User对象
     *
     * <p>请求处理完成后必须调用此方法！</p>
     *
     * <p>为什么必须清除？因为Tomcat使用线程池，线程会被复用。
     * 如果不清除ThreadLocal，下一个请求复用这个线程时，
     * 会拿到上一个请求的用户信息，导致安全问题。</p>
     */
    public static void clear() {
        USER_HOLDER.remove();
        log.debug("UserContext已清除");
    }
}
