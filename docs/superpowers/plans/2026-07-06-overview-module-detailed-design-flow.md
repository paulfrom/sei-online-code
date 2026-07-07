# Overview + Module Detailed Design Flow Plan

## Goal

Unify product semantics and execution flow:

`项目描述 -> 概要设计 -> 模块详细设计 -> 功能设计 -> 编码执行`

`Spec` remains an internal compatibility name for the detailed-design entity/table. Public API summaries, docs, and UI copy use `详细设计`.

## Backend Steps

1. Treat `ProjectApi.refineSpec` as a compatibility endpoint that starts overview design generation through `PlanService.regenerate`.
2. Extend overview design content so planning output can describe modules and module-scoped features.
3. Change `PlanService.confirm` so confirming overview design creates module detailed-design records instead of spawning FeatureDesign directly.
4. Change detailed-design confirmation so `SpecService.confirmSpec` spawns FeatureDesign only for the confirmed module.
5. Add module metadata to `Spec` / `SpecDto` so each detailed design can be reviewed independently.

## Frontend Steps

1. Change project-list action text from requirement/spec language to overview-design language.
2. Use the compatibility `refineSpec` call as `generateOverviewDesign` in frontend service naming.
3. Rename user-facing Spec page copy to detailed-design review.
4. Keep existing routes as compatibility paths unless a route rename is required by behavior.

## Verification

1. Backend compile for API and service modules.
2. Focused backend service tests.
3. Frontend production build.
4. `git diff --check`.
