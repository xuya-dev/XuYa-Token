# Changelog

本项目的所有显著变更都记录在此文件中。
All notable changes to this project will be documented in this file.

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),
版本管理遵循 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)。

## [Unreleased]

### Added
- 多体系(端)支持:体系为开放字符串标识(B/C/OPEN/MINI…数量不限),用户来源、会话空间、角色权限按体系隔离
- `UserType` 开放标识工具(常量 + 合法性校验),非法/缺省体系归一化为默认 B 端
- 会话按体系隔离:token 前缀标识体系(`B-xxx`/`C-xxx`),索引、并发限制、踢人(`invalidateByUserId(体系, 用户)`)按体系独立
- 按体系策略覆盖:`xuya.token.user-types.{id}.timeout-millis / max-sessions-per-user`,未覆盖沿用全局
- 体系路径隔离:`xuya.token.user-type-paths` 配置 Ant 模式,token 体系与路径不符返回 403
- SPI 体系感知:`UserProvider`/`PermissionLoader`/`SessionManager`/`Authenticator` 新增体系维度默认方法,存量实现零破坏
- 角色按体系隔离:`InMemoryPermissionLoader.addRole(体系, Role)` 注册体系专属角色,优先于共用角色,同名角色在不同体系可不同权限
- Redis 会话体系化:索引键含体系(`user:{体系}:{userId}`),体系登记集合支持跨体系在线列表与全端踢人
- JWT 会话体系化:typ 声明携带体系,旧令牌缺省归为 B 端
- demo:C 端手机号登录(`/c/login`)、体系专属 member 角色、体系路径隔离示例

## [0.1.0] - 2026-08-14

### Added
- 初始版本:RBAC + 资源鉴权核心模型(`xuya-token-core`)
- Spring Boot 3.x 自动装配、拦截器、注解切面、统一异常处理(`xuya-token-spring-boot-starter`)
- 演示应用(`xuya-token-demo`)
- 密码加密:`PasswordEncoder` SPI + BCrypt 实现,密码不再明文存储
- 分布式会话:`xuya-token-redis` 模块提供 Redis 会话管理器,多节点共享登录态
- 配置元数据提示:IDE 对 `xuya.token.*` 配置项提供自动补全与说明
- 并发登录控制:`max-sessions-per-user` / `evict-oldest-on-exceed` 配置,支持顶替登录或拒绝新登录
- 会话管理增强:`SessionManager` 新增 `invalidateByUserId`(踢人下线)与 `listActiveTokens`(在线列表)
- 权限缓存:`CachedPermissionLoader` TTL 装饰器,`permission-cache-ttl-millis` 开启自动包装
- 角色继承:`Role` 支持父角色,身份与权限沿继承链向下传递(多级继承、环形防护)
- JWT 无状态模式:`xuya-token-jwt` 模块,配置 `xuya.token.jwt.secret` 即启用
- 数据权限:SELF/DEPT/DEPT_AND_CHILD/ALL 四级行级过滤,`@RequiresDataScope` 注解 + `DataScopeContext`,`DeptProvider` SPI 提供部门层级
- 工程治理:Enforcer(JDK 17+)、JaCoCo 覆盖率报告、release profile(源码/Javadoc 附件)、SECURITY.md
- 中文网页文档:`docs/index.html`,IDE 终端风格,深浅双模式,含可交互鉴权演示

[0.1.0]: https://github.com/xuya-dev/XuYa-Token/releases/tag/v0.1.0
