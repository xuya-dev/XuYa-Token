package dev.xuya.token.core.crypto;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * 基于 BCrypt 的密码编码器(默认强度 10)。
 * 同一明文每次加密产生不同密文(自带随机盐),校验通过
 * {@code BCrypt.checkpw} 完成,天然抗彩虹表。
 *
 * @author 青衣
 */
public class BCryptPasswordEncoder implements PasswordEncoder {

    /** 默认 BCrypt 强度(2 的指数,即盐迭代轮数)。 */
    public static final int DEFAULT_STRENGTH = 10;

    private final int strength;

    /** 以默认强度 10 构造。 */
    public BCryptPasswordEncoder() {
        this(DEFAULT_STRENGTH);
    }

    /**
     * 以指定强度构造。
     *
     * @param strength BCrypt 强度,有效范围 4-31
     */
    public BCryptPasswordEncoder(int strength) {
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException("strength must be between 4 and 31, got: " + strength);
        }
        this.strength = strength;
    }

    /** 使用 BCrypt 加密明文密码,每次调用生成带随机盐的不同密文。 */
    @Override
    public String encode(CharSequence rawPassword) {
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt(strength));
    }

    /** 校验明文密码与 BCrypt 密文是否匹配;密文非法时返回 false 而非抛异常。 */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
