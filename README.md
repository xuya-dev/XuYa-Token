# XuYa-Token

简易、企业级的 Java 权限框架 / A simple, enterprise-ready Java permission framework.

基于 **RBAC + 资源鉴权**,为 Spring Boot 3.x 提供开箱即用的 starter。

📚 **完整文档**:打开 [`docs/index.html`](docs/index.html)(单文件网页,含可交互鉴权演示)。

## 特性

- **RBAC 模型**:用户 → 角色 → 权限(resource:action,支持 `*` 通配),角色支持继承
- **数据权限**:SELF / DEPT / DEPT_AND_CHILD / ALL 四级行级过滤,`@RequiresDataScope` 注解 + `DataScopeContext`
- **注解鉴权**:`@RequiresLogin` / `@RequiresRoles` / `@RequiresPermissions`
- **SPI 可扩展**:`UserProvider`、`PermissionLoader`、`SessionManager` 均可替换为数据库/Redis 实现
- **密码加密**:内置 `PasswordEncoder` 接口与 BCrypt 实现,避免明文存储
- **分布式会话**:`xuya-token-redis` 提供 Redis 会话存储,多节点部署共享登录态
- **并发登录控制**:单用户会话数上限,超限顶替登录(踢最旧)或拒绝新登录;支持踢人下线与在线列表
- **权限缓存**:`CachedPermissionLoader` TTL 装饰器,数据库类数据源免于每次回源
- **JWT 无状态模式**:`xuya-token-jwt` 模块,配置密钥即启用,天然支持多节点水平扩展
- **零侵入 core**:核心模块不依赖 Spring
- **统一异常**:自动映射 401(未登录)/ 403(无权限)JSON 响应

## 快速开始

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>xuya-token-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

1. 实现 `UserProvider` Bean(用户来源)
2. 可选:实现 `PermissionLoader`(默认内存实现)
3. 在接口上标注 `@RequiresPermissions("user:delete")` 等注解

```yaml
xuya:
  token:
    header-name: Authorization
    token-prefix: "Bearer "
    timeout-millis: 1800000
    max-sessions-per-user: 2        # 0 = 不限制;超限踢最旧会话
    evict-oldest-on-exceed: true    # false 则拒绝新登录
    permission-cache-ttl-millis: 60000  # 0 = 不缓存
    exclude-paths: [/login, /error]
```

完整示例见 `xuya-token-demo` 模块。

## 模块

| 模块 | 说明 |
|---|---|
| `xuya-token-core` | 核心抽象,零 Spring 依赖,含 BCrypt 密码编码 |
| `xuya-token-spring-boot-starter` | Spring Boot 3.x 自动装配 |
| `xuya-token-redis` | Redis 分布式会话(可选,引入即生效) |
| `xuya-token-jwt` | JWT 无状态会话(可选,配置密钥后生效) |
| `xuya-token-demo` | 演示应用 |

### 角色继承

```java
// manager 继承 user:身份与权限沿继承链向下传递,支持多级继承,环形配置自动防护
Role.builder("manager").name("经理")
    .permission("report:*")
    .parent("user")
    .build();
```

### JWT 无状态会话

引入 `xuya-token-jwt` 并配置密钥即启用(与 Redis 会话二选一)。注意无状态模式的取舍:过期为绝对时长,不支持服务端注销/踢人/在线列表。

```yaml
xuya:
  token:
    jwt:
      secret: "change-me-at-least-32-bytes-long-secret"
```

### 数据权限

角色配置数据权限级别,用户有效级别取全部角色(含继承)中最宽者;`DEPT_AND_CHILD` 需提供 `DeptProvider` Bean 解析部门层级。

```java
// 角色配置级别
Role.builder("user").permission("profile:read").dataScope(DataScopeType.DEPT).build();

// 接口要求最低级别,通过后从 DataScopeContext 取可见范围组装查询条件
@RequiresDataScope(DataScopeType.DEPT)
@GetMapping("/orders")
public ... {
    DataScope scope = DataScopeContext.get();
    scope.getVisibleDeptIds(); // DEPT={本部门}; ALL/SELF 为空集
}
```

### 分布式会话

引入 `xuya-token-redis` 后自动以 Redis 替代内存会话(可通过 `xuya.token.redis.enabled=false` 关闭):

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>xuya-token-redis</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: localhost
```

## 构建

要求 JDK 17+。

```bash
mvn clean verify
```

## License

Apache License 2.0
