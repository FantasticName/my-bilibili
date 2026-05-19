package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 注册请求DTO
 *
 * <p>前端注册接口提交的数据结构。</p>
 * <p>字段长度限制与数据库一致：</p>
 * <ul>
 *   <li>phone：11位（手机号）</li>
 *   <li>password：6-12位</li>
 *   <li>nickname：1-20位</li>
 * </ul>
 *
 * @author FantasticName
 */
@Data
public class RegisterDTO {

    /**
     * 手机号，1开头11位数字
     */
    private String phone;

    /**
     * 密码，6-12位
     */
    private String password;

    /**
     * 确认密码，必须与password一致
     */
    private String confirmPassword;

    /**
     * 昵称，1-20位
     */
    private String nickname;

    /**
     * 邀请码（仅管理员注册时需要，6位数字）
     */
    private String inviteCode;

    /**
     * 注册角色：0-普通用户，2-管理员
     */
    private Integer role;
}
