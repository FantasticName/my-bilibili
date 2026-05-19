package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色实体类，对应数据库中的 role 表（RBAC）
 *
 * <p>角色表为二轮管理员功能设计，每个角色拥有一组权限，
 * 如 admin 角色拥有所有权限，user 角色只有基本权限。</p>
 *
 * @author FantasticName
 */
@Data
public class Role implements Serializable {

    /**
     * 角色ID，主键
     */
    private Integer id;

    /**
     * 角色代码，唯一标识，如 "admin"
     */
    private String code;

    /**
     * 角色名称，如 "管理员"
     */
    private String name;
}
