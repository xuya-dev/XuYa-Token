package dev.xuya.token.core.model;

/**
 * 数据权限级别(行级数据可见范围),按 rank 从窄到宽排列。
 * 角色可配置级别,用户的有效级别取其全部角色(含继承)中最宽的一个。
 *
 * @author 青衣
 */
public enum DataScopeType {

    /** 仅本人数据。 */
    SELF(0),

    /** 本部门数据。 */
    DEPT(1),

    /** 本部门及全部子部门数据。 */
    DEPT_AND_CHILD(2),

    /** 全部数据,不限制。 */
    ALL(3);

    /** 宽度排名,数值越大可见范围越宽。 */
    private final int rank;

    DataScopeType(int rank) {
        this.rank = rank;
    }

    /** 获取宽度排名。 */
    public int getRank() {
        return rank;
    }

    /**
     * 判断当前级别是否覆盖给定级别(宽度不低于对方)。
     *
     * @param other 被比较的级别
     * @return 覆盖返回 true
     */
    public boolean covers(DataScopeType other) {
        return this.rank >= other.rank;
    }
}
