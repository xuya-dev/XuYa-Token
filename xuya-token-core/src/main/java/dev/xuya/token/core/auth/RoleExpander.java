package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.spi.PermissionLoader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 角色继承链展开工具:以用户直接角色为起点 BFS 遍历父角色,
 * 仅当角色在数据来源中真实存在时才计入结果(指向不存在的父角色被忽略);
 * 访问标记防止环形继承死循环,跳数上限防御超长链。
 * 权限校验与数据权限解析共用此逻辑。
 *
 * @author 青衣
 */
public final class RoleExpander {

    /** 角色继承展开的最大跳数,防御配置错误形成的环形/超长继承链。 */
    public static final int MAX_INHERIT_HOPS = 20;

    private RoleExpander() {
    }

    /**
     * 展开角色编码集合,得到含全部真实存在祖先角色的编码集合。
     *
     * @param loader    权限数据来源
     * @param roleCodes 用户直接持有的角色编码
     * @return 展开后的角色编码集合
     */
    public static Set<String> expand(PermissionLoader loader, Set<String> roleCodes) {
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
                loader.loadRole(code).ifPresent(role -> {
                    result.add(code);
                    role.getParentCodes().forEach(pending::add);
                });
            }
        }
        return result;
    }
}
