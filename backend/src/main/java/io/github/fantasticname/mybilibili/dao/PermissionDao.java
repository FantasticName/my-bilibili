package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.Permission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限DAO，封装权限表的数据库操作（RBAC）
 *
 * <p>继承 BaseDao&lt;Permission&gt;，自动获得通用CRUD方法。
 * 为二轮管理员权限设计。</p>
 *
 * @author FantasticName
 */
@Component
public class PermissionDao extends BaseDao<Permission> {

    /**
     * 根据ID查询权限
     *
     * @param id 权限ID
     * @return 权限对象，不存在返回null
     */
    public Permission findById(int id) {
        String sql = "SELECT * FROM permission WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 根据权限代码查询权限
     *
     * @param code 权限代码，如 "video:delete"
     * @return 权限对象，不存在返回null
     */
    public Permission findByCode(String code) {
        String sql = "SELECT * FROM permission WHERE code = ?";
        return queryOne(sql, code);
    }

    /**
     * 查询所有权限
     *
     * @return 权限列表
     */
    public List<Permission> findAll() {
        String sql = "SELECT * FROM permission ORDER BY id";
        return queryList(sql);
    }

    /**
     * 插入新权限
     *
     * @param permission 权限对象
     * @return 自增主键ID
     */
    public long insert(Permission permission) {
        String sql = "INSERT INTO permission (code, name, description) VALUES (?, ?, ?)";
        return executeInsert(sql, permission.getCode(), permission.getName(), permission.getDescription());
    }

    public List<String> findPermissionCodesByUserId(Long userId) {
        String sql = "SELECT DISTINCT p.code FROM permission p " +
                "JOIN role_permission rp ON p.id = rp.permission_id " +
                "JOIN user_role ur ON rp.role_id = ur.role_id " +
                "WHERE ur.user_id = ?";
        List<String> codes = new ArrayList<>();
        ConnectionHolder holder = borrow();
        try (PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codes.add(rs.getString("code"));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("查询用户权限失败: " + sql, e);
        } finally {
            holder.closeIfBorrowed();
        }
        return codes;
    }
}
