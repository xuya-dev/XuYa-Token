package dev.xuya.token.core.exception;

/**
 * 未认证异常,对应 HTTP 401(未登录或会话过期)。
 *
 * @author 青衣
 */
public class UnauthorizedException extends XuYaTokenException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
