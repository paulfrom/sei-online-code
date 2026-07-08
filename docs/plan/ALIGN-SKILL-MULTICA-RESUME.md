# 对齐 multica skill 存储模型 — 续作指引

> 本文档为跨会话续作专用。上下文重置后从此文件读起，配合 `git log` 与当前代码状态接续 PR2–5。
> 分支 `feat/align-skill-multica`（已推送 origin/feat/align-skill-multica，8 commits ahead of main）。

## 状态（截至 PR5 提交 `e7eaaa3`）

- **PR1 已完成**（commit `798d4a2`）：Phase 1 迁移 V7 + Phase 2 join 表 + Phase 0 测试
- **PR2 已完成**（commit `2b165b8`）：弃持久化 hash，name 去重 + 409，V8 迁移
- **PR3 已完成**（Phase 4，commit `fc22c00`）：来源→config JSONB，删 SkillSourceType，V9 迁移；后端 compileTestJava + skill/Converter 测试全过，前端 umi build 通过，V9 已应用本地库
- **PR4 已完成**（Phase 5，oc_skill_file，commit `a3c6fb4`）：新建 SkillFile 实体+DAO+SkillFileDto；Skill 加 @Transient files + SkillService populate(findOne/findByPage) + importSkill 持久化 files；SkillMaterializer 多文件+越界 guard；DispatchService/PlanAgentService 带 files（PlanAgentService 由 SkillDao 改注 SkillService 以 populate files，同步改 PlanAgentServiceTest）；迁移 V10。compileTestJava + `*SkillServiceTest`(5)+`*SkillMaterializerTest`(6)+`*SkillConfigConverterTest`(3)+`*PlanAgentServiceTest`(6) 全过；V10 已应用本地库（schema 确认：pk(id)/uk(skill_id,path)/idx(skill_id)/FK CASCADE）
- **PR5 已完成**（Phase 6+7，内置技能资源化，commit `e7eaaa3`）：vendor suid/eadp-backend 到 `src/main/resources/skills/`（SKILL.md+references/）+ project-planning/feature-design stub+TODO；新建 `BuiltInSkillRegistry`（@Component，classpath 加载 SKILL.md+references/**，`builtin:<name>`→`Optional<SkillPayload>`，origin=`builtin:<name>` 算 hash）；DispatchService/PlanAgentService 注入 registry 并在 `materializeSkills` 按 `builtin:` 前缀分流（PlanAgentServiceTest ctor 同步加 mock）；迁移 V11（UPDATE 2 join 行→`builtin:`，DELETE 4 oc_skill 种子行）；前端 `BUILTIN_SKILLS` 常量 + Agents.tsx 合并选项 + mock 移除内置 seed/suid-dev 绑 `builtin:suid`/attach 放行 `builtin:`；契约 API-CONTRACT-PHASE3.md §0/§1.1/§1.2/§2/§3/§4/§6 + PRE-BUILD §10 对齐。compileTestJava + `*BuiltInSkillRegistryTest`(6)+`*PlanAgentServiceTest`(6)+`*SkillServiceTest`(5)+`*SkillMaterializerTest`(6)+`*SkillConfigConverterTest`(3) 全过；V11 已应用本地库（join 2 行 `builtin:`、oc_skill 内置 4 行已删）；前端 `pnpm build` 通过
- **本地 DB**：`sei-online-code` 库（pg17 容器，`localhost:5433`，user/pass `postgres`/`lslin@32`）已手动应用 V1–V10。运行时无 Flyway；测试环境亦不启用 Flyway，故无 `flyway_schema_history` 表
- **已验证**：`compileTestJava` 通过；`*BuiltInSkillRegistryTest`(6)+`*SkillServiceTest`(5)+`*SkillConfigConverterTest`(3)+`*SkillMaterializerTest`(6)+`*PlanAgentServiceTest`(6) 全过；V11 已应用本地库（`oc_agent_skill` 2 行 `builtin:project-planning`/`builtin:feature-design`、`oc_skill` 内置 4 行已删）；V10 schema 已确认（`oc_skill_file`：id/skill_id/path/content+审计列，pk(id)/uk(skill_id,path)/idx(skill_id)/fk_skill_file_skill ON DELETE CASCADE）；前端 `pnpm build` 通过。PR3–PR4 既有验证（V9 schema）不受 PR5 影响
- **未验证缺口**：完整测试套件（DB 集成测试，本地 testcontainers env-blocked）；`--spring.profiles.active=local` 端到端冒烟受双重阻塞 —— (1) `bootstrap.yaml` 配置走 nacos（10.199.11.1:8848 内网，本地不可达），(2) dispatch 触发 materialize 依赖 Phase 2 运行期 spawn 接缝（`WorkspaceGcService` TODO(oma-deferred)）；import + files 持久化路径已由 `*SkillServiceTest`/`*SkillMaterializerTest`/`*BuiltInSkillRegistryTest` 单测覆盖（BUILD SUCCESSFUL）
- **§5 历史措辞**：已清理（对齐 PR2 name 去重 + V7 join 表，PR6）

## 决策汇总（用户已拍板）

- **范围**：a(join表) + b(完整内容入库) + c(弃hash改name去重) + d(来源→config JSONB) + e(skill_file辅助文件) + g(内置技能资源化)；**defer** f(UUID主键,受 `BaseAuditableEntity` 约束) 另立 epic；**不做** h(workspace多租户) —— 本项目不进行租户隔离（见项目 CLAUDE.md）
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

**起点**：`git checkout feat/align-skill-multica` → 读 `Skill.java`/`SkillHasher.java`/`SkillDao.java`/`SkillService.java`/`SkillMaterializer.java`/`SkillDto.java` 现状 → 按下文 Phase 3 实施。

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

**起点**：`git checkout feat/align-skill-multica` → 读 `Skill.java`/`SkillDto.java`/`ImportSkillRequest.java`/`SkillHasher.java`/`AbstractJsonConverter.java`+`FeatureDesignContentConverter.java`（converter 范例）/前端 `Skills.tsx`/`onlineCode.ts`/`mocks/db.ts`/`mocks/handlers.ts` 现状 → 按下文 Phase 4 实施。

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

## 续作：PR4 = Phase 5（oc_skill_file 辅助文件）—— ✅ 已完成

**起点**：`git checkout feat/align-skill-multica` → 读 `Skill.java`/`SkillDto.java`/`ImportSkillRequest.java`/`SkillMaterializer.java`/`DispatchService.java`/`PlanAgentService.java`/`AgentSkill.java`+`AgentSkillDao.java`（flat 实体+DAO 范例）/ 契约 Phase 3 §1.1（deferred 的 per-file `FileRef[]`）+ §6（hash recipe，normative）现状 → 按下文实施。

**设计决策（分析后选定）**：
1. **hash recipe 不变**。契约 §6 normative 且 "must update THIS file first"（更新 defer 到 PR5）；更关键——import 以 name 去重 + **无 skill update 端点** → 辅助文件导入后不可变 → hash 是否覆盖 files 对幂等无影响。`SkillHasher`/`getComputedHash()` 零改动，files 不进 lock。若未来加 update 端点，需回头把 files 纳入 hash（TODO）。
2. **SkillFile.skill_id 加 FK + ON DELETE CASCADE**（与 `AgentSkill.skill_id` 为 `builtin:` synthetic id 预留而不加 FK 不同——本表是真实 oc_skill 子行）；删技能级联清辅助文件，service 无需 delete。
3. **DTO 映射复用 Agent populate 模式**：已核实 `BaseEntityController.convertToDto` 用 **ModelMapper**（深递归），自动把 `Skill.files: List<SkillFile>` → `SkillDto.files: List<SkillFileDto>`（只拷贝同名 path/content，审计字段不泄漏）。故 Skill 加 `@Transient List<SkillFile> files`，`SkillService` override `findOne`/`findByPage` populate——**controller 无需 override**。
4. **路径校验走 bean-validation**：`SkillFileDto.path` 加 `@NotBlank`+`@Pattern(^(?!/)(?!.*(?:^|/)\.\.(?:/|$)).+$)`（禁绝对/`..` 段，允许子目录），`ImportSkillRequest.files` 加 `@Valid` 级联 → 400 自动出，免新增异常类。materializer 再加 `normalize()+startsWith(dir)` 越界 guard（defense-in-depth）。
5. **PR4 后端 only**（resume doc PR4 行未列前端）；前端 `files[]` 类型/mock/UI 同步留 PR5（契约漂移已接受，与 PR2–4 既有模式一致）。

**Phase 5 任务**：
1. 新建 `dto/skill/SkillFileDto.java`（api 模块）：path（@NotBlank+@Pattern）+ content，Serializable+@Schema+无参+全参构造 ✅
2. `SkillDto.java`：加 `List<SkillFileDto> files`（初始化空 ArrayList）+ accessors ✅
3. `ImportSkillRequest.java`：加 `@Valid List<SkillFileDto> files` + accessors ✅
4. 新建 `entity/SkillFile.java`（service 模块）：flat 实体 extends BaseAuditableEntity，列 skill_id/path/content TEXT，`@Table(indexes={uk(skill_id,path), idx(skill_id)})`，对称 AgentSkill ✅
5. 新建 `dao/SkillFileDao.java`：`findBySkillId`+`findBySkillIdIn`（无 deleteBySkillId——FK CASCADE 覆盖）✅
6. `Skill.java`：加 `@Transient List<SkillFile> files`+accessors；Javadoc 补注「辅助文件经 oc_skill_file，不进 §6 hash」；`getComputedHash()` 不动 ✅
7. `SkillService.java`：注入 SkillFileDao；`importSkill` 签名 += `List<SkillFileDto> files` → save Skill 后 `persistFiles`（DTO→entity+saveAll）+ 回填到返回 skill；override `findOne`/`findByPage` 调 `populateFiles`（单查/批 IN 查避免 N+1）✅
8. `SkillController.importSkill`：传 `request.getFiles()` ✅
9. `SkillMaterializer.java`：`SkillPayload` 加 `List<SkillFileRef> files`（保留 3-arg 便捷构造，旧测试不破）；新增 `record SkillFileRef(path,content)`；`writeSkill` 写 SKILL.md+.lock 后 `writeAuxFiles`（`resolve(path).normalize()`+`startsWith(dir)` 越界 guard+建父目录+writeString）；幂等仍由 `.lock==computedHash` gate ✅
10. `DispatchService.java`：`materializeSkills` 构造 SkillPayload 时 `toFileRefs(skill)` 映射 files（4-arg 构造）✅
11. `PlanAgentService.java`：**SkillDao → SkillService**（field/ctor/import/usage；skillDao 仅 findOne 一处用，SkillService.findOne 是 drop-in 且 populate files；镜像 DispatchService，消除 dao/service 不一致）；`toFileRefs` helper；4-arg SkillPayload ✅
12. `PlanAgentServiceTest.java`：SkillDao mock → SkillService mock（同包免 import），ctor 调用同步改；测试未触及 skill 加载路径（agents 无 skillIds），无需新增 stub ✅
13. 新迁移 `V10__skill_file.sql`：建 oc_skill_file + 审计列 + pk/uk/idx + FK CASCADE，无 seed ✅
14. `SkillServiceTest`：现有 4 测保留；`importSkill_throwsConflictWhenNameExists` 调用补 `null` files；`importSkill_insertsWhenNameFree` 传 2 files+stub saveAll+断言回填；新增 `findOne_populatesFiles`（stub `skillDao.findOne`——`BaseService.findOne` 经 `getDao().findOne(id)`，非 findById）✅
15. `SkillMaterializerTest`：现有 3 测保留（3-arg 构造）；新增「多文件含嵌套子目录写入」「越界 `../escape` 跳过」「带 files 幂等」3 测 ✅
16. 应用 V10 到本地库；`compileTestJava` + `*SkillServiceTest`/`*SkillMaterializerTest`/`*SkillConfigConverterTest`/`*PlanAgentServiceTest` 全过；`\d oc_skill_file` schema 确认 ✅

**Phase 5 注意**：
- `BaseService.findOne(id)` 经 `getDao().findOne(id)`（`BaseEntityDao` 空接口，`findOne` 抽象，非 JpaRepository.findById）——单测 stub `skillDao.findOne(id)` 返回实体，**不是** `findById(...).thenReturn(Optional)`（与 AgentServiceTest 的 findById stub 路径不同，因 AgentServiceTest 未断言 findOne 非空）。
- `PlanAgentService` 改注 `SkillService` 是 PR4 必要改动：原 `skillDao.findOne` 不 populate @Transient files，无法带 files materialize；改用 `SkillService.findOne` 与 DispatchService 对齐。ctor 签名变更 → PlanAgentServiceTest 同步改 mock 类型。
- 辅助文件不进 §6 hash → `.lock` 仍只覆盖 SKILL.md 五元组；import 不可变 → 幂等成立。契约 §1.1「single-file only, FileRef[] deferred」文字更新整体 defer 到 PR5（代码层 FileRef[] 已落地；§6 recipe 文字不变，仍准确）。
- 前端 `onlineCode.ts`/`mocks` 的 `files[]` 同步留 PR5（PR4 后端 only，契约漂移已接受）。
- 内置技能 `references/**` vendor 到 classpath + `BuiltInSkillRegistry` 在 PR5 处理；PR4 表与管道就绪，PR5 填内容。

## PR4 改动文件（Phase 5，commit `a3c6fb4`）

**新建**：
- `dto/skill/SkillFileDto.java`（api 模块）：path（@NotBlank+@Pattern）+ content
- `entity/SkillFile.java`（service 模块）：flat 实体，对称 AgentSkill
- `dao/SkillFileDao.java`：findBySkillId / findBySkillIdIn
- `db/migration/V10__skill_file.sql`：建 oc_skill_file + uk/idx + FK CASCADE，无 seed

**修改（后端）**：
- `dto/SkillDto.java`：加 `List<SkillFileDto> files`
- `dto/request/ImportSkillRequest.java`：加 `@Valid List<SkillFileDto> files`
- `entity/Skill.java`：加 `@Transient List<SkillFile> files` + Javadoc（不进 hash）
- `service/SkillService.java`：注入 SkillFileDao；importSkill += files 持久化；override findOne/findByPage + populateFiles
- `controller/SkillController.java`：importSkill 传 getFiles()
- `agent/SkillMaterializer.java`：SkillPayload += files + SkillFileRef record；writeAuxFiles + 越界 guard
- `service/DispatchService.java`：toFileRefs + 4-arg SkillPayload
- `service/PlanAgentService.java`：SkillDao→SkillService + toFileRefs + 4-arg SkillPayload
- `test/.../SkillServiceTest.java`：补 files 入参 + findOne_populatesFiles（5 测）
- `test/.../SkillMaterializerTest.java`：+3 测（多文件/越界/幂等，共 6 测）
- `test/.../PlanAgentServiceTest.java`：SkillDao mock→SkillService mock

## 续作：PR5 = Phase 6+7（内置技能资源化 + content vendor + 契约文档 + 全量验证）—— ✅ 已完成

**起点**：`git checkout feat/align-skill-multica` → 读 `SkillMaterializer.java`/`DispatchService.java`/`PlanAgentService.java`/`SkillHasher.java`/V3+V6+V7 迁移（内置 seed + join 表）/契约 Phase 3 §1.1/§3/§4/§6 + PRE-BUILD §10 现状 → 按下文实施。

**设计决策（分析后选定）**：
1. **`BuiltInSkillRegistry` = Spring `@Component`，落 `agent` 包**（与 `SkillMaterializer` 同包）。无 ctor 依赖（内部 `new PathMatchingResourcePatternResolver()`）。`resolve(skillId)` → `Optional<SkillPayload>`：仅处理 `builtin:` 前缀，加载 `classpath:skills/<name>/SKILL.md` + `references/**`。name 正则 `^[a-z0-9][a-z0-9-]{0,63}$` 校验（防 classpath 注入，defense-in-depth）。
2. **hash 输入**：origin=`builtin:<name>`，name=`<name>`，description=null，content=SKILL.md。`.lock` 仅幂等标记，内容不可变（classpath）→ 确定性即可。
3. **`references/**` 缺失容错**：`PathMatchingResourcePatternResolver.getResources` 对不存在的目录抛 IOException（stub 技能无 references/）；仅对 `getResources` 调用 try/catch 返回空列表（正常无辅助文件），SKILL.md 读失败仍上抛 → resolve 返回 empty。
4. **路由最小内联**：两 service 各在 `materializeSkills` 加 `if (skillId.startsWith(PREFIX)) registry.resolve(...).ifPresent(add) else skillService.findOne(...)`。**不**抽共享 builder（遵 PR4 既有重复模式，不顺手重构）。
5. **V11 FK 安全**：`oc_agent_skill.skill_id` 在 V7 故意未加 FK（为 `builtin:` 预留）→ UPDATE join + DELETE oc_skill 行无 FK 阻塞；`oc_skill_file.skill_id` FK CASCADE，但内置 4 行无 files → 级联空操作。先 UPDATE join（2 行），再 DELETE oc_skill（4 行）。
6. **不新增端点**：内置技能为固定编译期集合 → 前端 `BUILTIN_SKILLS` 常量；Agents 多选合并选项。
7. **project-planning/feature-design stub+TODO**：内容客观不存在，建 stub SKILL.md（骨架 + `TODO: replace with real skill content`），不伪造。
8. **mock 范围**：移除 suid/eadp-backend oc_skill seed；`suid-dev` 绑 `builtin:suid`；`attachAgentSkills` 放行 `builtin:` 前缀。**不**补 V6 planning/feature-design/dev agent seed（既有 mock 漂移，PR5 不扩面）。db.ts 自包含不 import `@/services`（保 PR3 既有模式），无 `BUILTIN_SKILLS` 常量（仅内联 `builtin:suid`）。
9. **Skills.tsx 不改**：内置技能不再入 oc_skill → `/skill/findByPage` 天然不返回 → 列表自动只显用户技能。

**Phase 6+7 任务**：
1. vendor `~/.claude/skills/suid/{SKILL.md,references/}` → `src/main/resources/skills/suid/`；eadp-backend 同理 ✅
2. 新建 `skills/project-planning/SKILL.md` + `skills/feature-design/SKILL.md`（stub + TODO）✅
3. 新建 `agent/BuiltInSkillRegistry.java`：`@Component`；`PREFIX="builtin:"`；`resolve`→`Optional<SkillPayload>`；name 正则校验；`ClassPathResource` 读 SKILL.md（不存在→empty）；`loadAuxFiles`（`getResources` IOException→空列表，path 取 URL 中 `skills/<name>/` 后相对串，跳过目录条目）；`SkillHasher.compute("builtin:"+name,name,null,content)` ✅
4. `DispatchService`/`PlanAgentService`：ctor += `BuiltInSkillRegistry`；`materializeSkills` `builtin:` 分流 ✅
5. `PlanAgentServiceTest`：ctor += `mock(BuiltInSkillRegistry.class)`（agents 无 skillIds，无 stub）✅
6. 新迁移 `V11__skill_builtin_synthetic_id.sql`：UPDATE join 2 行 → `builtin:`；DELETE oc_skill 4 行 ✅
7. 前端 `onlineCode.ts`：`BUILTIN_SKILLS` 常量（4 项，id=`builtin:<name>`）✅
8. 前端 `Agents.tsx`：`loadSkills` 合并 `BUILTIN_SKILLS` 到 `skillOptions` ✅
9. 前端 `mocks/db.ts`：删 suid/eadp-backend `importSkill` seed；`suid-dev` 绑 `['builtin:suid']`；`attachAgentSkills` 过滤改 `sid.startsWith('builtin:') || db.skills.has(sid)`；`handlers.ts` 零改（attach 委托 db.ts）✅
10. 契约 `API-CONTRACT-PHASE3.md`：§0 scope 行、§1.1（config+files+runtime hash+builtin 注）、§1.2（skillIds 可含 `builtin:`）、§2 ep#16（config+files+name 去重）、§3（builtin 分流+多文件）、§4（整段重写为 classpath 资源化）、§6（source→config.origin + builtin origin）✅
11. 契约 `API-CONTRACT-PRE-BUILD.md` §10：§10.1 改述内置技能不再 oc_skill seed（classpath 资源 + V11）；§10.2 agent `skill_ids` 示例改 `builtin:` + 注 V7 join 表 ✅
12. 应用 V11 到本地库；`compileTestJava` + `*BuiltInSkillRegistryTest`/`*PlanAgentServiceTest`/`*SkillServiceTest`/`*SkillMaterializerTest`/`*SkillConfigConverterTest` 全过；前端 `pnpm build` 通过 ✅

**Phase 6+7 注意**：
- `BuiltInSkillRegistryTest` 在 test classpath 解析真实 `skills/suid/SKILL.md`（main resources 在 test runtime 可见），非 mock——验证 vendor 资源真实可加载。
- 路由分流未抽公共 helper：两 service 各自内联 `if (startsWith(PREFIX))`，与 PR4 `toFileRefs`/`materializeSkills` 既有重复一致；未来若加第三处调用再抽。
- §5（sub-agent obligations）段未改：含历史措辞（"idempotent"/"V3 pick ONE"），属描述性 prose，不在 PR5 列定范围（§1.1/§1.2/§3/§4/§6），记为待清理。
- 内置技能 `builtin:<name>` 经 `oc_agent_skill` 绑定，**不**经 oc_skill；`SkillService.preDelete`/`findOne`/`findByPage` 天然不触及它们（DB 无行）。

## PR5 改动文件（Phase 6+7，commit `e7eaaa3`）

**新建**：
- `src/main/resources/skills/suid/SKILL.md` + `references/*.md`（vendor）
- `src/main/resources/skills/eadp-backend/SKILL.md` + `references/*.md`（vendor）
- `src/main/resources/skills/project-planning/SKILL.md`（stub + TODO）
- `src/main/resources/skills/feature-design/SKILL.md`（stub + TODO）
- `agent/BuiltInSkillRegistry.java`：@Component，classpath 加载 + builtin: 解析
- `db/migration/V11__skill_builtin_synthetic_id.sql`：UPDATE join + DELETE oc_skill 种子
- `test/.../agent/BuiltInSkillRegistryTest.java`：6 测

**修改（后端）**：
- `service/DispatchService.java`：ctor += BuiltInSkillRegistry；materializeSkills builtin: 分流
- `service/PlanAgentService.java`：同上
- `test/.../service/PlanAgentServiceTest.java`：ctor += BuiltInSkillRegistry mock

**修改（前端）**：
- `services/onlineCode.ts`：BUILTIN_SKILLS 常量
- `pages/OnlineCode/Agents.tsx`：loadSkills 合并 builtin 选项
- `mocks/db.ts`：删内置 skill seed + suid-dev 绑 builtin:suid + attach 放行 builtin:

**修改（文档）**：
- `docs/contracts/API-CONTRACT-PHASE3.md`：§0/§1.1/§1.2/§2/§3/§4/§6
- `docs/contracts/API-CONTRACT-PRE-BUILD.md`：§10.1/§10.2

## 后续 Phase 概要（详见各 Phase 实施时再展开）

- **PR3 = Phase 4**（来源→config JSONB）—— ✅ 已完成（commit `fc22c00`）：新建 `SkillConfig`+`SkillConfigConverter extends AbstractJsonConverter`；Skill 移除 source/sourceType、加 config TEXT 列；SkillDto 改 config.origin；删 SkillSourceType 枚举（origin 前缀编码类型）；SkillHasher 参数 source→origin；前端 Skills.tsx/onlineCode.ts/db.ts/handlers.ts 改读 config.origin；迁移 V9。详见下文 PR3 段落。
- **PR4 = Phase 5**（oc_skill_file）—— ✅ 已完成（commit `a3c6fb4`）：新建 `SkillFile` 实体+DAO+`SkillFileDto`；Skill 加 `@Transient files` + `SkillService` populate(findOne/findByPage)+importSkill 持久化；`SkillMaterializer` 多文件+越界 guard；`DispatchService`/`PlanAgentService` 带 files（PlanAgentService 改注 SkillService）；迁移 V10。hash recipe 不变（files 不进 lock）。详见上文 PR4 段落。
- **PR5 = Phase 6+7**（内置技能资源化 + content vendor + 契约文档 + 全量验证）—— ✅ 已完成（commit `e7eaaa3`）：vendor suid/eadp-backend 到 `src/main/resources/skills/`；project-planning/feature-design stub+TODO；新建 `BuiltInSkillRegistry`（ClassPathResource 加载）；materializeSkills 按 `builtin:` 前缀分流解析；迁移 V11（删 oc_skill 内置种子行 + 更新 oc_agent_skill.skill_id 为 `builtin:<name>`）；前端内置技能移出 /skill 列表、Agents.tsx 多选加 builtin 选项；更新 API-CONTRACT-PHASE3.md §0/§1.1/§1.2/§2/§3/§4/§6、PRE-BUILD §10。详见上文 PR5 段落。

## PR6 = 补充技能导入 + 租户声明 + 文档清理（2026-07-04，未提交）

**前端技能导入补 files（对应后端 PR4 `ImportSkillRequest.files`）**：
- `services/onlineCode.ts`：新增 `SkillFileDto` interface；`SkillDto` 加 `files`；`importSkill` params 加 `files?`
- `pages/OnlineCode/Skills.tsx`：`ImportForm` 加 `files`；`Form.List` 动态增删辅助文件（path+content，path 走与后端一致的 `^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$)).+$` 校验）；`handleImport` 传 files；subTitle 文案改「同名重复导入返回 409」（原「重复导入幂等」漂移）；查看 modal 展示 files
- `mocks/db.ts`：新增 `SkillFileDto`；`SkillDto` 加 `files`；`importSkill` 改 name 去重（返回 `null`=冲突）+ 存 files（对齐后端 PR2，消除 hash 幂等漂移）
- `mocks/handlers.ts`：import handler 透传 files + 同名返回 fail（409 风格）

**租户隔离声明（Part2/3）**：
- 全项目 grep 确认无租户隔离实现标记（`tenant`/`租户` 命中均为 sei-core 框架能力描述：eadp-backend skill 教学内容、`bootstrap.yaml` 的 `tenant-code: global` 框架上下文字段、`PlanContent` 示例字符串）—— 不删代码（删 `tenant-code` 会破坏 sei-core ContextUtil）
- 项目 `CLAUDE.md` 新增「## 租户隔离」节：声明本项目不进行租户隔离，multica h 维度明确不做
- 本文档 h defer 描述改为「不做」

**文档清理**：
- `API-CONTRACT-PHASE3.md` §5：清理 `V3__skill_agent.sql`/`pick ONE`/`idempotent` 历史措辞，对齐 V7 join 表 + name 去重
- 本文档：修正分支名拼写 `feat/align-skill-multaca`→`multica`（全文）；PR4/PR5「未提交」→ commit `a3c6fb4`/`e7eaaa3`；「本地未推送」→ 已推送

**去 nacos 走本地配置**：
- `bootstrap.yaml`：移除 nacos server-addr/credentials/config/discovery，改 `enabled: false`
- 新建 `application.yaml`：DB(pg17 5433/postgres/lslin@32)+ redis(6379)+ kafka 占位(localhost:9092)+ `spring.autoconfigure.exclude` 12 个 nacos 自动配置类(关键：sei-cloud-nacos-starter 的 `com.changhong.sei.config.autoconfigure.NacosDiscoveryAutoConfiguration`，alibaba 的 `UtilIPv6AutoConfiguration` **不要** exclude——它创建 `InetIPv6Utils`)
- `build.gradle`：`processResources.excludes` 移除 `application.yaml` 项(原注释"从配置中心获取"已过时)
- 验证：`bootRun` 启动成功(13.8s)，curl `/skill/findOne?id=probe` 返回 401(后端响应正常，仅认证拦截)
- 非致命 WARN：`PostgreSQLDialect` 显式指定(HHH90000025，可移除)、FreeMarker 模板位置(可配 `spring.freemarker.check-template-location=false`)

**验证**：
- 前端 `pnpm build` 通过（Webpack compiled，`p__OnlineCode__Skills` 构建通过，`Form.List` 兼容 @ead/suid）
- 后端 `./gradlew :sei-online-code-service:compileTestJava :sei-online-code-service:test --tests "*SkillServiceTest" --tests "*SkillMaterializerTest" --tests "*BuiltInSkillRegistryTest"` BUILD SUCCESSFUL
- A2 local 冒烟（2026-07-04 复测，commit 5090788 之后）：
  - **nacos 阻塞已解除**（去 nacos 走本地配置，`dev-start.sh` + `local-config/application-local.yaml`，端口 8091）
  - **auth 阻塞已解除**：JWT payload 无 `exp` 字段（永不过期），实测 token 有效——`Authorization: <raw>` 与 `Bearer <token>` 均 200，无 auth 401
  - **step1 PASS**：`POST /plan/{projectId}/confirm` → Plan DRAFT→CONFIRMED（造态 seed 1 行 DRAFT Plan 含 f1/f2/f3）
  - **D15 幂等 PASS**：预置 f1 latest FD 后 re-confirm，confirm 的 `existingFeatureIds` filter 正确跳过 f1（spawnFeatureDesign skip 日志仅 f2/f3）
  - **step2 已解**：`spawnFeatureDesign` 改「no FD → 建首版 PENDING FD 行」（`PlanAgentService`），避开 confirm `@Transactional` 与 async 跨事务可见性竞争。实测 3 行 FD 全部落库
  - **step3 已解（real-claude 路径打通）**：`ClaudeRunner` 加 `--output-format json` + 提取 `result` 字段 + 剥 markdown 围栏；claude 不可用走确定性 fallback（backend rule 11）。实测 real claude 跑通：f3 落 DRAFT 带真实内容、f1 落 DRAFT（内容偏空）、f2 FAILED（real-LLM 输出未符 FeatureDesignContent schema → parse 失败）
  - 残留（非 seam，属 `TODO(oma-deferred)` 鲁棒性）：real-LLM 输出契约符合率不稳（f2），需 stream-json 协议解析 + schema 校验/重试加固
  - 结论：A2 spawn seam 两阻塞已解，step1-3 端到端打通（real claude）；f2 类失败靠后续 stream-json 加固

## 验证检查点（每个 PR 必过）

1. `./gradlew :sei-online-code-service:compileTestJava` 通过
2. `./gradlew :sei-online-code-service:test --tests "*ServiceTest"` 通过
3. 新迁移手动应用到本地 `sei-online-code` 库：`docker exec -i pg17 psql -U postgres -d sei-online-code -v ON_ERROR_STOP=1 < <migration>.sql`
4. schema 变更用 `docker exec pg17 psql -U postgres -d sei-online-code -c "\d <table>"` 确认

## 不在本次（defer / 不做）

- **f** UUID 主键：受 sei-core `BaseAuditableEntity` 约束，影响全实体（defer epic）
- **h** workspace 多租户：本项目明确不进行租户隔离（见项目 CLAUDE.md），不做
