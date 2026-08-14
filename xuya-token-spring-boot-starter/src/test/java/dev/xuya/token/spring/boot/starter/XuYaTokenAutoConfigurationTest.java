package dev.xuya.token.spring.boot.starter;

import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import dev.xuya.token.core.spi.PermissionLoader;
import dev.xuya.token.core.spi.UserProvider;
import dev.xuya.token.spring.boot.starter.annotation.RequiresLogin;
import dev.xuya.token.spring.boot.starter.annotation.RequiresPermissions;
import dev.xuya.token.spring.boot.starter.annotation.RequiresRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = XuYaTokenAutoConfigurationTest.App.class,
        properties = {
                "spring.main.banner-mode=off",
                "xuya.token.exclude-paths=/login,/error,/c/login",
                "xuya.token.user-type-paths.b=/admin/**",
                "xuya.token.user-type-paths.c=/c/**",
                "xuya.token.user-types.c.timeout-millis=2592000000"
        })
@AutoConfigureMockMvc
class XuYaTokenAutoConfigurationTest {

    @SpringBootApplication
    static class App {

        @Bean
        public PermissionLoader permissionLoader() {
            return new InMemoryPermissionLoader()
                    .addRole(Role.builder("admin").permission("*:*")
                            .dataScope(dev.xuya.token.core.model.DataScopeType.ALL).build())
                    .addRole(Role.builder("user").permission("profile:read")
                            .dataScope(dev.xuya.token.core.model.DataScopeType.DEPT).build())
                    .addRole(Role.builder("guest")
                            .dataScope(dev.xuya.token.core.model.DataScopeType.SELF).build());
        }

        @Bean
        public dev.xuya.token.core.spi.DeptProvider deptProvider() {
            // 部门层级:d2 → {d3}
            return deptId -> "d2".equals(deptId) ? java.util.Set.of("d3") : java.util.Set.of();
        }

        @Bean
        public UserProvider userProvider() {
            return new UserProvider() {
                @Override
                public UserInfo authenticate(String username, String password) {
                    if ("admin".equals(username) && "pw".equals(password)) {
                        return new UserInfo("1", "admin", "d1", Set.of("admin"), Map.of());
                    }
                    if ("alice".equals(username) && "pw".equals(password)) {
                        return new UserInfo("2", "alice", "d2", Set.of("user"), Map.of());
                    }
                    if ("carol".equals(username) && "pw".equals(password)) {
                        return new UserInfo("3", "carol", "d3", Set.of("guest"), Map.of());
                    }
                    return null;
                }

                @Override
                public UserInfo authenticate(String userType, String username, String password) {
                    // C 端:手机号登录,角色复用 admin(用于验证体系路径隔离独立于角色)
                    if ("C".equalsIgnoreCase(userType) && "138".equals(username) && "1234".equals(password)) {
                        return new UserInfo("c1", "bob", null, Set.of("admin"), Map.of(), "C");
                    }
                    return authenticate(username, password);
                }

                @Override
                public UserInfo findById(String userId) {
                    return "1".equals(userId)
                            ? new UserInfo("1", "admin", "d1", Set.of("admin"), Map.of())
                            : "2".equals(userId)
                                    ? new UserInfo("2", "alice", "d2", Set.of("user"), Map.of())
                                    : "3".equals(userId)
                                            ? new UserInfo("3", "carol", "d3", Set.of("guest"), Map.of())
                                            : null;
                }

                @Override
                public UserInfo findById(String userType, String userId) {
                    if ("C".equalsIgnoreCase(userType)) {
                        return "c1".equals(userId)
                                ? new UserInfo("c1", "bob", null, Set.of("admin"), Map.of(), "C")
                                : null;
                    }
                    return findById(userId);
                }
            };
        }

        @RestController
        static class TestController {

            private final dev.xuya.token.core.auth.Authenticator authenticator;

            TestController(dev.xuya.token.core.auth.Authenticator authenticator) {
                this.authenticator = authenticator;
            }

            @PostMapping("/login")
            public Map<String, String> login(@RequestParam String username, @RequestParam String password) {
                return Map.of("token", authenticator.login(username, password).getToken());
            }

            @PostMapping("/c/login")
            public Map<String, String> cLogin(@RequestParam String phone, @RequestParam String code) {
                return Map.of("token", authenticator.login("C", phone, code).getToken());
            }

            @RequiresLogin
            @GetMapping("/c/me")
            public Map<String, String> cMe() {
                return Map.of("userType", LoginContext.getUser().getUserType());
            }

            @GetMapping("/me")
            @RequiresLogin
            public Map<String, String> me() {
                return Map.of("username", LoginContext.getUser().getUsername());
            }

            @GetMapping("/admin")
            @RequiresRoles("admin")
            public Map<String, String> admin() {
                return Map.of("ok", "admin");
            }

            @GetMapping("/profiles/delete")
            @RequiresPermissions("profile:delete")
            public Map<String, String> deleteProfile() {
                return Map.of("ok", "deleted");
            }

            @dev.xuya.token.spring.boot.starter.annotation.RequiresDataScope(
                    dev.xuya.token.core.model.DataScopeType.DEPT)
            @GetMapping("/scoped-data")
            public Map<String, Object> scopedData() {
                dev.xuya.token.core.model.DataScope scope = DataScopeContext.get();
                return Map.of(
                        "type", scope.getType().name(),
                        "visibleDeptIds", scope.getVisibleDeptIds());
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private String login(String username) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/login")
                        .param("username", username)
                        .param("password", "pw")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("token").asText();
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenAccessProtected() throws Exception {
        String token = login("alice");
        mockMvc.perform(MockMvcRequestBuilders.get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void roleCheck() throws Exception {
        String admin = login("admin");
        mockMvc.perform(MockMvcRequestBuilders.get("/admin").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        String alice = login("alice");
        mockMvc.perform(MockMvcRequestBuilders.get("/admin").header("Authorization", "Bearer " + alice))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionCheck() throws Exception {
        String admin = login("admin");
        mockMvc.perform(MockMvcRequestBuilders.get("/profiles/delete").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        String alice = login("alice");
        mockMvc.perform(MockMvcRequestBuilders.get("/profiles/delete").header("Authorization", "Bearer " + alice))
                .andExpect(status().isForbidden());
    }

    @Test
    void badLoginGets401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/login")
                        .param("username", "alice").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dataScopeBindsDeptContext() throws Exception {
        // alice:DEPT 级别,部门 d2
        String alice = login("alice");
        mockMvc.perform(MockMvcRequestBuilders.get("/scoped-data").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPT"))
                .andExpect(jsonPath("$.visibleDeptIds[0]").value("d2"));

        // admin:ALL 级别覆盖 DEPT 要求,不限制部门
        String admin = login("admin");
        mockMvc.perform(MockMvcRequestBuilders.get("/scoped-data").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ALL"));
    }

    @Test
    void dataScopeBelowRequirementGets403() throws Exception {
        // carol:SELF 级别,低于接口要求的 DEPT
        String carol = login("carol");
        mockMvc.perform(MockMvcRequestBuilders.get("/scoped-data").header("Authorization", "Bearer " + carol))
                .andExpect(status().isForbidden());
    }

    private String cLogin() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/c/login")
                        .param("phone", "138")
                        .param("code", "1234")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("token").asText();
    }

    @Test
    void cLoginIssuesTypedTokenAndPassesTypeCheck() throws Exception {
        String token = cLogin();
        org.junit.jupiter.api.Assertions.assertTrue(token.startsWith("C-"));
        mockMvc.perform(MockMvcRequestBuilders.get("/c/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("C"));
    }

    @Test
    void userTypePathIsolationBlocksCrossTypeAccess() throws Exception {
        // C 端 token 访问 B 端管理接口:即使拥有 admin 角色也 403(体系路径隔离)
        String cToken = cLogin();
        mockMvc.perform(MockMvcRequestBuilders.get("/admin").header("Authorization", "Bearer " + cToken))
                .andExpect(status().isForbidden());

        // B 端 token 访问 C 端接口同样 403
        String bToken = login("admin");
        mockMvc.perform(MockMvcRequestBuilders.get("/c/me").header("Authorization", "Bearer " + bToken))
                .andExpect(status().isForbidden());
    }
}
