package dev.xuya.token.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Mapper 查询方法上,声明该查询接受数据权限过滤:
 * 执行时若上下文存在数据权限({@code @RequiresDataScope} 已绑定),
 * 拦截器会自动向 WHERE 追加可见范围条件;无数据权限上下文则不改动。
 * 未标注的查询不受影响。
 *
 * @author 青衣
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScopeFilter {

    /** 表别名,如 "o";无别名时留空(直接使用列名)。 */
    String alias() default "";

    /** 部门字段名,默认 dept_id。 */
    String deptColumn() default "dept_id";

    /** 用户字段名(SELF 级别按此过滤),默认 create_by。 */
    String userColumn() default "create_by";
}
