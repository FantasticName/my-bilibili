package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.Role;

import java.util.List;

/**
 * 角色DAO，封装角色表的数据库操作（RBAC）
 *
 * <p>继承 BaseDao&lt;Role&gt;，自动获得通用CRUD方法。
 * 为二轮管理员权限设计。</p>
 *
 * @author FantasticName
 */
@Component
public class RoleDao extends BaseDao<Role> {

    /**
     * 根据ID查询角色
     *
     * @param id 角色ID
     * @return 角色对象，不存在返回null
     */
    public Role findById(int id) {
        String sql = "SELECT * FROM role WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 根据角色代码查询角色
     *
     * @param code 角色代码，如 "admin"
     * @return 角色对象，不存在返回null
     */
    public Role findByCode(String code) {
        String sql = "SELECT * FROM role WHERE code = ?";
        return queryOne(sql, code);
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    public List<Role> findAll() {
        String sql = "SELECT * FROM role ORDER BY id";
        return queryList(sql);
    }

    /**
     * 插入新角色
     *
     * @param role 角色对象
     * @return 自增主键ID
     */
    public long insert(Role role) {
        String sql = "INSERT INTO role (code, name) VALUES (?, ?)";
        return executeInsert(sql, role.getCode(), role.getName());
    }
}
