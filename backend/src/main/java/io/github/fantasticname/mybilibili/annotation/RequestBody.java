package io.github.fantasticname.mybilibili.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求体注解，贴在方法参数上，表示该参数需要从HTTP请求体中解析
 *
 * <p>DispatcherServlet会读取请求体的JSON字符串，
 * 然后使用Jackson将其反序列化为对应的Java对象。</p>
 *
 * <p>通常用于POST/PUT请求，前端以JSON格式提交数据。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   // 前端发送 JSON: {"username":"zhangsan","password":"123456"}
 *   @PostMapping
 *   public Result createUser(@RequestBody User user) {
 *       userService.create(user);
 *       return Result.success("创建成功");
 *   }
 * </pre>
 *
 * @author FantasticName
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestBody {
}
