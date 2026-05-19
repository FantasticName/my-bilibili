package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.entity.Comment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 评论DAO，封装评论表的数据库操作
 *
 * <p>继承 BaseDao&lt;Comment&gt;，自动获得通用CRUD方法。
 * 本类只编写评论相关的特定业务SQL。</p>
 *
 * @author FantasticName
 */
@Component
public class CommentDao extends BaseDao<Comment> {

    /**
     * 根据ID查询评论
     *
     * @param id 评论ID
     * @return 评论对象，不存在返回null
     */
    public Comment findById(long id) {
        String sql = "SELECT * FROM comment WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 查询顶层评论（热门排序 + 游标分页）
     *
     * <p>热门模式：按点赞数从高到低排序，游标为上一页最后一条评论的点赞数。
     * 当多条评论点赞数相同时，用id降序作为第二排序条件确保分页稳定。</p>
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param cursor     游标（上一页最后一条评论的likeCount，首次请求传null）
     * @param cursorId   游标对应的评论ID（点赞数相同时用于区分，首次请求传null）
     * @param size       每页数量
     * @return 顶层评论列表
     */
    public List<Comment> listTopCommentsHot(int targetType, long targetId,
                                             Integer cursor, Long cursorId, int size) {
        if (cursor == null) {
            String sql = "SELECT * FROM comment WHERE target_type = ? AND target_id = ? " +
                    "AND parent_id IS NULL AND status = 0 " +
                    "ORDER BY like_count DESC, id DESC LIMIT ?";
            return queryList(sql, targetType, targetId, size);
        } else {
            // 游标分页：点赞数小于cursor，或点赞数等于cursor但id小于cursorId
            String sql = "SELECT * FROM comment WHERE target_type = ? AND target_id = ? " +
                    "AND parent_id IS NULL AND status = 0 " +
                    "AND (like_count < ? OR (like_count = ? AND id < ?)) " +
                    "ORDER BY like_count DESC, id DESC LIMIT ?";
            return queryList(sql, targetType, targetId, cursor, cursor, cursorId, size);
        }
    }

    /**
     * 根据多个顶层评论ID批量查询所有子回复
     *
     * <p>一次查出这些顶层评论下的所有子回复（不限层级），
     * 然后在内存中用HashMap按parentId分组，O(n)组装树形结构。</p>
     *
     * @param topCommentIds 顶层评论ID列表
     * @return 所有子回复列表
     */
    public List<Comment> listRepliesByParentIds(List<Long> topCommentIds) {
        if (topCommentIds == null || topCommentIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        // 构建IN子句的占位符
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < topCommentIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String sql = "SELECT * FROM comment WHERE parent_id IN (" + placeholders + ") " +
                "AND status = 0 ORDER BY like_count DESC, id DESC";
        return queryList(sql, topCommentIds.toArray());
    }

    /**
     * 统计某条评论的子回复数量
     *
     * @param parentId 父评论ID
     * @return 子回复数量
     */
    public int countReplies(long parentId) {
        String sql = "SELECT COUNT(*) AS cnt FROM comment WHERE parent_id = ? AND status = 0";
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, parentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计子回复数量失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 批量统计多个父评论的子回复数量
     *
     * <p>一次SQL查询多个parentId的回复数量，避免N+1查询问题。
     * 返回结果是一个Map，key为parentId，value为对应的回复数量。</p>
     *
     * @param parentIds 父评论ID列表
     * @return parentId到回复数量的映射表
     */
    public Map<Long, Integer> countRepliesByParentIds(List<Long> parentIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (parentIds == null || parentIds.isEmpty()) {
            return result;
        }
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < parentIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String sql = "SELECT parent_id, COUNT(*) as reply_count " +
                     "FROM comment WHERE parent_id IN (" + placeholders + ") AND status = 0 " +
                     "GROUP BY parent_id";
        
        io.github.fantasticname.mybilibili.dao.base.ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            for (int i = 0; i < parentIds.size(); i++) {
                ps.setLong(i + 1, parentIds.get(i));
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong("parent_id"), rs.getInt("reply_count"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("批量统计子回复数量失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return result;
    }

    /**
     * 根据父评论ID查询全部回复（用于"展开更多回复"）
     *
     * @param parentId 父评论ID
     * @return 回复列表（按点赞数降序）
     */
    public List<Comment> listAllRepliesByParentId(long parentId) {
        String sql = "SELECT * FROM comment WHERE parent_id = ? AND status = 0 " +
                "ORDER BY like_count DESC, id DESC";
        return queryList(sql, parentId);
    }

    /**
     * 根据父评论ID查询直接子回复（支持游标分页，按点赞数降序）
     *
     * <p>用于"展开更多回复"功能，只查询直接子回复，不递归子孙。
     * 支持游标分页：cursor为上一页最后一条的likeCount，cursorId为对应的评论ID。
     * 当点赞数相同时，用id降序作为第二排序条件确保分页稳定。
     * 此方法避免一次性加载大量子孙回复，降低数据库压力和前端渲染负担。</p>
     *
     * @param parentId 父评论ID
     * @param cursor   游标（上一页最后一条的likeCount，首次请求传null）
     * @param cursorId 游标对应的评论ID（点赞数相同时用于区分，首次请求传null）
     * @param size     每页数量上限
     * @return 直接子回复列表（按点赞数降序）
     */
    public List<Comment> listRepliesWithCursor(long parentId, Integer cursor, Long cursorId, int size) {
        if (cursor == null) {
            // 首次加载：不传游标条件，直接按点赞数降序取前size条
            String sql = "SELECT * FROM comment WHERE parent_id = ? AND status = 0 " +
                    "ORDER BY like_count DESC, id DESC LIMIT ?";
            return queryList(sql, parentId, size);
        } else {
            // 游标分页：点赞数小于cursor，或点赞数等于cursor但id小于cursorId
            String sql = "SELECT * FROM comment WHERE parent_id = ? AND status = 0 " +
                    "AND (like_count < ? OR (like_count = ? AND id < ?)) " +
                    "ORDER BY like_count DESC, id DESC LIMIT ?";
            return queryList(sql, parentId, cursor, cursor, cursorId, size);
        }
    }

    /**
     * 根据目标查询评论列表（只查正常状态的评论，按时间排序）
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 评论列表
     */
    public List<Comment> listByTarget(int targetType, long targetId) {
        String sql = "SELECT * FROM comment WHERE target_type = ? AND target_id = ? AND status = 0 ORDER BY created_at DESC";
        return queryList(sql, targetType, targetId);
    }

    /**
     * 根据父评论ID查询回复列表（按时间排序）
     *
     * @param parentId 父评论ID
     * @return 回复列表
     */
    public List<Comment> listByParentId(long parentId) {
        String sql = "SELECT * FROM comment WHERE parent_id = ? AND status = 0 ORDER BY created_at ASC";
        return queryList(sql, parentId);
    }

    /**
     * 根据用户ID查询评论列表
     *
     * @param userId 用户ID
     * @return 评论列表
     */
    public List<Comment> listByUserId(long userId) {
        String sql = "SELECT * FROM comment WHERE user_id = ? AND status = 0 ORDER BY created_at DESC";
        return queryList(sql, userId);
    }

    /**
     * 插入新评论
     *
     * @param comment 评论对象
     * @return 自增主键ID
     */
    public long insert(Comment comment) {
        String sql = "INSERT INTO comment (content, user_id, target_type, target_id, parent_id, like_count, status) " +
                     "VALUES (?, ?, ?, ?, ?, 0, 0)";
        return executeInsert(sql,
                comment.getContent(),
                comment.getUserId(),
                comment.getTargetType(),
                comment.getTargetId(),
                comment.getParentId()
        );
    }

    /**
     * 软删除评论（将status改为1，不物理删除）
     *
     * @param commentId 评论ID
     */
    public void softDelete(long commentId) {
        String sql = "UPDATE comment SET status = 1, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, commentId);
    }

    /**
     * 增加评论点赞数
     *
     * @param commentId 评论ID
     */
    public void incrementLikeCount(long commentId) {
        String sql = "UPDATE comment SET like_count = like_count + 1 WHERE id = ?";
        executeUpdate(sql, commentId);
    }

    /**
     * 减少评论点赞数
     *
     * @param commentId 评论ID
     */
    public void decrementLikeCount(long commentId) {
        String sql = "UPDATE comment SET like_count = like_count - 1 WHERE id = ?";
        executeUpdate(sql, commentId);
    }
}
