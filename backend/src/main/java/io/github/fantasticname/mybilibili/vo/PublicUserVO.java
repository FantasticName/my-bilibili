package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公开用户VO（外人看博主），不包含手机号等敏感信息
 *
 * @author FantasticName
 */
@Data
public class PublicUserVO {

    private Long id;

    private String nickname;

    private String avatar;

    private Integer role;

    private Integer followCount;

    private Integer fansCount;

    private Boolean isFollowed;

    private LocalDateTime createdAt;
}
