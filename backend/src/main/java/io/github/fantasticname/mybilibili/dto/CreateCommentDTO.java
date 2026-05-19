package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 创建评论请求DTO
 *
 * @author FantasticName
 */
@Data
public class CreateCommentDTO {

    private String content;

    private Integer targetType;

    private Long targetId;

    private Long parentId;
}
