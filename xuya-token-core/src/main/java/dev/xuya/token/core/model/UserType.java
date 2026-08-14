package dev.xuya.token.core.model;

/**
 * 用户体系(端)标识:<b>开放字符串设计,不限定端数量</b>。
 * "B"(管理后台)、"C"(消费者端)只是框架预置的两个常用标识,
 * 接入方可自由定义新体系,如 "OPEN"(开放平台)、"MINI"(小程序)、"IOT" 等。
 *
 * <p>体系标识即隔离维度:用户来源、会话空间、角色权限、
 * 超时与并发策略均按标识独立;token 形如 {@code {体系}-{随机}}。
 *
 * <p>合法标识为 {@code [A-Za-z0-9_]+}(不得含 '-',避免与 token 前缀分隔符冲突);
 * 未指定或非法的体系一律归入 {@link #DEFAULT},保持向后兼容。
 *
 * @author 青衣
 */
public final class UserType {

    /** B 端:管理后台 / 企业员工体系(预置,默认体系)。 */
    public static final String B = "B";

    /** C 端:消费者 / 移动端体系(预置)。 */
    public static final String C = "C";

    /** 默认体系:未显式指定时使用,即 B 端。 */
    public static final String DEFAULT = B;

    private UserType() {
    }

    /**
     * 判断体系标识是否合法({@code [A-Za-z0-9_]+},不含 '-')。
     *
     * @param code 待校验的体系标识
     */
    public static boolean isValid(String code) {
        return code != null && !code.isEmpty() && code.matches("[A-Za-z0-9_]+");
    }

    /**
     * 归一化体系标识:非法或为 null 时返回 {@link #DEFAULT}。
     *
     * @param code 体系标识
     * @return 合法标识或默认体系
     */
    public static String normalize(String code) {
        return isValid(code) ? code : DEFAULT;
    }
}
