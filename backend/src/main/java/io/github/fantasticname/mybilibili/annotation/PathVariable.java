package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 路径变量注解，贴在方法参数上，用于从URI模板中提取变量值
 *
 * <p>当URL中使用 {varName} 形式的模板变量时，用此注解将模板变量的值
 * 绑定到方法参数上。DispatcherServlet会通过UriTemplateMatcher
 * 匹配URL模板和实际请求路径，提取出变量值。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   // 请求 GET /user/123，id 的值就是 123
 *   @GetMapping("/{id}")
 *   public Result getUser(@PathVariable("id") Long id) {
 *       return Result.success(userService.getById(id));
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathVariable {

    /**
     * 路径变量的名称，对应URI模板中 {varName} 的 varName
     *
     * <p>如果为空，则默认使用参数名</p>
     *
     * @return 变量名
     */
    String value() default "";
}
