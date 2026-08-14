package dev.xuya.token.spring.boot.starter.interceptor;

import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.spring.boot.starter.DataScopeContext;
import dev.xuya.token.spring.boot.starter.LoginContext;
import dev.xuya.token.spring.boot.starter.XuYaTokenProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拦截器:为每个非白名单请求解析 token 请求头,构建 {@code LoginContext}。
 *
 * @author 青衣
 */
public class XuYaTokenInterceptor implements HandlerInterceptor {

    /** 认证门面。 */
    private final Authenticator authenticator;

    /** 框架配置属性。 */
    private final XuYaTokenProperties properties;

    /**
     * 构造拦截器。
     *
     * @param authenticator 认证门面
     * @param properties    框架配置属性
     */
    public XuYaTokenInterceptor(Authenticator authenticator, XuYaTokenProperties properties) {
        this.authenticator = authenticator;
        this.properties = properties;
    }

    /**
     * 解析请求头中的 token 并绑定当前用户;未登录或会话过期时抛出 401。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(properties.getHeaderName());
        String token = extractToken(header);
        UserInfo user = token == null ? null : authenticator.getCurrentUser(token);
        if (user == null) {
            throw new UnauthorizedException("Not logged in or session expired");
        }
        LoginContext.set(user);
        request.setAttribute("xuya.token", token);
        return true;
    }

    /** 请求完成后清除 ThreadLocal,防止线程复用导致的数据串扰。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LoginContext.clear();
        // 兜底清理:正常由 @RequiresDataScope 的 @After 完成
        DataScopeContext.clear();
    }

    /**
     * 从请求头值中提取 token(去除配置的前缀)。
     *
     * @param header 请求头原始值
     * @return token;请求头为空返回 {@code null}
     */
    private String extractToken(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        String prefix = properties.getTokenPrefix();
        if (prefix != null && !prefix.isEmpty() && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }
}
