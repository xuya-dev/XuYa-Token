package dev.xuya.token.demo.controller;

import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.spring.boot.starter.LoginContext;
import dev.xuya.token.spring.boot.starter.annotation.RequiresLogin;
import dev.xuya.token.spring.boot.starter.annotation.RequiresPermissions;
import dev.xuya.token.spring.boot.starter.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示控制器:登录 / 注销 / 注解鉴权接口。
 *
 * @author 青衣
 */
@RestController
@RequestMapping
public class AuthController {

    /** 认证门面,用于登录与注销。 */
    private final Authenticator authenticator;

    /** 会话管理器,用于在线列表与踢人下线。 */
    private final SessionManager sessionManager;

    /**
     * 构造控制器。
     *
     * @param authenticator  认证门面
     * @param sessionManager 会话管理器
     */
    public AuthController(Authenticator authenticator, SessionManager sessionManager) {
        this.authenticator = authenticator;
        this.sessionManager = sessionManager;
    }

    /**
     * 登录接口:校验用户名密码并签发 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return 含 token 的响应
     */
    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String username, @RequestParam String password) {
        return Map.of("token", authenticator.login(username, password).getToken());
    }

    /**
     * C 端登录接口:手机号 + 短信验证码,签发 C 体系 token(前缀 C-)。
     *
     * @param phone 手机号
     * @param code  短信验证码(演示固定 1234)
     * @return 含 token 的响应
     */
    @PostMapping("/c/login")
    public Map<String, String> cLogin(@RequestParam String phone, @RequestParam String code) {
        return Map.of("token", authenticator.login(UserType.C, phone, code).getToken());
    }

    /**
     * C 端当前用户接口:演示 C 体系 token 的鉴权与上下文。
     *
     * @return 用户 ID、用户名、角色列表、所属体系
     */
    @RequiresLogin
    @GetMapping("/c/me")
    public Map<String, Object> cMe() {
        return Map.of(
                "id", LoginContext.getUser().getId(),
                "username", LoginContext.getUser().getUsername(),
                "roles", LoginContext.getUser().getRoleCodes(),
                "userType", LoginContext.getUser().getUserType());
    }

    /**
     * 注销接口:销毁当前 token 对应的会话。
     *
     * @param header Authorization 请求头(格式:Bearer {token})
     * @return 注销成功消息
     */
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader("Authorization") String header) {
        authenticator.logout(header.replaceFirst("^Bearer\\s+", ""));
        return Map.of("message", "logged out");
    }

    /**
     * 当前用户信息接口,演示 {@code @RequiresLogin}。
     *
     * @return 用户 ID、用户名、角色列表
     */
    @RequiresLogin
    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of(
                "id", LoginContext.getUser().getId(),
                "username", LoginContext.getUser().getUsername(),
                "roles", LoginContext.getUser().getRoleCodes());
    }

    /**
     * 管理员仪表盘接口,演示 {@code @RequiresRoles}。
     *
     * @return 欢迎信息
     */
    @RequiresRoles("admin")
    @GetMapping("/admin/dashboard")
    public Map<String, String> dashboard() {
        return Map.of("message", "welcome, admin");
    }

    /**
     * 查看用户资料接口,演示 {@code @RequiresPermissions}。
     *
     * @param id 资料 ID
     * @return 资料 ID
     */
    @RequiresPermissions("profile:read")
    @GetMapping("/profiles/{id}")
    public Map<String, String> getProfile(@PathVariable String id) {
        return Map.of("profileId", id);
    }

    /**
     * 删除用户资料接口,演示 {@code @RequiresPermissions}(仅 admin 角色拥有该权限)。
     *
     * @param id 资料 ID
     * @return 被删除的资料 ID
     */
    @RequiresPermissions("profile:delete")
    @DeleteMapping("/profiles/{id}")
    public Map<String, String> deleteProfile(@PathVariable String id) {
        return Map.of("deleted", id);
    }

    /**
     * 在线会话列表接口,演示 {@code SessionManager#listActiveTokens} 的在线用户查询能力。
     *
     * @param userId 用户 ID
     * @return 该用户当前有效的 token 集合
     */
    @RequiresRoles("admin")
    @GetMapping("/admin/sessions/{userId}")
    public Map<String, Object> listSessions(@PathVariable String userId) {
        return Map.of("userId", userId, "activeTokens", sessionManager.listActiveTokens(userId));
    }

    /**
     * 踢人下线接口,演示 {@code SessionManager#invalidateByUserId}。
     *
     * @param userId 用户 ID
     * @return 踢出结果消息
     */
    @RequiresRoles("admin")
    @PostMapping("/admin/kick/{userId}")
    public Map<String, String> kick(@PathVariable String userId) {
        sessionManager.invalidateByUserId(userId);
        return Map.of("message", "user " + userId + " kicked out");
    }
}
