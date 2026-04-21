package io.github.fantasticname.mybilibili.common;

/**
 * 错误码枚举类
 *
 * <p>统一管理系统中所有的错误码，避免在代码中硬编码数字。
 * 每个错误码包含一个状态码(code)和对应的描述信息(message)。</p>
 *
 * <p>错误码编码规则：</p>
 * <ul>
 *   <li>0：成功</li>
 *   <li>40xxx：客户端错误（参数错误、未登录、无权限等）</li>
 *   <li>50xxx：服务端错误（系统异常、操作失败等）</li>
 * </ul>
 *
 * @author FantasticName
 */
public enum ErrorCode {

    /**
     * 成功
     */
    SUCCESS(0, "ok"),

    /**
     * 请求参数错误
     */
    PARAMS_ERROR(40000, "请求参数错误"),

    /**
     * 未登录
     */
    NOT_LOGIN_ERROR(40100, "未登录"),

    /**
     * 无权限
     */
    NO_AUTH_ERROR(40101, "无权限"),

    /**
     * 请求数据不存在
     */
    NOT_FOUND_ERROR(40400, "请求数据不存在"),

    /**
     * 禁止访问
     */
    FORBIDDEN_ERROR(40300, "禁止访问"),

    /**
     * 系统内部异常
     */
    SYSTEM_ERROR(50000, "系统内部异常"),

    /**
     * 操作失败
     */
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    /**
     * 构造方法
     *
     * @param code    状态码
     * @param message 描述信息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取描述信息
     *
     * @return 描述信息
     */
    public String getMessage() {
        return message;
    }
}
