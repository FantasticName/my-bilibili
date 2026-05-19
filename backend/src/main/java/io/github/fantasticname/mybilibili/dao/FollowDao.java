package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.Follow;

import java.util.List;

/**
 * 关注DAO，封装关注表的数据库操作
 *
 * <p>继承 BaseDao&lt;Follow&gt;，自动获得通用CRUD方法。
 * 本类只编写关注相关的特定业务SQL。</p>
 *
 * @author FantasticName
 */
@Component
public class FollowDao extends BaseDao<Follow> {

    /**
     * 查询关注关系是否已存在
     *
     * @param followerId 关注者ID
     * @param followeeId 被关注者ID
     * @return 关注记录，不存在返回null
     */
    public Follow findByFollowerAndFollowee(long followerId, long followeeId) {
        String sql = "SELECT * FROM follow WHERE follower_id = ? AND followee_id = ?";
        return queryOne(sql, followerId, followeeId);
    }

    /**
     * 查询用户的关注列表（我关注了谁），分页
     *
     * @param followerId 关注者ID
     * @param offset     偏移量
     * @param limit      每页数量
     * @return 关注列表
     */
    public List<Follow> listFollowing(long followerId, int offset, int limit) {
        String sql = "SELECT * FROM follow WHERE follower_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, followerId, limit, offset);
    }

    /**
     * 查询用户的粉丝列表（谁关注了我），分页
     *
     * @param followeeId 被关注者ID
     * @param offset     偏移量
     * @param limit      每页数量
     * @return 粉丝列表
     */
    public List<Follow> listFollowers(long followeeId, int offset, int limit) {
        String sql = "SELECT * FROM follow WHERE followee_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, followeeId, limit, offset);
    }

    /**
     * 统计用户的关注数
     *
     * @param followerId 关注者ID
     * @return 关注数
     */
    public int countFollowing(long followerId) {
        String sql = "SELECT COUNT(*) AS cnt FROM follow WHERE follower_id = ?";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, followerId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询关注数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 统计用户的粉丝数
     *
     * @param followeeId 被关注者ID
     * @return 粉丝数
     */
    public int countFollowers(long followeeId) {
        String sql = "SELECT COUNT(*) AS cnt FROM follow WHERE followee_id = ?";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, followeeId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询粉丝数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 插入关注关系
     *
     * @param follow 关注对象
     * @return 自增主键ID
     */
    public long insert(Follow follow) {
        String sql = "INSERT INTO follow (follower_id, followee_id) VALUES (?, ?)";
        return executeInsert(sql, follow.getFollowerId(), follow.getFolloweeId());
    }

    /**
     * 删除关注关系（取关）
     *
     * @param followerId 关注者ID
     * @param followeeId 被关注者ID
     */
    public void delete(long followerId, long followeeId) {
        String sql = "DELETE FROM follow WHERE follower_id = ? AND followee_id = ?";
        executeUpdate(sql, followerId, followeeId);
    }
}
