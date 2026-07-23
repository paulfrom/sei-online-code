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

### POST `/requirement/{id}/mr/submit`

手动提交当前已完成交付物。只接受当前 loop 最新且状态为 `ACCEPTED` 的 ExecutionPlan，复用幂等 push/MR 交付链路。成功时创建或更新 MR；失败时 Requirement 进入 `WAITING_HUMAN` 并记录 `MR_FAILED`。

原 `/requirement/{id}/mr/retry` 保留兼容，并委托相同服务方法。

## 前端

- 项目设置增加“工作区基线分支”和“交付目标分支”。
- 需求交付页显示工作区状态，并提供“刷新工作区”和“手动提交交付物”。
- 手动提交按钮仅在存在已验收计划且当前未处于 `DELIVERING` 时启用；后端仍作为最终状态门禁。

## 非目标

- 不自动 reset、rebase 或合并基线分支。
- 不自动合并 MR，不直接部署。
- 不删除现有自动交付与 MR 重试能力。
