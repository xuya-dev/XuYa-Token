package dev.xuya.token.core.crypto;

/**
 * SPI:密码加密与校验。
 * {@code UserProvider} 实现应使用此接口校验密文存储的密码,
 * 避免在存储中出现明文密码。
 *
 * @author 青衣
 */
public interface PasswordEncoder {

    /**
     * 将明文密码加密为可存储的密文。
     *
     * @param rawPassword 明文密码
     * @return 密文(含盐与算法信息)
     */
    String encode(CharSequence rawPassword);

    /**
     * 校验明文密码与存储的密文是否匹配。
     *
     * @param rawPassword     待校验的明文密码
     * @param encodedPassword 存储的密文
     * @return 匹配返回 true
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
