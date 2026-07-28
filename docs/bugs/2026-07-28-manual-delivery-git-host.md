# 手动交付上传错误使用全局 GitLab Host

**日期**：2026-07-28  
**状态**：已修复代码层 host 传递问题  
**影响**：手动或自动交付在上传仓库变更、读取已提交分支 HEAD 时，可能访问错误的 GitLab 实例。

## 现象

手动提交交付物失败：

```text
UnknownHostException: rddgit.changhong.com
通过 Git API 上传仓库变更失败:
project=product-v7/sei/sei-eai-mono,
branch=feature/REQ-0002
```

## 根因

`RequirementDeliveryService` 已从项目 `Project.gitUrl` 解析出同一个
`GitApi.RepositoryTarget(host, projectPath)`，MR 操作也使用其中的 host。

但仓库上传 `GitApi.upload` 和幂等恢复时的 `GitApi.getBranchHead` 只接收
`projectPath`，内部调用 `client(null)`，因而退回全局 `oc.gitlab.host`。
这会导致一次交付的上传和 MR 操作访问不同的 GitLab host。

`UnknownHostException` 还表示实际被选中的 host 在应用运行环境中无法完成 DNS
解析。代码修复可避免选错全局 host；如果项目 `gitUrl` 本身仍指向
`rddgit.changhong.com`，部署环境仍需配置可解析该内网域名的 DNS。

## 修复

- `GitApi.upload` 改为接收完整 `RepositoryTarget`，使用 `target.host()` 创建客户端。
- `GitApi.getBranchHead` 同样接收完整 `RepositoryTarget`。
- `RequirementDeliveryService` 在上传和幂等恢复路径统一传递同一个交付目标。
- 上传和分支查询异常增加 `host`，便于区分配置错误和 DNS 故障。

## 验证

- `GitApiTest`：验证上传与分支 HEAD 查询使用项目仓库 host。
- `RequirementDeliveryServiceTest`：通过。
- 定向测试命令成功：

```bash
./gradlew :sei-online-code-service:test \
  --tests com.changhong.onlinecode.service.GitApiTest \
  --tests com.changhong.onlinecode.service.RequirementDeliveryServiceTest \
  --no-daemon
```

- 完整服务测试共执行 599 个，4 个既有失败：
  - `CodexRunnerRealCodexTest`：真实 Codex 调用 180 秒超时。
  - `ProgressLedgerMigrationStaticTest`：3 个迁移链静态检查失败。

上述失败不涉及本次修改的 Git 交付代码或测试。

## 部署侧检查

在实际应用容器或 Pod 内执行：

```bash
getent hosts rddgit.changhong.com
curl -I --connect-timeout 5 http://rddgit.changhong.com
```

若域名无法解析，应修复 Docker/Kubernetes DNS 转发或企业内网 DNS 配置，不应在
业务代码中硬编码 IP。
