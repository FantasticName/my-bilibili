package io.github.fantasticname.mybilibili.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用返回结果类，实现序列化接口，意味着这个类的对象可以被转换成字节流（用于网络传输、缓存到Redis等）
 *
 * <p>统一响应格式的好处：</p>
 * <ul>
 *   <li>前端只需要按照一种格式解析响应，简化前端代码</li>
 *   <li>后端所有接口返回格式一致，便于统一处理</li>
 *   <li>包含code、data、message、traceId四个字段，信息完整</li>
 * </ul>
 *
 * <p>响应格式示例：</p>
 * <pre>
 *   {
 *     "code": 0,
 *     "data": { ... },
 *     "message": "ok",
 *     "traceId": "550e8400-e29b-41d4-a716-446655440000"
 *   }
 * </pre>
 *
 * @param <T> 数据类型
 * @author FantasticName
 */
@Data
public class Result<T> implements Serializable {

    /**
     * 状态码
     *
     * <p>0 表示成功，其他值表示各种错误，具体参考 {@link ErrorCode}</p>
     */
    private int code;

    /**
     * 返回的数据
     *
     * <p>成功时携带业务数据，失败时为null</p>
     */
    private T data;

    /**
     * 返回信息
     *
     * <p>成功时为"ok"，失败时为错误描述信息</p>
     */
    private String message;

    /**
     * 全链路追踪ID
     *
     * <p>每个请求都有唯一的traceId，用于在日志中追踪一个请求的完整调用链。
     * 当后端返回响应对象Result时，也会把traceId塞进去，
     * 前端可以把这个ID反馈给后端，方便排查问题。</p>
     */
    private String traceId;

    /**
     * 全参构造方法
     *
     * @param code    状态码
     * @param data    数据
     * @param message 信息
     */
    public Result(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /**
     * 不带信息的构造方法，message默认为空字符串
     *
     * @param code 状态码
     * @param data 数据
     */
    public Result(int code, T data) {
        this(code, data, "");
    }

    /**
     * 通过错误码构造结果对象
     *
     * @param errorCode 错误码枚举
     */
    public Result(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应
     *
     * <p>使用示例：</p>
     * <pre>
     *   return Result.success(user);
     * </pre>
     *
     * @param data 返回的数据
     * @param <T>  数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, data, "ok");
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 成功的Result对象，data为null
     */
    public static <T> Result<T> success() {
        return new Result<>(0, null, "ok");
    }

    /**
     * 失败响应（通过错误码）
     *
     * <p>使用示例：</p>
     * <pre>
     *   return Result.error(ErrorCode.NOT_LOGIN_ERROR);
     * </pre>
     *
     * @param errorCode 错误码枚举
     * @return 失败的Result对象
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode);
    }

    /**
     * 失败响应（自定义状态码和信息）
     *
     * <p>使用示例：</p>
     * <pre>
     *   return Result.error(40001, "用户名不能为空");
     * </pre>
     *
     * @param code    状态码
     * @param message 错误信息
     * @return 失败的Result对象
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, null, message);
    }

    /**
     * 失败响应（错误码 + 自定义信息）
     *
     * <p>使用示例：</p>
     * <pre>
     *   return Result.error(ErrorCode.PARAMS_ERROR, "用户名长度不能超过20");
     * </pre>
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误信息（覆盖错误码默认信息）
     * @return 失败的Result对象
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), null, message);
    }
}
