package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收藏记录实体类，对应数据库中的 favorite_record 表
 *
 * @author FantasticName
 */
@Data
public class FavoriteRecord implements Serializable {

    private Long id;

    private Long folderId;

    private Long userId;

    private Integer targetType;

    private Long targetId;

    private LocalDateTime createdAt;
}
