package dev.xuya.token.spring.boot.starter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求调用方拥有给定角色。
 *
 * @author 青衣
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRoles {

    /** 要求的角色编码列表。 */
    String[] value();

    /** ALL(默认,需全部满足)或 ANY(满足其一即可)。 */
    RequiresRoles.Logical logical() default RequiresRoles.Logical.ALL;

    /** 多角色之间的逻辑关系。 */
    enum Logical {
        /** 全部满足。 */
        ALL,

        /** 满足其一。 */
        ANY
    }
}
