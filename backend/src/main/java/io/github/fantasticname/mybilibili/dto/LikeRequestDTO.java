package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 点赞请求DTO
 *
 * @author FantasticName
 */
@Data
public class LikeRequestDTO {

    private Integer targetType;

    private Long targetId;
}
