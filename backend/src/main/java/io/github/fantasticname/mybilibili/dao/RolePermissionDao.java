package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.RolePermission;

import java.util.List;

/**
 * 角色权限关联DAO，封装角色权限关联表的数据库操作（RBAC）
 *
 * <p>继承 BaseDao&lt;RolePermission&gt;，自动获得通用CRUD方法。
 * 为二轮管理员权限设计。</p>
 *
 * @author FantasticName
 */
@Component
public class RolePermissionDao extends BaseDao<RolePermission> {

    /**
     * 根据角色ID查询所有权限关联
     *
     * @param roleId 角色ID
     * @return 角色权限关联列表
     */
    public List<RolePermission> listByRoleId(int roleId) {
        String sql = "SELECT * FROM role_permission WHERE role_id = ?";
        return queryList(sql, roleId);
    }

    /**
     * 插入角色权限关联
     *
     * @param rolePermission 角色权限关联对象
     * @return 自增主键ID
     */
    public long insert(RolePermission rolePermission) {
        String sql = "INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)";
        return executeInsert(sql, rolePermission.getRoleId(), rolePermission.getPermissionId());
    }

    /**
     * 删除角色权限关联
     *
     * @param roleId       角色ID
     * @param permissionId 权限ID
     */
    public void delete(int roleId, int permissionId) {
        String sql = "DELETE FROM role_permission WHERE role_id = ? AND permission_id = ?";
        executeUpdate(sql, roleId, permissionId);
    }
}
