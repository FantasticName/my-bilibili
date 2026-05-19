package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人中心VO（自己看自己），包含手机号等敏感信息
 *
 * @author FantasticName
 */
@Data
public class ProfileVO {

    private Long id;

    private String phone;

    private String nickname;

    private String avatar;

    private Integer role;

    private Integer followCount;

    private Integer fansCount;

    private LocalDateTime createdAt;
}
