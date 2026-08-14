package dev.xuya.token.redis;

import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.spring.boot.starter.XuYaTokenAutoConfiguration;
import dev.xuya.token.spring.boot.starter.XuYaTokenProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 会话自动装配:当 classpath 存在 {@code StringRedisTemplate} 且
 * {@code xuya.token.redis.enabled} 未显式关闭时,以 Redis 会话管理器
 * 替代默认的内存实现,支持多节点部署共享会话。
 *
 * @author 青衣
 */
@Configuration
@AutoConfigureBefore(XuYaTokenAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(name = "xuya.token.redis.enabled", havingValue = "true", matchIfMissing = true)
public class XuYaTokenRedisAutoConfiguration {

    /** 注册 Redis 会话管理器(含并发会话限制),优先于 starter 的内存实现;应用也可自定义覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(StringRedisTemplate redisTemplate,
                                         XuYaTokenProperties properties) {
        return new RedisSessionManager(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper(),
                "xuya:token:", properties.getTimeoutMillis(),
                properties.getMaxSessionsPerUser(), properties.isEvictOldestOnExceed());
    }
}
