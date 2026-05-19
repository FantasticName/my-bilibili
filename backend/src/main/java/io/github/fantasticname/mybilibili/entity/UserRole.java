package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户角色关联实体类，对应数据库中的 user_role 表（RBAC）
 *
 * <p>多对多关联表，一个用户可以拥有多个角色，一个角色也可以分配给多个用户。</p>
 *
 * @author FantasticName
 */
@Data
public class UserRole implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Integer roleId;
}
