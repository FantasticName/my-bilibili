package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DELETE请求映射注解，贴在方法上，表示该方法处理DELETE请求
 *
 * <p>等价于 @RequestMapping(value="/xxx", method=DELETE)，
 * 是一种快捷方式，语义更明确。通常用于删除资源的接口。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @DeleteMapping("/{id}")
 *   public Result deleteUser(@PathVariable("id") Long id) {
 *       userService.delete(id);
 *       return Result.success("删除成功");
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DeleteMapping {

    /**
     * 请求路径
     *
     * @return 路径值
     */
    String value() default "";
}
