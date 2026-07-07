# Online Code Cleanup Contract

> Status: active cleanup contract. Supersedes the old Spec -> Iteration -> Dispatch -> Preview product flow for current development.

## Goal

Keep only the useful product flow and unified product language:

`Project Description` -> `Overview Design (Plan)` -> `Module Detailed Design (Spec)` -> `FeatureDesign` -> `FeatureDesignBuildService`

The old `Iteration` dispatch/preview loop is no longer a product path. Runtime execution still uses `Task`, `Run`, CLI runners, and run-log WebSocket records as the build substrate for feature-design coding.

Public UI, API summaries, and docs must use the following terms:

- `Project.design`: 项目描述
- `Plan`: 概要设计
- `Spec`: 详细设计，scoped to one module
- `FeatureDesign`: 功能设计
- `FeatureDesignBuildService`: 编码执行

Low-level database tables and Java class names may retain `Spec` as a compatibility implementation detail. User-facing copy must not call it "Spec".

## Kept Domains

### Project

- Stores project name and project description.
- New projects start the pre-build flow by generating an overview design.
- Project state exposed to the frontend must be derived from Plan/FeatureDesign state for pre-build screens.

### Plan

- Stores overview design versions generated from the project description.
- Overview design divides the project into modules and module features.
- Confirmed Plan starts module detailed design generation.

### Spec

- Stores module detailed design versions.
- Each detailed design belongs to one module from the latest confirmed overview design.
- Confirming detailed design starts FeatureDesign generation for that module's features.
- Confirming detailed design must not create or route to an Iteration dispatch flow.

### FeatureDesign

- Stores one coding design per feature.
- Confirmed FeatureDesign is the unit of coding execution.

### Task / Run

- Kept as internal runtime records for FeatureDesign coding.
- `FeatureDesignBuildService` creates a `Task` and a `Run` per build.
- External task dispatch screens and old iteration task slicing are removed.

## Removed Product Flow

The following flow is removed from user-facing behavior and public API use:

`Detailed Design CONFIRMED` -> `Iteration DISPATCHING` -> `DispatchService` -> `Task[]` from pages -> `Preview` -> `Accept/Optimize/Retry`

Removed operations:

- Iteration dispatch
- Iteration deploy
- Iteration merge
- Iteration accept
- Iteration cancel
- Iteration retry
- Project optimize feedback loop
- Frontend dispatch page
- Frontend preview page
- Frontend iteration timeline actions

## API Shape

### Project API

Keep:

- `POST /project/save`
- `GET /project/findOne`
- `POST /project/findByPage`
- `POST /project/refineSpec` as a compatibility endpoint that starts overview design generation
- `GET /project/state`
- `POST /project/{projectId}/build`

Remove:

- `POST /project/optimize`

### Spec API

Keep:

- Spec CRUD/query endpoints currently used by the detailed design review page.
- `POST /spec/confirm`: confirms a module detailed design and starts FeatureDesign generation for that module.
- `POST /spec/{projectId}/regenerate`: regenerates module detailed design.

### Iteration API

Remove public Iteration API and controller. Iteration is not part of the current product flow.

### Task / Run API

Task and Run entities remain internal runtime records. Public list/detail endpoints can be removed unless directly required by the current FeatureDesign build UI.

The current build UI subscribes to run logs by `featureDesignId` and `runId`; it does not require Task/Run list pages.

## State Rules

Pre-build state is computed from latest Plan, module detailed design, and latest FeatureDesign rows:

| State | Rule |
| --- | --- |
| `DRAFTING` | No latest Plan exists |
| `PLANNING` | latest Plan is `GENERATING` or `DRAFT` |
| `DESIGNING` | latest Plan is `CONFIRMED` and module detailed designs or FeatureDesigns are empty/incomplete |
| `READY_TO_BUILD` | latest Plan is `CONFIRMED` and all latest FeatureDesigns are `CONFIRMED` |
| `FAILED` | latest Plan is `FAILED`, any module detailed design is `FAILED`, or any latest FeatureDesign is `FAILED` |

Persisted legacy lifecycle values such as `DISPATCHING`, `DEVELOPING`, `MERGING`, `DEPLOYING`, `PREVIEW`, `ACCEPTED`, and `CANCELLED` must not drive new frontend routing.

## Database Cleanup

When historical data is abandoned, clear business data in dependency order:

1. `oc_run`
2. `oc_task`
3. `oc_feature_design`
4. `oc_plan`
5. `oc_iteration`
6. `oc_spec`
7. `oc_project`

Keep configuration and seed tables:

- `oc_agent`
- `oc_skill`
- `oc_skill_file`
- `oc_agent_skill`
- `oc_platform_config`
- `flyway_schema_history`

Migration files are not rewritten in this cleanup. Existing databases can be reset by clearing rows; new databases still need historical migrations to reach the current schema.
