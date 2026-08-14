package dev.xuya.token.jwt;

import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.spring.boot.starter.XuYaTokenAutoConfiguration;
import dev.xuya.token.spring.boot.starter.XuYaTokenProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 无状态会话自动装配:当配置了 {@code xuya.token.jwt.secret}
 * 且 classpath 存在本模块时,以 JWT 会话管理器替代内存/Redis 实现。
 * <p>注意:JWT 模式不支持服务端注销、踢人与在线列表;
 * 同时引入 xuya-token-redis 时请只启用其中一种(或自定义 SessionManager Bean)。
 *
 * @author 青衣
 */
@Configuration
@AutoConfigureBefore(XuYaTokenAutoConfiguration.class)
@ConditionalOnProperty("xuya.token.jwt.secret")
public class XuYaTokenJwtAutoConfiguration {

    /** 注册 JWT 会话管理器;有效期复用 xuya.token.timeout-millis,应用也可自定义覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(XuYaTokenProperties properties) {
        return new JwtSessionManager(properties.getJwt().getSecret(),
                properties.getTimeoutMillis());
    }
}
