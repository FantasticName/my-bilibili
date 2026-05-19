package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息VO
 *
 * <p>返回给前端的用户信息，不包含密码等敏感字段。</p>
 *
 * @author FantasticName
 */
@Data
public class UserVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 角色：0-普通用户，1-博主，2-管理员
     */
    private Integer role;

    /**
     * 注册时间
     */
    private LocalDateTime createdAt;
}
