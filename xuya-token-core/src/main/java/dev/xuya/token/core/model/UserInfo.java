package dev.xuya.token.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 用户身份与角色编码的不可变快照。
 * 权限通过 {@code PermissionLoader} 按角色解析。
 *
 * @author 青衣
 */
public class UserInfo {

    /** 用户唯一标识。 */
    private final String id;

    /** 用户名。 */
    private final String username;

    /** 所属部门 ID,可为 null(数据权限 DEPT/DEPT_AND_CHILD 依赖此字段)。 */
    private final String deptId;

    /** 用户拥有的角色编码集合。 */
    private final Set<String> roleCodes;

    /** 扩展属性(如邮箱、部门等)。 */
    private final Map<String, Object> attributes;

    /**
     * 构造无部门信息的用户。
     *
     * @param id         用户唯一标识
     * @param username   用户名
     * @param roleCodes  角色编码集合,可为 null(视为空)
     * @param attributes 扩展属性,可为 null(视为空)
     */
    public UserInfo(String id, String username, Set<String> roleCodes, Map<String, Object> attributes) {
        this(id, username, null, roleCodes, attributes);
    }

    /**
     * 构造用户信息(完整参数)。
     *
     * @param id         用户唯一标识
     * @param username   用户名
     * @param deptId     所属部门 ID,可为 null
     * @param roleCodes  角色编码集合,可为 null(视为空)
     * @param attributes 扩展属性,可为 null(视为空)
     */
    public UserInfo(String id, String username, String deptId,
                    Set<String> roleCodes, Map<String, Object> attributes) {
        this.id = id;
        this.username = username;
        this.deptId = deptId;
        this.roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** 获取用户唯一标识。 */
    public String getId() {
        return id;
    }

    /** 获取用户名。 */
    public String getUsername() {
        return username;
    }

    /** 获取所属部门 ID,可能为 null。 */
    public String getDeptId() {
        return deptId;
    }

    /** 获取角色编码集合(不可变)。 */
    public Set<String> getRoleCodes() {
        return Collections.unmodifiableSet(roleCodes);
    }

    /** 获取扩展属性(不可变)。 */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
