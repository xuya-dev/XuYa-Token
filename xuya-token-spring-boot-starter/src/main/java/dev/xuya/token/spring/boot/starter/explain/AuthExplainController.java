package dev.xuya.token.spring.boot.starter.explain;

import dev.xuya.token.core.auth.DataScopeResolver;
import dev.xuya.token.core.explain.AuthDecision;
import dev.xuya.token.core.explain.PermissionExplainer;
import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.spring.boot.starter.LoginContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权解释调试端点:对任意权限表达式 / 角色编码输出当前用户的
 * 完整判定依据(继承轨迹、命中权限、数据权限范围),用于排查
 * "为什么他被拒绝了"类问题。<b>仅调试用</b>,通过
 * {@code xuya.token.explain-enabled=true} 显式开启,需要登录后访问。
 *
 * @author 青衣
 */
@RestController
@ConditionalOnProperty(name = "xuya.token.explain-enabled", havingValue = "true")
public class AuthExplainController {

    /** 鉴权解释器。 */
    private final PermissionExplainer explainer;

    /** 数据权限解析器。 */
    private final DataScopeResolver dataScopeResolver;

    /**
     * 构造解释端点。
     *
     * @param explainer          鉴权解释器
     * @param dataScopeResolver  数据权限解析器
     */
    public AuthExplainController(PermissionExplainer explainer, DataScopeResolver dataScopeResolver) {
        this.explainer = explainer;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 解释一次判定。
     *
     * @param expr 权限表达式(含冒号)或角色编码(不含冒号)
     * @return 判定结论、命中依据、角色轨迹与数据权限范围
     */
    @GetMapping("/xuya/auth/explain")
    public Map<String, Object> explain(@RequestParam String expr) {
        UserInfo user = LoginContext.getUser();
        AuthDecision decision = explainer.explain(user, expr);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowed", decision.isAllowed());
        result.put("expression", decision.getExpression());
        result.put("roleCheck", decision.isRoleCheck());
        result.put("matchedBy", decision.getMatchedBy());
        result.put("reason", decision.getReason());
        result.put("roles", decision.getRoles().stream().map(trace -> {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("code", trace.getCode());
            role.put("inheritedFrom", trace.getInheritedFrom());
            role.put("direct", trace.isDirect());
            role.put("permissions", trace.getPermissions());
            return role;
        }).toList());
        DataScope scope = dataScopeResolver.resolve(user);
        if (scope != null) {
            result.put("dataScope", Map.of(
                    "type", scope.getType(),
                    "visibleDeptIds", scope.getVisibleDeptIds()));
        }
        return result;
    }
}
