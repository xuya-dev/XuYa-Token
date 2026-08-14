package dev.xuya.token.spring.boot.starter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求调用方拥有给定权限("resource:action" 格式,支持通配符)。
 *
 * @author 青衣
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermissions {

    /** 要求的权限表达式列表("resource:action" 格式)。 */
    String[] value();

    /** ALL 或 ANY(默认,满足其一即可)。 */
    RequiresRoles.Logical logical() default RequiresRoles.Logical.ANY;
}
