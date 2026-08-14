package dev.xuya.token.demo.config;

import dev.xuya.token.core.audit.AuthAuditListener;
import dev.xuya.token.core.crypto.BCryptPasswordEncoder;
import dev.xuya.token.core.crypto.PasswordEncoder;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.spi.DeptProvider;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import dev.xuya.token.core.spi.PermissionLoader;
import dev.xuya.token.core.spi.UserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示用的用户与 RBAC 数据,密码以 BCrypt 密文存储。
 * 生产环境请将这些 SPI 实现接入自己的数据库或身份服务。
 *
 * @author 青衣
 */
@Configuration
public class DemoSecurityConfig {

    /** 密码编码器,演示模块使用 BCrypt。 */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 注册密码编码器,业务代码也可注入使用。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    /** 权限数据来源:内置管理员/普通用户/访客三个角色,含数据权限级别;C 端注册体系专属 "member" 角色。 */
    @Bean
    public PermissionLoader permissionLoader() {
        return new InMemoryPermissionLoader()
                .addRole(Role.builder("admin").name("管理员").permission("*:*")
                        .dataScope(DataScopeType.ALL).build())
                .addRole(Role.builder("user").name("普通用户").permission("profile:read")
                        .dataScope(DataScopeType.DEPT).build())
                .addRole(Role.builder("guest").name("访客")
                        .dataScope(DataScopeType.SELF).build())
                // C 端专属角色:同名不影响 B 端,"member" 仅 C 体系可见
                .addRole(UserType.C, Role.builder("member").name("会员")
                        .permission("profile:read").dataScope(DataScopeType.SELF).build());
    }

    /** 部门层级:d2 下辖 d3,其余部门无子部门(DEPT_AND_CHILD 级别依赖)。 */
    @Bean
    public DeptProvider deptProvider() {
        Map<String, Set<String>> children = Map.of("d2", Set.of("d3"));
        return deptId -> children.getOrDefault(deptId, Set.of());
    }

    /** 审计监听:登录/注销事件输出日志,实际项目可落库或对接告警。 */
    @Bean
    public AuthAuditListener auditListener() {
        return new AuthAuditListener() {
            private final org.slf4j.Logger log =
                    org.slf4j.LoggerFactory.getLogger("xuya-audit");

            @Override
            public void onLoginSuccess(String userType, String username, String userId) {
                log.info("[审计] 登录成功 user={} username={} userId={}", userType, username, userId);
            }

            @Override
            public void onLoginFailure(String userType, String username) {
                log.warn("[审计] 登录失败 user={} username={}", userType, username);
            }

            @Override
            public void onLogout(String userType, String userId) {
                log.info("[审计] 注销 user={} userId={}", userType, userId);
            }
        };
    }

    /** 用户来源:内存用户表,密码在初始化时加密为 BCrypt 密文;B/C 端账号分表,按体系分别校验。 */
    @Bean
    public UserProvider userProvider() {
        Map<String, UserInfo> users = new ConcurrentHashMap<>(Map.of(
                "admin", new UserInfo("1", "admin", "d1", Set.of("admin"), Map.of(), UserType.B),
                "alice", new UserInfo("2", "alice", "d2", Set.of("user"), Map.of(), UserType.B),
                "carol", new UserInfo("3", "carol", "d3", Set.of("guest"), Map.of(), UserType.B),
                // C 端用户:手机号登录,轻量角色
                "13800000000", new UserInfo("c1", "bob", null, Set.of("member"), Map.of(), UserType.C)));

        // 演示用明文密码,启动时转为 BCrypt 密文;生产环境应直接从数据库读取密文
        Map<String, String> encodedPasswords = new ConcurrentHashMap<>(Map.of(
                "admin", passwordEncoder.encode("admin123"),
                "alice", passwordEncoder.encode("alice123"),
                "carol", passwordEncoder.encode("carol123"),
                // C 端短信验证码(演示用固定码)
                "13800000000", passwordEncoder.encode("1234")));

        return new UserProvider() {
            @Override
            public UserInfo authenticate(String username, String password) {
                UserInfo user = users.get(username);
                String encoded = encodedPasswords.get(username);
                return user != null && UserType.B.equals(user.getUserType()) && encoded != null
                        && passwordEncoder.matches(password, encoded) ? user : null;
            }

            @Override
            public UserInfo authenticate(String userType, String username, String password) {
                UserInfo user = users.get(username);
                String encoded = encodedPasswords.get(username);
                return user != null && user.getUserType().equalsIgnoreCase(userType) && encoded != null
                        && passwordEncoder.matches(password, encoded) ? user : null;
            }

            @Override
            public UserInfo findById(String userType, String userId) {
                return users.values().stream()
                        .filter(u -> u.getId().equals(userId)
                                && u.getUserType().equalsIgnoreCase(userType))
                        .findFirst().orElse(null);
            }
        };
    }
}
