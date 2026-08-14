package dev.xuya.token.core.model;

import java.util.Objects;

/**
 * 权限 = 资源 + 操作,如 "user" + "delete" 即 "user:delete"。
 * 通配符 {@code *} 可匹配任意资源或操作。
 *
 * @author 青衣
 */
public final class Permission {

    /** 通配符,匹配任意资源或操作。 */
    public static final String WILDCARD = "*";

    /** 资源标识,如 "user"、"profile"。 */
    private final String resource;

    /** 操作标识,如 "read"、"delete"。 */
    private final String action;

    /**
     * 构造权限。
     *
     * @param resource 资源标识,不可为 null
     * @param action   操作标识,不可为 null
     */
    public Permission(String resource, String action) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.action = Objects.requireNonNull(action, "action");
    }

    /**
     * 从 "resource:action" 格式解析;只有一段时操作视为 {@code *}。
     *
     * @param expr 权限表达式
     * @return 解析后的权限对象
     */
    public static Permission of(String expr) {
        int idx = expr.indexOf(':');
        if (idx < 0) {
            return new Permission(expr.trim(), WILDCARD);
        }
        return new Permission(expr.substring(0, idx).trim(), expr.substring(idx + 1).trim());
    }

    /** 获取资源标识。 */
    public String getResource() {
        return resource;
    }

    /** 获取操作标识。 */
    public String getAction() {
        return action;
    }

    /**
     * 判断当前权限是否涵盖给定权限(通配符匹配)。
     *
     * @param other 被比较的权限
     * @return 涵盖返回 true
     */
    public boolean implies(Permission other) {
        return (WILDCARD.equals(resource) || resource.equals(other.resource))
                && (WILDCARD.equals(action) || action.equals(other.action));
    }

    /** 判断与另一对象是否相等(资源与操作均相同)。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission)) {
            return false;
        }
        Permission that = (Permission) o;
        return resource.equals(that.resource) && action.equals(that.action);
    }

    /** 基于资源与操作计算哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(resource, action);
    }

    /** 返回 "resource:action" 格式的字符串。 */
    @Override
    public String toString() {
        return resource + ":" + action;
    }
}
