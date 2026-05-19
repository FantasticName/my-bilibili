package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论VO，返回给前端的评论信息，支持树形嵌套
 *
 * @author FantasticName
 */
@Data
public class CommentVO {

    private Long id;

    private String content;

    private Long userId;

    private String nickname;

    private String avatar;

    private Integer targetType;

    private Long targetId;

    private Long parentId;

    private Integer likeCount;

    private Boolean isLiked;

    private Boolean hasMoreReplies;

    private List<CommentVO> replies;

    private LocalDateTime createdAt;
}
