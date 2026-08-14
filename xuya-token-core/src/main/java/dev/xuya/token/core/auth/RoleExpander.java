package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.spi.PermissionLoader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 角色继承链展开工具:以用户直接角色为起点 BFS 遍历父角色,
 * 仅当角色在数据来源中真实存在时才计入结果(指向不存在的父角色被忽略);
 * 访问标记防止环形继承死循环,跳数上限防御超长链。
 * 权限校验与数据权限解析共用此逻辑;展开按体系进行(体系专属角色优先)。
 *
 * @author 青衣
 */
public final class RoleExpander {

    /** 角色继承展开的最大跳数,防御配置错误形成的环形/超长继承链。 */
    public static final int MAX_INHERIT_HOPS = 20;

    private RoleExpander() {
    }

    /**
     * 按默认体系展开角色编码集合。
     *
     * @param loader    权限数据来源
     * @param roleCodes 用户直接持有的角色编码
     * @return 展开后的角色编码集合
     */
    public static Set<String> expand(PermissionLoader loader, Set<String> roleCodes) {
        return expand(UserType.DEFAULT, loader, roleCodes);
    }

    /**
     * 按体系展开角色编码集合,得到含全部真实存在祖先角色的编码集合。
     *
     * @param userType  用户体系标识,如 "B"、"C"
     * @param loader    权限数据来源
     * @param roleCodes 用户直接持有的角色编码
     * @return 展开后的角色编码集合
     */
    public static Set<String> expand(String userType, PermissionLoader loader, Set<String> roleCodes) {
        return expand(userType, loader, roleCodes, null);
    }

    /**
     * 按体系展开角色编码集合,并可选地记录每个角色的获得路径
     * (角色编码 → 传入该角色的父角色编码;直接持有的角色不在此映射中)。
     * 供鉴权解释器还原继承链使用。
     *
     * @param userType         用户体系标识
     * @param loader           权限数据来源
     * @param roleCodes        用户直接持有的角色编码
     * @param inheritedFromOut 承接"角色 → 继承来源"的映射,可为 null(不记录)
     * @return 展开后的角色编码集合
     */
    public static Set<String> expand(String userType, PermissionLoader loader, Set<String> roleCodes,
                                     java.util.Map<String, String> inheritedFromOut) {
        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(roleCodes);
        int hops = 0;
        while (!pending.isEmpty() && hops++ < MAX_INHERIT_HOPS) {
            int levelSize = pending.size();
            for (int i = 0; i < levelSize; i++) {
                String code = pending.poll();
                if (!visited.add(code)) {
                    continue;
                }
                loader.loadRole(userType, code).ifPresent(role -> {
                    result.add(code);
                    for (String parent : role.getParentCodes()) {
                        if (inheritedFromOut != null) {
                            inheritedFromOut.putIfAbsent(parent, code);
                        }
                        pending.add(parent);
                    }
                });
            }
        }
        return result;
    }
}
