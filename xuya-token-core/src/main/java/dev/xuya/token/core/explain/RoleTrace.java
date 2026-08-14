package dev.xuya.token.core.explain;

import java.util.Collections;
import java.util.Set;

/**
 * 角色获得轨迹:该角色从何处来 —— 直接持有,或经继承链从某父角色获得,
 * 以及其直接持有的权限集合。用于鉴权解释的可视化还原。
 *
 * @author 青衣
 */
public class RoleTrace {

    /** 角色编码。 */
    private final String code;

    /** 继承来源:传入此角色的父角色编码;null 表示用户直接持有。 */
    private final String inheritedFrom;

    /** 该角色的直接权限表达式集合("resource:action" 格式)。 */
    private final Set<String> permissions;

    /**
     * 构造角色轨迹。
     *
     * @param code          角色编码
     * @param inheritedFrom 继承来源角色编码,可为 null
     * @param permissions   直接权限集合,可为 null(视为空)
     */
    public RoleTrace(String code, String inheritedFrom, Set<String> permissions) {
        this.code = code;
        this.inheritedFrom = inheritedFrom;
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** 获取角色编码。 */
    public String getCode() {
        return code;
    }

    /** 获取继承来源角色编码,直接持有为 null。 */
    public String getInheritedFrom() {
        return inheritedFrom;
    }

    /** 该角色是否为用户直接持有(非继承)。 */
    public boolean isDirect() {
        return inheritedFrom == null;
    }

    /** 获取直接权限集合(不可变)。 */
    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    /** 形如 "manager(直接)" 或 "user(继承自 manager)" 的可读描述。 */
    @Override
    public String toString() {
        return inheritedFrom == null
                ? code + "(直接)"
                : code + "(继承自 " + inheritedFrom + ")";
    }
}
