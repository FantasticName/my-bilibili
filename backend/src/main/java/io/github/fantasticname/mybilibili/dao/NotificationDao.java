package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.Notification;

import java.util.List;

/**
 * 消息通知DAO，封装通知表的数据库操作
 *
 * <p>继承 BaseDao&lt;Notification&gt;，获得通用CRUD方法。
 * 主要操作为：插入通知、查询用户通知列表、标记已读。</p>
 *
 * @author FantasticName
 */
@Component
public class NotificationDao extends BaseDao<Notification> {

    /**
     * 插入一条通知
     *
     * <p>由NotificationConsumer异步调用，写入DB。
     * 写入成功后通过WebSocket推送到客户端。</p>
     *
     * @param notification 通知实体
     * @return 自增主键ID
     */
    public long insert(Notification notification) {
        String sql = "INSERT INTO notification (user_id, from_user_id, notify_type, target_id, target_type, " +
                     "content, is_read, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 0, 0, NOW())";
        return executeInsert(sql,
                notification.getUserId(),
                notification.getFromUserId(),
                notification.getNotifyType(),
                notification.getTargetId(),
                notification.getTargetType(),
                notification.getContent()
        );
    }

    /**
     * 查询用户的通知列表（按时间倒序，分页）
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 通知列表
     */
    public List<Notification> listByUser(long userId, int offset, int limit) {
        String sql = "SELECT * FROM notification WHERE user_id = ? AND status = 0 " +
                     "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryList(sql, userId, limit, offset);
    }

    /**
     * 查询用户未读通知数
     *
     * <p>前端用于显示"消息中心"小红点数字。</p>
     *
     * @param userId 用户ID
     * @return 未读通知数
     */
    public int countUnread(long userId) {
        String sql = "SELECT COUNT(*) AS cnt FROM notification WHERE user_id = ? AND is_read = 0 AND status = 0";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询未读通知数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 将所有通知标记为已读
     *
     * @param userId 用户ID
     */
    public void markAllRead(long userId) {
        String sql = "UPDATE notification SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        executeUpdate(sql, userId);
    }

    /**
     * 将指定通知标记为已读
     *
     * @param notificationId 通知ID
     */
    public void markRead(long notificationId) {
        String sql = "UPDATE notification SET is_read = 1 WHERE id = ?";
        executeUpdate(sql, notificationId);
    }
}