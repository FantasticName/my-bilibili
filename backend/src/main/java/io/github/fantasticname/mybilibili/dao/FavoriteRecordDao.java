package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.FavoriteRecord;

import java.util.List;

/**
 * 收藏记录DAO，封装收藏记录表的数据库操作
 *
 * @author FantasticName
 */
@Component
public class FavoriteRecordDao extends BaseDao<FavoriteRecord> {

    /**
     * 查询收藏记录是否已存在
     *
     * @param folderId   收藏夹ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 收藏记录，不存在返回null
     */
    public FavoriteRecord findByFolderAndTarget(long folderId, int targetType, long targetId) {
        String sql = "SELECT * FROM favorite_record WHERE folder_id = ? AND target_type = ? AND target_id = ?";
        return queryOne(sql, folderId, targetType, targetId);
    }

    /**
     * 查询用户是否已收藏某个目标（任意收藏夹）
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 收藏记录，不存在返回null
     */
    public FavoriteRecord findByUserAndTarget(long userId, int targetType, long targetId) {
        String sql = "SELECT * FROM favorite_record WHERE user_id = ? AND target_type = ? AND target_id = ?";
        return queryOne(sql, userId, targetType, targetId);
    }

    /**
     * 根据收藏夹ID查询收藏记录列表（分页）
     *
     * @param folderId 收藏夹ID
     * @param offset   偏移量
     * @param limit    每页数量
     * @return 收藏记录列表
     */
    public List<FavoriteRecord> listByFolderId(long folderId, int offset, int limit) {
        String sql = "SELECT * FROM favorite_record WHERE folder_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, folderId, limit, offset);
    }

    /**
     * 统计收藏夹中的收藏数
     *
     * @param folderId 收藏夹ID
     * @return 收藏数
     */
    public int countByFolderId(long folderId) {
        String sql = "SELECT COUNT(*) AS cnt FROM favorite_record WHERE folder_id = ?";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, folderId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计收藏数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 插入收藏记录
     *
     * @param record 收藏记录对象
     * @return 自增主键ID
     */
    public long insert(FavoriteRecord record) {
        String sql = "INSERT INTO favorite_record (folder_id, user_id, target_type, target_id) VALUES (?, ?, ?, ?)";
        return executeInsert(sql, record.getFolderId(), record.getUserId(), record.getTargetType(), record.getTargetId());
    }

    /**
     * 删除收藏记录
     *
     * @param id 收藏记录ID
     */
    public void delete(long id) {
        String sql = "DELETE FROM favorite_record WHERE id = ?";
        executeUpdate(sql, id);
    }

    /**
     * 根据收藏夹和目标删除收藏记录
     *
     * @param folderId   收藏夹ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     */
    public void deleteByFolderAndTarget(long folderId, int targetType, long targetId) {
        String sql = "DELETE FROM favorite_record WHERE folder_id = ? AND target_type = ? AND target_id = ?";
        executeUpdate(sql, folderId, targetType, targetId);
    }

    /**
     * 查询用户对某个目标已收藏的所有收藏夹ID
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 已收藏的收藏夹ID列表
     */
    public List<Long> findFavoritedFolderIds(long userId, int targetType, long targetId) {
        String sql = "SELECT folder_id FROM favorite_record WHERE user_id = ? AND target_type = ? AND target_id = ?";
        ConnectionHolder holder = borrow();
        List<Long> folderIds = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, targetType);
            ps.setLong(3, targetId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    folderIds.add(rs.getLong("folder_id"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询已收藏的收藏夹ID失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return folderIds;
    }

    /**
     * 删除用户对某个目标的所有收藏记录
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     */
    public void deleteByUserAndTarget(long userId, int targetType, long targetId) {
        String sql = "DELETE FROM favorite_record WHERE user_id = ? AND target_type = ? AND target_id = ?";
        executeUpdate(sql, userId, targetType, targetId);
    }
}
