package dev.xuya.token.spring.boot.starter;

import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.exception.XuYaTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 将框架异常统一映射为 HTTP 401 / 403 / 500 的 JSON 响应。
 *
 * @author 青衣
 */
@RestControllerAdvice
public class XuYaTokenExceptionAdvice {

    /** 未认证异常 → HTTP 401。 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** 无权限异常 → HTTP 403。 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** 其他框架异常 → HTTP 500。 */
    @ExceptionHandler(XuYaTokenException.class)
    public ResponseEntity<Map<String, Object>> internal(XuYaTokenException e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /**
     * 构造统一格式的 JSON 错误响应:{code, message}。
     *
     * @param status  HTTP 状态码
     * @param message 错误消息
     */
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("code", status.value(), "message", message == null ? "" : message));
    }
}
