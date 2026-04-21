package io.github.fantasticname.mybilibili.ioc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * URI模板匹配器，用于将URL模板与实际请求路径进行匹配，并提取路径变量
 *
 * <p>URL模板如 /user/{id} 需要匹配实际请求 /user/123，并提取出 id=123。
 * 这是实现 @PathVariable 注解的核心组件。</p>
 *
 * <p>匹配算法：</p>
 * <ol>
 *   <li>将模板和实际路径按 "/" 分割成数组</li>
 *   <li>如果数组长度不同，直接不匹配</li>
 *   <li>逐段比较：如果模板段是 {varName} 形式，提取变量值；否则必须精确匹配</li>
 * </ol>
 *
 * <p>示例：</p>
 * <pre>
 *   模板: /user/{id}
 *   实际: /user/123
 *   结果: matches=true, variables={id=123}
 *
 *   模板: /user/{id}/posts/{postId}
 *   实际: /user/123/posts/456
 *   结果: matches=true, variables={id=123, postId=456}
 * </pre>
 *
 * @author FantasticName
 */
public class UriTemplateMatcher {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(UriTemplateMatcher.class);

    /**
     * 判断URL模板是否与实际路径匹配，并提取路径变量
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>将模板和实际路径按 "/" 分割</li>
     *   <li>比较段数，不同则不匹配</li>
     *   <li>逐段比较：变量段提取值，普通段必须精确匹配</li>
     * </ol>
     *
     * @param template   URL模板，如 "/user/{id}"
     * @param actualPath 实际请求路径，如 "/user/123"
     * @param variables  用于存放提取的路径变量的Map（调用者传入，方法内填充）
     * @return true表示匹配，false表示不匹配
     */
    public static boolean matches(String template, String actualPath, Map<String, String> variables) {
        // 1. 按 "/" 分割模板和实际路径
        String[] templateParts = template.split("/");
        String[] actualParts = actualPath.split("/");

        // 2. 如果段数不同，肯定不匹配
        //    比如 /user/{id} (3段) 和 /user/123/profile (4段) 不匹配
        if (templateParts.length != actualParts.length) {
            log.debug("路径段数不匹配: 模板{}段, 实际{}段", templateParts.length, actualParts.length);
            return false;
        }

        // 3. 逐段比较
        for (int i = 0; i < templateParts.length; i++) {
            String tPart = templateParts[i];
            String aPart = actualParts[i];

            // 3.1 如果模板段是 {varName} 形式，说明是路径变量
            //     提取变量名和变量值，存入 variables Map
            if (tPart.startsWith("{") && tPart.endsWith("}")) {
                // 去掉花括号，得到变量名
                // 比如 "{id}" → "id"
                String varName = tPart.substring(1, tPart.length() - 1);
                // 将变量名和对应的实际值存入Map
                // 比如 variables.put("id", "123")
                variables.put(varName, aPart);
                log.debug("提取路径变量: {}={}", varName, aPart);
            }
            // 3.2 如果不是变量段，必须精确匹配
            //     比如 "/user" 必须等于 "/user"
            else if (!tPart.equals(aPart)) {
                log.debug("路径段不匹配: 模板段={}, 实际段={}", tPart, aPart);
                return false;
            }
        }

        // 4. 所有段都匹配成功
        log.debug("路径匹配成功: 模板={}, 实际={}", template, actualPath);
        return true;
    }
}
