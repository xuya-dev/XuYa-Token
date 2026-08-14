package dev.xuya.token.spring.boot.starter;

import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.auth.DataScopeResolver;
import dev.xuya.token.core.auth.DefaultAuthenticator;
import dev.xuya.token.core.auth.DefaultDataScopeResolver;
import dev.xuya.token.core.auth.DefaultPermissionChecker;
import dev.xuya.token.core.auth.PermissionChecker;
import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.core.spi.CachedPermissionLoader;
import dev.xuya.token.core.spi.DeptProvider;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import dev.xuya.token.core.spi.PermissionLoader;
import dev.xuya.token.core.spi.UserProvider;
import dev.xuya.token.spring.boot.starter.aspect.AuthorizationAspect;
import dev.xuya.token.spring.boot.starter.interceptor.XuYaTokenInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XuYa-Token 自动装配。所有 Bean 均为 {@code @ConditionalOnMissingBean},
 * 应用可覆盖任意部分(如 JDBC 版 {@code PermissionLoader})。
 *
 * @author 青衣
 */
@Configuration
@EnableConfigurationProperties(XuYaTokenProperties.class)
public class XuYaTokenAutoConfiguration {

    /**
     * 兜底用户提供者,拒绝所有登录;应用应定义自己的 {@link UserProvider} Bean
     * 并接入真实存储。
     */
    /** 提供兜底的用户来源 Bean(拒绝所有登录),应用可自定义覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public UserProvider userProvider() {
        return (username, password) -> null;
    }

    /** 提供默认的权限数据来源(内存实现),应用可自定义覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public PermissionLoader permissionLoader() {
        return new InMemoryPermissionLoader();
    }

    /** 提供默认的会话管理器(内存实现,含并发会话限制与按体系策略覆盖),应用可自定义覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(XuYaTokenProperties properties) {
        Map<String, dev.xuya.token.core.session.UserTypeSettings> settings = new LinkedHashMap<>();
        properties.getUserTypes().forEach((type, props) -> settings.put(type,
                new dev.xuya.token.core.session.UserTypeSettings(
                        props.getTimeoutMillis(), props.getMaxSessionsPerUser())));
        return new InMemorySessionManager(properties.getTimeoutMillis(),
                properties.getMaxSessionsPerUser(), properties.isEvictOldestOnExceed(), settings);
    }

    /** 提供默认的认证门面,应用可自定义覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public Authenticator authenticator(UserProvider userProvider, SessionManager sessionManager) {
        return new DefaultAuthenticator(userProvider, sessionManager);
    }

    /** 提供默认的权限校验器;配置了缓存 TTL 时自动以 {@code CachedPermissionLoader} 包装数据来源。 */
    @Bean
    @ConditionalOnMissingBean
    public PermissionChecker permissionChecker(PermissionLoader permissionLoader,
                                               XuYaTokenProperties properties) {
        PermissionLoader effective = properties.getPermissionCacheTtlMillis() > 0
                ? new CachedPermissionLoader(permissionLoader, properties.getPermissionCacheTtlMillis())
                : permissionLoader;
        return new DefaultPermissionChecker(effective);
    }

    /** 提供默认的数据权限解析器;有 {@code DeptProvider} Bean 时自动接入部门层级。 */
    @Bean
    @ConditionalOnMissingBean
    public DataScopeResolver dataScopeResolver(PermissionLoader permissionLoader,
                                               ObjectProvider<DeptProvider> deptProvider) {
        return new DefaultDataScopeResolver(permissionLoader, deptProvider.getIfAvailable());
    }

    /** 注册 {@code @Requires*} 注解的鉴权切面。 */
    @Bean
    public AuthorizationAspect authorizationAspect(PermissionChecker permissionChecker,
                                                   DataScopeResolver dataScopeResolver) {
        return new AuthorizationAspect(permissionChecker, dataScopeResolver);
    }

    /** 注册统一异常处理(401/403/500 JSON 响应)。 */
    @Bean
    public XuYaTokenExceptionAdvice xuYaTokenExceptionAdvice() {
        return new XuYaTokenExceptionAdvice();
    }

    /** 注册 MVC 拦截器,拦截全部路径并排除配置的白名单。 */
    @Bean
    public WebMvcConfigurer xuYaTokenWebMvcConfigurer(Authenticator authenticator,
                                                      XuYaTokenProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new XuYaTokenInterceptor(authenticator, properties))
                        .addPathPatterns("/**")
                        .excludePathPatterns(properties.getExcludePaths());
            }
        };
    }
}
