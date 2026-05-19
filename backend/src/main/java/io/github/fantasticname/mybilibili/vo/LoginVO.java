package io.github.fantasticname.mybilibili.vo;

import lombok.Data;

/**
 * 登录响应VO
 *
 * <p>登录成功后返回给前端的数据。</p>
 *
 * @author FantasticName
 */
@Data
public class LoginVO {

    /**
     * JWT令牌
     */
    private String token;

    /**
     * Token过期时间（秒）
     */
    private Integer expiresIn;

    /**
     * 用户信息
     */
    private UserVO user;
}
