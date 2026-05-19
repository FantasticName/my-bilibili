package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 动态实体类，对应数据库中的 post 表
 *
 * @author FantasticName
 */
@Data
public class Post implements Serializable {

    private Long id;

    private String content;

    private String images;

    private Long userId;

    private Integer status;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
