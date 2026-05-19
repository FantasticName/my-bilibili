package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知实体类，对应数据库中的 notification 表
 *
 * <p>通知系统是社交平台的重要功能——用户需要知道自己被关注、被评论、被点赞等信息。
 * 通知通过两种方式触达用户：</p>
 * <ul>
 *   <li><b>持久化存储</b>：存入MySQL notification表，用户打开"消息中心"时查询历史通知</li>
 *   <li><b>实时推送</b>：通过WebSocket实时推送到客户端，用户在线时即时收到提醒</li>
 * </ul>
 *
 * <p>通知类型（notifyType）：</p>
 * <ul>
 *   <li>FOLLOW（关注）：XXX关注了你</li>
 *   <li>COMMENT（评论）：XXX评论了你的动态/视频</li>
 *   <li>LIKE（点赞）：XXX赞了你的动态/视频</li>
 *   <li>SYSTEM（系统）：系统公告、优惠券提醒等</li>
 * </ul>
 *
 * <p>通知产生流程：
 * 用户操作 → Controller → Service → 发RocketMQ消息 → NotificationConsumer → 写DB → WebSocket推送</p>
 *
 * @author FantasticName
 */
@Data
public class Notification implements Serializable {

    /**
     * 通知ID（主键，自增）
     */
    private Long id;

    /**
     * 接收通知的用户ID（被通知的人）
     */
    private Long userId;

    /**
     * 触发通知的用户ID（执行操作的人，如点赞者；系统通知时为null）
     */
    private Long fromUserId;

    /**
     * 通知类型（FOLLOW/COMMENT/LIKE/SYSTEM）
     */
    private String notifyType;

    /**
     * 关联数据ID（如动态ID、视频ID、评论ID，前端点击跳转用）
     */
    private Long targetId;

    /**
     * 关联数据类型（0-动态，1-视频，2-评论）
     */
    private Integer targetType;

    /**
     * 通知内容（如"XXX关注了你"、"XXX评论了你的动态"）
     */
    private String content;

    /**
     * 是否已读（0-未读，1-已读）
     */
    private Integer isRead;

    /**
     * 状态（0-正常，1-已删除）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}