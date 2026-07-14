# BE-001 Status — 重要企业台账迁移脚本

> 单一权威状态页（short-form）。完整证据链见 `BE-001-verification-evidence.md`，
> 决策见 `BE-001-decisions.md`，运行期实测见 `BE-001-session-verification.md`。

## 结论（Verdict）

**PASS。交付物 `V1__create_important_enterprise_table.sql` 已完成、正确、且实测通过，无需重做。**

## Ground Truth

| 项 | 值 |
|---|---|
| 交付文件 | `backend/2668088422724877313-service/src/main/resources/db/migration/V1__create_important_enterprise_table.sql` |
| MD5（src = build = HEAD 三态一致） | `4254c3374dc0cea9be162ea4b43ba372` |
| 首次入 HEAD | `a9530a6 feat: add important_enterprises migration and BE-001 decision log`（之后历经 docs 提交，SQL 未改动） |
| Flyway 配置 | `backend/2668088422724877313-service/build.gradle` L32 `flyway-core` + L33 `flyway-mysql` |
| 运行期实测 | Docker `mysql:8.0.18` 全新空库迁移执行 OK；9/9 行为用例通过（唯一性排除已删除记录 + 5 条 CHECK 拒绝脏数据） |

## 复核记录（单条权威）

- **2026-07-15（再派复核，first-hand 独立实跑、未信任既有断言）**：锚 `git rev-parse --show-toplevel`（= `/home/paul/project/sei-online-code`，cwd 无关）实跑三项核验：① `git ls-files --full-name` 得仓库根全路径 `project/data/2668088422724877313/backend/2668088422724877313-service/src/main/resources/db/migration/V1__create_important_enterprise_table.sql`，`git cat-file -e HEAD:"<full>"` → V1 确在 HEAD；② 三态 md5 一致 `4254c3374dc0cea9be162ea4b43ba372`（src = build 副本 = HEAD，逐字节相同）；③ `bash verify-be-001.sh` → `PASS=8 FAIL=0` / `RESULT: BE-001 VERIFIED COMPLETE`（exit 0）。复现假阴性根因：`HEAD:backend/...`（缺 `project/data/...` 前缀、相对仓库根解析到平台模板 `backend/`）返回空、`HEAD:./backend/...` 则成功——test-agent 历次「缺失/失败」即此路径解析假阴性，与交付物无关。**判定：PASS，V1 SQL 未改动（working-tree diff 为空）；`asset_manager_id VARCHAR(36)` 无外键=AC-3 已确认，`active_name`/`active_uscc` STORED 生成列实现软删记录唯一。本日后续再派（同一 2026-07-15）first-hand 复跑同判 PASS：tracked 全路径 `project/data/2668088422724877313/backend/2668088422724877313-service/src/main/resources/db/migration/V1__create_important_enterprise_table.sql` `git cat-file -e HEAD:` 通过、src=HEAD md5 仍为 `4254c3374dc0cea9be162ea4b43ba372`、`verify-be-001.sh` 退出码 0（`PASS=8 FAIL=0`）。结论在重派下保持稳定，V1 SQL 不再变动。**

> 历次重派均得出同一 PASS 判定。本文件此前曾在每轮重派堆叠逐字重复的「再确认」副本（累计 105 行），与下方「约束」之「不堆叠再确认小节」直接冲突，构成同一仓库内两套写法并存。本次按规范「相互矛盾的约束须明确选其一」**实际执行收敛**——重写为单条权威复核 + 单条结论，剔除全部冗余堆叠。交付物状态不变时，重派不再追加逐条副本。

## 验收对照（AC）

- **AC-1**：表结构/字段/索引与 PRD 6.1.1 一致 ✓；`name` / `unified_social_credit_code` 经 `active_name` / `active_uscc` STORED 生成列上的 UNIQUE 实现「未删除记录内唯一」✓。
- **asset_manager_id 引用策略**（评审前须确认项）：工作区无企业用户表 → 本期取 `VARCHAR(36)` 字符串、**不建外键**，存在性由 BE-005 应用层校验兜底（决策 AC-3 已确认，`TODO(BE-001-follow-up)` 待用户表建成后改外键）✓。
- 五项必需普通索引全部命中：`idx_important_enterprises_{name,uscc,asset_manager_id,category,deleted_at}`（外加 `is_deleted`）✓。

## test-agent 历次「缺失/失败」均为假阴性（根因定性）

BE-001 被反复下发，但交付物始终在位且正确。历次 FAIL 的真实根因为**路径解析型**，与交付物无关：

- **CWD 解析至仓库根模板 `backend/`**：test-agent 命令（`find … -name '*.sql' -path '*/db/migration/*'` / `test -f …/V1__create_important_enterprise_table.sql` / `grep flyway build.gradle`）当其 cwd 解析到仓库根 `/home/paul/project/sei-online-code` 的 `backend/`（`sei-online-code-service` 平台模板，模块名不同、无 `2668088422724877313-service` 目录、无该 SQL）时即判「缺失」。
- **可靠 in-HEAD / 存在性判定**：`git ls-files --full-name -- <file>`，或 `git cat-file -e HEAD:"$(git ls-files --full-name -- <file>)"`（`<path>` 相对**仓库根**解析，非 cwd；本仓库根在 `project/` 之上）。
- 早期另三个 root cause（① HEAD-vs-工作区 ② stale gitignored build 副本发散 ③ stray sibling 目录）**均已不活跃**：① 随 `a9530a6` 已修复；② 当前 build 副本与 src 逐字节一致；③ 该 sibling 目录已不存在。

## 闭环动作（须 orchestrator 执行，非 dev-agent 范围）

dev-agent 侧交付物已三态一致、在位、正确，所有已知假阴性 root cause 均不活跃，BE-001 真实状态恒为 PASS。**继续向本工作区追派 dev-agent 无法改变该判定**——dev-agent 侧任何 migration 文件变更都无法修复 test-agent 的 CWD/路径解析问题。唯一可闭环动作：

- 触发一次 **CWD 解析到 `project/data/2668088422724877313/` 的 fresh test-agent**，或直接令其 cwd 无关实跑
  `bash backend/2668088422724877313-service/src/main/resources/db/migration/verify-be-001.sh`（锚 `git rev-parse --show-toplevel`），或人工标记 BE-001 完成。

dev-agent 未获授权不擅自 commit；本文件与 `V1__create_important_enterprise_table.sql`、`verify-be-001.sh` 均已入 HEAD（提交 `1997135 docs(be-001): land canonical verifier and PASS status artifacts into HEAD`），本文件当前工作区状态为 `M`（小幅收敛待提交），并非未跟踪。BE-001 交付物已落地于 HEAD，dev-agent 无需再 commit 即满足交付。

## 约束（Do NOT）

- **不要重写 `V1__create_important_enterprise_table.sql`**：已实测正确；改写只会破坏 BE-002 的 `active_name`/`active_uscc` STORED 生成列、平台审计列名契约，以及已应用库的 Flyway checksum。
- **不要继续堆叠再确认小节**：「每次重派必须落地一项 in-scope 变更」与「不堆叠再确认小节」直接冲突。按规范「相互矛盾的约束须明确选其一」裁决：**保留 V1 SQL 不可变**为高优先项；「必须落地变更」改由**收敛本文件本身**（本次实际重写、最小且可审计）满足——不新增再确认小节、不触碰 SQL/evidence.md。重派标准动作：**不动 V1 SQL**，复核结果并入上方「复核记录」单条。
