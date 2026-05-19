package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点赞记录实体类，对应数据库中的 like_record 表
 *
 * <p>使用统一表存储视频点赞、评论点赞和动态点赞，通过 targetType 区分：</p>
 * <ul>
 *   <li>targetType = 1：视频点赞，targetId 为视频ID</li>
 *   <li>targetType = 2：评论点赞，targetId 为评论ID</li>
 *   <li>targetType = 3：动态点赞，targetId 为动态ID</li>
 * </ul>
 *
 * <p>联合唯一键 (user_id, target_type, target_id) 保证同一用户对同一目标只能点赞一次。</p>
 *
 * @author FantasticName
 */
@Data
public class LikeRecord implements Serializable {

    /**
     * 点赞记录ID，主键
     */
    private Long id;

    /**
     * 点赞用户ID
     */
    private Long userId;

    /**
     * 目标类型：1-视频，2-评论
     */
    private Integer targetType;

    /**
     * 目标ID（视频ID或评论ID）
     */
    private Long targetId;

    /**
     * 点赞时间
     */
    private LocalDateTime createdAt;
}
