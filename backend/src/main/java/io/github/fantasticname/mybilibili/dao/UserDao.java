package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.User;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户DAO，封装用户表的数据库操作
 *
 * <p>继承 BaseDao&lt;User&gt;，自动获得 queryOne、queryList、executeUpdate、executeInsert 方法。
 * 只需编写特定的业务SQL即可。</p>
 *
 * <p>通过泛型反射，BaseDao的构造函数会自动识别出实体类型为User，
 * 并创建对应的ResultSetMapper，无需手动指定。</p>
 *
 * @author FantasticName
 */
@Component
public class UserDao extends BaseDao<User> {

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户对象，不存在返回null
     */
    public User findById(long id) {
        String sql = "SELECT * FROM user WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 根据手机号查询用户（登录时使用）
     *
     * @param phone 手机号
     * @return 用户对象，不存在返回null
     */
    public User findByPhone(String phone) {
        String sql = "SELECT * FROM user WHERE phone = ?";
        return queryOne(sql, phone);
    }

    /**
     * 根据角色查询用户列表
     *
     * @param role 角色值（0-普通用户，1-博主，2-管理员）
     * @return 用户列表
     */
    public List<User> findByRole(int role) {
        String sql = "SELECT * FROM user WHERE role = ? ORDER BY created_at DESC";
        return queryList(sql, role);
    }

    /**
     * 插入新用户（注册时使用）
     *
     * <p>注意：不插入id（自增）、created_at（默认值）、updated_at（自动更新）</p>
     *
     * @param user 用户对象
     * @return 自增主键ID
     */
    public long insert(User user) {
        String sql = "INSERT INTO user (phone, password_hash, nickname, avatar, role, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        return executeInsert(sql,
                user.getPhone(),
                user.getPasswordHash(),
                user.getNickname(),
                user.getAvatar(),
                user.getRole(),
                user.getStatus()
        );
    }

    /**
     * 更新用户状态（封禁/解封）
     *
     * @param userId 用户ID
     * @param status 状态值（0-正常，1-封禁）
     */
    public void updateStatus(long userId, int status) {
        String sql = "UPDATE user SET status = ?, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, status, userId);
    }

    /**
     * 更新用户基本信息（昵称、头像、手机号、密码）
     *
     * @param user 用户对象（必须包含id）
     */
    public void update(User user) {
        String sql = "UPDATE user SET nickname = ?, avatar = ?, phone = ?, password_hash = ?, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, user.getNickname(), user.getAvatar(), user.getPhone(), user.getPasswordHash(), user.getId());
    }

    /**
     * 查询所有用户列表
     *
     * @return 用户列表
     */
    public List<User> findAll() {
        String sql = "SELECT * FROM user ORDER BY created_at DESC";
        return queryList(sql);
    }

    /**
     * 查询所有正常状态用户的ID列表
     *
     * <p>用于推荐池离线计算时遍历所有用户。
     * 只查询id字段，避免加载完整用户对象，节省内存。</p>
     *
     * @return 用户ID列表
     */
    public List<Long> findAllIds() {
        String sql = "SELECT id FROM user WHERE status = 0";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("查询所有用户ID失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
    }

    /**
     * 根据昵称模糊搜索用户
     *
     * @param keyword 搜索关键词
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 用户列表
     */
    public List<User> searchByNickname(String keyword, int offset, int limit) {
        String sql = "SELECT * FROM user WHERE nickname LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, "%" + keyword + "%", limit, offset);
    }

    /**
     * 根据昵称模糊搜索用户总数
     *
     * @param keyword 搜索关键词
     * @return 匹配总数
     */
    public int countByNickname(String keyword) {
        String sql = "SELECT COUNT(*) AS cnt FROM user WHERE nickname LIKE ?";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("搜索用户计数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }
}
