package dev.xuya.token.core;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.core.sql.DataScopeSql;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataScopeSqlTest {

    @Test
    void allScopeIsAlwaysTrue() {
        DataScopeSql.Condition c = DataScopeSql.of(
                new DataScope(DataScopeType.ALL, "u1", "d1", Set.of())).build();
        assertEquals("1=1", c.getSql());
        assertEquals(List.of(), c.getParams());
    }

    @Test
    void selfScopeFiltersByUserColumn() {
        DataScopeSql.Condition c = DataScopeSql.of(
                new DataScope(DataScopeType.SELF, "u1", "d1", Set.of()))
                .userColumn("creator_id").build();
        assertEquals("creator_id = ?", c.getSql());
        assertEquals(List.of("u1"), c.getParams());
    }

    @Test
    void deptScopeFiltersByDeptIn() {
        DataScopeSql.Condition c = DataScopeSql.of(
                new DataScope(DataScopeType.DEPT, "u1", "d2", Set.of("d2"))).build();
        assertEquals("dept_id IN (?)", c.getSql());
        assertEquals(List.of("d2"), c.getParams());
    }

    @Test
    void deptAndChildScopeIncludesDescendants() {
        DataScopeSql.Condition c = DataScopeSql.of(
                new DataScope(DataScopeType.DEPT_AND_CHILD, "u1", "d2", Set.of("d2", "d3")))
                .deptColumn("org_code").build();
        assertEquals("org_code IN (?, ?)", c.getSql());
        assertEquals(2, c.getParams().size());
        assertTrue(c.getParams().contains("d2"));
        assertTrue(c.getParams().contains("d3"));
    }

    @Test
    void emptyVisibleDeptIdsFailsSafe() {
        // DEPT 级别但无部门信息 → 恒假,安全侧失败
        DataScopeSql.Condition c = DataScopeSql.of(
                new DataScope(DataScopeType.DEPT, "u1", null, Set.of())).build();
        assertEquals("1=0", c.getSql());
        assertEquals(List.of(), c.getParams());
    }

    @Test
    void selfScopeWithoutUserIdFailsSafe() {
        DataScopeSql.Condition c = DataScopeSql.of(
                new DataScope(DataScopeType.SELF, null, null, Set.of())).build();
        assertEquals("1=0", c.getSql());
    }

    @Test
    void nullScopeRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataScopeSql.of(null));
    }
}
