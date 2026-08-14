package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.model.UserType;

/**
 * SPI:用户来源,用于登录校验。
 * 实现此接口可接入数据库、LDAP 或远程用户服务。
 * <p>多体系:默认两体系共用同一用户源;B 端 / C 端账号分表时,
 * 覆盖 {@link #authenticate(String, String, String)} 与 {@link #findById(String, String)} 按体系分别校验。
 *
 * @author 青衣
 */
public interface UserProvider {

    /**
     * 根据用户名查找用户并校验明文密码(默认体系)。
     *
     * @return 校验通过返回用户信息;凭证无效返回 {@code null}
     */
    UserInfo authenticate(String username, String password);

    /**
     * 按体系校验登录凭证;默认委托给 {@link #authenticate(String, String)}
     * (两体系共用用户源),体系分离时覆写。
     *
     * @param userType 用户体系标识,如 "B"、"C"
     * @return 校验通过返回用户信息;凭证无效返回 {@code null}
     */
    default UserInfo authenticate(String userType, String username, String password) {
        return authenticate(username, password);
    }

    /**
     * 按用户 ID 查找用户(默认体系,如重建会话);不存在返回 {@code null}。
     */
    default UserInfo findById(String userId) {
        return null;
    }

    /**
     * 按体系与用户 ID 查找用户;默认委托给 {@link #findById(String)},
     * 体系分离时覆写。
     *
     * @param userType 用户体系标识
     * @param userId   用户 ID
     * @return 用户信息;不存在返回 {@code null}
     */
    default UserInfo findById(String userType, String userId) {
        return findById(userId);
    }
}
