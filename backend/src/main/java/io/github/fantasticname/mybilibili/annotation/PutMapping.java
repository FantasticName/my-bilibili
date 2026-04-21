package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PUT请求映射注解，贴在方法上，表示该方法处理PUT请求
 *
 * <p>等价于 @RequestMapping(value="/xxx", method=PUT)，
 * 是一种快捷方式，语义更明确。通常用于更新资源的接口。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @PutMapping("/{id}")
 *   public Result updateUser(@PathVariable("id") Long id, @RequestBody User user) {
 *       userService.update(id, user);
 *       return Result.success("更新成功");
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PutMapping {

    /**
     * 请求路径
     *
     * @return 路径值
     */
    String value() default "";
}
