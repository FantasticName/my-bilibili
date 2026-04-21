package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * POST请求映射注解，贴在方法上，表示该方法处理POST请求
 *
 * <p>等价于 @RequestMapping(value="/xxx", method=POST)，
 * 是一种快捷方式，语义更明确。通常用于创建资源的接口。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @PostMapping
 *   public Result createUser(@RequestBody User user) {
 *       userService.create(user);
 *       return Result.success("创建成功");
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostMapping {

    /**
     * 请求路径
     *
     * @return 路径值
     */
    String value() default "";
}
