# XuYa-Token

简易、企业级的 Java 权限框架 / A simple, enterprise-ready Java permission framework.

基于 **RBAC + 资源鉴权**,为 Spring Boot 3.x 提供开箱即用的 starter。

📚 **完整文档**:打开 [`docs/index.html`](docs/index.html)(单文件网页,含可交互鉴权演示)。

## 特性

- **RBAC 模型**:用户 → 角色 → 权限(resource:action,支持 `*` 通配),角色支持继承
- **鉴权可解释**:任意判定输出"为什么"——角色继承轨迹、命中权限、拒绝原因;`explain-enabled` 开启运行时调试端点
- **SQL 级数据权限**:`xuya-token-mybatis` 拦截器按注解自动改写 SQL,Mapper 零侵入
- **登录防爆破**:连续失败锁定(`guard-max-failures` / `guard-lock-millis`),按体系:账号计数
- **审计事件**:`AuthAuditListener` SPI 回调登录成功/失败与注销,对接审计日志与告警
- **多体系(端)**:开放体系标识(B/C/OPEN/MINI…不限数量),用户来源、会话、角色、超时策略按体系隔离
- **数据权限**:SELF / DEPT / DEPT_AND_CHILD / ALL 四级行级过滤,`@RequiresDataScope` 注解 + `DataScopeContext` + **SQL 条件生成器**
- **注解鉴权**:`@RequiresLogin` / `@RequiresRoles` / `@RequiresPermissions` / `@RequiresDataScope`
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
| `xuya-token-mybatis` | MyBatis-Plus 数据权限拦截器(可选,自动改写 SQL) |
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

### 多体系(端)

体系是开放字符串标识,数量不限。同一应用可同时承载 B 端管理后台与 C 端消费者应用(乃至开放平台、小程序):

```java
// C 端登录(手机号 + 验证码),签发 C- 前缀 token
authenticator.login(UserType.C, phone, code);

// B/C 端账号分表:覆写 UserProvider 的体系方法
public UserInfo authenticate(String userType, String username, String password) { ... }

// 体系专属角色:仅 C 端可见,优先于共用角色
permissionLoader.addRole(UserType.C, Role.builder("member").permission("shop:discount").build());
```

```yaml
xuya:
  token:
    user-types:            # 按体系覆盖策略,未覆盖沿用全局
      c:
        timeout-millis: 2592000000        # C 端 30 天
    user-type-paths:       # 体系路径隔离:token 体系与路径不符 → 403
      b: [/admin/**]
      c: [/c/**]
```

踢人下线可按体系(`invalidateByUserId(UserType.C, userId)`)或跨全部体系执行。

### 鉴权可解释(Explainable Authorization)

"为什么他被拒绝了"不再靠猜:任意权限/角色判定可输出完整依据——角色继承轨迹(直接持有还是继承自谁)、命中权限(通配如何蕴含)、拒绝时的全部已授权清单。

```java
AuthDecision d = explainer.explain(user, "profile:delete");
d.isAllowed();   // false
d.getReason();   // 拒绝:已授予权限 [profile:read],无一蕴含 profile:delete
d.getRoles();    // [user(直接)],继承角色标注 来源
```

配置 `xuya.token.explain-enabled=true` 开启运行时调试端点(需登录):

```bash
GET /xuya/auth/explain?expr=profile:delete
# { "allowed": false, "matchedBy": null, "reason": "...",
#   "roles": [{"code":"user","direct":true,...}],
#   "dataScope": {"type":"DEPT","visibleDeptIds":["d2"]} }
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

**SQL 条件生成器**:把可见范围直接转成 WHERE 片段与占位参数(存储无关,可接 MyBatis / JdbcTemplate):

```java
var c = DataScopeSql.of(DataScopeContext.get())
        .deptColumn("dept_id").userColumn("create_by").build();
c.getSql();    // "dept_id IN (?, ?)"
c.getParams(); // ["d2", "d3"]
// ALL → "1=1";SELF → "create_by = ?";可见范围为空 → "1=0"(安全侧失败)
```

**MyBatis-Plus 自动拦截**(`xuya-token-mybatis`,Mapper 零侵入):

```java
// 注册拦截器
MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
interceptor.addInnerInterceptor(new DataScopeInterceptor());

// Mapper 方法标注即生效:查询时自动追加可见范围条件
@DataScopeFilter(alias = "o", deptColumn = "org_id")
@Select("SELECT * FROM orders o WHERE o.status = #{status}")
List<Order> selectByStatus(String status);
// 实际执行:... WHERE (o.status = ?) AND o.org_id IN (#{xuyaP0})
```

### 登录防爆破与审计

```yaml
xuya:
  token:
    guard-max-failures: 5     # 连续失败 5 次锁定,0 = 不启用
    guard-lock-millis: 300000 # 锁定 5 分钟
```

```java
@Bean
public AuthAuditListener auditListener() {
    return new AuthAuditListener() {
        @Override
        public void onLoginFailure(String userType, String username) {
            log.warn("[审计] 登录失败 user={} username={}", userType, username);
        }
        // onLoginSuccess / onLogout 同理,可落库或对接告警
    };
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
