package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.dao.NotificationDao;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.websocket.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 消息通知服务实现类
 *
 * <p>通知系统的核心职责：接收业务事件，持久化存储，实时推送。</p>
 *
 * <p>通知产生的触发点（在其他Service中调用本Service）：</p>
 * <ul>
 *   <li>用户A关注了用户B → FollowServiceImpl调用 → 通知B"XXX关注了你"</li>
 *   <li>用户A评论了用户B的动态 → CommentServiceImpl调用 → 通知B"XXX评论了你的动态"</li>
 *   <li>用户A点赞了用户B的视频 → LikeServiceImpl调用 → 通知B"XXX赞了你的视频"</li>
 * </ul>
 *
 * <p>WebSocket推送机制：
 * 每个已登录用户连接WebSocket后，服务端维护 userId → Session 映射。
 * 发送通知时，查找该用户的WebSocket Session，发送JSON消息。</p>
 *
 * @author FantasticName
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private NotificationDao notificationDao;

    public NotificationServiceImpl() {
    }

    @Override
    public long sendNotification(Notification notification) {
        log.info("发送通知: userId={}, notifyType={}, content={}",
                notification.getUserId(), notification.getNotifyType(), notification.getContent());

        // ===== 第1步：写入DB（持久化） =====
        long notificationId = notificationDao.insert(notification);

        // ===== 第2步：WebSocket实时推送（如果用户在线） =====
        try {
            // 构建推送数据
            String pushData = String.format(
                    "{\"id\":%d,\"type\":\"%s\",\"content\":\"%s\",\"fromUserId\":%d,\"targetId\":%d,\"time\":\"%s\"}",
                    notificationId,
                    notification.getNotifyType(),
                    notification.getContent(),
                    notification.getFromUserId() != null ? notification.getFromUserId() : 0,
                    notification.getTargetId() != null ? notification.getTargetId() : 0,
                    java.time.LocalDateTime.now().toString()
            );
            // 推送给目标用户
            WebSocketServer.sendToUser(notification.getUserId(), pushData);
            log.debug("WebSocket推送成功: userId={}", notification.getUserId());
        } catch (Exception e) {
            // 推送失败不影响主流程（用户可能不在线）
            log.debug("WebSocket推送失败（用户可能不在线）: userId={}", notification.getUserId());
        }

        return notificationId;
    }

    @Override
    public List<Notification> getUserNotifications(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        return notificationDao.listByUser(userId, offset, size);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return notificationDao.countUnread(userId);
    }

    @Override
    public void markAllRead(Long userId) {
        notificationDao.markAllRead(userId);
        log.info("所有通知已标记为已读: userId={}", userId);
    }

    @Override
    public void markRead(Long notificationId) {
        notificationDao.markRead(notificationId);
        log.info("通知已标记为已读: notificationId={}", notificationId);
    }
}