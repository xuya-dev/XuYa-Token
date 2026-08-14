package dev.xuya.token.core.model;

import java.util.Collections;
import java.util.Set;

/**
 * 解析后的数据权限:当前用户对行级数据的可见范围。
 * 业务代码按 {@code type} 与 {@code visibleDeptIds} 组装查询条件。
 *
 * @author 青衣
 */
public class DataScope {

    /** 有效数据权限级别(全部角色中最宽者)。 */
    private final DataScopeType type;

    /** 用户 ID(SELF 级别按此过滤)。 */
    private final String userId;

    /** 用户所属部门 ID,可能为 null。 */
    private final String deptId;

    /** 可见部门 ID 集合:DEPT 为本部门,DEPT_AND_CHILD 含全部子部门;ALL 与 SELF 为空集(由 type 表达语义)。 */
    private final Set<String> visibleDeptIds;

    /**
     * 构造数据权限。
     *
     * @param type           有效级别
     * @param userId         用户 ID
     * @param deptId         所属部门 ID,可为 null
     * @param visibleDeptIds 可见部门集合,可为 null(视为空)
     */
    public DataScope(DataScopeType type, String userId, String deptId, Set<String> visibleDeptIds) {
        this.type = type;
        this.userId = userId;
        this.deptId = deptId;
        this.visibleDeptIds = visibleDeptIds == null ? Set.of() : Set.copyOf(visibleDeptIds);
    }

    /** 获取有效数据权限级别。 */
    public DataScopeType getType() {
        return type;
    }

    /** 获取用户 ID。 */
    public String getUserId() {
        return userId;
    }

    /** 获取所属部门 ID,可能为 null。 */
    public String getDeptId() {
        return deptId;
    }

    /** 获取可见部门 ID 集合(不可变)。 */
    public Set<String> getVisibleDeptIds() {
        return Collections.unmodifiableSet(visibleDeptIds);
    }
}
