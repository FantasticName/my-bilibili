package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论实体类，对应数据库中的 comment 表
 *
 * <p>评论支持两种目标类型：视频(targetType=1)和动态(targetType=2)。</p>
 * <p>通过 parentId 支持无限层级嵌套回复。</p>
 *
 * @author FantasticName
 */
@Data
public class Comment implements Serializable {

    private Long id;

    private String content;

    private Long userId;

    private Integer targetType;

    private Long targetId;

    private Long parentId;

    private Integer likeCount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
