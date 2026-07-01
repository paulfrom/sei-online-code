All backend code must be developed following the eadp-backend skill (`.claude/skills/eadp-backend/SKILL.md`).
When generating code: add comments for every method and core logic. Multi-language keys must be considered and applied.

## Build Commands

```bash
# Build the entire project
./gradlew build

# Build without tests
./gradlew build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew :sei-online-code-service:test --tests "com.changhong.onlinecode.controller.reporting.HelloControllerTest"

# Publish API module to Nexus (credentials in gradle.properties)
./gradlew :sei-online-code-api:publish
```

| Item | Value                                        |
|------|----------------------------------------------|
| Group / artifact | `com.changhong.onlinecode`                   |
| Version | `7.0.1` (from `gradle.properties`)           |
| Java | 21                                           |
| Gradle | 8.5 (wrapper — always use `./gradlew`)       |
| Spring Boot | 3.3.9                                        |
| Spring Cloud | 2023.0.5                                     |
| SEI platform | `7.+` (`sei_version`, `util_version`)        |
| Service JAR | `sei-online-code-service/build/libs/sei-online-code.jar` |
| API JAR | `sei-online-code-api` plain library (published to Nexus) |

## Architecture

### Multi-Module Layout

```
sei-online-code-api/       →  Feign API contracts, DTOs, enums; published for other services
sei-online-code-service/   →  Spring Boot app: controllers, services, DAOs, entities
docs/sql/                   →  PostgreSQL / MySQL DDL for reporting tables
```

- **sei-online-code-api**: `@FeignClient` interfaces under `com.changhong.onlinecode.api`, DTOs under `com.changhong.onlinecode.dto`, shared enums.
- **sei-online-code-service**: Implements API interfaces in `controller` classes; depends on `sei-online-code-api` and SEI starters.
- Other services consume **sei-online-code-api** to call sei-online-code via Feign (`name = ${sei.feign.client.sei-online-code:sei-online-code}`).

### Standard EADP Layering

```
Entity → DAO → DAOImpl → Service → Controller → API (Feign)
                      ↕
                    DTO
```

Controllers **implement** the API interface; services extend `BaseEntityService` and do **not** implement the API. API I/O uses DTOs only.


### EADP / SEI Platform

| Concern | Usage in sei-online-code |
|---------|--------------------------|
| Session | `ContextUtil.getSessionUser()`, `SessionUser` |
| API response | `ResultData<T>`, `ResultDataUtil` |
| Pagination | `Search`, `PageResult`, `FindByPageApi` |
| CRUD stack | `BaseEntityApi` / `BaseEntityController` / `BaseEntityService` / `BaseEntityDao` |
| Distributed lock | `@SeiLock` on `deliverPlan` / `withdrawPlan` (anti duplicate dispatch) |
| Logging | `@Log`, `LogUtil.bizLog()` |
| BPM | `sei-bpm-sdk`; `ReportingPlanTaskApi` extends `BpmDefaultBaseApi` |
| Serial numbers | `sei-serial-sdk` via `SerialGenerator` |
| Testing | `BaseUnit5Test` + `sei-test-starter` (JUnit 5) |

**sei-online-code-service** dependencies: `sei-cloud-nacos-starter`, `sei-bpm-sdk`, `sei-serial-sdk`, MySQL connector (version from `gradle.properties`).

**sei-online-code-api** dependencies: `sei-core-api`, `sei-bpm-sdk`.

所有的dto和api都必须加上swagger注解，以提供完整的字段命名和接口说明