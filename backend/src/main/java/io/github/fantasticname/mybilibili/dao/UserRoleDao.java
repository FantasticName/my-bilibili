package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.UserRole;

import java.util.List;

/**
 * 用户角色关联DAO，封装用户角色关联表的数据库操作（RBAC）
 *
 * <p>继承 BaseDao&lt;UserRole&gt;，自动获得通用CRUD方法。
 * 为二轮管理员权限设计。</p>
 *
 * @author FantasticName
 */
@Component
public class UserRoleDao extends BaseDao<UserRole> {

    /**
     * 根据用户ID查询所有角色关联
     *
     * @param userId 用户ID
     * @return 用户角色关联列表
     */
    public List<UserRole> listByUserId(long userId) {
        String sql = "SELECT * FROM user_role WHERE user_id = ?";
        return queryList(sql, userId);
    }

    /**
     * 插入用户角色关联
     *
     * @param userRole 用户角色关联对象
     * @return 自增主键ID
     */
    public long insert(UserRole userRole) {
        String sql = "INSERT INTO user_role (user_id, role_id) VALUES (?, ?)";
        return executeInsert(sql, userRole.getUserId(), userRole.getRoleId());
    }

    /**
     * 删除用户角色关联
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    public void delete(long userId, int roleId) {
        String sql = "DELETE FROM user_role WHERE user_id = ? AND role_id = ?";
        executeUpdate(sql, userId, roleId);
    }
}
