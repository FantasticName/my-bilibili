package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.entity.Notification;

import java.util.List;
import java.util.Map;

/**
 * 消息通知服务接口
 *
 * <p>提供通知的发送、查询、标记已读功能。</p>
 *
 * @author FantasticName
 */
public interface NotificationService {

    /**
     * 发送通知（写入DB + WebSocket推送）
     *
     * <p>通知发送的完整流程：
     * <ol>
     *   <li>业务方法（如点赞、评论、关注）调用此方法</li>
     *   <li>通知写入MySQL notification表</li>
     *   <li>通过WebSocket实时推送给在线用户</li>
     * </ol>
     *
     * @param notification 通知实体（userId、fromUserId、notifyType、content等已填充）
     * @return 通知ID
     */
    long sendNotification(Notification notification);

    /**
     * 查询用户通知列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 通知列表
     */
    List<Notification> getUserNotifications(Long userId, int page, int size);

    /**
     * 查询未读通知数
     *
     * @param userId 用户ID
     * @return 未读数
     */
    int getUnreadCount(Long userId);

    /**
     * 标记所有通知为已读
     *
     * @param userId 用户ID
     */
    void markAllRead(Long userId);

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知ID
     */
    void markRead(Long notificationId);
}