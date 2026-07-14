# Agent 调用 Token 消耗统计改造计划

日期：2026-07-14

## 背景

当前系统已有 `generation_token` 字段，但它用于生成幂等和防重复，不用于统计模型 token 消耗。

系统中的 Agent 调用分为两类：

1. 已经创建 `oc_run` 的调用，例如开发、PM 规划、PM 验收和测试评审。
2. 仍通过无 `runId` 重载直接调用 CLI 的遗留流程，例如 PRD 生成、记忆审阅、旧规划和旧规格生成。

同时，`oc_run` 还记录 `DELIVERY`、`VALIDATION_COMMAND` 等非 Agent 执行，因此不能简单地把所有 Run 都视为 Agent 调用。

本次改造继续复用 `oc_run` 承载单次 Agent CLI 调用的 usage，但建立以下约束：

- 每次真正启动 CLI 前必须先创建并提交一个 Agent Run。
- Agent Run 与 CLI 调用保持一对一。
- 非 Agent Run 允许没有 Agent 和 usage 信息，不计入 Agent 调用汇总。
- usage 是否存在与业务执行成功、失败或取消相互独立。

## 目标

1. 记录每次 Agent CLI 调用的 token 消耗：
   - 总输入 token
   - 输出 token
   - 缓存读取 token
   - 缓存写入 token
   - 总 token
2. 保存 CLI 返回的原始 usage JSON，便于协议变化后的追溯和重新解析。
3. 保存调用时的 Agent 快照：
   - `agent_id`
   - `agent_name`
   - 实际使用的 `cli_tool`
   - 请求的 `model`
4. 所有 Agent CLI 调用统一使用带 `runId` 的入口。
5. usage 缺失时明确标识，不使用估算值填充真实统计字段。
6. 失败或取消的调用只要取得 usage，也必须记录实际消耗。
7. 第一阶段可通过现有 Run 查询获取归一化 usage，并可单独查询原始 usage。

## 非目标

第一阶段不做：

- token 费用换算。
- 用户级或项目级额度控制。
- 基于 token 的限流。
- prompt token 估算。
- 前端完整可视化大盘。
- 多次 CLI 调用复用同一个 Run。

## 已确认的现状

### `generation_token` 不是统计字段

`V23__generation_token.sql` 中的 `generation_token` 用于异步生成幂等，不能表达每次 CLI 调用的 token 消耗。

### Runner 入口

核心入口：

- `CliRunnerRegistry`

实际执行器：

- `CodexRunner`
- `ClaudeRunner`

当前 `CliRunner` 和 `CliRunnerRegistry` 都有带 `runId` 与不带 `runId` 的执行重载。

### 已带 Run 的调用

以下调用已传入 `runId`：

| 调用方 | Run 类型 | 说明 |
|---|---|---|
| `CodingTaskExecutionService` | `DEVELOPMENT` | 开发 Agent |
| `PmAgentClient` | `PM_PLANNING` / `PM_ACCEPTANCE` | PM Agent |
| `ValidationLoopService` | `TEST_REVIEW` | 测试 Agent |
| `FeatureDesignBuildService` | 待统一为 `DEVELOPMENT` | 旧功能设计构建流程 |

### 仍未创建 Run 的调用

以下生产调用仍使用无 `runId` 重载，本次必须迁移：

| 调用方 | 调用场景 | 新 Run 类型 |
|---|---|---|
| `RequirementAgentService` | PRD 生成 | `REQUIREMENT_GENERATION` |
| `RequirementAgentService` | 记忆审阅 | `MEMORY_REVIEW` |
| `PlanAgentService` | 项目规划 | `PROJECT_PLANNING` |
| `PlanAgentService` | 功能设计生成 | `FEATURE_DESIGN` |
| `SpecAgentService` | 规格生成 | `SPEC_GENERATION` |

迁移完成前不得删除无 `runId` 重载；迁移完成后必须删除，并通过编译和静态扫描确认没有遗漏。

### 非 Agent Run

以下 Run 不属于 Agent CLI 调用：

- `VALIDATION_COMMAND`
- `DELIVERY`

它们的 `agent_id`、`agent_name`、`cli_tool`、`model` 和 usage 字段保持 null 或 `UNAVAILABLE`，汇总时必须排除。

### 当前 CLI usage 协议

#### Codex app-server

当前 app-server 通过独立通知发送 token usage：

```text
thread/tokenUsage/updated
```

核心结构为 camelCase：

```json
{
  "threadId": "thr_xxx",
  "turnId": "turn_xxx",
  "tokenUsage": {
    "total": {
      "totalTokens": 100,
      "inputTokens": 80,
      "cachedInputTokens": 20,
      "outputTokens": 20,
      "reasoningOutputTokens": 5
    },
    "last": {}
  }
}
```

`turn/completed` 用于确认 turn 的终态，不能作为当前版本的主要 usage 来源。

#### Claude CLI

当前使用：

```bash
claude -p <prompt> --output-format json
```

最终 JSON 中的 `result` 是业务输出，`usage` 包含 `input_tokens`、`output_tokens`、`cache_read_input_tokens`、`cache_creation_input_tokens` 等统计字段。

## 核心设计原则

### 1. Agent Run 与 CLI 调用一对一

每次 Registry 调用只能对应一个 `runId`。重试如果重新启动 CLI，必须创建新的 Run，避免覆盖第一次调用的 usage。

### 2. usage 与业务状态解耦

以下组合都合法：

| Run 状态 | usage 状态 | 含义 |
|---|---|---|
| `SUCCEEDED` | `COMPLETE` | 成功并取得完整 usage |
| `FAILED` | `COMPLETE` | 业务或 CLI 失败，但取得完整 usage |
| `CANCELLED` | `PARTIAL` | 取消前取得部分 usage |
| 任意终态 | `UNAVAILABLE` | CLI 未返回或无法解析 usage |

不得因为 Run 失败或取消而清空已经取得的 token。

### 3. 快照在启动 CLI 前写入

Agent 快照必须与 Run 初次创建一起写入，避免 Agent 配置在调用期间发生变化。

- `agent_id`：调用时的 `oc_agent.id`，不建立数据库外键，保证 Agent 删除后历史仍可保留。
- `agent_name`：调用时名称快照，历史查询不依赖当前 `oc_agent`。
- `cli_tool`：Registry 解析后的实际 Runner 工具名，不保存未知值或空值。
- `model`：调用时请求的模型；null 表示使用 CLI 默认模型，不宣称是最终路由模型。

生产调用找不到指定 Agent 时，不再静默伪装成该 Agent 调用。调用方按原有业务规则失败或走确定性 fallback，但未真正启动 CLI 时不创建 Agent usage 记录。

### 4. 未知值使用 null，不使用 0

- CLI 明确返回 0 时保存 0。
- CLI 没有提供某个维度时保存 null。
- Codex 当前没有 cache-write 指标时，`cache_write_tokens` 保存 null。
- `usage_status = UNAVAILABLE` 时所有归一化 token 字段为 null。

### 5. 异步更新只修改 usage 列

异步回调不得 merge 调用方持有的旧 `Run` 实体。usage 写入使用按 `runId` 的定向 update，只更新 usage 相关列，避免覆盖业务侧刚更新的 `state`、`finished_date` 或 `failure_reason`。

## Token 归一化口径

归一化字段定义如下：

| 字段 | 统一含义 |
|---|---|
| `input_tokens` | 本次调用的总输入 token，包含缓存读写部分 |
| `output_tokens` | Provider 报告的输出 token 总量 |
| `cache_read_tokens` | 从缓存读取的输入 token |
| `cache_write_tokens` | 写入或创建缓存的输入 token |
| `total_tokens` | 归一化后的 `input_tokens + output_tokens`，优先使用语义一致的 Provider 总数 |

### Codex 映射

使用最终一次 `thread/tokenUsage/updated` 的 `tokenUsage.total`：

| Codex | 归一化字段 |
|---|---|
| `inputTokens` | `input_tokens` |
| `outputTokens` | `output_tokens` |
| `cachedInputTokens` | `cache_read_tokens` |
| 不提供 | `cache_write_tokens = null` |
| `totalTokens` | `total_tokens` |

`cachedInputTokens` 是 `inputTokens` 的分项，计算总量时不得再次相加。

### Claude 映射

Claude 的未缓存输入、缓存读和缓存创建是互斥分项：

```text
input_tokens = usage.input_tokens
             + usage.cache_read_input_tokens
             + usage.cache_creation_input_tokens

output_tokens = usage.output_tokens
cache_read_tokens = usage.cache_read_input_tokens
cache_write_tokens = usage.cache_creation_input_tokens
total_tokens = input_tokens + output_tokens
```

缺失的缓存分项在计算时可按 0 参与运算，但对应数据库字段仍保存 null，以区分“未提供”和“明确为 0”。

所有数值必须为非负 Long；负数、溢出或非数值字段使该 usage 降级为 `PARTIAL` 或 `UNAVAILABLE`，原始 JSON 仍保留。

## 数据模型设计

扩展 `oc_run`：

```sql
ALTER TABLE oc_run
    ADD COLUMN IF NOT EXISTS agent_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS agent_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cli_tool VARCHAR(32),
    ADD COLUMN IF NOT EXISTS model VARCHAR(100),
    ADD COLUMN IF NOT EXISTS input_tokens BIGINT,
    ADD COLUMN IF NOT EXISTS output_tokens BIGINT,
    ADD COLUMN IF NOT EXISTS cache_read_tokens BIGINT,
    ADD COLUMN IF NOT EXISTS cache_write_tokens BIGINT,
    ADD COLUMN IF NOT EXISTS total_tokens BIGINT,
    ADD COLUMN IF NOT EXISTS usage_status VARCHAR(20) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN IF NOT EXISTS raw_usage_json TEXT;

CREATE INDEX IF NOT EXISTS idx_run_agent
    ON oc_run (agent_id)
    WHERE agent_id IS NOT NULL;
```

不为低选择性的 `usage_status` 单独建立普通索引。后续出现明确的按状态运维查询后，再根据执行计划增加部分索引。

### usage 状态

新增枚举 `UsageStatus`：

```java
public enum UsageStatus {
    UNAVAILABLE,
    PARTIAL,
    COMPLETE
}
```

状态定义：

- `COMPLETE`：已经取得 Provider 对本次调用给出的最终 usage；Provider 本身不支持的维度仍可为 null。
- `PARTIAL`：只取得中间累计值，或最终 usage 中只有部分核心字段可用。
- `UNAVAILABLE`：没有取得任何可信的 token 数值。

旧数据和非 Agent Run 默认 `UNAVAILABLE`。

## Runner 返回结构

### 内部结果对象

```java
public class CliRunResult {
    private String output;
    private AgentUsage usage;
    private boolean processSucceeded;
    private String failureReason;
}
```

```java
public class AgentUsage {
    private Long inputTokens;
    private Long outputTokens;
    private Long cacheReadTokens;
    private Long cacheWriteTokens;
    private Long totalTokens;
    private UsageStatus status;
    private String rawUsageJson;
}
```

`processSucceeded` 只表示 CLI/协议层是否正常完成，不替代业务侧对输出内容、代码变更或验收结果的判断。

### 方法命名与兼容策略

Java 不能仅按返回类型重载方法，因此 Runner 新方法明确命名为：

```java
CompletableFuture<CliRunResult> executeDetailed(...);
```

调整方式：

1. `CliRunner` 以 `executeDetailed(...)` 作为唯一底层执行契约。
2. `CodexRunner` 和 `ClaudeRunner` 返回 `CliRunResult`。
3. `CliRunnerRegistry.execute(...)` 继续向业务调用方返回 `CompletableFuture<String>`，内部调用 `executeDetailed(...)`、先落 usage，再提取 output。
4. 删除 Registry 和 Runner 中无 `runId` 的执行入口。

## 统一调用上下文

为避免继续扩展位置参数，新增内部上下文：

```java
public record AgentInvocationContext(
        String runId,
        String iterationId,
        String taskId,
        String agentId,
        String agentName,
        String cliTool,
        String model) {
}
```

Registry 新入口示意：

```java
CompletableFuture<String> execute(
        AgentWorkspace workspace,
        AgentInvocationContext context,
        String prompt,
        String mcpConfig);
```

约束：

- `runId` 必填且对应已提交的 Agent Run。
- `agentId`、`agentName`、`cliTool`、`model` 必须与 Run 快照一致。
- `cliTool` 使用 Registry 解析后的实际 Runner 名称。
- 未知的非空 CLI 工具名直接失败；null 或 blank 仍按兼容规则解析为默认工具。

## Run 创建与写入流程

### 事务组件

新增专用组件，例如 `AgentRunRecorder`：

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
Run createAgentRun(AgentRunCreateCommand command);

@Transactional(propagation = Propagation.REQUIRES_NEW)
void updateUsage(String runId, AgentUsage usage);
```

实现要求：

- 必须通过独立 Spring Bean 调用，避免同类自调用导致 `REQUIRES_NEW` 失效。
- `createAgentRun` 在启动 CLI 前提交 Run 和 Agent 快照。
- `updateUsage` 使用 DAO 定向更新 usage 列，不保存旧 Run 实体。
- 更新行数为 0 时记录错误和监控指标，但不得把 usage 落库失败转换成 Agent 输出失败。
- 业务侧更新终态时重新加载 Run 或使用定向状态更新，不保存创建阶段持有的旧实体。

### 完整时序

1. 调用方加载并校验 Agent。
2. Registry 解析实际 CLI 工具名。
3. 调用 `AgentRunRecorder.createAgentRun(...)` 创建并提交 Run，写入 Agent 快照。
4. 使用已保存 Run 构造 `AgentInvocationContext`。
5. 调用 `CliRunnerRegistry.execute(...)`。
6. Runner 收集输出、协议状态和 usage。
7. Registry 调用 `AgentRunRecorder.updateUsage(...)`。
8. usage 更新完成或记录失败后，Registry 才完成返回给业务方的 String future。
9. 业务调用方按现有规则更新 Run 终态和业务对象状态。

CLI 启动不得发生在尚未提交的外层业务事务中，避免外层回滚后 CLI 仍继续执行。所有入口应拆分为“短事务准备业务状态和 Run、事务提交后执行 CLI、短事务更新终态”。

同步等待 CLI 的服务不得在数据库事务中持有连接等待 30 分钟。`ValidationLoopService` 是必须优先拆分的已知调用点。

## Codex usage 采集

改造位置：

- `CodexAppServerEvents.handleNotification(...)`
- `CodexRunner.runBlocking(...)`

采集规则：

1. 处理 `thread/tokenUsage/updated`。
2. 校验事件的 `turnId` 与当前 turn 一致。
3. 每次通知覆盖内存中的最新累计值，最终使用 `tokenUsage.total`。
4. 兼容 camelCase 当前协议；仅将历史 snake_case 作为显式兼容分支。
5. `turn/completed` 负责记录 `completed`、`failed`、`interrupted` 终态。
6. 收到终态且已有合法 usage 时标记 `COMPLETE`。
7. 进程提前退出或被强杀，但此前收到合法 usage 时标记 `PARTIAL`。
8. 从未收到合法 usage 时标记 `UNAVAILABLE`。
9. 保存最终 usage 节点原文到 `raw_usage_json`。
10. usage 解析失败不影响已有 Agent 输出。

当前 Runner 每次创建新 thread 且只启动一个 turn，这是使用 `tokenUsage.total` 作为单次 Run usage 的前提。将来复用 thread 时必须重新评审累计值口径。

## Claude usage 采集

改造位置：

- `ClaudeRunner.runBlocking(...)`
- 当前 `extractResultJson(...)` 拆分为 envelope 解析和业务 output 提取

采集规则：

1. stdout 合法 JSON 时，一次解析完整 envelope。
2. 从 `result` 提取业务输出，保持现有 markdown fence 处理。
3. 从 `usage` 提取 token，并按 Claude 归一化公式计算。
4. 保存原始 `usage` 节点。
5. envelope 表示错误但包含 usage 时，仍保存 usage，`processSucceeded = false`。
6. stdout 非法或 usage 格式不匹配时标记 `UNAVAILABLE`，不因 usage 解析失败额外改变现有业务输出行为。
7. 非零退出码如果 stdout 中存在可解析 usage，也应尽力保存。

## 后端分层改造

按 EADP/SEI 分层约定实施：

1. Entity
   - `Run` 新增 Agent 快照和 usage 字段。
2. DTO
   - `RunDto` 增加 Agent 快照、归一化 token 和 `usageStatus`，不包含 `rawUsageJson`。
   - 新增 `RunUsageDto` 用于单次 usage 详情，包含 `rawUsageJson`。
3. DAO
   - `RunDao` 或扩展 DAO 增加 usage 定向更新方法。
4. Service
   - 新增 `AgentRunRecorder`，负责独立事务中的创建和 usage 更新。
   - Run 终态更新必须重新加载或定向更新。
5. Controller/API
   - 现有 `GET /run/findOne?id=...` 通过 `RunDto` 返回归一化字段。
   - 新增 `GET /run/findUsage?runId=...` 返回 `RunUsageDto` 和原始 usage。

不把 `rawUsageJson` 加入共享 `RunDto`，避免 `findByPage`、`findByRequirement` 和 `findByCodingTask` 列表响应膨胀。

## 汇总口径

第一阶段不要求新增汇总 API，但所有后续汇总必须满足：

```sql
agent_id IS NOT NULL
```

并按以下口径处理：

- 调用次数：Agent Run 数量，与 `usage_status` 无关。
- 有 usage 调用数：`usage_status IN ('PARTIAL', 'COMPLETE')`。
- token 合计：对非 null token 求和。
- 成功/失败/取消次数：按 Run 业务状态统计。
- 非 Agent Run 不计入任何 Agent 调用指标。
- `UNAVAILABLE` 不当作 0 token 调用，应单独展示数量。

第二阶段可增加：

```text
GET /run/tokenSummary?requirementId=...
GET /run/tokenSummary?loopId=...
GET /run/tokenSummary?agentId=...
GET /run/tokenSummary?runType=...
```

## 实施拆分

### 阶段 1：协议 Fixture 和 Runner 结果结构

范围：

- 先补充当前 Codex 和 Claude 原始输出 fixture。
- 新增 `UsageStatus`、`AgentUsage`、`CliRunResult`。
- 将 `CliRunner` 底层契约改为 `executeDetailed(...)`。
- 改造 Codex 和 Claude usage 解析。

验收：

- 当前协议 fixture 可解析。
- 业务 output 与改造前一致。
- 成功、失败、中断和 usage 缺失均返回明确结果。
- 不同 Provider 的归一化总数无重复计算。

### 阶段 2：数据库和独立事务写入

范围：

- 新增 Flyway migration。
- 扩展 `Run` Entity。
- 新增 usage 定向更新 DAO。
- 新增 `AgentRunRecorder`。
- 增加并发和事务集成测试。

验收：

- PostgreSQL migration 可从旧结构执行且不破坏旧数据。
- Agent Run 在启动 CLI 前已提交。
- usage 回调不会覆盖 Run 终态字段。
- usage 更新失败不改变业务 output future。

### 阶段 3：统一调用入口

范围：

- 新增 `AgentInvocationContext`。
- 迁移所有已带 Run 的调用点。
- 为 5 个无 Run 调用点创建对应 Run。
- 检查所有调用入口，确保 CLI 在业务准备事务提交后启动。
- 拆分 `ValidationLoopService` 的长事务。
- 统一 `FeatureDesignBuildService` 的 Run 类型和终态维护。
- 最后删除无 `runId` 重载。

验收：

- 生产代码不存在无 `runId` 的 CLI 调用。
- 每次实际 CLI 启动前数据库已有唯一 Agent Run。
- 每次重试产生新的 Run。
- 非 Agent Run 不写入 Agent 快照。
- 全量后端编译和原有测试通过。

### 阶段 4：查询接口

范围：

- `RunDto` 返回归一化 usage 和 Agent 快照。
- 新增 `RunUsageDto` 和 `findUsage` 接口返回原始 usage。
- 第一阶段不修改前端。

验收：

- `GET /run/findOne?id=...` 返回归一化字段。
- `GET /run/findUsage?runId=...` 返回原始 usage。
- Run 列表接口不返回 `rawUsageJson`。
- 非 Agent Run 的 usage 状态为 `UNAVAILABLE`。

## 测试计划

### 1. Codex 协议测试

- 单次 `thread/tokenUsage/updated` camelCase fixture。
- 多次 usage 更新只保留最终累计值。
- `tokenUsage.total` 与 `last` 不混用。
- `turn/completed` 为 `completed`、`failed`、`interrupted`。
- 中断前有 usage 时保存 token。
- 进程提前退出但已有 usage 时标记 `PARTIAL`。
- 无 usage 和畸形 usage 时标记 `UNAVAILABLE`。
- `cachedInputTokens` 不重复计入 `totalTokens`。

### 2. Claude 协议测试

- stdout 同时包含 `result` 和 `usage`。
- cache read、cache creation 和未缓存 input 正确归一化。
- usage 明确返回 0 与字段缺失可区分。
- error envelope 带 usage 时仍保存 token。
- 非零退出码带 usage 时尽力保存。
- stdout 非法或缺少 usage 不改变原业务 output 行为。

### 3. Registry 测试

- `runId` 为空时拒绝执行。
- 两个 Agent 使用同一 CLI 时不会串写 `agent_id`。
- null CLI 使用解析后的默认工具快照。
- 未知非空 CLI 工具名失败。
- Registry 在 usage 落库完成后才完成 String future。
- usage 落库异常不把成功 output 改为失败。
- 无 `runId` 重载已删除。

### 4. Run 持久化与并发测试

- Run 在 CLI 线程启动前已能被新事务查询到。
- usage 定向更新不覆盖 `state`、`finished_date`、`failure_reason`。
- 业务终态更新不覆盖 token 字段。
- CLI 回调和取消请求并发时两类字段都保留。
- 重复 usage 通知采用最后累计值，更新保持幂等。
- 找不到 runId 时记录错误且不影响业务 output。

### 5. 调用点迁移测试

- 5 个遗留场景分别创建正确 `RunType`。
- CLI 未启动时不创建虚假的 Agent token 记录。
- 重试创建新 Run，不覆盖历史 usage。
- `DELIVERY`、`VALIDATION_COMMAND` 不计入 Agent 汇总。
- `ValidationLoopService` 等同步调用不在长事务中等待 CLI。

### 6. Migration 与 API 测试

- 在 PostgreSQL 上执行 migration。
- 旧数据 `usage_status` 为 `UNAVAILABLE`。
- 旧 `oc_run` 数据保持不变。
- `findOne` 返回归一化 usage。
- `findUsage` 返回原始 usage。
- 所有 Run 列表接口不返回 `rawUsageJson`。

## 可能出现的问题

### 1. CLI 协议继续变化

处理：保存原始 usage、为每个受支持版本保留 fixture、解析器按明确路径兼容，不递归猜测任意同名字段。

### 2. usage 事件是累计值

处理：当前 Codex 每个 Run 新建 thread 且只执行一个 turn，固定读取 `tokenUsage.total`。一旦引入 thread 复用，必须改为前后快照差值或按 turn 归属统计。

### 3. 失败调用低估消耗

处理：usage 与业务状态解耦；失败和取消只要有数据就保存，部分数据标记 `PARTIAL`。

### 4. 异步事务看不到 Run

处理：Run 使用独立事务创建并在 CLI 启动前提交；测试中用不同线程和不同事务验证可见性。

### 5. 并发保存覆盖 usage

处理：usage 和业务终态都使用定向更新或重新加载后的实体，不跨线程 merge 旧实体。

### 6. Provider token 含义不同

处理：固定归一化公式和 Provider fixture；未知维度保存 null，不用统一加法猜测。

### 7. 原始 JSON 增大查询响应

处理：原始 JSON 只通过单独详情接口返回，不加入共享列表 DTO。

### 8. Agent 被删除或配置变化

处理：保存 `agent_id`、`agent_name`、实际 `cli_tool` 和请求 `model` 快照，不通过外键强制历史依赖当前 Agent。

## 最终验收标准

1. 所有生产 Agent CLI 调用都有唯一、已提交的 `runId`。
2. 代码中不存在无 `runId` 的 Runner/Registry 执行入口。
3. Codex 当前 `thread/tokenUsage/updated` 和 Claude JSON usage 均可正确解析。
4. 失败和取消调用不会丢失已经产生的 token。
5. Codex 与 Claude 的输入、缓存和总 token 不重复计算。
6. usage 异步更新与业务状态更新不会互相覆盖。
7. `DELIVERY`、`VALIDATION_COMMAND` 等非 Agent Run 不进入 Agent 汇总。
8. PostgreSQL migration、Runner 测试、事务集成测试和后端全量测试通过。
9. 现有 Agent 业务输出与改造前保持兼容。
