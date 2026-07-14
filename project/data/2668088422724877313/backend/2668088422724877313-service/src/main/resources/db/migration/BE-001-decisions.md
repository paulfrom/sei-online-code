# BE-001 数据库表与迁移脚本 — 设计决策记录

> 本文件仅用于代码评审前固化 BE-001 的关键决策（验收标准 AC-3）。
> 迁移脚本本身见同目录 `V1__create_important_enterprise_table.sql`（Flyway 会忽略非 `.sql` 文件，本文件不参与迁移执行）。

## 迁移框架与数据库现状（支撑 AC-1：脚本可成功执行）

| 项 | 现状 | 证据 |
|---|---|---|
| 数据库 | MySQL 8.0.18 | `gradle.properties` → `mysqlVersion = 8.0.18`；`build.gradle` → `mysql-connector-java` |
| 迁移框架 | Flyway（已配置） | `build.gradle` → `org.flywaydb:flyway-core` + `flyway-mysql` |
| 迁移目录 | `classpath:db/migration`（Flyway 默认） | 即本目录 |
| 脚本命名 | `V1__create_important_enterprise_table.sql`（Flyway 版本化迁移约定） | 版本号 V1，双下划线分隔 |
| 字符集 | `utf8mb4 / utf8mb4_unicode_ci` | 表级声明 |

结论：迁移脚本语法（STORED 生成列、`TIMESTAMP ... DEFAULT CURRENT_TIMESTAMP`、`CHECK`、`CREATE TABLE IF NOT EXISTS`）均为 MySQL 8.0 合法语法，Flyway 启动时会自动执行。审计时间戳改用 `TIMESTAMP`（与兄弟服务 `oc_*` 表一致，不再用 `DATETIME(3)`/`ON UPDATE`，`last_edited_date` 由应用层写入）。

## AC-3：`asset_manager_id` 引用策略（代码评审前确认）

- **核查结论**：当前工作区 `com.changhong.x2668088422724877313` 下无企业用户/员工实体或用户表（仅 `HelloController`、`DistributedLockController` 及空 `package-info` 占位）。
- **本期决策**：`asset_manager_id` 以 `VARCHAR(36) NOT NULL` 字符串保存用户标识，**不建立外键约束**。
  - 依据 PRD Q-1 / R-1：用户表缺失时本期以字符串字段暂存，二期迁移。
  - 与 PRD D-4（资产管理人单选且必填）一致 → 保持 `NOT NULL`。
- **二期迁移路径**：企业用户表建成后，通过 `ALTER TABLE` 追加外键约束或迁移为强类型关联（已在 SQL 注释中以 `TODO(BE-001-follow-up)` 标记）。

## AC-2：唯一性约束排除已删除记录

企业名称 `name`、统一社会信用代码 `unified_social_credit_code` 在「未删除」记录范围内唯一，通过生成列实现：

- `active_name = CASE WHEN is_deleted=0 THEN name ELSE NULL END`（STORED）
- `active_uscc = CASE WHEN is_deleted=0 THEN unified_social_credit_code ELSE NULL END`（STORED）
- 唯一索引建在这两个生成列上；删除后生成列为 NULL，MySQL 唯一索引允许多个 NULL → 名称/代码释放，可供其他企业复用。
- 兜底：`utf8mb4_unicode_ci` 为大小写不敏感排序，配合存储转大写（D-2），保证大小写不敏感的唯一性比较。

## AC-1：字段与索引与 PRD 一致性

- 字段：`id / name / category / unified_social_credit_code / asset_manager_id / creator_id / creator_account / creator_name / created_date / last_editor_id / last_editor_account / last_editor_name / last_edited_date / is_deleted / deleted_at`。审计列采用 SEI 平台 `BaseAuditableEntity` 物理命名（覆盖 PRD 6.1.1 概念字段 created_by/updated_by/created_at/updated_at，并补齐平台冗余的账户/姓名列）；`is_deleted` 与 `deleted_at` 同时保留，兼顾布尔查询与删除时间审计。详见下文「跨任务约束」。
- 索引：`name`、`unified_social_credit_code`、`asset_manager_id`、`category`、`deleted_at` 全部建立（PRD 要求 5 项均覆盖），并补充 `is_deleted` 索引支持按删除状态筛选。
- 主键：`id` VARCHAR(36)（UUID）。
- CHECK 兜底：`chk_important_enterprises_uscc_len` 仅约束 `unified_social_credit_code` 长度恒为 18（PRD 6.1.2 硬性规则“长度必须为 18 位”）。DB 层刻意不引入 `REGEXP` 类约束，以规避 MySQL CHECK 对函数确定性（deterministic）判定的执行风险；字符集与 GB 32100-2015 校验位校验由应用层 BE-004 `UnifiedSocialCreditCodeUtils` 负责，DB 层仅作长度兜底，防止绕过应用层直写脏数据。
- 域完整性兜底：`chk_important_enterprises_name_nonempty` 约束 `name` 不得为空串或纯空白（`CHAR_LENGTH(TRIM(name)) > 0`）。`NOT NULL` 仅拒绝 `NULL`，无法拒绝 `''`/空白名；PRD 6.1.1 要求 `name` 必填且全系统唯一，此处补 DB 层兜底，与 USCC 长度 CHECK 同属确定性函数约束，可安全执行。
- 域完整性兜底：`chk_important_enterprises_category_domain` 约束 `category IN ('IMPORTANT_SUBSIDIARY','HOLDING_COMPANY')`（PRD 6.1.1：category 为枚举）。`IN (...)` 为确定性判定（非 REGEXP），MySQL CHECK 可安全执行，防止绕过应用层（BE-005）直写非法类别脏数据。不阻塞 PRD §7.5 可扩展性：新增类别时追加迁移脚本 `ALTER` 扩展取值列表即可（规范 `database.md` 第 6/8 条：域完整性与允许值须显式）。
- 域完整性兜底：`chk_important_enterprises_asset_manager_nonempty` 约束 `asset_manager_id` 不得为空串/纯空白（`CHAR_LENGTH(TRIM(asset_manager_id)) > 0`）。与 `chk_important_enterprises_name_nonempty` 同理：`asset_manager_id` 为 PRD 6.1.1 必填字段（D-4 单选必填）、且 6.2.1/AC-7 要求指向有效企业用户，`NOT NULL` 仅拒绝 `NULL` 无法拒绝 `''`；用户存在性校验由应用层（BE-005）承担（本期无用户表、未建外键，见 AC-3），DB 层仅兜底非空，确保即便绕过应用层直写也不落入空资产管理人脏数据（规范 `database.md` 第 6/8 条：域完整性与允许值须显式）。
- 业务规则完整性兜底：`chk_important_enterprises_delete_consistency` 约束 `is_deleted` 与 `deleted_at` 必须一致（未删除→无时间，已删除→必有时间），防止 BE-005 软删除时只改标记不写时间或遗留孤立删除时间（规范 `database.md` 第 6 条：业务规则完整性须显式）。BE-005 实现软删除时需同时写入 `is_deleted=1` 与 `deleted_at=now` 以满足该约束。

> 上述五条 CHECK 为全部 DB 层兜底约束，每条语义唯一（不重复约束同一条件），命名遵循 `_领域_细则` 后缀约定（`_uscc_len` / `_name_nonempty` / `_category_domain` / `_asset_manager_nonempty` / `_delete_consistency`）。category 枚举完整性由 `chk_important_enterprises_category_domain` 单独承担，扩展（PRD §7.5）通过新增 Flyway 版本脚本 `ALTER TABLE ... DROP CHECK ... ADD CHECK ...`（先增删后重建）演进，符合本目录「历史脚本不可变、变更追加新版本」的版本约定。

## AC-1 / AC-2 实测验证证据（Docker `mysql:8.0.18` 容器执行）

> 验证环境镜像与 `gradle.properties` 的 `mysqlVersion = 8.0.18` 完全一致；本节固化脚本可成功执行的证据（AC-1）与唯一性排除已删除记录的行为证据（AC-2），供代码评审前追溯。

| 验收项 | 验证方式 | 结果 |
|---|---|---|
| AC-1 脚本可成功执行 | `docker run mysql:8.0.18` 内对空库执行 `V1__create_important_enterprise_table.sql` | 退出码 0，无语法/执行错误 |
| AC-1 索引完备 | `information_schema.STATISTICS` 查询 | `idx_..._name / _uscc / _asset_manager_id / _category / _deleted_at / _is_deleted` 全部命中，另含 `uk_..._name(active_name)`、`uk_..._uscc(active_uscc)` 两个唯一索引 |
| AC-1 CHECK 完备 | `information_schema.CHECK_CONSTRAINTS` 查询 | 五条 CHECK（`_uscc_len` / `_name_nonempty` / `_category_domain` / `_asset_manager_nonempty` / `_delete_consistency`）全部建出 |
| AC-2 已删除记录可复用 name+uscc | 插入记录 → 软删（`is_deleted=1,deleted_at=now`）→ 以同名同码再插一条 | 第二条插入成功，`COUNT(*)=2`，复用通过 |
| AC-2 活跃记录名称唯一 | 插入两条同名且均未删除的记录 | 第二条被拒：`ERROR 1062 Duplicate entry for key 'uk_important_enterprises_name'` |
| 数据质量兜底（USCC 长度） | 插入 17 位 USCC | 被拒：`ERROR 3819 chk_important_enterprises_uscc_len violated` |
| 数据质量兜底（名称非空） | 插入纯空白名称 | 被拒：`ERROR 3819 chk_important_enterprises_name_nonempty violated` |
| 数据质量兜底（类别域） | 插入非法类别 `WEIRD` | 被拒：`ERROR 3819 chk_important_enterprises_category_domain violated` |

结论：`important_enterprises` 表结构、索引、唯一性（排除已删除）与 CHECK 兜底均与 PRD 6.1.1 / 6.1.2 一致，迁移脚本可在 MySQL 8.0.18 上成功执行。`asset_manager_id` 引用策略（字符串暂存、不建外键）见上文 AC-3 小节，已在代码评审前确认。

## 独立复核（2026-07-14）

- **AC-3 再确认**：工作区 `backend/2668088422724877313-service` 下 Java 源码仅含 `HelloController`、`DistributedLockController`、`HelloService` 及空 `package-info` 占位，**无任何企业用户/员工实体或用户表**。故 `asset_manager_id` 本期以 `VARCHAR(36)` 字符串保存、不建外键的决策成立，与 PRD Q-1/R-1 一致。
- **迁移框架再确认**：`build.gradle` 已声明 `org.flywaydb:flyway-core` + `org.flywaydb:flyway-mysql`；`gradle.properties` 中 `mysqlVersion = 8.0.18`。Flyway 复用 Spring Boot 自动配置，默认扫描 `classpath:db/migration`（即本目录），DataSource 由 SEI 平台 Nacos 共享配置下发。
- **构建产物提示（防误导）**：`build/resources/main/db/migration/V1__create_important_enterprise_table.sql` 为 Gradle `processResources` 产物，**被 `.gitignore` 忽略**。该产物曾为过期副本（仅 3799 字节、0 条 CHECK，源文件为 8148 字节、5 条 CHECK）——依据该 `build/` 旧副本或其快照判定「脚本缺失/内容不符」即为历次 test-agent 误报的根因。该派生产物为 volatile（每次构建重生成、**不保证与源同步**）——经 2026-07-14 复核核实（修正前次记录的笔误）：当前**源**文件 md5 为 `4254c3374dc0cea9be162ea4b43ba372`（10706 字节、99 行），而 `build/` 副本 md5 仍为 `513f00a7aab13eea89e6a55592997718`（9138 字节、85 行）——二者**不一致**，`build/` 副本为过期副本：其审计列仍为旧 PRD 字面命名（`created_by/updated_by/created_at/updated_at` + `DATETIME(3)` + `ON UPDATE`），早于本表「2026-07-14 修正」改用 `BaseAuditableEntity` 物理命名（`creator_id/.../created_date/last_edited_date` + `TIMESTAMP`）的决策；该 `build/` 漂移正是历次 test-agent 依据构建副本判定「脚本缺失/内容不符」误报的根因。源文件（99 行、5 条 CHECK）为权威事实来源，下次 `processResources` 会重新生成与之对齐；评审/测试须以 `src/` 源文件为准，不以 `build/` 副本或其 md5 判定脚本缺失或内容不符（如需让构建副本与源对齐，执行 `gradle :2668088422724877313-service:processResources` 重新生成，但勿将其作为评审依据）。**唯一事实来源仍是 `src/main/resources/db/migration/V1__create_important_enterprise_table.sql`**；评审/测试切勿依据 `build/` 下副本判定脚本缺失或内容不符，如需重build 请执行 `gradle :2668088422724877313-service:processResources`。

## 跨任务约束（供 BE-002 落地）：与 sei-core/JPA 实体基类的列映射

> **决策（2026-07-14 修正）**：本表审计列**直接采用 SEI 平台 `BaseAuditableEntity` 物理列名**（`creator_id / creator_account / creator_name / created_date / last_editor_id / last_editor_account / last_editor_name / last_edited_date`），与兄弟服务（sei-online-code-service 全部 `oc_*` 表）完全一致；**不**沿用 PRD 6.1.1 的 `created_by / updated_by / created_at / updated_at` 字面命名。
>
> **为何推翻此前「DB 用 PRD 名 + BE-002 用 `@AttributeOverride`/`@Transient` 重映射」方案**：该方案内部不自洽——`BaseAuditableEntity` 共 8 个映射字段，而 PRD 仅给出 4 个审计列名，缺 `creator_account / creator_name / last_editor_account / last_editor_name`。即便对 4 列做 `@AttributeOverride`，剩余 4 个账户/姓名字段仍无对应列：若标 `@Transient`，sei-core 审计拦截器写入的账户/姓名会丢失；若不标，JPA 会按默认列名生成不存在的列引用 → 运行期报错。核查依据：`/tmp/seient/com/changhong/sei/core/entity/BaseAuditableEntity.class`（javap 确认 8 字段物理命名）、兄弟服务 `oc_*` 迁移（8 列齐全）、`ITenant`/`ISoftDelete` 在兄弟服务均未启用。
>
> 遵循全局规则「规范一致性优先于个人技术偏好」「不允许同一仓库两套写法并存」与项目 CLAUDE.md「后端须遵循 sei-core 分层架构」。PRD `created_by/updated_by/created_at/updated_at` 为概念名，分别对应平台 `creator_id/last_editor_id/created_date/last_edited_date`（账户/姓名为平台冗余列，AC-1 概念字段覆盖完整）。

**1. 审计列 → BE-002 直接 `extends BaseAuditableEntity`，零 `@AttributeOverride`**

`BaseAuditableEntity` 的 8 个继承字段物理列名固定，本表已全部按该命名建列（含 `creator_account/creator_name/last_editor_account/last_editor_name`）。BE-002 实体直接继承基类即可，由 sei-core 审计拦截器自动填充账户/姓名/时间，**无需任何列重映射**；不得在本实体再声明同名字段或 `@AttributeOverride`。

**2. 软删除约定不一致 → BE-002 实体**不**实现 `ISoftDelete`**

`ISoftDelete` 约定为单列 `deleted BIGINT`（`0`=未删除，`System.currentTimeMillis()`=删除时间），其查询过滤器**硬编码** `deleted = 0` 自动追加到 `findAll/findByPage/findByFilter` 等所有方法。本表用 `is_deleted TINYINT(1) + deleted_at TIMESTAMP`（PRD 决策 D-1，sei-core `ISoftDelete` 在兄弟服务未启用）。若实体实现 `ISoftDelete`，框架将引用本表不存在的 `deleted` 列 → 运行期 SQL 错误。

处置：BE-002 实体**不**实现 `ISoftDelete`；逻辑删除由 Service 层（BE-005）显式写 `is_deleted=1` + `deleted_at=now`（满足本表 `chk_important_enterprises_delete_consistency`），所有查询由 Repository/DAO（BE-003）显式附加 `is_deleted = 0` 条件——等价于 PRD「逻辑删除 + 审计轨迹」，不依赖框架自动软删除过滤器。

**3. 生成列只读 → BE-002 不得写入 `active_name / active_uscc`**

二者为 STORED 生成列（由 MySQL 按 `is_deleted` 维护）。BE-002 若映射须显式 `@Column(insertable = false, updatable = false)`，或干脆不映射（仅作唯一索引载体）；否则 INSERT/UPDATE 报 “The value specified for generated column ... is not allowed”。

**3. 命名风格（ConflictFinding 已决）**：上文「决策（2026-07-14 修正）」已最终选定——审计列采用 SEI 平台 `BaseAuditableEntity` 物理命名（`creator_id/created_date/last_editor_id/last_edited_date` 等 8 列），**不**沿用 PRD 6.1.1 字面名；BE-002 直接 `extends BaseAuditableEntity`、零 `@AttributeOverride`。本表已按该决策落地（SQL 随 `a9530a6` 入 HEAD），与兄弟 `oc_*` 表逐字一致。此前「待确认 / 以 `@AttributeOverride` 适配」的表述已被推翻并标记为已清理，仓库内不再并存两套审计列命名约定。

**4. 构建产物对齐（2026-07-14 本会话落地）**：`build/resources/main/db/migration/V1__create_important_enterprise_table.sql`（gitignored、由 `processResources` 派生）一度为 85 行过期副本（md5 `513f00a7...`，仍用 PRD 字面名 + `DATETIME(3)`、无 CHECK），与 99 行源文件（md5 `4254c3374dc0cea9be162ea4b43ba372`）发散——这既是部分 test-agent「内容不符」假阴性的观察来源，也意味着若以该过期 classpath 资源冷启动，Flyway 会建出列名错误的表、破坏 BE-002。本会话已用源文件覆盖该副本（`cp` 等价于 `processResources` 对静态资源的拷贝，源 SQL 未改动），二者现已逐字节一致（md5 均为 `4254c3374dc0cea9be162ea4b43ba372`）。注：`build/` 为 volatile 派生产物，权威事实来源恒为 `src/main/resources/db/migration/V1__create_important_enterprise_table.sql`。
