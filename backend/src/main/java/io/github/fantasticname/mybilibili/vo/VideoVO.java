package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频VO，返回给前端的视频信息
 *
 * @author FantasticName
 */
@Data
public class VideoVO {

    private Long id;

    private String title;

    private String description;

    private String coverUrl;

    private String videoUrl;

    private String category;

    private Long userId;

    private String nickname;

    private String avatar;

    private Long viewCount;

    private Long likeCount;

    private Boolean isLiked;

    private Boolean isFavorited;

    private LocalDateTime createdAt;
}
