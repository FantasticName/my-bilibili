package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

/**
 * 搜索结果VO，统一封装视频/动态/用户三种类型的搜索结果
 *
 * @author FantasticName
 */
@Data
public class SearchResultVO {

    private String type;

    private Long id;

    private String title;

    private String cover;

    private String avatar;

    private String nickname;

    private Long userId;

    private String description;

    private Long viewCount;

    private Long likeCount;

    private String category;

    private Integer fansCount;

    private java.time.LocalDateTime createdAt;
}