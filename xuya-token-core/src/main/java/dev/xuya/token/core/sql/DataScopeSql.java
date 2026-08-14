package dev.xuya.token.core.sql;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.DataScopeType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限 → SQL 查询条件生成器:把 {@link DataScope} 的可见范围
 * 直接转成可拼接的 WHERE 片段与占位参数,补齐从"可见范围"到
 * "查询条件"的最后一公里。存储无关 —— 产出的片段可用于
 * MyBatis、JdbcTemplate 或任何 SQL 构建器。
 *
 * <pre>{@code
 * Condition c = DataScopeSql.of(DataScopeContext.get())
 *         .deptColumn("dept_id")
 *         .userColumn("create_by")
 *         .build();
 * // c.getSql()    -> "dept_id IN (?, ?)"
 * // c.getParams() -> ["d2", "d3"]
 * }</pre>
 *
 * @author 青衣
 */
public final class DataScopeSql {

    /** 恒真条件(ALL 级别,不限制数据)。 */
    public static final String ALWAYS_TRUE = "1=1";

    /** 恒假条件(可见范围为空的兜底,安全侧失败)。 */
    public static final String ALWAYS_FALSE = "1=0";

    /** 待转换的数据权限。 */
    private final DataScope scope;

    /** 部门字段名,默认 dept_id。 */
    private String deptColumn = "dept_id";

    /** 用户字段名(SELF 级别按创建人过滤),默认 create_by。 */
    private String userColumn = "create_by";

    private DataScopeSql(DataScope scope) {
        this.scope = scope;
    }

    /**
     * 以给定数据权限创建生成器。
     *
     * @param scope 数据权限,不可为 null
     */
    public static DataScopeSql of(DataScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        return new DataScopeSql(scope);
    }

    /** 设置部门字段名(默认 dept_id)。 */
    public DataScopeSql deptColumn(String deptColumn) {
        this.deptColumn = deptColumn;
        return this;
    }

    /** 设置用户字段名,SELF 级别按此过滤(默认 create_by)。 */
    public DataScopeSql userColumn(String userColumn) {
        this.userColumn = userColumn;
        return this;
    }

    /**
     * 生成查询条件:
     * <ul>
     *   <li>ALL → {@code 1=1}(不限制)</li>
     *   <li>SELF → {@code 用户字段 = ?}(用户 ID 缺失时 1=0)</li>
     *   <li>DEPT / DEPT_AND_CHILD → {@code 部门字段 IN (?, ...)}(可见部门为空时 1=0)</li>
     * </ul>
     */
    public Condition build() {
        DataScopeType type = scope.getType();
        if (type == DataScopeType.ALL) {
            return new Condition(ALWAYS_TRUE, List.of());
        }
        if (type == DataScopeType.SELF) {
            return scope.getUserId() == null
                    ? new Condition(ALWAYS_FALSE, List.of())
                    : new Condition(userColumn + " = ?", List.of(scope.getUserId()));
        }
        Set<String> deptIds = scope.getVisibleDeptIds();
        if (deptIds.isEmpty()) {
            return new Condition(ALWAYS_FALSE, List.of());
        }
        String placeholders = deptIds.stream().map(d -> "?").collect(Collectors.joining(", "));
        return new Condition(deptColumn + " IN (" + placeholders + ")", List.copyOf(deptIds));
    }

    /** 生成的查询条件:SQL 片段 + 顺序对应的占位参数。 */
    public static final class Condition {

        /** SQL 片段,如 "dept_id IN (?, ?)"。 */
        private final String sql;

        /** 与占位符顺序对应的参数列表。 */
        private final List<Object> params;

        Condition(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        /** 获取 SQL 片段。 */
        public String getSql() {
            return sql;
        }

        /** 获取占位参数(不可变)。 */
        public List<Object> getParams() {
            return params;
        }

        /** "dept_id IN (?, ?) | [d2, d3]" 的可读描述。 */
        @Override
        public String toString() {
            return sql + " | " + params;
        }
    }
}
