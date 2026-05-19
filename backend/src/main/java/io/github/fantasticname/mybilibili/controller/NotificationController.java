package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息通知控制器，处理通知相关的接口
 *
 * <p>接口列表：</p>
 * <ul>
 *   <li>GET /api/notification/list - 通知列表</li>
 *   <li>GET /api/notification/unread - 未读通知数</li>
 *   <li>POST /api/notification/read-all - 全部标记已读</li>
 *   <li>POST /api/notification/read/{id} - 单条标记已读</li>
 * </ul>
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/notification")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private NotificationService notificationService;

    public NotificationController() {
    }

    /**
     * 获取通知列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 通知列表
     */
    @GetMapping("/list")
    @RequirePermission("notification:view")
    public Result<List<Notification>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        User currentUser = UserContext.get();
        log.info("查询通知列表: userId={}, page={}, size={}", currentUser.getId(), page, size);
        List<Notification> notifications = notificationService.getUserNotifications(currentUser.getId(), page, size);
        return Result.success(notifications);
    }

    /**
     * 获取未读通知数
     *
     * @return 未读通知数
     */
    @GetMapping("/unread")
    @RequirePermission("notification:view")
    public Result<Map<String, Object>> unreadCount() {
        User currentUser = UserContext.get();
        int count = notificationService.getUnreadCount(currentUser.getId());
        log.info("查询未读通知数: userId={}, count={}", currentUser.getId(), count);
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return Result.success(data);
    }

    /**
     * 将所有通知标记为已读
     *
     * @return 操作结果
     */
    @PostMapping("/read-all")
    @RequirePermission("notification:read")
    public Result<String> readAll() {
        User currentUser = UserContext.get();
        log.info("全部通知标记已读: userId={}", currentUser.getId());
        notificationService.markAllRead(currentUser.getId());
        return Result.success("已全部标记为已读");
    }

    /**
     * 将单条通知标记为已读
     *
     * @param notificationId 通知ID
     * @return 操作结果
     */
    @PostMapping("/read/{id}")
    @RequirePermission("notification:read")
    public Result<String> readOne(@PathVariable("id") Long notificationId) {
        log.info("单条通知标记已读: notificationId={}", notificationId);
        notificationService.markRead(notificationId);
        return Result.success("已标记为已读");
    }
}