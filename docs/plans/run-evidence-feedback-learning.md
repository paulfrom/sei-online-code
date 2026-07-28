# Run 证据持久化、PM 修复闭环与 Agent 行为记忆实施方案

日期：2026-07-28

状态：待实施

范围：后端、前端、数据库、Agent 提示词与回归测试

## 1. 结论

采用“原始日志归档 + 结构化证据投影 + PM 修复包 + Agent 行为记忆”的分层方案：

1. Run 的 stdout、stderr、system frame 按顺序脱敏后压缩分块持久化，同时继续通过 WebSocket 实时广播。
2. Run 结束且仍持有工作区写租约时，固化 CLI exitCode、最终输出、Git diff、changed files、命令结果、findings 和本次任务实际验收标准，形成不可变的 `RunEvidenceBundle`。
3. PM 只基于结构化证据和必要的日志片段作出决定，并输出可校验的修复包，不直接消费整段原始日志。
4. RETRY 或 REPLAN 后的下一次 Agent 必须收到完整修复包、失败验收项、验证命令和证据引用。
5. PM 可以提出长期行为记忆候选，但单次失败不能直接污染长期记忆；候选经过重复命中或人工批准后才进入后续 Agent prompt。
6. `Run.state` 继续只表示本次调用是否获得可审阅结果。证据完整度和交付质量使用独立状态，由 PM 决定下一步。

这里的“原始日志”指保持原始顺序和 stream 类型的执行日志，但在进入持久层前必须完成凭证和敏感值脱敏。默认不持久化未脱敏密钥。

## 2. 目标与非目标

### 2.1 必须实现

- Run 结束后可以回放本次 stdout、stderr、system 日志，并能判断日志是否完整、截断或缺失。
- 持久化 CLI 进程 exitCode；验证报告中的每条 command 另外保存自己的 exitCode。
- 在工作区被下一个 Run 使用前捕获本次 Git base/head、diff stat、changed files 和 patch。
- 将执行计划中的实际 `acceptanceCriteria` 固化到 CodingTask 和本次 Run 证据，不能在 PM 审阅时传空列表。
- PM 的每条 finding 都必须引用证据，并输出 root cause、必改项、验证步骤和完成证据要求。
- RETRY 和 REPLAN 创建的新 Run 必须收到上述 PM 修复包。
- 建立受控的长期行为记忆，支持候选、启用、禁用、过期、去重和效果追踪。
- Run 日志、证据、PM 决策、反馈包和长期记忆都可审计。

### 2.2 不在本次范围

- 不进行模型微调，不修改模型权重。
- 不把完整原始日志直接放进 PM 或开发 Agent prompt。
- 不允许 PM 单方面把一次偶发失败升级成永久项目规则。
- 不改变“Run 有可审阅结果即成功，交付质量由 PM 判断”的状态语义。
- 不用进度账本替代执行计划、CodingTask 或 TaskDeliveryReview。
- 不回填历史 Run 的原始日志；历史数据只能尽力从已有 summary、Git 和评论生成不完整证据。

## 3. 当前缺口

| 缺口 | 当前表现 | 目标状态 |
|---|---|---|
| 原始日志 | 只通过 WebSocket best-effort 广播 | 脱敏、分块、可校验、可回放 |
| 最终输出 | 已进入 `Run.summary` | 同时作为 `FINAL_OUTPUT` artifact 和结构化证据来源 |
| exitCode | `Run` 有字段，但 `CliRunResult` 没有统一返回 | CLI 进程 exitCode 统一贯穿 Runner、Run 和证据 |
| Git 成果 | 后续 prompt 临时读取当前工作区 | Run 结束、释放租约前固化本次 diff |
| 验收标准 | PlanTask 有数据，CodingTask 未持久化；PM 输入传空列表 | CodingTask 和 Evidence 均保存不可变验收快照 |
| Observation | 多数只写 summary，`evidenceData` 为空 | Observation 引用 EvidenceBundle，不承载大正文 |
| PM 输入 | 主要是 Run summary 和任务失败字段 | 完整 EvidenceBundle + 有界日志片段 |
| PM 输出 | summary/findings/retryReason，修复要求不够强 | 可验证 `RemediationBrief` |
| 下一次 Agent | 主要读取评论正文和 failureReason | 明确注入修复包、失败验收项和证据摘要 |
| 长期改进 | 项目记忆偏代码事实，无行为学习生命周期 | 受控的 AgentBehaviorMemory |

## 4. 架构选择

### 4.1 备选方案

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 每条日志一行数据库记录，并把完整日志传给 Agent | 实现直观、查询简单 | 写放大严重、prompt 膨胀、泄密面大、PM 容易被噪声干扰 | 拒绝 |
| 只保存 Run.summary 和 Git diff | 成本最低 | 无法回放、缺少命令上下文、证据不可验证 | 拒绝 |
| ArtifactStore 压缩分块 + EvidenceBundle 结构化投影 | 可回放、可扩展、提示词有界、证据可引用 | 需要新增存储抽象、提取器和生命周期管理 | 采用 |

### 4.2 推荐架构

```text
ClaudeRunner / CodexRunner
        │ RunLogFrame(sequenceNo, stream, line, ts)
        ▼
CompositeRunLogSink
  ├─ WebSocketSink ───────────────► 浏览器实时日志
  └─ PersistentArtifactSink
        ├─ 脱敏
        ├─ 64 KiB / 1 秒分块
        └─ gzip + sha256 ─────────► RAW_LOG artifact

CLI 完成（仍持有 workspace lease）
        │
        ├─ final output / process exitCode
        ├─ base/head/changed files/Git patch
        ├─ task acceptance criteria snapshot
        └─ validation commands/findings
        ▼
RunEvidenceCaptureCoordinator
        ▼
RunEvidenceBundle(READY / PARTIAL / FAILED)
        │
        ├─► RunObservation（只存摘要与 evidenceBundleId）
        └─► TaskDeliveryReview
                 ▼
            PM Agent
                 ▼
       decision + RemediationBrief
          ├─► 下一次 Run Prompt
          └─► BehaviorLessonCandidate
                    ▼
             审批/重复命中/效果评估
                    ▼
             ACTIVE Behavior Memory
                    ▼
              后续 Agent Prompt
```

### 4.3 不变量

1. 日志或证据持久化失败不得把已有执行结果改成 Run 失败。
2. EvidenceBundle 不完整时必须明确记录 `missingEvidence`，PM 不得把“不知道”当成“通过”。
3. PM `APPROVE` 要求 EvidenceBundle 至少为 `READY`，且所有必须验收项都有通过证据。
4. Git diff 必须在本 Run 的 workspace lease 释放前采集，避免混入后续 Run 修改。
5. 原始日志、Git patch 和最终输出都是不可变 artifact；重新提取证据只能创建新 evidence version。
6. prompt 只包含有界摘要和命中片段，完整 artifact 通过 ID 审计和按需查看。
7. 长期记忆必须保留来源 evidence IDs，并能撤销。

## 5. 数据模型

建议使用下一可用数据库版本创建迁移；当前仓库存在重复 V13 文件，实施前先由构建检查确定版本号，预期使用 V14。

### 5.1 `oc_run_artifact`

保存 artifact 元数据，不直接把大正文放在 Run 表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(36) PK | Artifact ID |
| `run_id` | varchar(36), not null | 所属 Run |
| `artifact_type` | varchar(32) | `RAW_LOG/GIT_DIFF/FINAL_OUTPUT/VALIDATION_REPORT` |
| `version` | int | 同类 Artifact 版本，从 1 递增 |
| `state` | varchar(20) | `OPEN/COMPLETE/TRUNCATED/FAILED/EXPIRED` |
| `content_type` | varchar(100) | 如 `application/x-ndjson` |
| `content_encoding` | varchar(20) | `gzip/identity` |
| `storage_backend` | varchar(20) | 第一阶段固定 `DB_CHUNK` |
| `byte_size` | bigint | 压缩前大小 |
| `stored_size` | bigint | 实际保存大小 |
| `frame_count` | bigint | 日志 frame 数，可空 |
| `sha256` | varchar(64) | 完整内容摘要 |
| `redaction_version` | varchar(32) | 脱敏规则版本 |
| `completeness` | varchar(20) | `COMPLETE/TRUNCATED/MISSING` |
| `failure_reason` | text | 写入或固化失败原因 |
| `metadata_json` | text | first/last sequence、Git 元数据等 |
| `expires_at` | timestamp | 原始大对象过期时间 |
| `created_date` | timestamp | 创建时间 |

约束与索引：

- `unique(run_id, artifact_type, version)`；重新生成创建新版本，不覆盖旧正文。
- 索引 `(run_id, artifact_type)`、`expires_at`。
- Artifact 删除只允许由保留策略任务执行；Run、Evidence 和 Review 不级联物理删除。

### 5.2 `oc_run_artifact_chunk`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(36) PK | Chunk ID |
| `artifact_id` | varchar(36), not null | Artifact |
| `sequence_no` | bigint | Chunk 顺序 |
| `first_frame_seq` | bigint | 首 frame 序号 |
| `last_frame_seq` | bigint | 末 frame 序号 |
| `payload` | bytea/blob | gzip 内容 |
| `sha256` | varchar(64) | 单块摘要 |
| `created_date` | timestamp | 创建时间 |

约束：`unique(artifact_id, sequence_no)`。

禁止一条日志一行入库。默认每 64 KiB 或 1 秒 flush 一块，相关阈值配置化。

### 5.3 `oc_run_evidence`

每次提取生成一个不可变 version；`unique(run_id, version)`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(36) PK | Evidence ID |
| `run_id` | varchar(36), not null | Run |
| `version` | int | 证据版本 |
| `schema_version` | varchar(20) | JSON 契约版本 |
| `status` | varchar(20) | `CAPTURING/READY/PARTIAL/FAILED` |
| `completeness` | varchar(20) | `COMPLETE/PARTIAL/MISSING` |
| `summary` | text | 结构化摘要 |
| `process_exit_code` | int | CLI 进程 exitCode |
| `base_commit` | varchar(64) | Run 基线 |
| `head_commit` | varchar(64) | 捕获时 HEAD |
| `changed_files_json` | text | 变更文件和状态 |
| `diff_stat` | text | Git diff stat |
| `command_results_json` | text | 命令、exitCode、结果摘要 |
| `findings_json` | text | finding + evidenceRefs |
| `acceptance_criteria_json` | text | 本次实际验收标准快照 |
| `acceptance_results_json` | text | 每项 `PASSED/FAILED/NOT_VERIFIED` |
| `missing_evidence_json` | text | 缺失项及原因 |
| `artifact_refs_json` | text | RAW_LOG/GIT_DIFF/FINAL_OUTPUT 等 ID |
| `created_date` | timestamp | 创建时间 |

`RunObservation.evidenceData` 只保存如下小引用，不复制大正文：

```json
{
  "schemaVersion": "1",
  "evidenceId": "ev-...",
  "completeness": "COMPLETE",
  "artifactRefs": ["artifact-..."]
}
```

### 5.4 现有表增强

#### `oc_run`

- 复用现有 `exit_code`，从 `CliRunResult.exitCode` 统一写入。
- `summary` 继续保存最终业务输出。
- 可选增加 `latest_evidence_id`，便于详情查询；权威关系仍在 `oc_run_evidence.run_id`。

#### `oc_coding_task`

新增：

- `acceptance_criteria TEXT`：使用现有 StringListConverter。

创建普通任务和 PlanPatch 修订任务时都必须写入。历史任务使用：

```text
CodingTask.acceptanceCriteria
  > ExecutionPlan.planJson 中按 planTaskKey 解析
  > 空列表，同时 Evidence 标记 ACCEPTANCE_CRITERIA_MISSING
```

不在迁移 SQL 中冒险解析历史 plan JSON；由 `AcceptanceCriteriaResolver` 兼容读取，并提供可选后台回填。

#### `PlanPatchOperation`

新增 `acceptanceCriteria`，否则 AMEND/ADD 会再次丢失验收标准。

#### `oc_task_delivery_review`

新增：

- `evidence_id varchar(36)`
- `remediation_brief_json text`
- `feedback_applied_run_id varchar(36)`

`decision_json` 继续保存 PM 原始结构化决定；`remediation_brief_json` 保存经服务端校验和规范化后的下一次 Agent 输入。

### 5.5 `oc_agent_behavior_memory`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(36) PK | 记忆 ID |
| `project_id` | varchar(36) | 项目范围 |
| `agent_name` | varchar(64) | 可空；为空表示所有开发 Agent |
| `task_area` | varchar(32) | frontend/backend/validation/any |
| `category` | varchar(32) | TESTING/DEBUGGING/SCOPE/SECURITY/DELIVERY 等 |
| `fingerprint` | varchar(64) | 归一化规则指纹 |
| `status` | varchar(20) | `CANDIDATE/ACTIVE/DISABLED/REJECTED/EXPIRED` |
| `rule_text` | text | 可直接注入 prompt 的行为规则 |
| `rationale` | text | 原因 |
| `evidence_refs_json` | text | 来源 Evidence/Review |
| `occurrence_count` | int | 独立命中次数 |
| `success_count` | int | 注入后成功次数 |
| `recurrence_count` | int | 注入后同类问题再次发生次数 |
| `confidence` | decimal | 0–1 |
| `first_seen_at` | timestamp | 首次出现 |
| `last_seen_at` | timestamp | 最近出现 |
| `approved_by/approved_at` | varchar/timestamp | 人工批准信息 |
| `expires_at` | timestamp | 可选过期时间 |
| `version` | int | 乐观锁 |

唯一键建议：`(project_id, coalesce(agent_name,''), task_area, fingerprint)`。

### 5.6 `oc_run_behavior_memory`

记录某次 Run 实际注入了哪些长期记忆以及效果：

- `run_id`
- `behavior_memory_id`
- `injected_at`
- `outcome`：`UNKNOWN/HELPFUL/INEFFECTIVE/CONTRADICTED`
- `evaluated_by_review_id`

唯一键：`(run_id, behavior_memory_id)`。

## 6. 结构化证据契约

`RunEvidenceDto`：

```json
{
  "id": "ev-1",
  "runId": "run-1",
  "version": 1,
  "status": "READY",
  "completeness": "COMPLETE",
  "summary": "执行完成，3 条命令中 1 条失败",
  "processExitCode": 0,
  "git": {
    "baseCommit": "abc",
    "headCommit": "def",
    "diffStat": "3 files changed",
    "changedFiles": [
      {"path": "backend/A.java", "status": "MODIFIED"}
    ],
    "diffArtifactId": "artifact-diff"
  },
  "commands": [
    {
      "id": "cmd-1",
      "command": "./gradlew test",
      "exitCode": 1,
      "result": "1 test failed",
      "evidenceRefs": [
        {"artifactId": "artifact-log", "fromSequence": 120, "toSequence": 145}
      ]
    }
  ],
  "findings": [
    {
      "id": "finding-1",
      "severity": "HIGH",
      "criterionId": "AC-2",
      "message": "失败场景未覆盖",
      "evidenceRefs": [
        {"artifactId": "artifact-log", "fromSequence": 130, "toSequence": 138},
        {"artifactId": "artifact-diff", "path": "backend/A.java"}
      ]
    }
  ],
  "acceptance": {
    "criteria": [
      {"id": "AC-1", "text": "构建通过", "required": true},
      {"id": "AC-2", "text": "失败场景测试通过", "required": true}
    ],
    "results": [
      {"criterionId": "AC-1", "status": "PASSED", "evidenceRefs": ["cmd-1"]},
      {"criterionId": "AC-2", "status": "FAILED", "evidenceRefs": ["finding-1"]}
    ]
  },
  "artifacts": [
    {"id": "artifact-log", "type": "RAW_LOG", "completeness": "COMPLETE"},
    {"id": "artifact-diff", "type": "GIT_DIFF", "completeness": "COMPLETE"}
  ],
  "missingEvidence": []
}
```

所有 finding、command 和 acceptance result 都应优先使用稳定 ID 相互引用，禁止只靠自然语言位置描述。

## 7. 日志与证据采集

### 7.1 Runner 日志

引入：

```java
interface RunLogSink {
    void open(String runId);
    void accept(RunLogFrame frame);
    ArtifactFinalizeResult close(String runId);
    void fail(String runId, Throwable error);
}
```

实现：

- `WebSocketRunLogSink`
- `PersistentRunLogSink`
- `CompositeRunLogSink`

`RunLogFrame` 增加 `sequenceNo`。同一 frame 同时进入持久层和 WebSocket，前端可使用 sequence 去重和补洞。

### 7.2 脱敏

持久化前统一处理：

- Authorization/Bearer/token/password/secret/api-key/private-key。
- 常见云凭证和 Git access token。
- 配置化敏感环境变量名。
- 超长单行限制；截断必须带显式标记。
- `.env`、私钥和凭证文件的 diff 正文不得进入 artifact。

脱敏规则必须版本化并有正反例测试。日志查看接口不能返回脱敏前内容。

### 7.3 exitCode

`CliRunResult` 增加 `Integer exitCode`：

- 正常退出保存真实进程 exitCode。
- spawn 失败或超时无法取得 exitCode 时为 null，同时写 failureReason。
- Codex app-server 协议失败与 OS 进程 exitCode 分开表达。
- 验证输出中的 command exitCode 保存在 `commandResults`，不能覆盖进程 exitCode。

### 7.4 Git diff

`RunEvidenceCaptureCoordinator` 必须在 `AgentExecutionService` 释放 workspace lease 前执行：

1. 使用 `Run.baseCommit` 作为基线。
2. 采集当前 HEAD、tracked diff、staged diff 和 untracked files。
3. 生成 changed files、diff stat 和 patch artifact。
4. 排除敏感路径并记录排除项，不得静默伪装为完整。
5. 对 diff artifact 计算 sha256。

如果 baseCommit 缺失或 Git 采集失败，Evidence 进入 `PARTIAL`，并记录 `GIT_DIFF_MISSING`。

### 7.5 Evidence 提取

提取器按来源组合：

- `FinalOutputEvidenceExtractor`：解析 Agent 最终 JSON/文本。
- `ValidationEvidenceExtractor`：提取 commands、exitCode、findings。
- `GitEvidenceExtractor`：生成文件与 diff 证据。
- `AcceptanceEvidenceEvaluator`：将证据映射到实际验收标准。

提取器失败不得删除原 artifact。可对同一 Run 重新提取，生成新 evidence version。

## 8. PM 获取证据与修复方案

### 8.1 审阅时序

```text
Run 得到结果
  -> artifact close
  -> Git capture（lease 内）
  -> Evidence READY/PARTIAL/FAILED
  -> 结算 Run
  -> 创建 TaskDeliveryReview(evidenceId)
  -> PM review
```

不得在 Evidence 仍为 `CAPTURING` 时调用 PM。

### 8.2 PM 输入

`DeliveryReviewInput` 必须包含：

- Requirement 与任务上下文。
- 本次实际 acceptance criteria。
- 完整 `RunEvidenceDto` 的有界版本。
- 每个 failed criterion 对应的日志片段。
- changed files、diff stat 和必要 patch 摘要。
- CLI process exitCode 和 command exitCodes。
- Evidence completeness、missingEvidence。
- 上一次 PM feedback 和本次注入的 behavior memory IDs。

默认 prompt 预算：

- 最终输出摘要：最多 8 KiB。
- Git patch 摘要：最多 12 KiB。
- 命中日志片段总计：最多 16 KiB。
- 不把完整 RAW_LOG artifact 放入 prompt。

### 8.3 PM 输出

PM 必须返回：

```json
{
  "decision": "APPROVE | RETRY | REPLAN | WAIT_HUMAN",
  "summary": "结论",
  "failureCategory": "NONE | TRANSIENT_INFRA | DELIVERY_INCOMPLETE | VALIDATION_FAILED | UPSTREAM_INCOMPLETE | PLAN_DEFECT",
  "rootCauses": ["根因"],
  "findings": [
    {
      "id": "finding-1",
      "severity": "HIGH",
      "criterionIds": ["AC-2"],
      "message": "事实",
      "evidenceRefs": ["ev-1/finding-1"]
    }
  ],
  "remediationBrief": {
    "objective": "本次修复目标",
    "requiredChanges": ["必须修改什么"],
    "prohibitedChanges": ["不能改变什么"],
    "verificationSteps": [
      {
        "command": "./gradlew test",
        "expected": "exitCode=0",
        "criterionIds": ["AC-1", "AC-2"]
      }
    ],
    "completionEvidence": ["命令结果", "关键 diff"]
  },
  "retryReason": "RETRY 时必填",
  "remediationTasks": [],
  "memoryCandidates": [
    {
      "category": "TESTING",
      "rule": "修改该模块后必须运行指定测试",
      "scope": "backend",
      "evidenceRefs": ["ev-1/finding-1"]
    }
  ]
}
```

### 8.4 服务端校验

- `APPROVE`：Evidence 必须 `READY/COMPLETE`，所有 required criteria 为 PASSED，无 HIGH/CRITICAL 未解决 finding。
- `RETRY`：必须有 rootCause、requiredChanges、verificationSteps、retryReason；同一 Agent 和任务范围可以安全完成。
- `REPLAN`：只有当前任务边界、Agent 或 fileScope 无法安全修复时允许；按照现有规则收敛为一个“修复并验证”的编码任务。
- `WAIT_HUMAN`：证据不足、输出不合法、修复上限或需要业务决策时使用。
- 每条 finding 必须有合法 evidenceRef。
- 每个失败 criterion 必须被至少一个 requiredChange 和 verificationStep 覆盖。
- 服务端规范化后保存 `remediation_brief_json`；原始 PM JSON 仍保存到 `decision_json`。

## 9. 下一次 Agent 获取反馈

### 9.1 Prompt 组成

`CodingTaskExecutionService.buildExecutionPrompt` 增加专用 `AgentFeedbackPromptAssembler`，按以下顺序注入：

1. 当前 PRD、计划和任务。
2. 实际验收标准。
3. PM 修复包：
   - source review/run/evidence ID
   - root causes
   - required changes
   - prohibited changes
   - verification steps
   - completion evidence
4. 上一次 Run 的结构化证据摘要和命中日志片段。
5. 当前 Git 工作区差异。
6. 当前任务适用的 ACTIVE behavior memory。
7. 完成输出契约。

禁止仅依赖 RequirementComment 文本传递修复要求。评论用于展示，`TaskDeliveryReview.remediationBriefJson` 才是权威机器输入。

### 9.2 Agent 完成输出契约

开发 Agent 必须在最终输出中返回：

```json
{
  "summary": "做了什么",
  "changes": [{"path": "backend/A.java", "reason": "修复 finding-1"}],
  "commands": [{"command": "./gradlew test", "exitCode": 0, "result": "passed"}],
  "resolvedFindings": ["finding-1"],
  "acceptanceResults": [{"criterionId": "AC-2", "status": "PASSED", "evidence": "cmd-1"}],
  "appliedBehaviorMemoryIds": ["bm-1"],
  "remainingRisks": []
}
```

服务端不因 JSON 内容决定 Run 成败，但 EvidenceExtractor 会据此生成证据；缺字段会降低 completeness，交由 PM 判断。

### 9.3 应用追踪

- 创建下一次 Run 时，把 `feedback_applied_run_id` 回写到 Review。
- 将注入的 behavior memory 写入 `oc_run_behavior_memory`。
- 若下一次 PM 再次发现相同 fingerprint，标记该记忆 `INEFFECTIVE` 并增加 recurrenceCount。

## 10. Agent 长期行为改进

长期改进定义为“可检索、可撤销、可衡量的行为规则记忆”，不是模型训练。

### 10.1 候选产生

来源：

- PM `memoryCandidates`。
- 同类 finding 在不同 Run 重复出现。
- 人工添加。

每个候选必须有：

- 清晰、可执行的 rule。
- 项目/Agent/area 范围。
- 至少一个 Evidence 引用。
- 归一化 fingerprint。

### 10.2 晋升规则

默认策略：

- 单次 PM 输出：`CANDIDATE`，不自动注入所有未来 Run。
- 两个不同 CodingTask/Run 在 30 天内出现相同 fingerprint：confidence 提升，可自动晋升为 `ACTIVE`。
- SECURITY、权限、发布和数据破坏类规则必须人工批准。
- 人工可直接批准、拒绝或禁用。
- 与现有项目规范冲突时保持 `CANDIDATE` 并转人工，不覆盖 agent-memory 文件。

阈值配置化，不能硬编码在 prompt 中。

### 10.3 检索与注入

按以下条件过滤：

- projectId
- assignedAgent
- task area
- fileScope/module
- status=ACTIVE
- 未过期

排序：

1. 强制人工批准规则。
2. 与 fileScope/module 高相关。
3. confidence 与 successCount 高。
4. 最近命中。

每次最多注入 10 条或 4 KiB，超出时记录被预算淘汰的规则 ID。

### 10.4 效果评估

- 规则注入后任务被 APPROVE，且未出现同类 finding：successCount +1。
- 同类 finding 复发：recurrenceCount +1，outcome=INEFFECTIVE。
- PM 明确认为规则错误：outcome=CONTRADICTED，自动降为 CANDIDATE 或 DISABLED。
- 90 天无命中可标记 EXPIRED；人工批准的强制规则不自动过期。

### 10.5 与现有项目记忆关系

- 现有 `WorkspaceMemory` 和 agent-memory 文件继续表示项目事实、规范、模块和设计决策。
- `AgentBehaviorMemory` 表示执行行为经验，单独管理生命周期。
- `DesignContextPromptAssembler` 新增“适用行为记忆”段，但不自动修改工作区中的 agent-memory 文件。
- 经长期验证、确实属于项目规范的记忆，可由人工另行晋升到项目 memory-rules。

## 11. API 契约

遵循现有 Feign API 风格：

### 11.1 Run API

```http
GET /run/findLogFrames?runId={id}&afterSequence={n}&limit={1..1000}
```

返回：

```json
{
  "artifactState": "COMPLETE",
  "completeness": "COMPLETE",
  "nextSequence": 501,
  "hasMore": true,
  "frames": [
    {"sequenceNo": 1, "stream": "stdout", "line": "...", "ts": "..."}
  ]
}
```

```http
GET /run/findEvidence?runId={id}
```

返回最新 `RunEvidenceDto`。

```http
GET /run/downloadArtifact?runId={id}&artifactId={artifactId}
```

只允许下载属于该 Run 的 artifact；响应携带 content type、encoding、sha256 和 completeness。必须复用现有 Run 访问控制，并记录下载审计。

### 11.2 Behavior Memory API

```http
POST /agentBehaviorMemory/findByPage
GET  /agentBehaviorMemory/findOne?id={id}
POST /agentBehaviorMemory/approve
POST /agentBehaviorMemory/reject
POST /agentBehaviorMemory/disable
```

请求体统一为 `{"id":"...","reason":"..."}`。修改状态的接口必须记录操作人、时间和理由。

## 12. 前端

`RunLogDrawer` 调整为：

1. 打开时先调用 `findLogFrames` 分页加载历史日志。
2. 对 RUNNING Run 再订阅 WebSocket；根据 `sequenceNo` 去重并补洞。
3. 明确展示日志 `COMPLETE/TRUNCATED/MISSING`。
4. Evidence 页展示：
   - CLI process exitCode
   - commands 和各自 exitCode
   - acceptance criteria/result
   - findings 和证据跳转
   - changed files、diff stat、artifact 下载
   - PM remediation brief
   - 本 Run 注入的行为记忆及效果
5. 不在浏览器控制台输出 artifact 正文或敏感字段。

前端实现必须遵循项目 `suid` skill 与 `@ead/suid` 组件规范。

## 13. 保留、安全与容量

默认配置建议：

```yaml
onlinecode:
  run-evidence:
    enabled: true
    raw-log-retention-days: 30
    git-diff-retention-days: 90
    max-artifact-bytes-per-run: 52428800
    chunk-bytes: 65536
    flush-interval-ms: 1000
    pm-log-excerpt-bytes: 16384
    behavior-memory:
      auto-promote-distinct-runs: 2
      auto-promote-window-days: 30
      max-prompt-items: 10
      max-prompt-bytes: 4096
```

- 结构化 Evidence、Review 决策和行为记忆随业务审计数据长期保留。
- Raw log 和 Git diff 到期后只删除 chunk，将 artifact 标记为 EXPIRED，保留 hash、大小、状态和引用。
- 超过单 Run 上限时停止保存后续 chunk，artifact 标记 TRUNCATED；不得报告 COMPLETE。
- 数据库容量告警必须覆盖 artifact 表大小、每日增长、截断率和写入失败率。
- 第一阶段使用 DB chunk；后续可实现 `ObjectStorageArtifactStore`，业务层和 API 契约不变。

## 14. 故障与一致性

| 故障 | 系统行为 |
|---|---|
| WebSocket 发送失败 | 不影响持久化；移除慢连接 |
| artifact chunk 写入失败 | 标记 artifact FAILED，Evidence missingEvidence 记录 RAW_LOG_WRITE_FAILED |
| Git diff 采集失败 | Evidence=PARTIAL；PM 不得无证据 APPROVE |
| Evidence extractor 失败 | 保留 artifacts，Evidence=PARTIAL/FAILED，可重新提取 |
| PM 调用失败 | 沿用 WAIT_HUMAN/补偿恢复机制 |
| feedback package 无效 | 不启动 RETRY/REPLAN Run，转 WAIT_HUMAN |
| behavior memory 写入失败 | 不阻断当前任务；记录告警，不丢 PM feedback |
| artifact 到期 | Evidence 保留 hash 和摘要，完整正文不可再下载 |

## 15. 实施阶段与任务

### Phase 1：数据契约与持久层

#### EV-001 数据模型、迁移和 API 契约

- Agent：backend-dev-agent
- 优先级：1
- 依赖：无
- 范围：
  - `backend/sei-online-code-api/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/`
  - `backend/sei-online-code-service/src/main/resources/db/migration/`
- 交付：
  - Artifact、Chunk、Evidence、BehaviorMemory、RunBehaviorMemory 实体/DAO/DTO。
  - CodingTask acceptanceCriteria、PlanPatchOperation acceptanceCriteria。
  - TaskDeliveryReview evidence/remediation 字段。
  - CliRunResult exitCode。
- 验收：
  - 迁移静态测试和实体列定义一致。
  - 唯一约束、索引、TEXT/BLOB 大字段正确。
  - 新旧任务 acceptance criteria resolver 测试覆盖。
  - 不引入 tenant_code。

### Phase 2：原始日志和 Run 证据

#### EV-002 日志持久化与回放

- Agent：backend-dev-agent
- 优先级：2
- 依赖：EV-001
- 范围：
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/`
  - 新增 `service/evidence/`
  - Run API/controller
- 交付：
  - CompositeRunLogSink、脱敏器、gzip chunk writer、历史查询。
  - RunLogFrame sequenceNo。
  - 保留策略与配置。
- 验收：
  - Claude/Codex stdout、stderr、system 顺序可回放。
  - 10 万 frame 测试不产生逐行数据库写入。
  - 密钥、Bearer token、`.env` 内容不出现在 chunk。
  - 写入失败和截断状态可观察且不改变 Run state。

#### EV-003 exitCode、Git diff 与 EvidenceBundle

- Agent：backend-dev-agent
- 优先级：2
- 依赖：EV-001、EV-002
- 范围：
  - `service/agent/`
  - `service/evidence/`
  - `service/memory/CodingTaskChangeCollector`
- 交付：
  - Runner exitCode 贯穿。
  - lease 释放前 Git capture。
  - FinalOutput/Validation/Git/Acceptance extractors。
  - Evidence version 与 Observation 引用。
- 验收：
  - CLI 非零 exitCode 可查询。
  - process exitCode 与 command exitCode 不混淆。
  - 下一个 Run 修改工作区不会改变旧 Evidence。
  - 每项 acceptance criterion 有明确结果或 NOT_VERIFIED。
  - 提取失败可以基于原 artifact 重跑。

### Phase 3：PM 与下一次 Agent

#### EV-004 PM 结构化审阅和修复包

- Agent：backend-dev-agent
- 优先级：3
- 依赖：EV-003
- 范围：
  - `service/agent/PmAgentClient`
  - `service/review/`
  - TaskDeliveryReview tests
- 交付：
  - 新 DeliveryReviewInput/Decision 契约。
  - 有界证据 prompt。
  - RemediationBrief 校验和持久化。
- 验收：
  - PM 能看到 raw log 命中片段、Git diff 摘要、process/command exitCode 和实际验收标准。
  - Evidence 不完整时 APPROVE 被拒绝。
  - finding 无 evidenceRef、失败验收项无修复/验证覆盖时拒绝决策。
  - REPLAN 仍收敛为一个修复并验证任务。

#### EV-005 下一次 Agent 反馈注入

- Agent：backend-dev-agent
- 优先级：4
- 依赖：EV-004
- 范围：
  - `CodingTaskExecutionService`
  - 新增 `AgentFeedbackPromptAssembler`
  - prompt tests
- 交付：
  - RETRY/REPLAN prompt 注入权威 remediation brief。
  - structured completion output contract。
  - feedback_applied_run_id 追踪。
- 验收：
  - 下一次 Agent 收到 rootCause、requiredChanges、verificationSteps、failed criteria 和证据摘要。
  - 不依赖评论 metadata 传递机器指令。
  - prompt 不包含完整原始日志并受预算限制。
  - 同一 feedback 只关联一次目标 Run，重试幂等。

### Phase 4：长期行为记忆

#### EV-006 行为记忆生命周期和效果评估

- Agent：backend-dev-agent
- 优先级：5
- 依赖：EV-004、EV-005
- 范围：
  - 新增 `service/behavior/`
  - `DesignContextPromptAssembler`
  - Behavior Memory API/controller
- 交付：
  - 候选去重、晋升、人工状态变更、检索和 prompt 注入。
  - Run-memory 关联及效果反馈。
- 验收：
  - 单次普通 finding 不会直接变成 ACTIVE。
  - 两个独立 Run 重复命中可按配置晋升。
  - 安全/权限类必须人工批准。
  - ACTIVE 规则按项目/Agent/area/fileScope 检索，超预算稳定截断。
  - 规则可禁用，禁用后不再进入新 prompt。
  - 同类问题复发会降低效果状态，不无限强化错误规则。

### Phase 5：可视化与系统验收

#### EV-007 Run 日志、证据和反馈 UI

- Agent：frontend-dev-agent
- 优先级：6
- 依赖：EV-002、EV-003、EV-004、EV-006
- 范围：
  - `frontend/src/services/`
  - `frontend/src/pages/OnlineCode/components/RequirementWorkspace/RunLogDrawer.jsx`
  - 相关样式与测试
- 交付：
  - 历史日志 + 实时日志无缝展示。
  - Evidence、PM 修复包和行为记忆展示。
- 验收：
  - 结束 Run 重新打开仍能查看日志。
  - sequence 去重、分页和截断状态正确。
  - finding 可以定位到日志片段或 diff 文件。
  - 大日志不一次性加载，不泄露敏感值。

#### EV-008 端到端、故障注入与安全验收

- Agent：test-agent
- 优先级：7
- 依赖：EV-001 至 EV-007
- 范围：
  - 后端/前端相关测试
  - `docs/bugs/`（仅发现问题时）
- 验收场景：
  1. Agent 输出缺陷结果但 Run 成功，PM RETRY，下一 Run 按修复包修复并验证。
  2. validation command exitCode 非零，PM 不得 APPROVE。
  3. 日志包含 token，持久化和 API 返回均已脱敏。
  4. Git diff 捕获后启动下一 Run，旧证据保持不变。
  5. artifact 写失败，Run 状态不被污染，PM 收到缺失证据。
  6. 同类 finding 两次出现，生成并晋升行为记忆；禁用后不再注入。
  7. 50 MiB 日志触发配置上限，状态为 TRUNCATED，UI 和 PM 都不得显示 COMPLETE。

## 16. 发布顺序

1. 部署数据库迁移和只读 API，功能开关默认关闭。
2. 开启 artifact dual-write：继续 WS，同时持久化；PM 暂不消费新 Evidence。
3. 观察 3–7 天写入失败率、容量、脱敏命中、单 Run 大小和性能。
4. 开启 EvidenceBundle 和 UI 历史回放。
5. 开启 PM 新证据输入与 RemediationBrief 校验。
6. 开启下一次 Agent feedback 注入。
7. 行为记忆先以 candidate-only shadow 模式运行，确认候选质量后再开启自动晋升。

建议功能开关：

- `onlinecode.run-evidence.enabled`
- `onlinecode.run-evidence.pm-consume-enabled`
- `onlinecode.run-evidence.feedback-injection-enabled`
- `onlinecode.run-evidence.behavior-memory-shadow-enabled`
- `onlinecode.run-evidence.behavior-memory-auto-promote-enabled`

## 17. 指标与告警

- `run_artifact_write_failure_rate`
- `run_artifact_truncated_rate`
- `run_evidence_complete_rate`
- `run_evidence_capture_latency`
- `pm_review_missing_evidence_rate`
- `pm_decision_invalid_rate`
- `feedback_applied_rate`
- `same_finding_recurrence_rate`
- `behavior_memory_candidate_to_active_rate`
- `behavior_memory_ineffective_rate`
- artifact 数据库日增长和剩余容量

发布门槛建议：

- artifact 写入成功率 ≥ 99.9%。
- Evidence COMPLETE 率 ≥ 99%，其余必须明确 PARTIAL 原因。
- PM 的 failed criterion 修复覆盖率 = 100%。
- feedback_applied_rate = 100%（对 RETRY/REPLAN）。
- 敏感信息脱敏回归测试 100% 通过。

## 18. 风险与控制

| 风险 | 控制 |
|---|---|
| 日志导致数据库快速增长 | gzip chunk、单 Run 上限、TTL、容量告警、可替换 ArtifactStore |
| 敏感信息落库 | 入库前脱敏、敏感路径排除、规则版本、下载审计 |
| Evidence 与工作区不一致 | lease 释放前 capture、artifact hash、不可变 version |
| PM 被日志噪声误导 | 只传结构化证据和命中片段 |
| PM 生成不可执行修复方案 | 服务端覆盖校验、失败转 WAIT_HUMAN |
| 长期记忆被单次错误污染 | candidate 状态、独立 Run 阈值、人工批准和可撤销 |
| 错误记忆长期强化 | recurrence/effectiveness 评估、自动降级、过期 |
| 新链路影响 Run 简单状态 | Evidence 状态独立，Run 状态不依赖持久化成功 |
| 历史任务没有验收标准 | Resolver 回退 planJson，缺失时显式标记 |

## 19. 完成定义

全部满足才视为完成：

1. 任意新 Run 结束后可以回放脱敏日志，并验证 chunk hash 和 completeness。
2. Run Evidence 同时包含 raw-log ref、Git diff、process exitCode、command exitCodes 和实际验收标准。
3. PM 输入不再传空 acceptance criteria，并能用 evidenceRefs 引用事实。
4. PM RETRY/REPLAN 必须产生经服务端校验的 RemediationBrief。
5. 下一次 Agent 的实际 prompt 可证明包含该 Brief、失败验收项和验证步骤。
6. 同一次失败不再只通过一句评论 summary 流转。
7. 行为记忆具备 candidate、active、disabled、expired 生命周期以及来源证据。
8. 可证明 ACTIVE 记忆进入了适用 Agent prompt，并能跟踪是否复发。
9. 日志/证据失败不改变 Run 的简单成功语义，但会阻止无证据 APPROVE。
10. 后端测试、前端测试、构建、脱敏安全测试、容量测试和端到端修复闭环全部通过。
