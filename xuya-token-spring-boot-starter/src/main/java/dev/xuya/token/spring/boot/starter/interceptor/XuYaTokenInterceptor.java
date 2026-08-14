package dev.xuya.token.spring.boot.starter.interceptor;

import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.spring.boot.starter.DataScopeContext;
import dev.xuya.token.spring.boot.starter.LoginContext;
import dev.xuya.token.spring.boot.starter.XuYaTokenProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 拦截器:为每个非白名单请求解析 token 请求头,构建 {@code LoginContext},
 * 并按 {@code xuya.token.user-type-paths} 规则校验体系(端)归属 ——
 * 路径命中某体系的模式而 token 体系不符时返回 403。
 *
 * @author 青衣
 */
public class XuYaTokenInterceptor implements HandlerInterceptor {

    /** Ant 路径匹配器,用于体系路径规则。 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
     * 解析请求头中的 token 并绑定当前用户;未登录或会话过期时抛出 401,
     * 之后按体系路径规则校验 token 体系归属。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(properties.getHeaderName());
        String token = extractToken(header);
        UserInfo user = token == null ? null : authenticator.getCurrentUser(token);
        if (user == null) {
            throw new UnauthorizedException("Not logged in or session expired");
        }
        checkUserTypePath(request, user.getUserType());
        LoginContext.set(user);
        request.setAttribute("xuya.token", token);
        return true;
    }

    /**
     * 体系路径校验:请求 URI 命中任一体系的模式时,要求 token 体系与之相符
     * (大小写不敏感),否则抛出 403;未配置规则的路径不限制体系。
     */
    private void checkUserTypePath(HttpServletRequest request, String userType) {
        String uri = request.getRequestURI();
        for (Map.Entry<String, List<String>> entry : properties.getUserTypePaths().entrySet()) {
            for (String pattern : entry.getValue()) {
                if (PATH_MATCHER.match(pattern, uri)
                        && !entry.getKey().trim().equalsIgnoreCase(userType)) {
                    throw new ForbiddenException(
                            "Endpoint belongs to user type: " + entry.getKey().trim());
                }
            }
        }
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
