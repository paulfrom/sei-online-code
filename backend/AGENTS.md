# Backend Contributor Guide

## Scope
This module contains the Java backend for `sei-online-code`. Work under `backend/` only and keep frontend concerns out of this module.

## Module Layout
- `sei-online-code-api/`: shared API contracts, DTOs, and enums for external callers
- `sei-online-code-service/`: Spring Boot service implementation
- `sei-online-code-service/src/main/java`: business code
- `sei-online-code-service/src/main/resources/db/migration`: Flyway migrations
- `sei-online-code-service/src/test/java`: JUnit tests
- `gradlew`, `build.gradle`, `settings.gradle`: Gradle entrypoints

## Build and Run
Run commands from `backend/`.

```bash
./gradlew test
./gradlew :sei-online-code-service:bootRun
./gradlew build
./gradlew :sei-online-code-service:test --tests "com.changhong.onlinecode.service.ProjectServiceTest"
```

Use the wrapper only. The project targets Java 21. Local startup also depends on `sei-online-code-service/local-config/application-local.yaml`.

## Coding Rules
- Follow the `eadp-backend` skill and existing sei-core layering.
- Keep the structure aligned with `entity -> dao -> service -> controller`.
- Put shared contracts in `sei-online-code-api`; keep implementation details in `sei-online-code-service`.
- Use PascalCase for classes, camelCase for methods and fields, and UTF-8 source files.
- Add Swagger annotations to API and DTO fields when introducing or changing external contracts.
- This repository is single-tenant: do not add `tenant_code`, `ITenant`, or tenant filters.

## Testing
Backend tests run on JUnit Platform. Place tests in `src/test/java` and name them `*Test.java`. Add or update tests for service logic, converters, DAO behavior, and validation changes. Prefer focused tests near the changed package.

## Acceptance Rhythm
For planned work, acceptance must be represented as explicit `test-agent` tasks in the PM plan. Do not trigger `test-agent` implicitly after every backend coding task. PM decides each acceptance task's scope and dependencies based on the requirement and risk.

## Commits and PRs
Use commit messages in the format `feat: description`, `fix: description`, `refactor: description`, and so on. Keep PRs scoped, describe impacted modules, mention schema or config changes, and include sample requests or screenshots when the API behavior affects the UI.
