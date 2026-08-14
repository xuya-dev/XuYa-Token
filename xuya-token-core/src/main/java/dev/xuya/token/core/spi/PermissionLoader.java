package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;

import java.util.Optional;
import java.util.Set;

/**
 * SPI:角色与权限定义的数据来源。
 * 实现此接口可从数据库或缓存加载。
 *
 * @author 青衣
 */
public interface PermissionLoader {

    /** 按角色编码解析角色。 */
    Optional<Role> loadRole(String roleCode);

    /** 加载给定角色集合的全部权限(并集)。 */
    Set<Permission> loadPermissions(Set<String> roleCodes);
}
