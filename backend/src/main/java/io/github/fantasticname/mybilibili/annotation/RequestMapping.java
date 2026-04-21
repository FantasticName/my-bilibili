package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求映射注解，可贴在类上或方法上，用于指定请求路径
 *
 * <p>贴在类上时，指定Controller的根路径（basePath）；
 * 贴在方法上时，指定方法的子路径，且匹配所有HTTP方法。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @RestController
 *   @RequestMapping("/user")
 *   public class UserController {
 *
 *       // 匹配所有HTTP方法的 /user/test
 *       @RequestMapping("/test")
 *       public Result test() {
 *           return Result.success("test");
 *       }
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequestMapping {

    /**
     * 请求路径
     *
     * @return 路径值，如 "/user"
     */
    String value() default "";
}
