package io.github.fantasticname.mybilibili.util;

import java.util.regex.Pattern;

/**
 * 正则校验工具类，提供常用的格式校验方法
 *
 * <p>所有用户输入在业务层都必须进行格式校验，不能只依赖数据库约束。
 * 前后端都要做校验，前端校验提升用户体验，后端校验保证数据安全。</p>
 *
 * <p>校验规则：</p>
 * <ul>
 *   <li>手机号：1开头，11位数字</li>
 *   <li>密码：6-12位，任意字符</li>
 *   <li>昵称：1-20位，支持中英文、数字、下划线</li>
 * </ul>
 *
 * @author FantasticName
 */
public final class RegexUtil {

    /**
     * 手机号正则：1开头，11位数字
     *
     * <p>例如：13812345678</p>
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    /**
     * 密码正则：6-12位任意字符
     *
     * <p>允许字母、数字、特殊字符，长度6-12</p>
     */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[\\s\\S]{6,12}$");

    /**
     * 昵称正则：1-20位，支持中英文、数字、下划线、中划线
     *
     * <p>允许中文、英文字母、数字、下划线、中划线、空格</p>
     */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9_\\- ]{1,20}$");

    /**
     * 邀请码正则：6位数字
     */
    private static final Pattern INVITE_CODE_PATTERN = Pattern.compile("^\\d{6}$");

    /**
     * 私有构造方法，防止实例化
     */
    private RegexUtil() {
    }

    /**
     * 校验手机号格式
     *
     * @param phone 手机号
     * @return true表示格式正确，false表示格式错误
     */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 校验密码格式
     *
     * @param password 密码
     * @return true表示格式正确，false表示格式错误
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 校验昵称格式
     *
     * @param nickname 昵称
     * @return true表示格式正确，false表示格式错误
     */
    public static boolean isValidNickname(String nickname) {
        return nickname != null && NICKNAME_PATTERN.matcher(nickname).matches();
    }

    /**
     * 校验邀请码格式
     *
     * @param inviteCode 邀请码
     * @return true表示格式正确，false表示格式错误
     */
    public static boolean isValidInviteCode(String inviteCode) {
        return inviteCode != null && INVITE_CODE_PATTERN.matcher(inviteCode).matches();
    }
}
