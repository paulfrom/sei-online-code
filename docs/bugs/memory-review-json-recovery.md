# 记忆审阅 JSON 恢复修复

## 现象

- `memory-review-agent` 的 CLI 进程执行成功，但 finding 文本包含未转义 ASCII 双引号时，
  Jackson 二次解析失败，审阅结果被丢弃。

## 根因

1. CLI 的 JSON 输出格式只约束外层执行 envelope，不保证 agent 文本本身是合法 JSON。

## 修复

- 强化记忆审阅提示词，要求只输出 JSON，并要求内容中的 ASCII 双引号转义。
- 严格解析失败后，通过字符串状态机修复不能作为 JSON 字符串终止符的未转义双引号，
  再由 Jackson 完整校验；合法 JSON 仍走原有严格路径。

## 流程边界

- 记忆审阅在 PRD 进入 `PRD_REVIEW` 后介入，用于提供非阻塞设计提醒。
- 计划生成仍由用户确认 PRD 后的 `confirmPrd → startInitialLoop` 启动。
- 记忆审阅成功不直接触发计划。记忆审阅和 PM 共用 requirement workspace slot；
  已确认需求的主流程由计划补偿在该 slot 空闲后恢复。

## 验证

- 新增未转义引号恢复测试。
- `:sei-online-code-service:compileJava` 通过。
- 定向测试目前被仓库内其他测试的既有构造器签名错误阻断：
  `WorkspaceManagerTest`、`CompensationServiceTest`、`MemoryJobExecutorTest`、`ConfigServiceTest`。
