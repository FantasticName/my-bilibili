package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * REST控制器注解，贴在类上，表示这是一个REST风格的控制器，交给IoC容器管理
 *
 * <p>@RestController 本质上也是一个 @Component，语义更明确：告诉开发者这个类是控制层，
 * 负责接收HTTP请求并返回JSON格式的响应数据。</p>
 *
 * <p>IoC容器扫描到贴有此注解的类时，会自动创建该类的实例并存入容器，
 * 并且后续的路由分发模块会识别这些类，将HTTP请求映射到对应的方法上。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @RestController
 *   public class UserController {
 *       @Autowired
 *       private UserService userService;
 *
 *       // 处理用户注册请求
 *       public Result register(HttpServletRequest req, HttpServletResponse resp) {
 *           // ...
 *       }
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestController {

    /**
     * 控制器的名称，用于按名称获取Bean
     * 如果不指定，默认使用类名首字母小写作为Bean名称
     *
     * @return 控制器名称
     */
    String value() default "";
}
