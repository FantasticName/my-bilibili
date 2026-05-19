package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色权限关联实体类，对应数据库中的 role_permission 表（RBAC）
 *
 * <p>多对多关联表，一个角色可以拥有多个权限，一个权限也可以分配给多个角色。</p>
 *
 * @author FantasticName
 */
@Data
public class RolePermission implements Serializable {

    /**
     * 角色ID
     */
    private Integer roleId;

    /**
     * 权限ID
     */
    private Integer permissionId;
}
