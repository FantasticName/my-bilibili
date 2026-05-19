package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 发布视频请求DTO
 *
 * @author FantasticName
 */
@Data
public class PublishVideoDTO {

    private String title;

    private String description;

    private String coverUrl;

    private String videoUrl;

    private String category;
}
