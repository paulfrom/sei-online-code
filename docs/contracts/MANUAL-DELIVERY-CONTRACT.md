# 手动工作区刷新与交付契约

## 目标

当当前需求的执行计划已经验收完成时，用户可以主动检查需求工作区并手动提交现有交付物。项目保存工作区基线分支和交付目标分支，避免继续依赖平台级隐式默认值。

## 项目配置

- `workspaceBaseBranch`：需求工作区的更新基线分支，默认 `main`。本期用于记录和展示；手动刷新不执行可能覆盖成果的 reset/rebase。
- `deliveryTargetBranch`：创建或更新 GitLab MR 时使用的目标分支。项目未配置时回退平台 `oc.gitlab.target-branch`。

## API

### POST `/requirement/{id}/workspace/refresh`

重新解析需求工作区并读取 Git 实际状态，持久化 `RequirementWorkspace.branchName/currentHead/snapshotVersion/lastProgressAt`。

返回 `RequirementWorkspaceStatusDto`：

- `workspacePath`
- `branchName`
- `baseBranch`
- `deliveryTargetBranch`
- `baseCommit`
- `currentHead`
- `dirty`
- `changedFiles`
- `refreshedAt`

若需求不存在、工作区无法解析或存在活动写租约，返回业务失败。

### POST `/requirement/{id}/workspace/sync`

从远端更新项目配置的 `workspaceBaseBranch`（默认 `main`），更新对应本地基线分支，随后将基线合并到需求工作区当前分支。该操作不切换需求分支，且要求工作区干净、没有活动写租约。合并冲突时自动中止本次 merge 并返回失败。

### POST `/requirement/{id}/mr/submit`

手动提交当前已完成交付物。只接受当前 loop 最新且状态为 `ACCEPTED` 的 ExecutionPlan，使用需求工作区当前所在分支作为 source branch，不切换或限制分支；随后提交未提交修改并复用幂等 push/MR 交付链路。成功时创建或更新 MR；失败时 Requirement 进入 `WAITING_HUMAN` 并记录 `MR_FAILED`。

原 `/requirement/{id}/mr/retry` 保留兼容，并委托相同服务方法。

## MR 合并与评论路由

- Requirement 独立记录 `deliveryMrIid/deliveryMrStatus/deliveryMergedAt/deliveryMergeCommitHash`。
- MR 合并代表当前 Loop 已交付，Requirement 进入 `WAITING_FEEDBACK`，并不等于整个需求完成。
- 用户在已交付需求上发送评论时，系统先通过 GitLab API 刷新 MR 权威状态。
- MR 尚未合并：评论保留在当前 Loop，由 PM 发起当前 Loop 的增量计划修订。
- MR 已合并：系统必须先执行工作区基线同步；成功后才创建新的 `CHANGE_REQUEST` Loop。
- 基线同步失败或冲突：需求进入 `WAITING_HUMAN`，保留原 Loop，不发布新 Loop 事件。

## 需求完成判定

### POST `/requirement/{id}/confirmCompletion`

“完成需求”是用户的显式确认动作，不由 ExecutionPlan 完成或 MR 合并自动触发。后端逐项校验：

- 当前 Loop 的 `automationStatus` 为 `COMPLETED`；
- 不存在待处理或失败的计划修订；
- 不存在 `RUNNING` 状态的 Run；
- 通过 GitLab API 查询到当前交付 MR 的权威状态为 `MERGED`；
- 完成前成功更新工作区基线分支，并将其合并到当前需求分支。

全部通过后 Requirement 进入 `COMPLETED` 并记录 `REQUIREMENT_COMPLETED`。完成态不删除工作区，以支持后续重新打开；完成态拒绝直接追加评论。

### POST `/requirement/{id}/reopen`

仅 `COMPLETED` 需求可重新打开。操作将 Requirement 恢复为 `WAITING_FEEDBACK` 并记录 `REQUIREMENT_REOPENED`，但不会立即创建 Loop。用户发送下一条评论时，系统按“MR 已合并”路径再次同步基线，然后创建新的 `CHANGE_REQUEST` Loop。

## 前端

- 项目设置增加“工作区基线分支”和“交付目标分支”。
- 需求交付页显示工作区状态，并提供“刷新工作区”和“手动提交交付物”。
- 需求交付页提供“同步主分支”，手动执行与新 Loop 前置步骤相同的 fetch + merge 操作。
- 需求交付页在当前 Loop 已交付后提供“完成需求”；完成态提供“重新打开需求”。
- 完成态评论输入被禁用，避免绕过重新打开动作。
- 手动提交按钮仅在存在已验收计划且当前未处于 `DELIVERING` 时启用；后端仍作为最终状态门禁。

## 非目标

- 不自动 reset、rebase 或合并基线分支。
- 手动提交不把工作区强制切换到系统生成的 `feature/<requirement>` 分支。
- 不自动合并 MR，不直接部署。
- 不删除现有自动交付与 MR 重试能力。
