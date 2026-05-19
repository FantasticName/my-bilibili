package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.LikeRecord;

import java.util.List;

/**
 * 点赞记录DAO，封装点赞记录表的数据库操作
 *
 * <p>继承 BaseDao&lt;LikeRecord&gt;，自动获得通用CRUD方法。
 * 本类只编写点赞相关的特定业务SQL。</p>
 *
 * <p>点赞表使用统一表存储视频点赞和评论点赞，
 * 通过 targetType 区分：1-视频，2-评论。</p>
 *
 * @author FantasticName
 */
@Component
public class LikeRecordDao extends BaseDao<LikeRecord> {

    /**
     * 查询点赞记录是否已存在
     *
     * @param userId     点赞用户ID
     * @param targetType 目标类型（1-视频，2-评论）
     * @param targetId   目标ID
     * @return 点赞记录，不存在返回null
     */
    public LikeRecord findByUserAndTarget(long userId, int targetType, long targetId) {
        String sql = "SELECT * FROM like_record WHERE user_id = ? AND target_type = ? AND target_id = ?";
        return queryOne(sql, userId, targetType, targetId);
    }

    /**
     * 查询用户的所有点赞记录
     *
     * @param userId 用户ID
     * @return 点赞记录列表
     */
    public List<LikeRecord> listByUserId(long userId) {
        String sql = "SELECT * FROM like_record WHERE user_id = ? ORDER BY created_at DESC";
        return queryList(sql, userId);
    }

    /**
     * 查询某个目标的所有点赞记录
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 点赞记录列表
     */
    public List<LikeRecord> listByTarget(int targetType, long targetId) {
        String sql = "SELECT * FROM like_record WHERE target_type = ? AND target_id = ? ORDER BY created_at DESC";
        return queryList(sql, targetType, targetId);
    }

    /**
     * 统计某个目标的点赞数
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 点赞数
     */
    public int countByTarget(int targetType, long targetId) {
        String sql = "SELECT COUNT(*) AS cnt FROM like_record WHERE target_type = ? AND target_id = ?";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setInt(1, targetType);
            ps.setLong(2, targetId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询点赞数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 插入点赞记录
     *
     * @param likeRecord 点赞记录对象
     * @return 自增主键ID
     */
    public long insert(LikeRecord likeRecord) {
        String sql = "INSERT INTO like_record (user_id, target_type, target_id) VALUES (?, ?, ?)";
        return executeInsert(sql, likeRecord.getUserId(), likeRecord.getTargetType(), likeRecord.getTargetId());
    }

    /**
     * 删除点赞记录（取消点赞）
     *
     * @param userId     点赞用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     */
    public void delete(long userId, int targetType, long targetId) {
        String sql = "DELETE FROM like_record WHERE user_id = ? AND target_type = ? AND target_id = ?";
        executeUpdate(sql, userId, targetType, targetId);
    }
}
