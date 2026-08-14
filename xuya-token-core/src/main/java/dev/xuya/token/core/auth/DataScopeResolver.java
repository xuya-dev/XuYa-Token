package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.UserInfo;

/**
 * SPI:数据权限解析,计算用户对行级数据的可见范围。
 *
 * @author 青衣
 */
public interface DataScopeResolver {

    /**
     * 解析用户的有效数据权限。
     *
     * @param user 当前用户
     * @return 数据权限;user 为 null 时返回 null
     */
    DataScope resolve(UserInfo user);
}
