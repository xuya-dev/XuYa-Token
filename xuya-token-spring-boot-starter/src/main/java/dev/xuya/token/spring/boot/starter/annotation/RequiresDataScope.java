package dev.xuya.token.spring.boot.starter.annotation;

import dev.xuya.token.core.model.DataScopeType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求调用方的数据权限达到给定级别:校验通过后将解析结果绑定到
 * {@code DataScopeContext},业务代码据此组装行级查询条件。
 * 默认 SELF 为下限,等价于仅绑定上下文不做拦截。
 *
 * @author 青衣
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresDataScope {

    /** 方法要求的最低数据权限级别。 */
    DataScopeType value() default DataScopeType.SELF;
}
