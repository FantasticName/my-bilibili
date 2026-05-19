package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.Post;

import java.util.List;

/**
 * 动态DAO，封装动态表的数据库操作
 *
 * @author FantasticName
 */
@Component
public class PostDao extends BaseDao<Post> {

    /**
     * 根据ID查询动态
     *
     * @param id 动态ID
     * @return 动态对象，不存在返回null
     */
    public Post findById(long id) {
        String sql = "SELECT * FROM post WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 根据用户ID查询动态列表（分页）
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 动态列表
     */
    public List<Post> listByUserId(long userId, int offset, int limit) {
        String sql = "SELECT * FROM post WHERE user_id = ? AND status = 0 ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, userId, limit, offset);
    }

    /**
     * 根据用户ID查询动态列表（游标分页）
     *
     * <p>游标分页比普通分页更适合瀑布流场景。
     * 使用created_at作为游标，避免数据重复或遗漏。</p>
     *
     * @param userId 用户ID
     * @param cursor 游标（上一页最后一条的created_at，首次请求传null）
     * @param limit  每页数量
     * @return 动态列表
     */
    public List<Post> listByUserIdCursor(long userId, String cursor, int limit) {
        if (cursor == null || cursor.isEmpty()) {
            // 首次请求，不需要游标条件
            String sql = "SELECT * FROM post WHERE user_id = ? AND status = 0 ORDER BY created_at DESC LIMIT ?";
            return queryList(sql, userId, limit);
        }
        // 带游标条件：查询created_at小于游标值的记录
        String sql = "SELECT * FROM post WHERE user_id = ? AND status = 0 AND created_at < ? ORDER BY created_at DESC LIMIT ?";
        return queryList(sql, userId, cursor, limit);
    }

    /**
     * 根据用户ID统计动态总数
     *
     * @param userId 用户ID
     * @return 动态总数
     */
    public int countByUserId(long userId) {
        String sql = "SELECT COUNT(*) AS cnt FROM post WHERE user_id = ? AND status = 0";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计动态数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 游标分页查询关注用户的动态列表（Feed流）
     *
     * @param followeeIds 关注的用户ID列表
     * @param cursor      游标（上一页最后一条的created_at）
     * @param limit       每页数量
     * @return 动态列表
     */
    public List<Post> listFeedByCursor(List<Long> followeeIds, String cursor, int limit) {
        if (followeeIds == null || followeeIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM post WHERE status = 0 AND user_id IN (");
        for (int i = 0; i < followeeIds.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");
        if (cursor != null && !cursor.isEmpty()) {
            sql.append(" AND created_at < ?");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");

        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Long id : followeeIds) {
                ps.setLong(idx++, id);
            }
            if (cursor != null && !cursor.isEmpty()) {
                ps.setString(idx++, cursor);
            }
            ps.setInt(idx, limit);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                List<Post> list = new java.util.ArrayList<>();
                while (rs.next()) {
                    list.add(mapper.mapRow(rs));
                }
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询Feed流失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
    }

    /**
     * 插入动态
     *
     * @param post 动态对象
     * @return 自增主键ID
     */
    public long insert(Post post) {
        String sql = "INSERT INTO post (content, images, user_id, status) VALUES (?, ?, ?, 0)";
        return executeInsert(sql, post.getContent(), post.getImages(), post.getUserId());
    }

    /**
     * 软删除动态
     *
     * @param id 动态ID
     */
    public void softDelete(long id) {
        String sql = "UPDATE post SET status = 1, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, id);
    }

    /**
     * 更新动态内容和图片
     *
     * @param id      动态ID
     * @param content 新的文字内容（可为null）
     * @param images  新的图片文件名（逗号分隔，可为null）
     */
    public void update(long id, String content, String images) {
        String sql = "UPDATE post SET content = ?, images = ?, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, content, images, id);
    }

    /**
     * 增加动态点赞数
     *
     * @param postId 动态ID
     */
    public void incrementLikeCount(long postId) {
        String sql = "UPDATE post SET like_count = like_count + 1 WHERE id = ?";
        executeUpdate(sql, postId);
    }

    /**
     * 减少动态点赞数
     *
     * @param postId 动态ID
     */
    public void decrementLikeCount(long postId) {
        String sql = "UPDATE post SET like_count = like_count - 1 WHERE id = ?";
        executeUpdate(sql, postId);
    }

    /**
     * 增加动态评论数
     *
     * @param postId 动态ID
     */
    public void incrementCommentCount(long postId) {
        String sql = "UPDATE post SET comment_count = comment_count + 1 WHERE id = ?";
        executeUpdate(sql, postId);
    }

    /**
     * 减少动态评论数
     *
     * @param postId 动态ID
     */
    public void decrementCommentCount(long postId) {
        String sql = "UPDATE post SET comment_count = comment_count - 1 WHERE id = ?";
        executeUpdate(sql, postId);
    }

    /**
     * 根据内容模糊搜索动态
     *
     * @param keyword 搜索关键词
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 动态列表
     */
    public List<Post> searchByContent(String keyword, int offset, int limit) {
        String sql = "SELECT * FROM post WHERE status = 0 AND content LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, "%" + keyword + "%", limit, offset);
    }

    /**
     * 根据内容模糊搜索动态总数
     *
     * @param keyword 搜索关键词
     * @return 匹配总数
     */
    public int countByContent(String keyword) {
        String sql = "SELECT COUNT(*) AS cnt FROM post WHERE status = 0 AND content LIKE ?";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("搜索动态计数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 根据ID列表批量查询动态（用于Feed流从Redis收件箱拿到ID后批量查DB）
     *
     * <p>典型使用场景：Push-Pull混合Feed流中，先从Redis收件箱拉取动态ID列表，
     * 然后通过此方法批量查询完整的Post数据（包含内容、图片等）。</p>
     *
     * <p>使用UNION ALL + 子查询的方式保证返回顺序与传入ID列表一致。
     * 注意：如果ids为空，直接返回空列表。</p>
     *
     * @param ids 动态ID列表（按需要的顺序排列）
     * @return 动态列表（顺序与ids一致）
     */
    public List<Post> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 动态拼接 IN (?,?,?) 的占位符
        StringBuilder sql = new StringBuilder("SELECT * FROM post WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(") AND status = 0");

        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                List<Post> list = new java.util.ArrayList<>();
                while (rs.next()) {
                    list.add(mapper.mapRow(rs));
                }
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("批量查询动态失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
    }

    /**
     * 查询热门动态列表（按点赞数+评论数降序，带时间衰减）
     *
     * <p>简单的热度算法：like_count + comment_count * 2 降序排列。
     * 只查询最近7天的动态，避免历史数据占据热榜。</p>
     *
     * <p>使用场景：
     * <ul>
     *   <li>Feed流兜底：当用户没有关注任何人时，展示热门动态</li>
     *   <li>Sentinel限流降级：接口被限流时，返回热榜兜底数据</li>
     *   <li>推荐流种子数据</li>
     * </ul>
     *
     * @param limit 查询条数
     * @return 热门动态列表
     */
    public List<Post> listHotPosts(int limit) {
        // 热度 = 点赞数 + 评论数×2（评论互动性更强，权重更高）
        // 仅查询最近7天的动态
        String sql = "SELECT * FROM post WHERE status = 0 AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY) " +
                     "ORDER BY (like_count + comment_count * 2) DESC LIMIT ?";
        return queryList(sql, limit);
    }

    /**
     * 获取所有正常状态的动态ID列表（用于布隆过滤器全量重建）
     *
     * @return 动态ID列表
     */
    public List<Long> findAllIds() {
        String sql = "SELECT id FROM post WHERE status = 0";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            List<Long> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("查询动态ID列表失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
    }
}
