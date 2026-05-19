package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.FavoriteFolder;

import java.util.List;

/**
 * 收藏夹DAO，封装收藏夹表的数据库操作
 *
 * @author FantasticName
 */
@Component
public class FavoriteFolderDao extends BaseDao<FavoriteFolder> {

    /**
     * 根据ID查询收藏夹
     *
     * @param id 收藏夹ID
     * @return 收藏夹对象，不存在返回null
     */
    public FavoriteFolder findById(long id) {
        String sql = "SELECT * FROM favorite_folder WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 根据用户ID查询所有收藏夹
     *
     * @param userId 用户ID
     * @return 收藏夹列表
     */
    public List<FavoriteFolder> listByUserId(long userId) {
        String sql = "SELECT * FROM favorite_folder WHERE user_id = ? ORDER BY is_default DESC, created_at ASC";
        return queryList(sql, userId);
    }

    /**
     * 查询用户的默认收藏夹
     *
     * @param userId 用户ID
     * @return 默认收藏夹，不存在返回null
     */
    public FavoriteFolder findDefaultByUserId(long userId) {
        String sql = "SELECT * FROM favorite_folder WHERE user_id = ? AND is_default = 1";
        return queryOne(sql, userId);
    }

    /**
     * 插入收藏夹
     *
     * @param folder 收藏夹对象
     * @return 自增主键ID
     */
    public long insert(FavoriteFolder folder) {
        String sql = "INSERT INTO favorite_folder (user_id, name, is_default) VALUES (?, ?, ?)";
        return executeInsert(sql, folder.getUserId(), folder.getName(), folder.getIsDefault());
    }

    /**
     * 删除收藏夹
     *
     * @param id 收藏夹ID
     */
    public void delete(long id) {
        String sql = "DELETE FROM favorite_folder WHERE id = ?";
        executeUpdate(sql, id);
    }

    /**
     * 重命名收藏夹
     *
     * @param id   收藏夹ID
     * @param name 新名称
     */
    public void rename(long id, String name) {
        String sql = "UPDATE favorite_folder SET name = ? WHERE id = ?";
        executeUpdate(sql, name, id);
    }

    /**
     * 检查用户是否已有同名收藏夹
     *
     * @param userId 用户ID
     * @param name   收藏夹名称
     * @return 是否存在同名收藏夹
     */
    public boolean existsByName(long userId, String name) {
        String sql = "SELECT COUNT(*) AS cnt FROM favorite_folder WHERE user_id = ? AND name = ?";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt") > 0;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("检查收藏夹名称失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return false;
    }

    /**
     * 统计收藏夹中的视频数量
     *
     * @param folderId 收藏夹ID
     * @return 视频数量
     */
    public int countVideosByFolderId(long folderId) {
        String sql = "SELECT COUNT(*) AS cnt FROM favorite_record WHERE folder_id = ?";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, folderId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计收藏夹视频数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 删除收藏夹中的所有记录
     *
     * @param folderId 收藏夹ID
     */
    public void deleteRecordsByFolderId(long folderId) {
        String sql = "DELETE FROM favorite_record WHERE folder_id = ?";
        executeUpdate(sql, folderId);
    }
}
