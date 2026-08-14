package dev.xuya.token.core.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色,聚合一组权限并配置数据权限级别,同时支持继承父角色
 * (子角色自动拥有父角色的全部角色身份、权限与数据权限)。
 *
 * @author 青衣
 */
public class Role {

    /** 角色编码,如 "admin"。 */
    private final String code;

    /** 角色显示名称,如 "管理员"。 */
    private final String name;

    /** 角色直接持有的权限集合(不含继承部分)。 */
    private final Set<Permission> permissions;

    /** 父角色编码集合,权限与角色身份沿继承链向下传递。 */
    private final Set<String> parentCodes;

    /** 数据权限级别,未配置时视为 {@link DataScopeType#SELF}(最窄)。 */
    private final DataScopeType dataScopeType;

    /**
     * 构造无父角色、数据权限为 SELF 的角色。
     *
     * @param code        角色编码
     * @param name        角色名称
     * @param permissions 权限集合,可为 null(视为空)
     */
    public Role(String code, String name, Set<Permission> permissions) {
        this(code, name, permissions, Set.of(), DataScopeType.SELF);
    }

    /**
     * 构造角色。
     *
     * @param code        角色编码
     * @param name        角色名称
     * @param permissions 直接权限集合,可为 null(视为空)
     * @param parentCodes 父角色编码集合,可为 null(视为空)
     */
    public Role(String code, String name, Set<Permission> permissions, Set<String> parentCodes) {
        this(code, name, permissions, parentCodes, DataScopeType.SELF);
    }

    /**
     * 构造角色(完整参数)。
     *
     * @param code          角色编码
     * @param name          角色名称
     * @param permissions   直接权限集合,可为 null(视为空)
     * @param parentCodes   父角色编码集合,可为 null(视为空)
     * @param dataScopeType 数据权限级别,可为 null(视为 SELF)
     */
    public Role(String code, String name, Set<Permission> permissions, Set<String> parentCodes,
                DataScopeType dataScopeType) {
        this.code = code;
        this.name = name;
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        this.parentCodes = parentCodes == null ? Set.of() : Set.copyOf(parentCodes);
        this.dataScopeType = dataScopeType == null ? DataScopeType.SELF : dataScopeType;
    }

    /** 获取角色编码。 */
    public String getCode() {
        return code;
    }

    /** 获取角色显示名称。 */
    public String getName() {
        return name;
    }

    /** 获取直接持有的权限集合(不含继承,不可变)。 */
    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    /** 获取父角色编码集合(不可变)。 */
    public Set<String> getParentCodes() {
        return Collections.unmodifiableSet(parentCodes);
    }

    /** 获取数据权限级别(未配置时为 SELF,永不为 null)。 */
    public DataScopeType getDataScopeType() {
        return dataScopeType;
    }

    /** 获取权限表达式字符串集合("resource:action" 格式,不含继承)。 */
    public Set<String> getPermissionStrings() {
        return permissions.stream().map(Permission::toString).collect(Collectors.toSet());
    }

    /**
     * 创建角色构建器。
     *
     * @param code 角色编码
     * @return 构建器实例
     */
    @SuppressWarnings("unused")
    public static Builder builder(String code) {
        return new Builder(code);
    }

    /** 便捷构建器。 */
    public static class Builder {

        private final String code;
        private String name;
        private final Set<Permission> permissions = new HashSet<>();
        private final Set<String> parents = new HashSet<>();
        private DataScopeType dataScopeType = DataScopeType.SELF;

        /**
         * 以角色编码创建构建器,名称默认与编码相同。
         *
         * @param code 角色编码
         */
        public Builder(String code) {
            this.code = code;
            this.name = code;
        }

        /** 设置角色显示名称。 */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** 以资源 + 操作形式添加权限。 */
        public Builder permission(String resource, String action) {
            permissions.add(new Permission(resource, action));
            return this;
        }

        /** 以 "resource:action" 表达式形式添加权限。 */
        public Builder permission(String expr) {
            permissions.add(Permission.of(expr));
            return this;
        }

        /** 添加单个父角色编码。 */
        public Builder parent(String roleCode) {
            parents.add(roleCode);
            return this;
        }

        /** 添加多个父角色编码。 */
        public Builder parents(String... roleCodes) {
            parents.addAll(java.util.Arrays.asList(roleCodes));
            return this;
        }

        /** 设置数据权限级别。 */
        public Builder dataScope(DataScopeType dataScopeType) {
            this.dataScopeType = dataScopeType;
            return this;
        }

        /** 构建不可变的角色对象。 */
        public Role build() {
            return new Role(code, name, permissions, parents, dataScopeType);
        }
    }
}
