package dev.xuya.token.core.explain;

import java.util.List;

/**
 * 一次鉴权判定的完整解释:结论、命中依据与角色继承轨迹。
 * 用于权限配置排错与审计,让"为什么放行 / 为什么拒绝"可被程序化读取。
 *
 * @author 青衣
 */
public class AuthDecision {

    /** 判定结论:true 放行,false 拒绝。 */
    private final boolean allowed;

    /** 被解释的表达式:含冒号按权限("resource:action")解释,否则按角色编码解释。 */
    private final String expression;

    /** 本次是否为角色检查(false 表示权限检查)。 */
    private final boolean roleCheck;

    /** 展开后的角色获得轨迹(含直接与继承,按用户体系加载)。 */
    private final List<RoleTrace> roles;

    /** 命中依据:角色检查为角色编码,权限检查为命中的已授予权限串;拒绝时为 null。 */
    private final String matchedBy;

    /** 人话结论,如 "允许:权限 profile:* 蕴含 profile:read"。 */
    private final String reason;

    /**
     * 构造判定解释(完整参数)。
     *
     * @param allowed    结论
     * @param expression 被解释的表达式
     * @param roleCheck  是否角色检查
     * @param roles      角色轨迹列表
     * @param matchedBy  命中依据,可为 null
     * @param reason     人话结论
     */
    public AuthDecision(boolean allowed, String expression, boolean roleCheck,
                        List<RoleTrace> roles, String matchedBy, String reason) {
        this.allowed = allowed;
        this.expression = expression;
        this.roleCheck = roleCheck;
        this.roles = List.copyOf(roles);
        this.matchedBy = matchedBy;
        this.reason = reason;
    }

    /** 未登录的拒绝解释。 */
    public static AuthDecision anonymous(String expression) {
        return new AuthDecision(false, expression, expression.indexOf(':') < 0,
                List.of(), null, "拒绝:未登录或会话无效");
    }

    /** 判定是否放行。 */
    public boolean isAllowed() {
        return allowed;
    }

    /** 获取被解释的表达式。 */
    public String getExpression() {
        return expression;
    }

    /** 是否为角色检查。 */
    public boolean isRoleCheck() {
        return roleCheck;
    }

    /** 获取角色获得轨迹(不可变)。 */
    public List<RoleTrace> getRoles() {
        return roles;
    }

    /** 获取命中依据,拒绝时为 null。 */
    public String getMatchedBy() {
        return matchedBy;
    }

    /** 获取人话结论。 */
    public String getReason() {
        return reason;
    }

    /** "允许/拒绝:表达式 —— 结论" 的可读描述。 */
    @Override
    public String toString() {
        return (allowed ? "允许" : "拒绝") + " " + expression + " —— " + reason;
    }
}
