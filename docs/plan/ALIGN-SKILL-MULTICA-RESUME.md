# 对齐 multica skill 存储模型 — 续作指引

> 本文档为跨会话续作专用。上下文重置后从此文件读起，配合 `git log` 与当前代码状态接续 PR2–5。
> 分支 `feat/align-skill-multaca`（本地，未推送）。

## 状态（截至 PR3 完成）

- **PR1 已完成**（commit `798d4a2`）：Phase 1 迁移 V7 + Phase 2 join 表 + Phase 0 测试
- **PR2 已完成**（commit `2b165b8`）：弃持久化 hash，name 去重 + 409，V8 迁移
- **PR3 已完成**（Phase 4，commit `fc22c00`）：来源→config JSONB，删 SkillSourceType，V9 迁移；后端 compileTestJava + skill/Converter 测试全过，前端 umi build 通过，V9 已应用本地库
- **本地 DB**：`sei-online-code` 库（pg17 容器，`localhost:5433`，user/pass `postgres`/`lslin@32`）已手动应用 V1–V9。运行时无 Flyway（仅 test profile 用），故无 `flyway_schema_history` 表
- **已验证**：`compileTestJava` 通过；`*SkillServiceTest`(4)+`*SkillConfigConverterTest`(3)+`*SkillMaterializerTest`(3)+`*PlanContentConverterTest`+`*FeatureDesignContentConverterTest` 全过；V9 schema 已确认（`source`/`source_type` 列已删，`config TEXT` 已加，4 行回填 `{"origin":"local:..."}`，`uk_skill_name` 保留）；前端 `pnpm build` 通过
- **未验证缺口**：完整测试套件（Flyway DB 集成测试，本地 testcontainers env-blocked）/ `--spring.profiles.active=local` 冒烟

## 决策汇总（用户已拍板）

- **范围**：a(join表) + b(完整内容入库) + c(弃hash改name去重) + d(来源→config JSONB) + e(skill_file辅助文件) + g(内置技能资源化)；**defer** f(UUID主键,受 `BaseAuditableEntity` 约束) / h(workspace多租户,零基础) 另立 epic
- **a**：AgentDto 仍内联 `skillIds[]`，后端 join 表↔数组转换，前端零改动 ✅(PR1 已实现)
- **c**：同名导入冲突返回 409（复用 `ConflictException`）
- **b/g**：技能内容 vendor 进 `src/main/resources/skills/`，suid/eadp-backend 从 `~/.claude/skills/` 拷入，project-planning/feature-design 内容不存在先 stub+TODO
- **内置技能绑定**：synthetic id `builtin:<name>` 经 `oc_agent_skill` 显式绑定，内容从 classpath 加载；join 表 `skill_id` 不加 FK（仅 `agent_id` 保留 FK）

## 关键约束（已核实）

- `StringListConverter` 还被 `Task.fileScope` 复用 → 保留类，仅 Agent 不再用 ✅(PR1)
- `ConflictException` 经 `PreBuildExceptionHandler` 映射 409，但 Javadoc 仅提 BUILDING 态 → Phase 3 需确认 skill 端点(Phase 3 controller)也能走到该 handler，必要时拓宽
- `AbstractJsonConverter<T>` 单对象 JSONB 基类现成（Plan/FeatureDesign content 在用）→ Phase 4 `SkillConfig` 直接套
- sei 实体全扁平、无 JPA 关系映射 → 不引入 @OneToMany，关系在 service/DAO 层用显式查询 ✅(PR1 遵守)
- `BaseService.findOne/findByPage/save` 均 public 非 final，可 override ✅(PR1 用到)
- `BaseEntityDao<T> extends JpaRepository<T,String>` → Spring Data 派生查询可用 ✅(PR1 用到)
- `PageResult.getRows()` 返回 `ArrayList<T>`（不是 getData）✅(PR1 踩坑修正)

## PR1 改动文件（commit 798d4a2）

- `entity/Agent.java`：`skillIds` 字段 → `@Transient`（移除 `@Convert`/`@Column`）
- `entity/AgentSkill.java`（新）：扁平实体，agentId + skillId，UNIQUE(agent_id, skill_id)
- `dao/AgentSkillDao.java`（新）：findByAgentId/findByAgentIdIn/findBySkillId/deleteByAgentId
- `service/AgentService.java`：注入 AgentSkillDao；override findOne/findByPage/save/findByName populate skillIds；attachSkills 重写为整体替换（删旧+插新+去重）
- `service/SkillService.java`：构造器 `(SkillDao, AgentDao)` → `(SkillDao, AgentSkillDao)`；preDelete 全表扫描 → `findBySkillId` 单查询
- `db/migration/V7__agent_skill_join_table.sql`（新）：建 oc_agent_skill、迁移 V6 种子绑定、删 skill_ids 列
- `test/.../AgentServiceTest.java`（新，4 测试）、`SkillServiceTest.java`（新，2 测试）

## 续作：PR2 = Phase 3（弃持久化 hash，name 去重，409）—— ✅ 已完成

**起点**：`git checkout feat/align-skill-multaca` → 读 `Skill.java`/`SkillHasher.java`/`SkillDao.java`/`SkillService.java`/`SkillMaterializer.java`/`SkillDto.java` 现状 → 按下文 Phase 3 实施。

**Phase 3 任务**：
1. `Skill.java`：移除 `computedHash` 持久化字段；改 `@Transient getComputedHash()` 运行时由 `SkillHasher.compute(source,name,description,content)` 计算 ✅
2. `SkillDao`：删 `findByComputedHash` ✅
3. `SkillService.importSkill`：改为 name 去重 —— `findByName(name)` 命中 → `throw new ConflictException("技能名已存在: "+name+" (id="+existing.getId()+")")`；未命中 → insert ✅
4. `SkillHasher`：保留（materializer `.lock` + DTO 运行时计算仍需）✅
5. `SkillDto.computedHash`：保留（运行时计算，前端 Hash 列不变）✅
6. 确认 `ConflictException`→409 映射覆盖 skill 端点：`PreBuildExceptionHandler` 为 `@RestControllerAdvice` 全局，已覆盖；仅拓宽 Javadoc ✅
7. 新迁移 `V8__skill_drop_hash.sql`：`ALTER TABLE oc_skill DROP COLUMN computed_hash`；`DROP INDEX idx_skill_hash`；V3/V6 种子里硬编码的 computed_hash 随列删除失效（无需改 V3/V6）✅
8. 更新 `SkillServiceTest`：补 importSkill 的 name 去重 + 409 测试（现有 2 测试保留）✅
9. 应用 V8 到本地库；`./gradlew :sei-online-code-service:test --tests "*SkillServiceTest"` ✅

**Phase 3 注意**：materializer `.lock` 幂等（`SkillMaterializer.java:88-93`）依赖 computedHash —— 改运行时计算后，hash 从 DB content 实时算，`.lock` 仍可比较，不破坏幂等。但 `SkillMaterializerTest` 的 `.lock == computedHash` 断言需确认仍成立（hash 改为 @Transient getter，materializer 调 getter 取值）。
→ **已核实**：`SkillMaterializerTest` 用字面量 hash（`"sha256:aaa"`）直接构造 `SkillPayload` record，从不触碰 `Skill` 实体的 `getComputedHash()`，故不受影响；3 测全过。`DispatchService:289` / `PlanAgentService:176` 调 `skill.getComputedHash()` 取已加载实体的运行时 hash，实体四字段（source/name/description/content）均从 DB 装载，计算正确。

## PR2 改动文件（Phase 3，已提交，见 git log）

- `entity/Skill.java`：删 `computedHash` 字段 + setter；`getComputedHash()` 改 `@Transient` 运行时 `SkillHasher.compute(...)`；`@Table` 移除 `idx_skill_hash`；Javadoc 更新
- `dao/SkillDao.java`：删 `findByComputedHash`，保留 `findByName`
- `service/SkillService.java`：`importSkill` 改 name 去重——`findByName` 命中抛 `ConflictException`，未命中 insert；移除 hash 计算与 `setComputedHash`；Javadoc 更新
- `api/SkillApi.java`：端点 16 注释 + Operation description 改「name 去重 + 409」（原「hash 幂等」）
- `controller/PreBuildExceptionHandler.java` + `exception/ConflictException.java`：Javadoc 拓宽覆盖 skill 同名冲突（映射逻辑无改动，本就全局）
- `db/migration/V8__skill_drop_hash.sql`（新）：删 `computed_hash` 列 + `idx_skill_hash` 索引
- `test/.../SkillServiceTest.java`：补 `importSkill_throwsConflictWhenNameExists` + `importSkill_insertsWhenNameFree`（共 4 测）；`setUp` 加 `getEntityClass()` mock 绕过 `BaseService.validateUniqueCode` NPE

## 续作：PR3 = Phase 4（来源→config JSONB）—— ✅ 已完成

**起点**：`git checkout feat/align-skill-multaca` → 读 `Skill.java`/`SkillDto.java`/`ImportSkillRequest.java`/`SkillHasher.java`/`AbstractJsonConverter.java`+`FeatureDesignContentConverter.java`（converter 范例）/前端 `Skills.tsx`/`onlineCode.ts`/`mocks/db.ts`/`mocks/handlers.ts` 现状 → 按下文 Phase 4 实施。

**设计决策（用户拍板）**：`SkillConfig = { origin: string }` 单字段；删 `SkillSourceType` 枚举（GITHUB/LOCAL/INLINE 由 origin 前缀 `github:`/`local:`/`inline` 隐式编码，最贴 multica frontmatter 模型）；§6 hash recipe 的 source 部分改取 `config.origin`（值与原 source 同串 → hash 不变，无复现破坏）。

**Phase 4 任务**：
1. 新建 `dto/skill/SkillConfig.java`：`origin` 字段，`Serializable`+`@Schema`+无参构造（Jackson）+`SkillConfig(String origin)` 便捷构造 ✅
2. 新建 `entity/converter/SkillConfigConverter.java`：`extends AbstractJsonConverter<SkillConfig>`，`@Converter`，对称于 PlanContentConverter ✅
3. `Skill.java`：删 `source`/`sourceType` 字段+accessors+`EnumType`/`Enumerated` import；加 `config` 字段（`@Convert(SkillConfigConverter)`+`@Column(name="config", columnDefinition=TEXT)`）；`getComputedHash()` 改 `SkillHasher.compute(config==null?null:config.getOrigin(), name, description, content)`；Javadoc recipe tuple `(v1|source|...)`→`(v1|config.origin|...)` ✅
4. `SkillDto.java`/`ImportSkillRequest.java`：删 source/sourceType + accessors；加 `config`（ImportSkillRequest 上 `@NotNull`，取代原 sourceType 的 @NotNull）✅
5. `SkillService.importSkill`：签名 `(name,desc,source,sourceType,content)`→`(name,desc,SkillConfig config,content)`；`setConfig(config)`；Javadoc 更新 ✅
6. `SkillController.importSkill`：传 `request.getConfig()` 取代 getSource/getSourceType ✅
7. `SkillHasher.compute`：参数 `source`→`origin`（签名+Javadoc+recipe 注释+body `writePart(digest, origin)`）；语义不变，hash 输入值同 ✅
8. 删 `dto/enums/SkillSourceType.java` ✅
9. 新迁移 `V9__skill_source_to_config.sql`：`ADD COLUMN config TEXT`；`UPDATE ... SET config = jsonb_build_object('origin', source)::text WHERE source IS NOT NULL`；`DROP COLUMN source`/`source_type`（V3 种子 INSERT 仍引用此两列，按序执行 V9 前列仍在，合法，同 V8 处理 computed_hash）✅
10. 前端 `onlineCode.ts`：`SkillSourceType` type → `SkillConfig` interface；SkillDto.source/sourceType → config；importSkill params source/sourceType → config；注释 "idempotent by hash" → "dedup by name"（对齐 PR2 后端实际）✅
11. 前端 `mocks/db.ts`：SkillSourceType → SkillConfig；SkillDto/computeSkillHash（`s.config.origin`）/importSkill/seed 全改 config；mock 的 hash 幂等保留（PR2 未触及 mock，本次仅 source→config.origin，不改 mock 去重策略，避免越界）✅
12. 前端 `mocks/handlers.ts`：import body `source?/sourceType?` → `config?:{origin?}`；默认 `inline:${name}` ✅
13. 前端 `Skills.tsx`：删 `SkillSourceType` import + `SOURCE_TYPE_META`/`SOURCE_TYPE_OPTIONS` + `Select` import；改 `originTypeMeta(origin)` 由前缀派生 Tag；ImportForm sourceType/source → origin；表单 sourceType Select+source Input → 单 origin Input；列「来源类型」Tag 由 `config.origin` 派生、「来源」读 `config.origin`；header 注释 "idempotent by hash" → "dedup by name 409" ✅
14. 新建 `SkillConfigConverterTest`（3 测：null→null、blank→null、round-trip origin），对称 PlanContentConverterTest ✅
15. `SkillServiceTest`：两处 importSkill 调用 `("...", SkillSourceType.LOCAL, ...)` → `(new SkillConfig("local:..."), ...)` ✅
16. 应用 V9 到本地库；`compileTestJava` + `*SkillServiceTest`/`*SkillConfigConverterTest`/`*SkillMaterializerTest`/`*PlanContentConverterTest`/`*FeatureDesignContentConverterTest` 全过；前端 `pnpm build`（umi build, ESLINT=none）通过 ✅

**Phase 4 注意**：
- `config` 列 TEXT（非 JSONB），与 Plan/FeatureDesign content 一致（`columnDefinition=TEXT` + AbstractJsonConverter 序列化 JSON 串）。`jsonb_build_object(...)::text` 回填的 JSON 串带空格（`{"origin": "..."}`），Jackson ObjectMapper 反序列化空白不敏感，兼容。
- §6 hash recipe doc（API-CONTRACT-PHASE3.md §1.1/§2 端点16/§6）仍写 `source`/`sourceType`，**契约文档更新整体 defer 到 PR5**（resume doc 既有规划）；PR3–PR4 期间契约 doc 与实际 API 漂移，已知。代码层 `SkillHasher` 已用 origin，hash 值不变。
- mock（db.ts）importSkill 仍按 hash 幂等，与后端 name 去重（PR2）漂移——PR2 既有，本次不修（越界）；仅同步 source→config.origin。
- 本地库 oc_skill 现有 4 行（suid/eadp-backend/project-planning/feature-design，后两者为前序会话补种），V9 `UPDATE 4` 全部回填 config，source/source_type 列已删，uk_skill_name 保留。
- 前端 `db.ts` 被 grep 判为 binary（含非文本字节），需 `grep -a` 读取；Read/Edit 不受影响。
- standalone `tsc --noEmit`/`eslint` 在本环境被既有工具链问题阻断（tsconfig `ignoreDeprecations:"6.0"`、umi eslint `es2022` unknown）——非本次引入；前端验证走 `pnpm build`（umi 自带工具链）。

## PR3 改动文件（Phase 4，commit `fc22c00`）

**新建**：
- `dto/skill/SkillConfig.java`（api 模块）：origin 字段 + 双构造
- `entity/converter/SkillConfigConverter.java`（service 模块）：extends AbstractJsonConverter
- `db/migration/V9__skill_source_to_config.sql`：加 config TEXT + 回填 + 删 source/source_type
- `test/.../entity/converter/SkillConfigConverterTest.java`：3 测

**修改（后端）**：
- `entity/Skill.java`：source/sourceType → config；getComputedHash 用 config.origin；Javadoc
- `dto/SkillDto.java`：source/sourceType → config
- `dto/request/ImportSkillRequest.java`：source/sourceType → config（@NotNull）
- `service/SkillService.java`：importSkill 签名 + body + Javadoc
- `controller/SkillController.java`：传 getConfig
- `service/support/SkillHasher.java`：参数 source→origin + Javadoc
- `test/.../service/SkillServiceTest.java`：两处调用改 SkillConfig

**删除**：
- `dto/enums/SkillSourceType.java`

**修改（前端）**：
- `services/onlineCode.ts`：SkillConfig interface + SkillDto + importSkill
- `mocks/db.ts`：SkillConfig + SkillDto + computeSkillHash + importSkill + seed
- `mocks/handlers.ts`：import handler body
- `pages/OnlineCode/Skills.tsx`：originTypeMeta + 表单 origin + 列读 config.origin + 删 Select/SkillSourceType

## 后续 Phase 概要（详见各 Phase 实施时再展开）

- **PR3 = Phase 4**（来源→config JSONB）—— ✅ 已完成（commit `fc22c00`）：新建 `SkillConfig`+`SkillConfigConverter extends AbstractJsonConverter`；Skill 移除 source/sourceType、加 config TEXT 列；SkillDto 改 config.origin；删 SkillSourceType 枚举（origin 前缀编码类型）；SkillHasher 参数 source→origin；前端 Skills.tsx/onlineCode.ts/db.ts/handlers.ts 改读 config.origin；迁移 V9。详见下文 PR3 段落。
- **PR4 = Phase 5**（oc_skill_file）：新建 SkillFile 实体+DAO；SkillDto 加 files[]；SkillMaterializer 写多文件；DispatchService/PlanAgentService 带 files。迁移 V10。
- **PR5 = Phase 6+7**（内置技能资源化 + content vendor + 契约文档 + 全量验证）：vendor suid/eadp-backend 到 `src/main/resources/skills/`；project-planning/feature-design stub+TODO；新建 `BuiltInSkillRegistry`（ClassPathResource 加载）；materializeSkills 按 `builtin:` 前缀分流解析；迁移 V11（删 oc_skill 内置种子行 + 更新 oc_agent_skill.skill_id 为 `builtin:<name>`）；前端内置技能移出 /skill 列表、Agents.tsx 多选加 builtin 选项；更新 API-CONTRACT-PHASE3.md §1.1/§1.2/§3/§4/§6、PRE-BUILD §10。

## 验证检查点（每个 PR 必过）

1. `./gradlew :sei-online-code-service:compileTestJava` 通过
2. `./gradlew :sei-online-code-service:test --tests "*ServiceTest"` 通过
3. 新迁移手动应用到本地 `sei-online-code` 库：`docker exec -i pg17 psql -U postgres -d sei-online-code -v ON_ERROR_STOP=1 < <migration>.sql`
4. schema 变更用 `docker exec pg17 psql -U postgres -d sei-online-code -c "\d <table>"` 确认

## 不在本次（defer epic）

- **f** UUID 主键：受 sei-core `BaseAuditableEntity` 约束，影响全实体
- **h** workspace 多租户：sei 零基础，平台级改造
