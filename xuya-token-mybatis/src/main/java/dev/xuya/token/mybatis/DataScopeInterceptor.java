package dev.xuya.token.mybatis;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.sql.DataScopeSql;
import dev.xuya.token.spring.boot.starter.DataScopeContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis-Plus {@code InnerInterceptor}:对标注 {@link DataScopeFilter} 的查询,
 * 依据当前上下文的数据权限自动向 WHERE 追加可见范围条件,业务 SQL 零侵入。
 * 条件由 {@link DataScopeSql} 生成,参数经 BoundSql 附加参数以
 * {@code #{xuyaP_n}} 占位符绑定,保持类型安全。
 *
 * <p>注册方式:
 * <pre>{@code
 * MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
 * interceptor.addInnerInterceptor(new DataScopeInterceptor());
 * }</pre>
 *
 * @author 青衣
 */
public class DataScopeInterceptor implements com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(DataScopeInterceptor.class);

    /** Mapper 方法 → 注解解析缓存(含"无注解"的负缓存)。 */
    private final Map<String, Optional<DataScopeFilter>> filterCache = new ConcurrentHashMap<>();

    /**
     * 查询前改写:存在数据权限上下文且语句标注了 {@link DataScopeFilter} 时,
     * 向 WHERE 追加可见范围条件;无上下文或未标注则原样放行。
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        DataScope scope = DataScopeContext.get();
        if (scope == null) {
            return;
        }
        DataScopeFilter filter = resolveFilter(ms);
        if (filter == null) {
            return;
        }
        DataScopeSql.Condition condition = DataScopeSql.of(scope)
                .deptColumn(column(filter.alias(), filter.deptColumn()))
                .userColumn(column(filter.alias(), filter.userColumn()))
                .build();
        try {
            rewrite(boundSql, condition);
        } catch (Exception e) {
            // 解析/改写失败按无数据权限处理并放行?不 —— 安全侧:抛出异常阻断查询
            throw new SQLException("Failed to apply data scope filter to: " + ms.getId(), e);
        }
    }

    /**
     * 改写 SQL:恒真条件跳过;其余把 "?" 替换为 JSqlParser 安全的字符串
     * 字面量标记并绑定附加参数,序列化后再把标记替换为 {@code #{xuyaP_n}}
     * 占位符(避免解析器吞掉 MyBatis 占位语法,也不触碰原有 ? 占位)。
     */
    private void rewrite(BoundSql boundSql, DataScopeSql.Condition condition) throws Exception {
        String sql = condition.getSql();
        if (DataScopeSql.ALWAYS_TRUE.equals(sql)) {
            return;
        }
        StringBuilder expr = new StringBuilder();
        List<Object> params = condition.getParams();
        int index = 0;
        for (char ch : sql.toCharArray()) {
            if (ch == '?') {
                boundSql.setAdditionalParameter("xuyaP" + index, params.get(index));
                expr.append('\'').append(marker(index)).append('\'');
                index++;
            } else {
                expr.append(ch);
            }
        }
        Statement statement = CCJSqlParserUtil.parse(boundSql.getSql());
        if (!(statement instanceof Select)) {
            return;
        }
        PlainSelect plain = ((Select) statement).getPlainSelect();
        String combined = plain.getWhere() == null
                ? expr.toString()
                : "(" + plain.getWhere() + ") AND " + expr;
        plain.setWhere(CCJSqlParserUtil.parseCondExpression(combined));
        String finalSql = plain.toString();
        for (int i = 0; i < index; i++) {
            finalSql = finalSql.replace("'" + marker(i) + "'", "#{xuyaP" + i + "}");
        }
        // 通过 MetaObject 替换 BoundSql 的 sql 字段(与分页插件同款做法)
        Configuration configuration = new Configuration();
        MetaObject metaObject = configuration.newMetaObject(boundSql);
        metaObject.setValue("sql", finalSql);
        log.debug("data scope applied: {}", finalSql);
    }

    /** 第 n 个参数的解析期标记(字符串字面量,序列化后替换为 MyBatis 占位符)。 */
    private static String marker(int index) {
        return "__XUYA_P_" + index + "__";
    }

    /** 解析语句对应的注解(带缓存;按 Mapper 接口方法反射获取)。 */
    private DataScopeFilter resolveFilter(MappedStatement ms) {
        return filterCache.computeIfAbsent(ms.getId(), id -> Optional.ofNullable(lookup(id)))
                .orElse(null);
    }

    /** 反射查找 Mapper 方法上的注解。 */
    private DataScopeFilter lookup(String id) {
        try {
            int dot = id.lastIndexOf('.');
            Class<?> mapper = Class.forName(id.substring(0, dot));
            String method = id.substring(dot + 1);
            for (Method m : mapper.getMethods()) {
                if (m.getName().equals(method) && m.isAnnotationPresent(DataScopeFilter.class)) {
                    return m.getAnnotation(DataScopeFilter.class);
                }
            }
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** 组合别名前缀的列名。 */
    private static String column(String alias, String column) {
        return alias == null || alias.isEmpty() ? column : alias + "." + column;
    }

    /** 供测试与自定义扩展使用:清空注解解析缓存。 */
    void clearCache() {
        filterCache.clear();
    }

    /** 供测试断言:获取 BoundSql 附加参数的便捷入口。 */
    static Object additionalParameter(BoundSql boundSql, String name) {
        return boundSql.hasAdditionalParameter(name) ? boundSql.getAdditionalParameter(name) : null;
    }
}
