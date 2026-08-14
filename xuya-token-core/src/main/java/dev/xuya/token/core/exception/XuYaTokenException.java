package dev.xuya.token.core.exception;

/**
 * XuYa-Token 所有异常的基类。
 *
 * @author 青衣
 */
public class XuYaTokenException extends RuntimeException {

    public XuYaTokenException(String message) {
        super(message);
    }

    public XuYaTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
