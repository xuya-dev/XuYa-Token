package dev.xuya.token.spring.boot.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置属性,前缀 {@code xuya.token}。
 *
 * @author 青衣
 */
@ConfigurationProperties(prefix = "xuya.token")
public class XuYaTokenProperties {

    /** 携带 token 的 HTTP 请求头名称。 */
    private String headerName = "Authorization";

    /** 需去除的 Bearer 前缀;留空表示无前缀。 */
    private String tokenPrefix = "Bearer ";

    /** 会话空闲超时时间,单位毫秒。 */
    private long timeoutMillis = 30 * 60 * 1000L;

    /** 免认证路径白名单。 */
    private List<String> excludePaths = new ArrayList<>(List.of("/login", "/error"));

    /** 单用户最大并发会话数,<=0 表示不限制。 */
    private int maxSessionsPerUser = 0;

    /** 超出并发上限时踢掉最旧会话(true)或拒绝新登录(false)。 */
    private boolean evictOldestOnExceed = true;

    /** 权限缓存 TTL(毫秒),<=0 表示不缓存;适用于数据库等加载较慢的 PermissionLoader。 */
    private long permissionCacheTtlMillis = 0;

    /** JWT 相关配置。 */
    private final Jwt jwt = new Jwt();

    /** 登录防爆破:触发锁定的连续失败次数,<=0 表示不启用。 */
    private int guardMaxFailures = 0;

    /** 登录防爆破:锁定时长(毫秒),默认 5 分钟。 */
    private long guardLockMillis = 300_000L;

    /** 用户缓存 TTL(毫秒),<=0 表示不缓存;缓存 findById 减轻每请求用户查询。 */
    private long userCacheTtlMillis = 0;

    /** 按体系的策略覆盖(键为体系标识,如 b / c / open),未覆盖的体系沿用全局配置。 */
    private final Map<String, UserTypeProperties> userTypes = new LinkedHashMap<>();

    /**
     * 按体系隔离的路径规则:键为体系标识,值为 Ant 通配模式列表;
     * 请求路径命中某体系模式而 token 体系不符时返回 403。
     */
    private Map<String, List<String>> userTypePaths = new LinkedHashMap<>();

    /** 获取携带 token 的 HTTP 请求头名称。 */
    public String getHeaderName() {
        return headerName;
    }

    /** 设置携带 token 的 HTTP 请求头名称。 */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /** 获取需去除的 token 前缀。 */
    public String getTokenPrefix() {
        return tokenPrefix;
    }

    /** 设置需去除的 token 前缀。 */
    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    /** 获取会话空闲超时时间(毫秒)。 */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /** 设置会话空闲超时时间(毫秒)。 */
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /** 获取免认证路径白名单。 */
    public List<String> getExcludePaths() {
        return excludePaths;
    }

    /** 设置免认证路径白名单。 */
    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    /** 获取单用户最大并发会话数。 */
    public int getMaxSessionsPerUser() {
        return maxSessionsPerUser;
    }

    /** 设置单用户最大并发会话数,<=0 表示不限制。 */
    public void setMaxSessionsPerUser(int maxSessionsPerUser) {
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    /** 超出并发上限时是否踢掉最旧会话。 */
    public boolean isEvictOldestOnExceed() {
        return evictOldestOnExceed;
    }

    /** 设置超限策略:true 踢掉最旧会话,false 拒绝新登录。 */
    public void setEvictOldestOnExceed(boolean evictOldestOnExceed) {
        this.evictOldestOnExceed = evictOldestOnExceed;
    }

    /** 获取权限缓存 TTL(毫秒)。 */
    public long getPermissionCacheTtlMillis() {
        return permissionCacheTtlMillis;
    }

    /** 设置权限缓存 TTL(毫秒),<=0 表示不缓存。 */
    public void setPermissionCacheTtlMillis(long permissionCacheTtlMillis) {
        this.permissionCacheTtlMillis = permissionCacheTtlMillis;
    }

    /** 获取 JWT 配置。 */
    public Jwt getJwt() {
        return jwt;
    }

    /** 获取按体系的策略覆盖(键为体系标识)。 */
    public Map<String, UserTypeProperties> getUserTypes() {
        return userTypes;
    }

    /** 获取按体系隔离的路径规则。 */
    public Map<String, List<String>> getUserTypePaths() {
        return userTypePaths;
    }

    /** 设置按体系隔离的路径规则(键为体系标识,值为 Ant 模式列表)。 */
    public void setUserTypePaths(Map<String, List<String>> userTypePaths) {
        this.userTypePaths = userTypePaths;
    }

    /** 获取防爆破失败阈值。 */
    public int getGuardMaxFailures() {
        return guardMaxFailures;
    }

    /** 设置防爆破失败阈值,<=0 不启用。 */
    public void setGuardMaxFailures(int guardMaxFailures) {
        this.guardMaxFailures = guardMaxFailures;
    }

    /** 获取防爆破锁定时长(毫秒)。 */
    public long getGuardLockMillis() {
        return guardLockMillis;
    }

    /** 设置防爆破锁定时长(毫秒)。 */
    public void setGuardLockMillis(long guardLockMillis) {
        this.guardLockMillis = guardLockMillis;
    }

    /** 获取用户缓存 TTL(毫秒)。 */
    public long getUserCacheTtlMillis() {
        return userCacheTtlMillis;
    }

    /** 设置用户缓存 TTL(毫秒),<=0 不缓存。 */
    public void setUserCacheTtlMillis(long userCacheTtlMillis) {
        this.userCacheTtlMillis = userCacheTtlMillis;
    }

    /** 单个体系的策略覆盖,未设置(null)的项继承全局配置。 */
    public static class UserTypeProperties {

        /** 该体系空闲超时时间(毫秒),null 继承全局 timeout-millis。 */
        private Long timeoutMillis;

        /** 该体系单用户最大并发会话数,null 继承全局 max-sessions-per-user。 */
        private Integer maxSessionsPerUser;

        /** 获取该体系空闲超时(毫秒),可能为 null。 */
        public Long getTimeoutMillis() {
            return timeoutMillis;
        }

        /** 设置该体系空闲超时(毫秒)。 */
        public void setTimeoutMillis(Long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        /** 获取该体系最大并发会话数,可能为 null。 */
        public Integer getMaxSessionsPerUser() {
            return maxSessionsPerUser;
        }

        /** 设置该体系最大并发会话数。 */
        public void setMaxSessionsPerUser(Integer maxSessionsPerUser) {
            this.maxSessionsPerUser = maxSessionsPerUser;
        }
    }

    /** JWT 无状态会话配置(需引入 xuya-token-jwt 模块)。 */
    public static class Jwt {

        /** HS256 签名密钥,至少 32 字节;配置后 JWT 会话模式生效。 */
        private String secret;

        /** 获取签名密钥。 */
        public String getSecret() {
            return secret;
        }

        /** 设置签名密钥,至少 32 字节。 */
        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
