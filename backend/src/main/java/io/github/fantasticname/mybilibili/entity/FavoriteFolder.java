package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收藏夹实体类，对应数据库中的 favorite_folder 表
 *
 * @author FantasticName
 */
@Data
public class FavoriteFolder implements Serializable {

    private Long id;

    private Long userId;

    private String name;

    private Integer isDefault;

    private LocalDateTime createdAt;
}
