package dev.xuya.token.spring.boot.starter.aspect;

import dev.xuya.token.core.auth.DataScopeResolver;
import dev.xuya.token.core.auth.PermissionChecker;
import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.spring.boot.starter.DataScopeContext;
import dev.xuya.token.spring.boot.starter.LoginContext;
import dev.xuya.token.spring.boot.starter.annotation.RequiresDataScope;
import dev.xuya.token.spring.boot.starter.annotation.RequiresLogin;
import dev.xuya.token.spring.boot.starter.annotation.RequiresPermissions;
import dev.xuya.token.spring.boot.starter.annotation.RequiresRoles;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 切面:基于 {@code PermissionChecker} 执行 {@code @Requires*} 注解的鉴权,
 * 并执行 {@code @RequiresDataScope} 的数据权限校验与上下文绑定。
 * 方法级与类级注解分别使用独立 advice,保证各切点绑定各自的注解实例。
 *
 * @author 青衣
 */
@Aspect
@Component
public class AuthorizationAspect {

    /** 权限校验器。 */
    private final PermissionChecker permissionChecker;

    /** 数据权限解析器。 */
    private final DataScopeResolver dataScopeResolver;

    /**
     * 构造鉴权切面。
     *
     * @param permissionChecker 权限校验器
     * @param dataScopeResolver 数据权限解析器
     */
    public AuthorizationAspect(PermissionChecker permissionChecker, DataScopeResolver dataScopeResolver) {
        this.permissionChecker = permissionChecker;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 方法级 {@code @RequiresLogin} 校验:要求已登录。 */
    @Before("@annotation(requiresLogin)")
    public void checkLoginOnMethod(RequiresLogin requiresLogin) {
        requireUser();
    }

    /** 类级 {@code @RequiresLogin} 校验:要求已登录。 */
    @Before("@within(requiresLogin)")
    public void checkLoginOnType(RequiresLogin requiresLogin) {
        requireUser();
    }

    /** 方法级 {@code @RequiresRoles} 校验。 */
    @Before("@annotation(requiresRoles)")
    public void checkRolesOnMethod(RequiresRoles requiresRoles) {
        checkRoles(requiresRoles);
    }

    /** 类级 {@code @RequiresRoles} 校验。 */
    @Before("@within(requiresRoles)")
    public void checkRolesOnType(RequiresRoles requiresRoles) {
        checkRoles(requiresRoles);
    }

    /** 方法级 {@code @RequiresPermissions} 校验。 */
    @Before("@annotation(requiresPermissions)")
    public void checkPermissionsOnMethod(RequiresPermissions requiresPermissions) {
        checkPermissions(requiresPermissions);
    }

    /** 类级 {@code @RequiresPermissions} 校验。 */
    @Before("@within(requiresPermissions)")
    public void checkPermissionsOnType(RequiresPermissions requiresPermissions) {
        checkPermissions(requiresPermissions);
    }

    /**
     * 角色校验:按注解的 logical 模式校验,不满足抛出 403。
     *
     * @param requiresRoles 角色注解
     */
    private void checkRoles(RequiresRoles requiresRoles) {
        UserInfo user = requireUser();
        boolean ok = requiresRoles.logical() == RequiresRoles.Logical.ALL
                ? permissionChecker.hasAllRoles(user, requiresRoles.value())
                : permissionChecker.hasAnyRole(user, requiresRoles.value());
        if (!ok) {
            throw new ForbiddenException("Missing required role");
        }
    }

    /**
     * 权限校验:按注解的 logical 模式校验,不满足抛出 403。
     *
     * @param requiresPermissions 权限注解
     */
    private void checkPermissions(RequiresPermissions requiresPermissions) {
        UserInfo user = requireUser();
        boolean ok = requiresPermissions.logical() == RequiresRoles.Logical.ALL
                ? allPermissions(user, requiresPermissions.value())
                : permissionChecker.hasAnyPermission(user, requiresPermissions.value());
        if (!ok) {
            throw new ForbiddenException("Missing required permission");
        }
    }

    /** 方法级 {@code @RequiresDataScope} 校验:级别不足抛 403,通过后绑定上下文。 */
    @Before("@annotation(requiresDataScope)")
    public void checkDataScopeOnMethod(RequiresDataScope requiresDataScope) {
        checkDataScope(requiresDataScope);
    }

    /** 类级 {@code @RequiresDataScope} 校验。 */
    @Before("@within(requiresDataScope)")
    public void checkDataScopeOnType(RequiresDataScope requiresDataScope) {
        checkDataScope(requiresDataScope);
    }

    /** 方法级数据权限上下文清理。 */
    @After("@annotation(requiresDataScope)")
    public void clearDataScopeOnMethod(RequiresDataScope requiresDataScope) {
        DataScopeContext.clear();
    }

    /** 类级数据权限上下文清理。 */
    @After("@within(requiresDataScope)")
    public void clearDataScopeOnType(RequiresDataScope requiresDataScope) {
        DataScopeContext.clear();
    }

    /**
     * 数据权限校验:未登录抛 401,有效级别低于注解要求抛 403;
     * 通过后把解析结果绑定到 {@code DataScopeContext}。
     *
     * @param requiresDataScope 数据权限注解
     */
    private void checkDataScope(RequiresDataScope requiresDataScope) {
        UserInfo user = requireUser();
        DataScope scope = dataScopeResolver.resolve(user);
        if (scope == null || !scope.getType().covers(requiresDataScope.value())) {
            throw new ForbiddenException("Insufficient data scope");
        }
        DataScopeContext.set(scope);
    }

    /** 判断用户是否拥有全部给定权限。 */
    private boolean allPermissions(UserInfo user, String[] exprs) {
        for (String expr : exprs) {
            if (!permissionChecker.hasPermission(user, expr)) {
                return false;
            }
        }
        return true;
    }

    /** 获取当前登录用户;未登录抛出 401。 */
    private UserInfo requireUser() {
        UserInfo user = LoginContext.getUser();
        if (user == null) {
            throw new UnauthorizedException("Not logged in");
        }
        return user;
    }
}
