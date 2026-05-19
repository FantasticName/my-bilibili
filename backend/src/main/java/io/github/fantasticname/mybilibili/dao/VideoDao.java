package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.Video;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频DAO，封装视频表的数据库操作
 *
 * <p>继承 BaseDao&lt;Video&gt;，自动获得通用CRUD方法。
 * 本类只编写视频相关的特定业务SQL。</p>
 *
 * @author FantasticName
 */
@Component
public class VideoDao extends BaseDao<Video> {

    /**
     * 根据ID查询视频
     *
     * @param id 视频ID
     * @return 视频对象，不存在返回null
     */
    public Video findById(long id) {
        String sql = "SELECT * FROM video WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 根据发布者ID查询视频列表（分页）
     *
     * @param userId 发布者ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 视频列表
     */
    public List<Video> listByUserId(long userId, int offset, int limit) {
        String sql = "SELECT * FROM video WHERE user_id = ? AND status = 0 ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, userId, limit, offset);
    }

    /**
     * 根据发布者ID统计视频总数
     *
     * @param userId 发布者ID
     * @return 视频总数
     */
    public int countByUserId(long userId) {
        String sql = "SELECT COUNT(*) AS cnt FROM video WHERE user_id = ? AND status = 0";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计视频数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 查询所有正常状态的视频列表（分页）
     *
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 视频列表
     */
    public List<Video> listAll(int offset, int limit) {
        String sql = "SELECT * FROM video WHERE status = 0 ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, limit, offset);
    }

    /**
     * 统计所有正常状态的视频总数
     *
     * @return 视频总数
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) AS cnt FROM video WHERE status = 0";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计视频总数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 根据分区查询视频列表（分页）
     *
     * @param category 分区名称
     * @param offset   偏移量
     * @param limit    每页数量
     * @return 视频列表
     */
    public List<Video> listByCategory(String category, int offset, int limit) {
        String sql = "SELECT * FROM video WHERE category = ? AND status = 0 ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, category, limit, offset);
    }

    /**
     * 根据分区统计视频总数
     *
     * @param category 分区名称
     * @return 视频总数
     */
    public int countByCategory(String category) {
        String sql = "SELECT COUNT(*) AS cnt FROM video WHERE category = ? AND status = 0";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setString(1, category);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计分区视频数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 游标分页查询视频列表（用于Feed流）
     *
     * @param cursor 游标（上一页最后一条的created_at）
     * @param limit  每页数量
     * @return 视频列表
     */
    public List<Video> listByCursor(String cursor, int limit) {
        if (cursor == null || cursor.isEmpty()) {
            String sql = "SELECT * FROM video WHERE status = 0 ORDER BY created_at DESC LIMIT ?";
            return queryList(sql, limit);
        }
        String sql = "SELECT * FROM video WHERE status = 0 AND created_at < ? ORDER BY created_at DESC LIMIT ?";
        return queryList(sql, cursor, limit);
    }

    /**
     * 插入新视频（发布视频时使用）
     *
     * @param video 视频对象
     * @return 自增主键ID
     */
    public long insert(Video video) {
        String sql = "INSERT INTO video (title, description, cover_url, video_url, category, user_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 0)";
        return executeInsert(sql,
                video.getTitle(),
                video.getDescription(),
                video.getCoverUrl(),
                video.getVideoUrl(),
                video.getCategory(),
                video.getUserId()
        );
    }

    /**
     * 增加播放量
     *
     * @param videoId 视频ID
     */
    public void incrementViewCount(long videoId) {
        String sql = "UPDATE video SET view_count = view_count + 1 WHERE id = ?";
        executeUpdate(sql, videoId);
    }

    /**
     * 增加点赞数
     *
     * @param videoId 视频ID
     */
    public void incrementLikeCount(long videoId) {
        String sql = "UPDATE video SET like_count = like_count + 1 WHERE id = ?";
        executeUpdate(sql, videoId);
    }

    /**
     * 减少点赞数
     *
     * @param videoId 视频ID
     */
    public void decrementLikeCount(long videoId) {
        String sql = "UPDATE video SET like_count = like_count - 1 WHERE id = ?";
        executeUpdate(sql, videoId);
    }

    /**
     * 更新视频状态（下架/恢复）
     *
     * @param videoId 视频ID
     * @param status  状态值（0-正常，1-下架）
     */
    public void updateStatus(long videoId, int status) {
        String sql = "UPDATE video SET status = ?, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, status, videoId);
    }

    /**
     * 根据标题模糊搜索视频
     *
     * @param keyword 搜索关键词
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 视频列表
     */
    public List<Video> searchByTitle(String keyword, int offset, int limit) {
        String sql = "SELECT * FROM video WHERE status = 0 AND title LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, "%" + keyword + "%", limit, offset);
    }

    /**
     * 根据标题模糊搜索视频总数
     *
     * @param keyword 搜索关键词
     * @return 匹配总数
     */
    public int countByTitle(String keyword) {
        String sql = "SELECT COUNT(*) AS cnt FROM video WHERE status = 0 AND title LIKE ?";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("搜索视频计数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 获取所有正常状态的视频ID列表（用于布隆过滤器全量重建）
     *
     * <p>布隆过滤器重建时，需要从DB拉取全量有效ID。
     * 这个方法只查询ID列（不是SELECT *），减少数据库传输量。</p>
     *
     * @return 视频ID列表
     */
    public List<Long> findAllIds() {
        String sql = "SELECT id FROM video WHERE status = 0";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("查询视频ID列表失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
    }
}
