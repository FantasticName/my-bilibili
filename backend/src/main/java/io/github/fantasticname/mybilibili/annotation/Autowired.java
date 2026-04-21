package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动注入注解，贴在字段上，告诉IoC容器：这个字段需要一个对象，请帮我注入
 *
 * <p>当IoC容器完成所有Bean的实例化后，会遍历每个Bean的所有字段，
 * 如果发现字段上贴有 @Autowired 注解，就会从容器的Bean池中找到类型匹配的对象，
 * 通过反射（field.set()）把它塞进这个字段。</p>
 *
 * <p>即使字段是 private 的也没关系，因为反射可以"暴力"访问私有字段（setAccessible(true)），
 * 这就是Spring @Autowired的底层原理。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @Service
 *   public class UserService {
 *       // 告诉容器：我需要一个 UserMapper 类型的对象
 *       @Autowired
 *       private UserMapper userMapper;
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Autowired {
}
