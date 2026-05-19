package io.github.fantasticname.mybilibili.dto;

import lombok.Data;

/**
 * 修改个人信息请求DTO
 *
 * <p>前端修改个人信息接口提交的数据结构。
 * 所有字段均为可选，只传需要修改的字段。</p>
 *
 * @author FantasticName
 */
@Data
public class UpdateProfileDTO {

    /**
     * 新昵称，1-20位
     */
    private String nickname;

    /**
     * 新手机号，1开头11位数字（修改手机号时使用）
     */
    private String newPhone;

    /**
     * 旧密码（修改密码或修改手机号时需要验证）
     */
    private String oldPassword;

    /**
     * 新密码，6-12位（修改密码时使用）
     */
    private String newPassword;

    /**
     * 确认新密码，必须与newPassword一致
     */
    private String confirmNewPassword;

    /**
     * 新头像文件名（上传头像后返回的文件名）
     */
    private String newAvatar;
}
