package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.UserInfo;

/**
 * SPI:用户来源,用于登录校验。
 * 实现此接口可接入数据库、LDAP 或远程用户服务。
 *
 * @author 青衣
 */
public interface UserProvider {

    /**
     * 根据用户名查找用户并校验明文密码。
     *
     * @return 校验通过返回用户信息;凭证无效返回 {@code null}
     */
    UserInfo authenticate(String username, String password);

    /** 按用户 ID 查找用户(如重建会话);不存在返回 {@code null}。 */
    default UserInfo findById(String userId) {
        return null;
    }
}
