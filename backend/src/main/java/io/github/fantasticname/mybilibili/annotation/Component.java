package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 组件注解，贴在类上，表示这个类交给IoC容器管理
 *
 * <p>这是最基础的注解，@Service 和 @RestController 都可以看作是 @Component 的特化版本。
 * 当IoC容器扫描到贴有此注解的类时，会自动创建该类的实例并存入容器。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @Component
 *   public class MyUtil {
 *       // 这个类会被IoC容器自动管理
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

    /**
     * 组件的名称，用于按名称获取Bean
     * 如果不指定，默认使用类名首字母小写作为Bean名称
     *
     * @return 组件名称
     */
    String value() default "";
}
