package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态VO，返回给前端的动态信息
 *
 * <p>images字段为图片URL列表，由数据库中的逗号分隔字符串转换而来。
 * 前端可直接遍历该列表展示多张图片。</p>
 *
 * @author FantasticName
 */
@Data
public class PostVO {

    /**
     * 动态ID
     */
    private Long id;

    /**
     * 动态文字内容
     */
    private String content;

    /**
     * 图片URL列表（多图）
     */
    private List<String> images;

    /**
     * 发布者用户ID
     */
    private Long userId;

    /**
     * 发布者昵称
     */
    private String nickname;

    /**
     * 发布者头像URL
     */
    private String avatar;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 当前用户是否已点赞
     */
    private Boolean isLiked;

    /**
     * 发布时间
     */
    private LocalDateTime createdAt;
}
