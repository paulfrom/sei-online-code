# ContextUtil 上下文工具类

`ContextUtil` 是 SEI 框架的核心工具类，提供统一的会话上下文访问能力。基于 `ThreadLocal` 机制，在同一线程内任意位置均可调用，无需层层传参。

## 核心机制

### ThreadLocal 生命周期

```
请求进入 → ThreadLocalTranVarFilter (传播Header→TranVar)
         → SessionUserFilter (JWT解析→SessionUser→SessionContext→ThreadLocal)
         → 业务代码内任意调用 ContextUtil.xxx()
         → 请求结束 → MDC清理
```

**关键规则**：
- Web 请求：框架自动初始化，无需手动管理
- 测试代码：必须手动调用 `ThreadLocalHolder.begin()` / `ThreadLocalHolder.end()`
- 异步任务：必须使用 `ContextTaskDecorator` 传递上下文
- 响应式编程：必须使用 `ReactiveContextUtil` 包装

### 两层存储

| 存储层 | API | 用途 | 传播性 |
|--------|-----|------|--------|
| LocalVar | `ThreadLocalUtil.setLocalVar/getLocalVar` | SessionContext（会话数据） | 仅当前线程 |
| TranVar | `ThreadLocalUtil.setTranVar/getTranVar` | Token、projectId 等需跨服务传播的数据 | 通过 Feign Header 自动传播 |

## API 分类参考

### 1. 获取当前会话用户

```java
// 用户ID（平台唯一）
String userId = ContextUtil.getUserId();

// 用户账号
String account = ContextUtil.getUserAccount();

// 用户名
String userName = ContextUtil.getUserName();

// 所属组织ID（仅 UserType.Employee 有效）
String orgId = ContextUtil.getUserOrgId();

// 租户代码
String tenantCode = ContextUtil.getTenantCode();

// 是否匿名用户（无 sessionId 或 userId=anonymous）
boolean isAnonymous = ContextUtil.isAnonymous();

// 获取完整 SessionUser 对象
SessionUser sessionUser = ContextUtil.getSessionUser();
```

**SessionUser 关键字段**：

| 字段 | 说明 | 匿名用户默认值 |
|------|------|---------------|
| sessionId | 会话ID | null |
| token | JWT Token | null |
| userId | 用户ID | "anonymous" |
| account | 主账号 | "anonymous" |
| loginAccount | 当前登录账号 | "anonymous" |
| userName | 用户名 | "anonymous" |
| tenantCode | 租户代码 | null |
| orgId | 组织ID | null |
| userType | 用户类型 | Employee |
| authorityPolicy | 权限策略 | NormalUser |
| ip | 客户端IP | "Unknown" |

### 2. 获取会话上下文

```java
// 获取完整会话上下文（含 traceId、projectId、sessionUser、workbench）
SessionContext context = ContextUtil.getSessionContext();

// 直接获取上下文中的字段
String traceId = context.getTraceId();
String projectId = context.getProjectId();
String sid = context.getSid();
String token = context.getToken();
String tenantCode = context.getTenantCode();
Map<String, Object> otherInfo = context.getOtherInfo();
```

**SessionContext 构造**：
```java
new SessionContext(traceId)                                    // 仅traceId
new SessionContext(traceId, projectId, sessionUser)            // 标准
new SessionContext(traceId, projectId, sessionUser, workbench) // 含工作台
```

### 3. 国际化与语言环境

```java
// 获取当前语言环境
Locale locale = ContextUtil.getLocale();

// 获取当前语言字符串（如 "zh_CN"、"en_US"）
String lang = ContextUtil.getLocaleLang();

// 获取默认语言（始终返回 Locale.CHINA）
String defaultLang = ContextUtil.getDefaultLanguage(); // "zh_CN"
Locale defaultLocale = ContextUtil.getDefaultLocale(); // Locale.CHINA

// 设置当前语言环境
ContextUtil.setLocale(Locale.US);

// 解析 Accept-Language 请求头值
Locale parsed = ContextUtil.parseLanguage("zh-CN,zh;q=0.9,en;q=0.8"); // → Locale.CHINA
```

### 4. 国际化消息

**获取单个消息**：
```java
// 无参数
String msg = ContextUtil.getMessage("core_service_00001");

// 带参数（{0}, {1}...占位）
String msg = ContextUtil.getMessage("core_service_00003", "paramA", "paramB");
```

**指定语言环境**：
```java
String msg = ContextUtil.getMessage("key", new Object[]{"arg1"}, Locale.US);
String msg = ContextUtil.getMessage("key", new Object[]{"arg1"}, "默认值", Locale.US);
```

**规范**：所有面向用户的消息必须通过 `getMessage()` 国际化，禁止硬编码中英文。

### 5. 配置属性

```java
// 获取配置值
String appName = ContextUtil.getProperty("spring.application.name");
int port = ContextUtil.getProperty("server.port", Integer.class, 8080);

// 带默认值
String env = ContextUtil.getProperty("sei.application.env", "dev");

// 必须存在的配置（不存在抛异常）
String required = ContextUtil.getRequiredProperty("some.required.key");

// 占位符解析
String resolved = ContextUtil.resolvePlaceholders("${spring.application.name}");

// 检查是否存在
boolean exists = ContextUtil.containsProperty("some.key");
```

### 6. 项目ID与工作台

```java
// 获取当前项目ID（优先级：SessionContext > workbench.project.id > TranVar > 默认）
String projectId = ContextUtil.getProjectId();

// 获取当前个人工作台
CurrentMyWorkbench workbench = ContextUtil.getMyWorkbench();
```

**projectId 获取优先级**：
1. SessionContext 中直接存储的 projectId（如果不是默认值）
2. 当前工作台中的 project.id
3. 线程全局变量中的 `HEADER_PROJECT_KEY`
4. 返回默认值 `Constants.DEFAULT_PARENT_ENTITY_ID`

### 7. Token 与 JWT

```java
// 获取当前用户 Token
String token = ContextUtil.getToken();

// 生成 Token（默认不过期）
String token = ContextUtil.generateToken(sessionUser);

// 生成 Token（指定过期时间，秒）
String token = ContextUtil.generateToken(sessionUser, 3600);

// 解析 Token 获取 SessionUser
SessionUser user = ContextUtil.getSessionUser(token);
```

### 8. Spring Bean 获取

```java
// 按类型获取
CacheBuilder cacheBuilder = ContextUtil.getBean(CacheBuilder.class);

// 按名称获取
Object bean = ContextUtil.getBean("someBeanName");
```

### 9. 应用与环境信息

```java
// 应用代码（默认从 spring.application.name 取，无则返回 "sei"）
String appCode = ContextUtil.getAppCode();

// 运行环境（从 sei.application.env 取）
String env = ContextUtil.getEnv();

// 获取 TraceId（用于链路追踪，不存在则自动生成）
String traceId = ContextUtil.getTraceId();
```

### 10. 版本信息

```java
// 获取当前应用版本
Version currentVersion = ContextUtil.getCurrentVersion();
currentVersion.getName();                  // 应用代码
currentVersion.getCurrentVersion();         // 版本号
currentVersion.getCompleteVersionString();  // 完整版本字符串
currentVersion.getBuildTime();             // 构建时间

// 获取 SEI 平台版本
Version platformVersion = ContextUtil.getPlatformVersion();

// 获取所有依赖的版本信息
Set<Version> deps = ContextUtil.getDependVersions();
```

### 11. Header 常量

```java
ContextUtil.HEADER_TOKEN_KEY       // "Authorization"
ContextUtil.HEADER_PROJECT_KEY     // "x-project"
ContextUtil.HEADER_WORKBENCH_KEY   // "x-workbench"
ContextUtil.TRACE_ID               // "traceId"
ContextUtil.TRACE_PATH             // "tracePath"
```

## 异步任务中的上下文传递

### ContextTaskDecorator（推荐）

配置一个 `TaskExecutor` 使用 `ContextTaskDecorator`：

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new ContextTaskDecorator());
    return executor;
}
```

`ContextTaskDecorator` 自动将父线程的 TranVars、MDC、SessionContext 传递到子线程，执行完毕后自动清理。

### 手动传递

如果无法使用 TaskDecorator，可以手动传递：

```java
// 父线程
Map<String, Object> transMap = ThreadLocalHolder.getTranVars();
SessionContext context = ContextUtil.getSessionContext();
Map<String, String> mdcMap = MDC.getCopyOfContextMap();

// 子线程
try {
    ThreadLocalHolder.begin(transMap);
    ThreadLocalUtil.setLocalVar(SessionContext.class.getSimpleName(), context);
    if (mdcMap != null) MDC.setContextMap(mdcMap);
    // 业务逻辑...
} finally {
    ThreadLocalHolder.end();
    MDC.clear();
}
```

## 响应式编程中的上下文传递

在 WebFlux 响应式流中使用 `ReactiveContextUtil`（位于 `sei-cloud` 模块）：

```java
// 方式一：包装 Mono/Flux，内部直接使用 ContextUtil
public Mono<Result> doSomething() {
    return ReactiveContextUtil.mono(Mono.fromCallable(() -> {
        SessionUser user = ContextUtil.getSessionUser();  // 正常工作
        String tenantCode = ContextUtil.getTenantCode();   // 正常工作
        return doBusiness(user);
    }));
}

// 方式二：包装阻塞调用，自动在 boundedElastic 调度器上执行
public Mono<Result> doBlocking() {
    return ReactiveContextUtil.mono(() -> {
        SessionUser user = ContextUtil.getSessionUser();
        return doBlockingBusiness(user);
    });
}

// 方式三：包装 Runnable
public Mono<Void> doAsync() {
    return ReactiveContextUtil.mono(() -> {
        log.info("用户: {}", ContextUtil.getUserName());
    });
}
```

**底层原理**：
1. `ReactiveContextFilter` 将 ThreadLocal 上下文写入 Reactor Context
2. `ReactiveContextUtil.mono/flux` 方法从 Reactor Context 恢复 ThreadLocal
3. 流结束后自动清理

## 测试中的 ContextUtil

### 使用 BaseUnit5Test（推荐）

```java
public class FooTest extends BaseUnit5Test {

    @Test
    public void testWithContext() {
        // BaseUnit5Test 已在 @BeforeEach 中初始化了 SessionUser
        String userId = ContextUtil.getUserId();
        String tenantCode = ContextUtil.getTenantCode();
    }
}
```

**BaseUnit5Test 生命周期**：
```
@BeforeAll   → ThreadLocalHolder.begin()
@BeforeEach  → mockUser.mockUser(properties) → 设置 SessionContext 到 ThreadLocal
  ... @Test 方法执行 → ContextUtil 可用 ...
@AfterEach   → 计时统计
@AfterAll    → ThreadLocalHolder.end()
```

### 手动初始化（不使用基类时）

```java
@Test
public void testSomething() {
    ThreadLocalHolder.begin();
    try {
        SessionUser sessionUser = new SessionUser();
        sessionUser.setUserId("test-user-001");
        sessionUser.setAccount("testuser");
        sessionUser.setUserName("测试用户");
        sessionUser.setTenantCode("10044");
        ContextUtil.generateToken(sessionUser);
        SessionContext context = new SessionContext("trace-001", "project-001", sessionUser);
        ThreadLocalUtil.setLocalVar(SessionContext.class.getSimpleName(), context);
        ThreadLocalUtil.setTranVar(ContextUtil.HEADER_TOKEN_KEY, sessionUser.getToken());

        String tenantCode = ContextUtil.getTenantCode(); // "10044"
    } finally {
        ThreadLocalHolder.end();
        MDC.clear();
    }
}
```

### 使用 MockUser 接口

```java
@Autowired
private MockUser mockUser;

// 按租户+账号模拟
SessionUser user = mockUser.mockUser("10044", "admin");

// 按配置模拟
SessionUser user = mockUser.mockUser(mockUserProperties);

// 带项目ID模拟
SessionUser user = mockUser.mockUser("10044", "admin", "project-001");

// 直接设置已有的 SessionUser
SessionUser user = mockUser.mock(sessionUser);
```

## 常见问题

**Q: ContextUtil.getSessionUser() 返回匿名用户？**
A: 检查请求是否经过 `SessionUserFilter`（需要 JWT Token），或者 `MockUser.mockCurrentUser()` 是否调用。

**Q: 异步线程中 ContextUtil 为 null？**
A: 确保使用了 `ContextTaskDecorator`，或者手动传递上下文。

**Q: ContextUtil.getTenantCode() 返回 null？**
A: `SessionUser.tenantCode` 未设置。对于不需要租户的场景，接口实现 `ITenant` 的实体的查询会被自动过滤为 null。

**Q: 日志中 MDC 字段丢失？**
A: `SessionUserFilter` 在 finally 块中清理了 MDC。如需在 finally 后记录日志，需重新设置 MDC。
