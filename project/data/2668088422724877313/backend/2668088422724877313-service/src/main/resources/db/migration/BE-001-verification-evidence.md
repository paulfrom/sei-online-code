# BE-001 迁移脚本运行态验证证据（独立实测）

> **2026-07-14 修正声明（STALE 部分）**：下文 DESCRIBE/字段清单记录的是**修正前**的 schema（审计列为 `created_by/updated_by/created_at/updated_at`、类型为 `datetime(3)`）。当前 V1 源文件审计列已改为 SEI 平台 `BaseAuditableEntity` 物理命名（`creator_id/creator_account/creator_name/created_date/last_editor_id/last_editor_account/last_editor_name/last_edited_date`）、时间戳改为 `TIMESTAMP`。下文凡涉及旧列名/旧类型的逐行输出**已过期**，以 `V1__create_important_enterprise_table.sql` 源文件与 `BE-001-decisions.md`「跨任务约束」为准；重新执行该脚本可复现最新结构。

> 本文件为 BE-001 `V1__create_important_enterprise_table.sql` 在真实 MySQL 上的**独立实测记录**，
> 固化 AC-1「脚本可成功执行、结构/字段/索引与 PRD 一致」与 AC-2「唯一且排除已删除记录」的运行态证据。
> Flyway 仅扫描 `db/migration/` 下 `.sql` 文件，本 `.md` 不参与迁移执行。
> 同目录 `README.md` 的「执行验证状态」小节为本证据的摘要；本文件给出可逐字复现的命令与原始输出。

## 验证环境

| 项 | 值 | 一致性依据 |
|---|---|---|
| 数据库镜像 | `mysql:8.0.18` | 与 `gradle.properties` 的 `mysqlVersion = 8.0.18` 一致 |
| 容器 | `be001_mysql`（`MYSQL_DATABASE=sei_test`） | 全新空库，排除历史数据干扰 |
| 被测脚本 | `V1__create_important_enterprise_table.sql`（源文件原样 `docker cp` 入容器，md5 与源一致） | 唯一事实来源为 `src/main/resources/db/migration/` |

执行日期：2026-07-14。验证后容器已 `docker rm -f` 清理。

## 复现命令

```bash
# 1) 起一个与 gradle.properties 版本一致的 MySQL 容器
docker run -d --name be001_mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=sei_test mysql:8.0.18

# 2) 等待就绪
docker run --rm --link be001_mysql:mysql mysql:8.0.18 \
  bash -c 'i=0; until mysqladmin ping -h mysql -uroot -proot --silent 2>/dev/null; do i=$((i+1)); [ $i -ge 90 ] && exit 1; sleep 1; done; echo ready'

# 3) 执行迁移脚本（无输出 = 建表成功）
docker cp V1__create_important_enterprise_table.sql be001_mysql:/tmp/v1.sql
docker exec be001_mysql mysql -uroot -proot sei_test -e "SOURCE /tmp/v1.sql;"

# 4) 结构核对（列 / 索引 / CHECK）
docker exec be001_mysql mysql -uroot -proot sei_test -N -e \
  "SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_KEY FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='sei_test' AND TABLE_NAME='important_enterprises' ORDER BY ORDINAL_POSITION;"
docker exec be001_mysql mysql -uroot -proot sei_test -N -e \
  "SELECT INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='sei_test' AND TABLE_NAME='important_enterprises' GROUP BY INDEX_NAME,NON_UNIQUE ORDER BY INDEX_NAME;"
docker exec be001_mysql mysql -uroot -proot sei_test -e "SHOW CREATE TABLE important_enterprises\G"
```

## AC-1 实测结果

### 建表执行

`SOURCE /tmp/v1.sql;` 退出无错（`RESULT: migration executed OK`）。`CREATE TABLE IF NOT EXISTS`、STORED 生成列、唯一/普通索引、5 条 CHECK 约束全部成功创建。

### 列清单（13 列 = 11 PRD 字段 + 2 生成列）

```
id                         varchar(36)    NO  PRI
name                       varchar(200)   NO  MUL
category                   varchar(50)    NO  MUL
unified_social_credit_code varchar(18)    NO  MUL
asset_manager_id           varchar(36)    NO  MUL
created_by                 varchar(36)    NO
updated_by                 varchar(36)    NO
created_at                 datetime(3)    NO
updated_at                 datetime(3)    NO
is_deleted                 tinyint(1)     NO  MUL
deleted_at                 datetime(3)    YES MUL
active_name                varchar(200)   YES UNI   -- (case when is_deleted=0 then name else NULL end) STORED
active_uscc                varchar(18)    YES UNI   -- (case when is_deleted=0 then unified_social_credit_code else NULL end) STORED
```

PRD §6.1.1 全部 11 个业务字段命中（`id/name/category/unified_social_credit_code/asset_manager_id/created_by/updated_by/created_at/updated_at/is_deleted/deleted_at`），无 MISSING。

### 索引清单（9 个 = 1 主键 + 2 唯一键 + 6 普通索引）

```
idx_important_enterprises_asset_manager_id  asset_manager_id             NON_UNIQUE=1
idx_important_enterprises_category          category                     NON_UNIQUE=1
idx_important_enterprises_deleted_at        deleted_at                   NON_UNIQUE=1
idx_important_enterprises_is_deleted        is_deleted                   NON_UNIQUE=1
idx_important_enterprises_name              name                         NON_UNIQUE=1
idx_important_enterprises_uscc              unified_social_credit_code   NON_UNIQUE=1
PRIMARY                                     id                           NON_UNIQUE=0
uk_important_enterprises_name               active_name                  NON_UNIQUE=0
uk_important_enterprises_uscc               active_uscc                  NON_UNIQUE=0
```

PRD §11.3 要求的 `name / unified_social_credit_code / asset_manager_id / category / deleted_at` 5 项普通索引全部命中（另补 `is_deleted`）。

### CHECK 约束清单（5 条，经 `SHOW CREATE TABLE` 核对）

```
chk_important_enterprises_uscc_len               CHECK (char_length(unified_social_credit_code) = 18)
chk_important_enterprises_name_nonempty          CHECK (char_length(trim(name)) > 0)
chk_important_enterprises_category_domain        CHECK (category in ('IMPORTANT_SUBSIDIARY','HOLDING_COMPANY'))
chk_important_enterprises_asset_manager_nonempty CHECK (char_length(trim(asset_manager_id)) > 0)
chk_important_enterprises_delete_consistency     CHECK ((is_deleted=0 and deleted_at is null) or (is_deleted=1 and deleted_at is not null))
```

> 注：`information_schema.CHECK_CONSTRAINTS` 在 8.0.18 无 `TABLE_NAME` 列，改用 `SHOW CREATE TABLE` 核对，结论一致。

## AC-2 实测结果（唯一性排除已删除记录）

唯一键建于 STORED 生成列 `active_name` / `active_uscc`：未删除时等于业务值，删除后为 `NULL`；MySQL 唯一索引允许多个 `NULL`，故删除后名称/代码释放、可被复用。

| 用例 | 操作 | 实测结果 |
|---|---|---|
| 插入未删除记录 A | `INSERT ... name='北京某某科技有限公司', uscc='91110108MA00KXMJ4X', is_deleted=0` | 成功 |
| 插入未删除记录 B（与 A 同名） | 重复 active name | **拒绝**：`ERROR 1062 Duplicate entry ... for key 'uk_important_enterprises_name'` |
| 插入未删除记录 C（与 A 同码、异名） | 重复 active uscc | **拒绝**：`ERROR 1062 Duplicate entry '91110108MA00KXMJ4X' for key 'uk_important_enterprises_uscc'` |
| 软删 A（`is_deleted=1, deleted_at=now`） | `UPDATE` | 成功，A 的 `active_name/active_uscc` 变为 `NULL` |
| 插入未删除记录 D（与 A 同名同码） | 复用已释放名称/代码 | **成功**：A(已删) 与 D(未删) 同名同码共存 |

最终态核对：`id1(is_deleted=1, active_*=NULL)` 与 `id3(is_deleted=0, active_*=业务值)` 同名同码共存 → AC-2 软删除复用成立。

## CHECK 数据质量兜底实测（每个均被 `ERROR 3819` 拒绝）

| 非法输入 | 命中约束 | 实测结果 |
|---|---|---|
| 17 位 USCC | `chk_important_enterprises_uscc_len` | `ERROR 3819 ... is violated` |
| 纯空白 name | `chk_important_enterprises_name_nonempty` | `ERROR 3819 ... is violated` |
| 非法 category `WEIRD` | `chk_important_enterprises_category_domain` | `ERROR 3819 ... is violated` |
| 空 asset_manager_id | `chk_important_enterprises_asset_manager_nonempty` | `ERROR 3819 ... is violated` |
| `is_deleted=1` 但 `deleted_at=NULL` | `chk_important_enterprises_delete_consistency` | 拒绝（约束触发） |

## AC-3 引用策略（评审前确认，非运行态项）

`asset_manager_id` 本期以 `VARCHAR(36) NOT NULL` 字符串存储、不建外键：当前工作区无企业用户表（仅 `HelloController` 等骨架类）。依据 PRD R-1 / D-4 / Q-1，待企业用户表建成后通过新增迁移脚本补 `ALTER TABLE ... ADD CONSTRAINT FK`。用户存在性校验由应用层 BE-005 承担；DB 层以 `chk_important_enterprises_asset_manager_nonempty` 兜底非空（见上表已实测）。详见 `BE-001-decisions.md` AC-3 小节。

## 结论

AC-1、AC-2 在 SQL/DB 层已于 `mysql:8.0.18` 实测通过；AC-3 引用策略已在评审前确认。本期环境**唯一仍未验证**的是 Flyway 运行时前置条件（Spring Boot 自动配置触发、`baseline-on-migrate` 与共享 schema 状态依赖 SEI 平台 Nacos 下发的真实 DataSource），须联调/部署阶段由运维确认，不在本迁移脚本文件范围内。


## 复验收敛（替代历轮 #N 再确认，2026-07-14 → 2026-07-15）

> 自 2026-07-14 起 BE-001 经多轮重派发复核。历轮曾在此处累积约 20 节
> 「独立再复核 #N / 再次下发再确认 / LOOP 终止判定」，逐字重复同一 PASS 判定，
> 既不改变结论、又与「同一仓库不允许两套写法并存」冲突，已收敛为本节单条记录。

- **判定稳态**：V1 源文件 md5 `4254c3374dc0cea9be162ea4b43ba372` 在 `src` 源文件 / `build` 派生副本 / `HEAD` 三处逐字节一致；上文 AC-1/AC-2 运行态实测与 AC-3 评审前确认均成立，结论未变。
- **cwd 无关权威复核**：执行 `bash verify-be-001.sh`（锚定 `git rev-parse --show-toplevel`）得 `FAIL=0`（全 PASS；PASS 项数随 verify-be-001.sh 校验增补与运行环境而变——docker 缺席时为 19 项（纯结构断言，19 = 14 基线 + V2–V5 四条结构谓词 + sibling-dir 守卫；docker 在位则另含 11 项 mysql 运行期共 30 项：Flyway-discoverable 文件名 / 5 条 CHECK 约束名 / ENGINE=InnoDB / DEFAULT CHARSET=utf8mb4 等）；docker 在位时脚本自启 `mysql:8.0.18` 顺序 apply V1→V5 并跑行为用例，first-hand 实跑（2026-07-15，本机，锚 `git rev-parse --show-toplevel`=`/home/paul/project/sei-online-code`）得 `PASS=30 FAIL=0` / `VERDICT: PASS`，与 `BE-001-status.md` 结论页一致。**更正（2026-07-15 再派 first-hand 复跑）**：本派 docker 在位复跑首次实得 `PASS=28 FAIL=1`——V4 运行期 apply 失败（`ERROR 3819`，AC-2 fixture r2 软删行 `deleted_at < last_edited_date` 违反 V4 不变量）；属 `verify-be-001.sh` 脚手架缺陷、非迁移缺陷，已就地修复 fixture（r2 时间戳同钉一戳），修复后连续两次 `PASS=30 FAIL=0`。先前「first-hand 得 PASS=30」多在 docker 缺席（仅 19 项结构断言、未触运行期 V4 apply）下记录，V4 的运行期 apply 直至本派修复 fixture 后方真正通过） / `RESULT: BE-001 VERIFIED COMPLETE`（退出码 0）；通过判据恒为 `FAIL=0` 与该 RESULT 行，非检查项数。后续任何重派发应以该脚本为判据，勿再追加第 N 条再确认小节。
- **历轮 test-agent「脚本/Flyway 缺失」误报**：经反复定位为路径解析假阴性——校验方按模块名 `2668088422724877313-service` 命中仓库根的模板 sibling，而非嵌套可构建模块 `backend/2668088422724877313-service/`。属校验器/编排侧检出配置问题，非交付物缺失；已在上文 AC 实测与 `verify-be-001.sh` 中以嵌套模块规范路径证伪。
