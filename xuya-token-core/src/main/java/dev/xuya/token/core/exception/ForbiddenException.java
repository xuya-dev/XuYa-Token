package dev.xuya.token.core.exception;

/**
 * 无权限异常,对应 HTTP 403(已登录但权限不足)。
 *
 * @author 青衣
 */
public class ForbiddenException extends XuYaTokenException {

    public ForbiddenException(String message) {
        super(message);
    }
}
