package io.github.fantasticname.mybilibili.common;

/**
 * 自定义业务异常类
 *
 * <p>在业务逻辑中，当遇到不符合预期的业务情况时（比如用户名已存在、余额不足等），
 * 应该抛出此异常，而不是直接返回错误响应。</p>
 *
 * <p>全局异常处理器会捕获此异常，并自动将异常信息转换为统一的 Result 响应格式，
 * 返回给前端。这样后端代码就不需要在每个方法里手动构造错误响应了。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   if (userExists) {
 *       throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
 *   }
 * </pre>
 *
 * <p>继承 RuntimeException 而不是 Exception 的原因：</p>
 * <ul>
 *   <li>RuntimeException 是非受检异常，不需要在方法签名上声明 throws</li>
 *   <li>业务异常通常是不可恢复的，调用者不需要显式捕获</li>
 *   <li>全局异常处理器会统一处理，不需要在每个调用点try-catch</li>
 * </ul>
 *
 * @author FantasticName
 */
public class BusinessException extends RuntimeException {

    /**
     * 状态码
     *
     * <p>与 {@link ErrorCode} 中的code对应，标识具体的业务错误类型</p>
     */
    private final int code;

    /**
     * 通过状态码和错误信息构造业务异常
     *
     * @param code    状态码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通过错误码枚举构造业务异常
     *
     * <p>使用示例：</p>
     * <pre>
     *   throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
     * </pre>
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 通过错误码枚举和自定义错误信息构造业务异常
     *
     * <p>当错误码的默认信息不够具体时，可以传入自定义信息覆盖。
     * 比如错误码是 PARAMS_ERROR（"请求参数错误"），
     * 但你想告诉前端更具体的原因："用户名长度不能超过20"。</p>
     *
     * <p>使用示例：</p>
     * <pre>
     *   throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名长度不能超过20");
     * </pre>
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误信息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }
}
