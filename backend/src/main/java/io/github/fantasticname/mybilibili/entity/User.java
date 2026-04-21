package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户实体类，对应数据库中的用户表
 *
 * <p>这个类是框架层的核心实体，AuthInterceptor解析JWT后查数据库得到的用户信息
 * 会封装成此对象，然后通过 UserContext 绑定到当前请求线程。</p>
 *
 * <p>业务代码中可以通过 UserContext.get() 获取当前登录用户的信息，
 * 不需要每次都从数据库查询。</p>
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
     * 用户名
     */
    private String username;

    /**
     * 密码（加密存储）
     */
    private String password;

    /**
     * 用户角色
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>"user"：普通用户</li>
     *   <li>"admin"：管理员</li>
     * </ul>
     */
    private String role;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户签名/简介
     */
    private String sign;
}
