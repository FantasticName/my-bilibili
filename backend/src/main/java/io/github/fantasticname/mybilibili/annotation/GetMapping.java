package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GET请求映射注解，贴在方法上，表示该方法处理GET请求
 *
 * <p>等价于 @RequestMapping(value="/xxx", method=GET)，
 * 是一种快捷方式，语义更明确。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @GetMapping("/{id}")
 *   public Result getUser(@PathVariable("id") Long id) {
 *       return Result.success(userService.getById(id));
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GetMapping {

    /**
     * 请求路径
     *
     * @return 路径值，如 "/{id}"
     */
    String value() default "";
}
