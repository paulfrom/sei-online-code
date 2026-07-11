# Workspace Memory 实施计划落地审计报告

## 1. 审计对象

- 计划文档：`docs/plan/WORKSPACE-MEMORY-IMPLEMENTATION-PLAN.md`
- 审计范围：Phase 1～Phase 4 后端、前端、数据库迁移、记忆扫描、上下文注入、设计校验、CodingTask 回写及相关测试
- 审计方式：对照计划原文检查实际实现，不以类、接口或测试文件是否存在作为唯一完成标准，同时检查数据正确性、优先级闭环、降级行为和测试有效性

## 2. 整体结论

四阶段主链路已经真实落地，未发现整阶段空壳或完全缺失：数据库迁移、核心实体与 DAO/DTO、枚举、seed 模板和 bootstrap、扫描到 DB 再到 `platform-memory` 与前端展示、PRD/概览设计/详细设计/CodingTask 的 context 注入、confirm 拦截、CodingTask 后回写、任务隔离及 focused tests 均有可验证实现，且不少关键代码包含 WHY 注释。

但“主链路存在”不等于“计划完整达标”。当前扣分主要集中在语义正确性和工程质量，并呈现出高度一致的系统性短板：冲突检测覆盖面过窄、规范覆盖关系未闭环、所谓增量回写实为全量扫描且无法正确处理删除、REBUILD 降级判断容易误判、设计记忆校验仍以占位式启发规则为主。

综合判断：**整体约 90% 认可原审计结论。Phase 2～4 的主要问题成立；Phase 1 中关于两个枚举偏差的判断不成立，已在本报告中纠正。**

## 3. 跨阶段系统性缺口

### 3.1 冲突检测依赖固定技术栈关键词字典

**结论：成立，高优先级。**

`WorkspaceMemoryScannerService.CONTRADICTION_PAIRS` 与 `CodingTaskMemoryUpdateAssembler.CONTRADICTION_PAIRS` 使用同类硬编码规则。当前 Map 有 8 个条目，实际约覆盖 5 组概念冲突，且部分只做单向映射。

当前能力主要覆盖：

- React / Vue
- Java / Kotlin
- Gradle / Maven
- antd / suid
- UmiJS / Next.js

存在以下实质限制：

- 无法识别 Java 17 / Java 21、PostgreSQL / MySQL 等版本或产品差异；
- 无法识别接口名、模块名、数据模型和职责边界冲突；
- 无法识别“设计采用 REST、实现改为 RPC”等架构语义冲突；
- 所有命中统一设置为 `HIGH`，处理方式统一设置为 `clarify`，没有 MEDIUM/LOW 分级；
- 初始化扫描和 CodingTask 回写各维护一份相近逻辑，存在规则漂移风险。

计划允许第一版使用确定性规则并在后续替换为 LLM，但当前覆盖范围仍是最大的实质功能缺口，影响 Phase 2 ConflictFinding 质量和 Phase 4 Conflict Updates 可信度。

### 3.2 `MemoryNormClaim.overrides` 只建模、未形成覆盖闭环

**结论：成立，高优先级。**

`MemoryNormClaim` 已定义 `overrides` 字段，但当前实现没有为其赋值，也没有执行覆盖消解。

`buildWorkspaceNorms` 仅按 claim type 分桶：

- 没有按 priority 排序；
- 没有剔除被高优先级 claim 覆盖的低优先级 claim；
- P0 项目自维护记忆可能与 P5S seed 同时出现在最终聚合中；
- “seed 仅作默认基线”和“高优先级覆盖低优先级”只在部分冲突检测门槛中间接体现，数据层本身未闭环。

该问题影响 Phase 2 WorkspaceNorms 的确定性，也会传递到 DesignBasis、prompt 注入和设计校验。

### 3.3 CodingTask 增量回写实际执行全量扫描，删除文件会留下旧事实

**结论：成立，高优先级数据正确性问题。**

`MemoryJobExecutor.executeCodingTaskUpdate` 在收集变更后仍调用：

```java
scannerService.scan(project.getId(), worktreePath)
```

该调用扫描整个 worktree，因此性能上没有获得真正的增量收益。

随后 `CodingTaskMemoryUpdateAssembler.mergeRealityClaims` 以旧 RealityClaim 为基础，按 `source` 覆盖本次扫描结果，但不会移除本次扫描中已不存在的 source。与此同时，`CodingTaskChangeResult` 只提供 `changedFiles`，没有 A/M/D 或 rename 状态。

由此会产生：

- 删除文件后旧 RealityClaim 永久残留；
- 文件重命名可能同时保留旧路径和新路径；
- WorkspaceSnapshot 和 source fingerprints 可能继续包含失效事实；
- “代码现状”随多轮回写逐渐失真。

这不仅是性能问题，更是 Phase 4 数据正确性缺陷。

### 3.4 REBUILD 降级规则容易误判且未配置化

**结论：成立，高优先级。**

`MemoryJobExecutor.CRITICAL_PATHS` 包含裸路径片段 `/main/`。常规 Java 工程中的大量文件位于 `src/main/java`，因此修改三个普通后端文件就可能满足 `criticalHits >= 3`，误降级为全量 REBUILD。

同时存在以下问题：

- 关键路径集合硬编码；
- `criticalHits >= 3` 阈值硬编码；
- 只做字符串 `contains`，没有区分构建入口、业务源码和框架入口；
- 真正的入口类如果不包含已列路径片段，反而可能不降级。

这与计划中扫描和降级策略应保持可配置、可解释的方向不一致。

### 3.5 `DesignMemoryValidationService` 多项校验仍是占位式启发规则

**结论：成立，高优先级。**

当前六类校验中，多项没有真正使用结构化上下文完成比对：

- `checkForbiddenChoices` 只硬编码识别 Vue 和 antd；
- `checkReusedModules` 只判断 snapshot JSON 是否为空，没有解析“声称复用的模块”并与实际模块/API 对照；
- `checkSourceBackedImpact` 只检查正文中是否包含 `/` 或 `\`，没有与 `RealityClaim.source` 比对；
- `checkHighSeverityConflicts` 使用 conflict summary 前 40 个字符做 substring 匹配，容易产生误报和漏报；
- required section 校验依赖关键词存在，不验证章节内容是否有效。

`DesignMemoryValidationServiceTest` 当前主要覆盖“遗漏 HIGH 冲突导致 FAILED”场景，缺少以下路径：

- PASSED；
- WARNING；
- forbidden choice；
- 模块复用有/无依据；
- source-backed impact；
- 上下文引用；
- malformed conflict JSON；
- 不同文档类型的必填章节。

## 4. 分阶段审计结果

### 4.1 Phase 1：记忆基础设施

Phase 1 主体实现完整，包括 V24 迁移、核心实体/DAO/DTO、seed 模板、bootstrap、归档后补齐策略、front matter、MemoryJob 投递与项目工作区 ready 后 afterCommit 初始化。

原审计中以下两条枚举偏差不成立：

1. `MemoryJobStatus`：计划原文要求 5 个值，实际实现也是 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`，完全一致，不缺第六个状态。
2. `MemoryJobTriggerSource`：计划原文要求 9 个值，并明确包含 `VALIDATION`；实际实现与计划完全一致，不属于额外枚举。

因此 Phase 1 不应因枚举口径扣分。

### 4.2 Phase 2：WorkspaceMemory 生成

除第 3.1、3.2 节所述系统性问题外，还有以下显著缺口。

#### 4.2.1 `requirement-conflict-report.md` 仍为空骨架

WorkspaceMemory 写 `platform-memory` 时，`PlatformMemoryWriterService` 对 `requirement-conflict-report.md` 只调用 skeleton renderer。虽然 `RequirementDesignContextService` 会在 DB 中生成 HIGH/MEDIUM/LOW 分级 JSON，但未发现把实际 requirement conflict report 渲染回 latest Markdown 的完整链路。

当前 decision-log 中只有计数或摘要性质的信息，不能替代计划要求的分级明细、Open Questions、Assumptions 和 Source Files。

#### 4.2.2 Freshness Checker 存在死代码和重复全量扫描

`WorkspaceMemoryFreshnessChecker` 定义了三个 `compute*Fingerprint` 私有方法，但 `check` 实际重新构造 `WorkspaceMemoryScannerService` 并全量扫描。这些私有方法当前没有参与判断。

影响包括：

- 不必要的全量解析和 claim 构造；
- Scanner 依赖通过 `new` 创建，绕过 Spring 管理；
- 类内保留两套 fingerprint 计算路径，增加维护歧义。

#### 4.2.3 构建配置仅作为 RealityClaim

`build.gradle`、`pom.xml`、`package.json` 等会作为 `build_config` RealityClaim 进入代码现状，但没有显式作为“项目规范来源”参与 NormClaim 建模。

这属于轻微口径差异：构建配置首先是代码事实，但其中的运行时版本、构建工具和依赖约束也可能承担规范作用，当前没有结构化区分。

### 4.3 Phase 3：RequirementDesignContext 接入文档生成

#### 4.3.1 当前模块相关补扫摘要未实现

计划要求 DetailedDesign prompt 注入同一版 RequirementDesignContext 和“当前模块相关补扫摘要”。当前 `RequirementDesignContextService.prepare` 只接受 `requirementId`，没有 `moduleId` 维度。

`DetailedDesignAgentService` 虽然把当前模块标题和 ID 拼入 prompt，但 `DesignContextPromptAssembler` 仍渲染 requirement 级 snapshot，不会按当前模块裁剪 RealityClaim。

此外，`RequirementDesignContextService.matchesKeywords` 在未提前匹配时最终无条件返回 `true`，导致所谓需求关键词过滤很可能退化为全量 claim。该问题比“没有 moduleId 过滤”更严重，会增加上下文噪声并削弱 source-backed 设计质量。

#### 4.3.2 项目自维护记忆 prompt 内容缩水

`DesignContextPromptAssembler.renderAgentMemory` 只输出：

- WorkspaceMemory ID；
- RequirementContext ID 和版本。

没有把 `agent_memory_markdown` 正文真正拼入 prompt。`renderNorms` 也只要求模型查看 DesignBasis，没有独立注入 WorkspaceNorms 的结构化内容。

因此“项目自维护记忆优先级最高”在 prompt 中主要停留在提示语和间接引用，没有完整携带原始项目记忆。

#### 4.3.3 前端状态条未展示真实 validation finding

`DesignContextStatusBar` 已支持 `validationMessage`，但 PRD、Overview、Detailed 三个 Panel 调用时没有透传该字段。

结果是 WARNING/FAILED Alert 只能展示默认兜底文案，无法向用户展示真实 finding 或具体修复建议。

#### 4.3.4 AgentService 测试没有证明真实 prompt 注入

PRD、Overview、Detailed、CodingTask 相关 AgentServiceTest 直接 mock `DesignContextPromptAssembler`，主要证明“调用发生”，无法证明真实 prompt 中包含：

- 项目自维护记忆；
- NormClaim；
- RealityClaim；
- ConflictFinding；
- DesignBasis；
- 当前模块相关摘要。

需要增加至少一个不 mock assembler 的集成式 prompt 组装测试。

### 4.4 Phase 4：CodingTask 后记忆回写

除第 3.1、3.3、3.4 节所述问题外，还存在以下缺口。

#### 4.4.1 增量分析异常没有按计划降级 REBUILD

当前只有以下情况会显式降级：

- base WorkspaceMemory 不存在或不是 CURRENT；
- 变更采集失败；
- 变更文件数或关键路径命中降级规则。

但以下异常会进入 `execute` 的统一 catch 并调用 `markFailed`，而不是降级投递 `MEMORY_REBUILD`：

- `scannerService.scan` 异常；
- `updateAssembler.assemble` 异常；
- `createNewVersionFromScan` 返回失败或抛出异常。

这不符合计划 §16.5“增量分析失败 → 降级 REBUILD”的明确口径。

## 5. 建议整改优先级

### P0：数据正确性

1. 为 CodingTaskChangeResult 增加 A/M/D/R 状态，删除和重命名时移除旧 RealityClaim、snapshot source 和 fingerprint。
2. 修复 `/main/` 导致的 REBUILD 误判，并将关键路径和阈值配置化。
3. 修复 requirement 相关 claim 过滤无条件返回 true 的逻辑。
4. 明确定义并实现 NormClaim priority、overrides 和 seed fallback 的覆盖算法。

### P1：功能可信度

1. 抽取统一 ConflictDetectionService，避免初始化扫描和回写各维护一份字典。
2. 第一阶段先增加结构化、可扩展的确定性规则和严重度分级，再评估引入 LLM 语义判定。
3. 将 DesignMemoryValidationService 改为解析结构化 NormClaim、RealityClaim、module 和 conflict id 后比对。
4. 实现 moduleId 级 DetailedDesign context 裁剪或补扫。
5. 把真实 agent-memory 和 WorkspaceNorms 内容注入 prompt，而不只输出 ID。

### P2：展示、性能与测试

1. 渲染真实 `requirement-conflict-report.md` latest 内容。
2. 前端透传 validationMessage，并展示 finding 列表及 suggestedAction。
3. 清理 Freshness Checker 死代码，避免为了比较 fingerprint 构建完整 claim 集合。
4. 增加不 mock assembler 的真实 prompt 注入测试。
5. 扩充设计校验 PASSED/WARNING/FAILED 及全部校验分支测试。

## 6. 建议测试用例

### 6.1 冲突检测

- Java 17 与 Java 21 版本冲突；
- PostgreSQL 与 MySQL 数据库冲突；
- REST 设计与 RPC 实现冲突；
- MEDIUM/LOW 分级和不同 recommendedHandling；
- 初始化扫描与 CodingTask 回写使用同一规则服务。

### 6.2 NormClaim 覆盖

- P0 与 P5S 同 type 冲突时只保留 P0 生效；
- `overrides` 指定 claim 后，被覆盖项不进入有效 WorkspaceNorms；
- 空项目只存在 seed 时 seed 正常生效；
- seed 与代码事实不一致时保留 RealityClaim 并生成 ConflictFinding。

### 6.3 CodingTask 回写

- 新增文件产生新 RealityClaim；
- 修改文件覆盖同 source 旧 claim；
- 删除文件移除旧 RealityClaim；
- 重命名文件删除旧 source、加入新 source；
- 增量 scanner/assembler 异常时投递 MEMORY_REBUILD；
- 三个普通 `src/main/java` 文件变更不触发 REBUILD；
- 构建入口、路由根和迁移大量变化正确触发 REBUILD。

### 6.4 设计上下文与校验

- DetailedDesign 只注入当前 moduleId 相关 reality；
- prompt 中包含 agent-memory 正文，而不只是 ID；
- ForbiddenChoice 可识别任意结构化禁止项；
- 声称复用不存在模块时 FAILED/WARNING；
- 影响文件必须命中真实 `RealityClaim.source`；
- HIGH conflict 通过 conflict id 或结构化 resolution 明确处理；
- PASSED、WARNING、FAILED 三种结果均有覆盖；
- 前端展示真实 validation finding 和 suggestedAction。

## 7. 最终审计意见

本次实现可以认定为“四阶段主链路已落地”，不应评价为空壳或大面积缺失。但当前仍存在会影响记忆真实性、降级准确性和设计校验可信度的系统性问题，因此不建议直接认定为计划完全验收通过。

建议验收结论为：

> **有条件通过。** Phase 1 基础设施和四阶段端到端链路通过；将“删除文件导致 RealityClaim 残留”“REBUILD `/main/` 误判”“NormClaim 覆盖未闭环”“requirement/module 过滤失效”列为上线前整改项，其余语义冲突增强、完整校验和前端 finding 展示进入紧随其后的质量迭代。
