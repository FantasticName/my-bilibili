package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 登录请求DTO
 *
 * <p>前端登录接口提交的数据结构。</p>
 *
 * @author FantasticName
 */
@Data
public class LoginDTO {

    /**
     * 手机号，1开头11位数字
     */
    private String phone;

    /**
     * 密码，6-12位
     */
    private String password;
}
