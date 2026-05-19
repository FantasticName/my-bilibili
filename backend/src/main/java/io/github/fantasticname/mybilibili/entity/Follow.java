package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关注实体类，对应数据库中的 follow 表
 *
 * <p>记录用户之间的关注关系：</p>
 * <ul>
 *   <li>followerId：关注者（主动关注别人的用户）</li>
 *   <li>followeeId：被关注者（被别人关注的用户）</li>
 * </ul>
 *
 * <p>联合唯一键 (follower_id, followee_id) 保证同一用户不能重复关注同一个人。</p>
 *
 * @author FantasticName
 */
@Data
public class Follow implements Serializable {

    /**
     * 关注关系ID，主键
     */
    private Long id;

    /**
     * 关注者用户ID（主动关注的人）
     */
    private Long followerId;

    /**
     * 被关注者用户ID（被关注的人，即博主）
     */
    private Long followeeId;

    /**
     * 关注时间
     */
    private LocalDateTime createdAt;
}
