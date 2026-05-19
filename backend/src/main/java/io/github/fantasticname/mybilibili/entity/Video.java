package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视频实体类，对应数据库中的 video 表
 *
 * <p>视频是本平台的核心内容，由博主发布，普通用户可以观看、点赞、评论。</p>
 *
 * @author FantasticName
 */
@Data
public class Video implements Serializable {

    private Long id;

    private String title;

    private String description;

    private String coverUrl;

    private String videoUrl;

    private String category;

    private Long userId;

    private Integer status;

    private Long viewCount;

    private Long likeCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
