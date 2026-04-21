package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.entity.User;

/**
 * 用户服务接口，定义用户相关的业务方法
 *
 * <p>这是框架层的契约接口，AuthInterceptor 在鉴权时需要通过此接口查询用户信息。
 * 具体的业务实现类（如 UserServiceImpl）需要实现此接口，
 * 并使用 @Service 注解注册到 IoC 容器中。</p>
 *
 * <p>为什么要定义接口而不是直接用实现类？</p>
 * <ul>
 *   <li>解耦：AuthInterceptor 依赖接口而非实现，不关心具体怎么查数据库</li>
 *   <li>可替换：未来可以换不同的实现（比如从缓存查、从远程服务查）</li>
 *   <li>可测试：单元测试时可以用Mock实现替代真实数据库查询</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 *   @Service
 *   public class UserServiceImpl implements UserService {
 *       @Override
 *       public User getById(Long id) {
 *           // 查数据库，返回User对象
 *       }
 *   }
 * </pre>
 *
 * @author FantasticName
 */
public interface UserService {

    /**
     * 根据用户ID查询用户信息
     *
     * <p>AuthInterceptor在解析JWT获取userId后，
     * 调用此方法查询完整的用户信息，用于后续的角色鉴权。</p>
     *
     * @param id 用户ID
     * @return 用户实体对象，如果用户不存在则返回null
     */
    User getById(Long id);
}
