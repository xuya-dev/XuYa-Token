package dev.xuya.token.spring.boot.starter;

import dev.xuya.token.core.model.DataScope;

/**
 * 当前请求数据权限的 ThreadLocal 持有器,由 {@code @RequiresDataScope}
 * 切面在校验通过后绑定,业务代码读取以组装行级查询条件。
 *
 * @author 青衣
 */
public final class DataScopeContext {

    /** 当前请求的数据权限持有器。 */
    private static final ThreadLocal<DataScope> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /**
     * 绑定当前请求的数据权限。
     *
     * @param scope 数据权限
     */
    public static void set(DataScope scope) {
        HOLDER.set(scope);
    }

    /** 获取当前数据权限;未经 @RequiresDataScope 的路径为 {@code null}。 */
    public static DataScope get() {
        return HOLDER.get();
    }

    /** 清除当前线程绑定,防止线程复用导致的数据串扰。 */
    public static void clear() {
        HOLDER.remove();
    }
}
