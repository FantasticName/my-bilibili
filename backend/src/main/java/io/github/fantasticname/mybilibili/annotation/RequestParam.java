package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求参数注解，贴在方法参数上，用于从URL查询字符串中获取参数值
 *
 * <p>DispatcherServlet会通过 request.getParameter(paramName) 获取参数值，
 * 然后自动进行类型转换（如 String → Long）。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   // 请求 GET /user/search?name=张三
 *   @GetMapping("/search")
 *   public Result search(@RequestParam("name") String name) {
 *       return Result.success(userService.search(name));
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {

    /**
     * 请求参数的名称，对应URL查询字符串中的key
     *
     * <p>如果为空，则默认使用参数名</p>
     *
     * @return 参数名
     */
    String value() default "";
}
