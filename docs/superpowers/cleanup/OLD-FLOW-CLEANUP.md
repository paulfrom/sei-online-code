# 旧流程下线清理清单

> 待需求驱动新流程（Project → Requirement → OverviewDesign → DetailedDesign → CodingTask → Run）稳定运行后，执行本清理。

## 后端可删除项

| 类别 | 路径/名称 | 说明 |
|------|-----------|------|
| Entity | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Plan.java` | 旧规划实体 |
| Entity | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Spec.java` | 旧概要设计实体 |
| Entity | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/FeatureDesign.java` | 旧功能设计实体 |
| Entity | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Task.java` | 旧任务实体 |
| DAO | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/PlanDao.java` | 及其可能存在的 DAOImpl |
| DAO | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/SpecDao.java` | 及其可能存在的 DAOImpl |
| DAO | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/FeatureDesignDao.java` | 及其可能存在的 DAOImpl |
| DAO | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/TaskDao.java` | 及其可能存在的 DAOImpl |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanAgentService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/SpecService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/SpecAgentService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/TaskService.java` | |
| Service | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/ProjectStateService.java` | 若仅用于旧生命周期聚合状态 |
| Controller | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/PlanController.java` | |
| Controller | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/SpecController.java` | |
| Controller | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/FeatureDesignController.java` | |
| Controller | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/TaskController.java` | |
| API | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/PlanApi.java` | |
| API | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/SpecApi.java` | |
| API | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/FeatureDesignApi.java` | |
| API | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/TaskApi.java` | |
| DTO | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/PlanDto.java` | 及其子包 dto/plan/ |
| DTO | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/SpecDto.java` | 及其子包 dto/spec/ |
| DTO | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/FeatureDesignDto.java` | 及其子包 dto/featuredesign/ |
| DTO | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/TaskDto.java` | |
| DTO | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/ProjectStateDto.java` | 若仅用于旧状态 |
| Enums | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/PlanStatus.java` | |
| Enums | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/SpecState.java` | |
| Enums | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/FeatureDesignStatus.java` | |
| Enums | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/FeatureDesignBuildStatus.java` | |
| Enums | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/TaskState.java` | |
| Enums | `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/LifecycleState.java` | 若 Project 不再使用旧生命周期 |
| Converter | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/converter/PlanContentConverter.java` | |
| Converter | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/converter/FeatureDesignContentConverter.java` | |
| Converter | `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/converter/Spec*Converter.java` | |
| SQL migrations | 旧表迁移文件 V1/V6 中不再需要的部分 | 历史文件不动，新增清理迁移 |

## 前端可删除项

| 路径 | 说明 |
|------|------|
| `frontend/src/pages/OnlineCode/PlanTab.tsx` | 旧概要设计 tab |
| `frontend/src/pages/OnlineCode/DetailedDesignTab.tsx` | 旧详细设计 tab |
| `frontend/src/pages/OnlineCode/FeatureDesignTab.tsx` | 旧功能设计 tab |
| `frontend/src/pages/OnlineCode/FeatureDesignEditor.tsx` | 旧功能设计编辑器 |
| `frontend/src/pages/OnlineCode/BuildActions.tsx` | 旧批量构建入口 |
| `frontend/src/pages/OnlineCode/Spec.tsx` | 旧 Spec 页面 |
| `frontend/src/services/onlineCode.ts` 中旧 Spec/Plan/FeatureDesign 相关方法 | |
| `frontend/src/mocks/handlers/plan.ts` | 旧 plan mock |
| `frontend/src/mocks/handlers/featureDesign.ts` | 旧 featureDesign mock |

## 数据库下线迁移

待确认无线上旧数据依赖后，新增 SQL 迁移脚本：

1. 删除旧表：`oc_plan`、`oc_spec`、`oc_feature_design`、`oc_task`（确认旧 `oc_run` 的 `task_id` 引用已迁移到 `coding_task_id`）。
2. 删除 `oc_project` 中不再使用的列：`state`、`current_spec_id`。
3. 删除 `oc_run` 中旧 `task_id` 列（迁移完成后）。

## 验证命令

```bash
./gradlew :sei-online-code-service:test
./gradlew :sei-online-code-service:compileJava
pnpm -C frontend build
rg "PlanApi|SpecApi|FeatureDesignApi|TaskApi|PlanController|SpecController|FeatureDesignController|TaskController" backend/sei-online-code-api backend/sei-online-code-service
rg "PlanTab|DetailedDesignTab|FeatureDesignTab|BuildActions|/online-code/spec" frontend/src frontend/config
```

## 前置条件

- [ ] 新流程完成端到端验收（创建项目 → 需求 → PRD → 概览设计 → 详细设计 → 编码任务 → Run）。
- [ ] 旧数据已归档或确认可丢弃（本重构不做旧数据迁移）。
- [ ] 所有前端入口已移除旧流程链接至少一个迭代。
- [ ] 后端旧 API 无外部服务通过 Feign 调用。
