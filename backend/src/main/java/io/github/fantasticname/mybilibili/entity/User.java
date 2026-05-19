package io.github.fantasticname.mybilibili.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类，对应数据库中的 user 表
 *
 * <p>这个类是框架层的核心实体，AuthInterceptor解析JWT后查数据库得到的用户信息
 * 会封装成此对象，然后通过 UserContext 绑定到当前请求线程。</p>
 *
 * <p>业务代码中可以通过 UserContext.get() 获取当前登录用户的信息，
 * 不需要每次都从数据库查询。</p>
 *
 * <p>字段采用驼峰命名，与数据库下划线列名自动映射（由ResultSetMapper完成）：
 * phone → phone，passwordHash → password_hash，createdAt → created_at，等等。</p>
 *
 * @author FantasticName
 */
@Data
public class User implements Serializable {

    /**
     * 用户ID，主键
     */
    private Long id;

    /**
     * 手机号，用于登录，唯一
     */
    private String phone;

    /**
     * 密码哈希（BCrypt加密存储，内含盐值，不存明文）
     */
    private String passwordHash;

    /**
     * 昵称，用户展示的名称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 用户角色
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>0：普通用户</li>
     *   <li>1：博主</li>
     *   <li>2：管理员（为二轮预留）</li>
     * </ul>
     */
    private Integer role;

    /**
     * 用户状态
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>0：正常</li>
     *   <li>1：封禁（二轮管理员功能）</li>
     * </ul>
     */
    private Integer status;

    /**
     * 注册时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
