package dev.xuya.token.mybatis;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.spring.boot.starter.DataScopeContext;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataScopeInterceptorTest {

    /** 演示 Mapper:带注解与不带注解的查询各一。 */
    interface OrderMapper {
        @DataScopeFilter(alias = "o", deptColumn = "org_id")
        @Select("SELECT * FROM orders o WHERE o.status = #{status}")
        Object selectByStatus(String status);

        @Select("SELECT * FROM orders")
        Object selectAll();
    }

    private static Configuration configuration;
    private final DataScopeInterceptor interceptor = new DataScopeInterceptor();

    @BeforeAll
    static void setUpMapper() {
        configuration = new Configuration();
        configuration.addMapper(OrderMapper.class);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private MappedStatement ms(String statement) {
        return configuration.getMappedStatement(OrderMapper.class.getName() + "." + statement);
    }

    @Test
    void appendsDeptConditionWithAliasAndParams() throws Exception {
        DataScopeContext.set(new DataScope(DataScopeType.DEPT, "u1", "d2", Set.of("d2")));
        BoundSql sql = ms("selectByStatus").getBoundSql("DONE");

        interceptor.beforeQuery(null, ms("selectByStatus"), "DONE", null, null, sql);

        assertTrue(sql.getSql().contains("o.org_id IN"), "actual: " + sql.getSql());
        assertTrue(sql.getSql().contains("#{xuyaP0}"), "actual: " + sql.getSql());
        assertEquals("d2", DataScopeInterceptor.additionalParameter(sql, "xuyaP0"));
        // 原有 WHERE 条件保留
        assertTrue(sql.getSql().toUpperCase().contains("STATUS"), "actual: " + sql.getSql());
    }

    @Test
    void deptAndChildBindsMultipleParams() throws Exception {
        DataScopeContext.set(new DataScope(DataScopeType.DEPT_AND_CHILD, "u1", "d2",
                Set.of("d2", "d3")));
        BoundSql sql = ms("selectByStatus").getBoundSql("DONE");
        interceptor.beforeQuery(null, ms("selectByStatus"), "DONE", null, null, sql);

        assertTrue(sql.getSql().contains("#{xuyaP0}") && sql.getSql().contains("#{xuyaP1}"),
                "actual: " + sql.getSql());
        // Set 无序,IN 语义与顺序无关
        assertEquals(Set.of("d2", "d3"), Set.of(
                DataScopeInterceptor.additionalParameter(sql, "xuyaP0"),
                DataScopeInterceptor.additionalParameter(sql, "xuyaP1")));
    }

    @Test
    void selfScopeFiltersByUserColumn() throws Exception {
        DataScopeContext.set(new DataScope(DataScopeType.SELF, "u1", null, Set.of()));
        BoundSql sql = ms("selectByStatus").getBoundSql("DONE");
        interceptor.beforeQuery(null, ms("selectByStatus"), "DONE", null, null, sql);

        assertTrue(sql.getSql().contains("o.create_by = #{xuyaP0}"), "actual: " + sql.getSql());
        assertEquals("u1", DataScopeInterceptor.additionalParameter(sql, "xuyaP0"));
    }

    @Test
    void emptyScopeFailsSafeWithAlwaysFalse() throws Exception {
        DataScopeContext.set(new DataScope(DataScopeType.DEPT, "u1", null, Set.of()));
        BoundSql sql = ms("selectByStatus").getBoundSql("DONE");
        interceptor.beforeQuery(null, ms("selectByStatus"), "DONE", null, null, sql);

        assertTrue(sql.getSql().replace(" ", "").contains("1=0")
                || sql.getSql().contains("1 = 0"), "actual: " + sql.getSql());
    }

    @Test
    void allScopeLeavesSqlUntouched() throws Exception {
        DataScopeContext.set(new DataScope(DataScopeType.ALL, "u1", "d1", Set.of()));
        BoundSql sql = ms("selectByStatus").getBoundSql("DONE");
        String before = sql.getSql();
        interceptor.beforeQuery(null, ms("selectByStatus"), "DONE", null, null, sql);
        assertEquals(before, sql.getSql());
    }

    @Test
    void noAnnotationIsNotRewritten() throws Exception {
        DataScopeContext.set(new DataScope(DataScopeType.DEPT, "u1", "d2", Set.of("d2")));
        BoundSql sql = ms("selectAll").getBoundSql(null);
        interceptor.beforeQuery(null, ms("selectAll"), null, null, null, sql);
        assertFalse(sql.getSql().contains("dept_id"));
    }

    @Test
    void noContextIsNotRewritten() throws Exception {
        BoundSql sql = ms("selectByStatus").getBoundSql("DONE");
        interceptor.beforeQuery(null, ms("selectByStatus"), "DONE", null, null, sql);
        assertFalse(sql.getSql().contains("xuyaP"));
    }
}
