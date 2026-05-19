package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 权限实体类，对应数据库中的 permission 表（RBAC）
 *
 * <p>权限表为二轮管理员功能设计，每个权限对应一个操作，
 * 如 video:delete（删除视频）、user:ban（封禁用户）等。</p>
 *
 * @author FantasticName
 */
@Data
public class Permission implements Serializable {

    /**
     * 权限ID，主键
     */
    private Integer id;

    /**
     * 权限代码，唯一标识，如 "video:delete"
     */
    private String code;

    /**
     * 权限名称，如 "删除视频"
     */
    private String name;

    /**
     * 权限描述
     */
    private String description;
}
