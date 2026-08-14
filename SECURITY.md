# 安全策略 | Security Policy

## 支持版本 | Supported Versions

| 版本 | 支持状态 |
|---|---|
| 0.1.x | ✅ 支持 |

## 报告漏洞 | Reporting a Vulnerability

请勿通过公开 Issue 报告安全漏洞。

Please do NOT report security vulnerabilities through public GitHub Issues.

- 邮箱 | Email: security@xuya.dev(示例,发布前替换为真实邮箱)
- 请包含:影响版本、复现步骤/POC、影响评估
- 我们将在 72 小时内确认收悉,修复后通过 CHANGELOG 与 GitHub Security Advisories 公告

## 安全设计说明 | Security Notes

- token 使用 `SecureRandom` 生成(256 位),不可预测
- 密码推荐 BCrypt 存储(`PasswordEncoder` SPI),框架不落明文
- JWT 密钥要求至少 32 字节(HS256),启动时强制校验
- 登录失败统一返回 401,不区分"用户不存在"与"密码错误"(防用户枚举)
- 已知取舍:JWT 无状态模式不支持服务端注销/踢人,高敏场景请使用内存/Redis 会话
