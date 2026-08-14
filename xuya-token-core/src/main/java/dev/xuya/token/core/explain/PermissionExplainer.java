package dev.xuya.token.core.explain;

import dev.xuya.token.core.model.UserInfo;

/**
 * SPI:鉴权解释 —— 输出一次权限/角色判定的完整依据,
 * 包括角色继承轨迹、命中权限与拒绝原因,用于配置排错与审计。
 *
 * @author 青衣
 */
public interface PermissionExplainer {

    /**
     * 解释一次判定:表达式含冒号按权限("resource:action")解释,
     * 否则按角色编码解释。
     *
     * @param user       当前用户,可为 null(输出未登录拒绝)
     * @param expression 权限表达式或角色编码
     * @return 完整判定解释
     */
    AuthDecision explain(UserInfo user, String expression);
}
