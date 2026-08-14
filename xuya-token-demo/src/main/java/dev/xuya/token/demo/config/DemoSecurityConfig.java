package dev.xuya.token.demo.config;

import dev.xuya.token.core.crypto.BCryptPasswordEncoder;
import dev.xuya.token.core.crypto.PasswordEncoder;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
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

    /** 权限数据来源:内置管理员/普通用户/访客三个角色,含数据权限级别。 */
    @Bean
    public PermissionLoader permissionLoader() {
        return new InMemoryPermissionLoader()
                .addRole(Role.builder("admin").name("管理员").permission("*:*")
                        .dataScope(DataScopeType.ALL).build())
                .addRole(Role.builder("user").name("普通用户").permission("profile:read")
                        .dataScope(DataScopeType.DEPT).build())
                .addRole(Role.builder("guest").name("访客")
                        .dataScope(DataScopeType.SELF).build());
    }

    /** 部门层级:d2 下辖 d3,其余部门无子部门(DEPT_AND_CHILD 级别依赖)。 */
    @Bean
    public DeptProvider deptProvider() {
        Map<String, Set<String>> children = Map.of("d2", Set.of("d3"));
        return deptId -> children.getOrDefault(deptId, Set.of());
    }

    /** 用户来源:内存用户表,密码在初始化时加密为 BCrypt 密文。 */
    @Bean
    public UserProvider userProvider() {
        Map<String, UserInfo> users = new ConcurrentHashMap<>(Map.of(
                "admin", new UserInfo("1", "admin", "d1", Set.of("admin"), Map.of()),
                "alice", new UserInfo("2", "alice", "d2", Set.of("user"), Map.of()),
                "carol", new UserInfo("3", "carol", "d3", Set.of("guest"), Map.of())));

        // 演示用明文密码,启动时转为 BCrypt 密文;生产环境应直接从数据库读取密文
        Map<String, String> encodedPasswords = new ConcurrentHashMap<>(Map.of(
                "admin", passwordEncoder.encode("admin123"),
                "alice", passwordEncoder.encode("alice123"),
                "carol", passwordEncoder.encode("carol123")));

        return new UserProvider() {
            @Override
            public UserInfo authenticate(String username, String password) {
                UserInfo user = users.get(username);
                String encoded = encodedPasswords.get(username);
                return user != null && encoded != null
                        && passwordEncoder.matches(password, encoded) ? user : null;
            }

            @Override
            public UserInfo findById(String userId) {
                return users.values().stream()
                        .filter(u -> u.getId().equals(userId))
                        .findFirst().orElse(null);
            }
        };
    }
}
