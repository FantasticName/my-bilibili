package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 服务层注解，贴在类上，表示这是一个业务服务类，交给IoC容器管理
 *
 * <p>@Service 本质上就是一个 @Component，语义更明确：告诉开发者这个类是业务逻辑层。
 * IoC容器扫描到贴有此注解的类时，会自动创建该类的实例并存入容器。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @Service
 *   public class UserService {
 *       @Autowired
 *       private UserMapper userMapper;
 *
 *       public void register(String username) {
 *           userMapper.findUser(username);
 *       }
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {

    /**
     * 服务的名称，用于按名称获取Bean
     * 如果不指定，默认使用类名首字母小写作为Bean名称
     *
     * @return 服务名称
     */
    String value() default "";
}
