package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 收藏请求DTO
 *
 * @author FantasticName
 */
@Data
public class FavoriteRequestDTO {

    private Long folderId;

    private Integer targetType;

    private Long targetId;
}
