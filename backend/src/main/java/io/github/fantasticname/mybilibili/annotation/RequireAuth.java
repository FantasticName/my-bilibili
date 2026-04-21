package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 鉴权注解，可贴在类上或方法上，表示访问该接口需要登录/特定角色
 *
 * <p>鉴权逻辑由 AuthInterceptor 实现，在 DispatcherServlet 调用 Controller 方法前执行。</p>
 *
 * <p>鉴权规则：</p>
 * <ul>
 *   <li>没有 @RequireAuth 注解：不需要登录，任何人都可以访问（如登录、注册接口）</li>
 *   <li>@RequireAuth 或 @RequireAuth("user")：需要登录，任何登录用户都可以访问</li>
 *   <li>@RequireAuth("admin")：需要登录且角色为admin，只有管理员可以访问</li>
 * </ul>
 *
 * <p>优先级：方法上的注解优先于类上的注解。如果方法上没有注解，再看类上有没有。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @RestController
 *   @RequestMapping("/user")
 *   @RequireAuth  // 类上标注，表示该Controller下所有接口都需要登录
 *   public class UserController {
 *
 *       @GetMapping("/{id}")
 *       public Result getUser(@PathVariable("id") Long id) {
 *           // 需要登录才能访问
 *       }
 *
 *       @DeleteMapping("/{id}")
 *       @RequireAuth("admin")  // 方法上标注，覆盖类上的注解，需要admin角色
 *       public Result deleteUser(@PathVariable("id") Long id) {
 *           // 只有管理员才能访问
 *       }
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireAuth {

    /**
     * 要求的角色，默认为 "user"（即只需要登录即可）
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>"user"：任何登录用户都可以访问</li>
     *   <li>"admin"：只有管理员角色才能访问</li>
     * </ul>
     *
     * @return 角色名称
     */
    String value() default "user";
}
