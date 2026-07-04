# API Interface

Patterns and conventions for API interfaces in EADP/SEI backend development.

## Positioning

API interfaces are Feign client contracts with Spring MVC annotations + Swagger `@Operation`. They define the transport contract only — no business logic, persistence, or transaction concerns.

## Base API Interfaces

### BaseEntityApi<T extends BaseEntityDto> — Minimal CRUD

```java
ResultData<T> save(@RequestBody @Valid T dto)       // POST /save
ResultData<?> delete(@PathVariable String id)        // DELETE /delete/{id}
ResultData<T> findOne(@RequestParam String id)       // GET /findOne
```

### FindAllApi<T> — List All

```java
ResultData<List<T>> findAll()                        // GET /findAll
ResultData<List<T>> findAllUnfrozen()                // GET /findAllUnfrozen
```

### FindByPageApi<T> — Paginated Query

```java
ResultData<PageResult<T>> findByPage(@RequestBody Search search)  // POST /findByPage
```

### BaseTreeApi<T> — Tree Operations (extends BaseEntityApi)

```java
ResultData<?> move(@RequestBody TreeNodeMoveParam)   // POST /move
ResultData<List<T>> getAllRootNode()                  // GET /getAllRootNode
ResultData<T> getTree(@RequestParam String nodeId)    // GET /getTree
ResultData<List<T>> getChildrenNodes(...)             // GET /getChildrenNodes
ResultData<List<T>> getParentNodes(...)               // GET /getParentNodes
ResultData<List<T>> queryTree(@RequestParam String)   // GET /queryTree
```

## Composing Custom API Interfaces

Compose base interfaces as needed:

```java
@Valid
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}", path = FooApi.PATH)
public interface FooApi extends BaseEntityApi<FooDto>, FindAllApi<FooDto>, FindByPageApi<FooDto> {
    String PATH = "foo";

    @PostMapping(path = "query", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "分页查询业务数据")
    ResultData<PageResult<FooDto>> query(@RequestBody FooQuickQueryParam queryParam);

    @GetMapping(path = "findByCode")
    @Operation(summary = "通过代码获取业务数据")
    ResultData<FooDto> findByCode(@RequestParam("code") String code);

    @PostMapping(path = "findByIds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按ID集合查询业务数据")
    ResultData<List<FooDto>> findByIds(@RequestBody List<String> ids);
}
```

### Common Compositions

**CRUD + paging module**:
```java
public interface EmployeeApi extends BaseEntityApi<EmployeeDto>,
        FindByPageApi<EmployeeDto>,
        ExportTableDataApi {
}
```

**Tree module**:
```java
public interface MenuApi extends BaseTreeApi<MenuDto> {
}
```

**Rich aggregate module**:
```java
public interface OrganizationApi extends BaseTreeApi<OrganizationDto>,
        FindByPageApi<OrganizationDto>,
        DataAuthTreeEntityApi<OrganizationDto>,
        ExportTableDataApi {
}
```

**Non-CRUD protocol module** (does NOT extend BaseEntityApi):
```java
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}")
public interface AuthenticationApi {
    // login, logout, token, SSO, OAuth endpoints explicitly
}
```

## Core Conventions

### 1. Use @FeignClient as the API entry marker

```java
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}", path = ProjectApi.PATH)
public interface ProjectApi extends BaseEntityApi<ProjectDto> {
    String PATH = "project";
}
```

- Prefer a `PATH` constant for the module root path.
- Use `path = XxxApi.PATH` for consistency and reuse.

### 2. Put HTTP contract annotations on every custom method

- `@GetMapping` for simple reads with query params.
- `@PostMapping(..., consumes = MediaType.APPLICATION_JSON_VALUE)` for complex queries and request bodies.
- `@DeleteMapping` when deleting by path variable.

Examples:

```java
@GetMapping(path = "findByCode")
ResultData<EmployeeDto> findByCode(@RequestParam("code") String code);

@PostMapping(path = "queryEmployees", consumes = MediaType.APPLICATION_JSON_VALUE)
ResultData<PageResult<EmployeeDto>> queryEmployees(@RequestBody EmployeeQuickQueryParam queryParam);

@DeleteMapping(path = "deleteAttrDefine/{id}")
ResultData<Void> deleteAttrDefine(@PathVariable("id") String id);
```

### 3. Standardize return types

Business APIs usually return:

- `ResultData<Dto>`
- `ResultData<List<Dto>>`
- `ResultData<PageResult<Dto>>`
- `ResultData<Void>`

Do not default to raw DTOs, raw lists, or `ResponseEntity` unless the real endpoint behavior requires it.

### 4. Add @Operation metadata

```java
@Operation(summary = "通过代码获取业务数据", description = "通过代码获取业务数据")
```

- Keep `summary` short and action-oriented.
- Keep `description` as a fuller restatement when useful.

### 5. Validation on API contract

```java
@Valid
public interface ProjectApi extends BaseEntityApi<ProjectDto> {
}

ResultData<SessionUserResponse> login(@RequestBody @Valid LoginRequest loginRequest);
```

## BPM API

When the module enters workflow, the API extends `BpmDefaultBaseApi`:

```java
@Valid
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}", path = ContractApi.PATH)
public interface ContractApi extends BaseEntityApi<ContractDto>,
        FindByPageApi<ContractDto>,
        BpmDefaultBaseApi<ContractDto> {
    String PATH = "contract";
}
```

## Naming Conventions

- Interface names use `XxxApi`.
- DTO names use `XxxDto`.
- Query objects use `XxxQueryParam` or `XxxQuickQueryParam`.
- Root path usually matches module name.
- Method names are verb-first and business-specific: `findByCode`, `findByIds`, `queryEmployees`.

Avoid vague names like `getData`, `handle`, or `process`.

## What the API Should NOT Include

- Transaction logic
- DAO access
- BeanMapper/ModelMapper conversion
- EDM binding logic
- BPM state update logic

Those belong in the Service or Controller layers.

## Best Practices

1. Treat API interfaces as Feign contracts, not service implementation abstractions.
2. Import base interfaces from `com.changhong.sei.core.api`.
3. Reuse existing base APIs before adding custom CRUD/page signatures.
4. Use `@FeignClient` and a stable module `path`.
5. Add REST mapping annotations on every custom method.
6. Use `MediaType.APPLICATION_JSON_VALUE` for POST methods with JSON request bodies.
7. Keep return values wrapped in `ResultData`.
8. Add `@Operation` metadata for public API methods.
9. Put validation annotations on request bodies and parameters.
10. Keep the interface free of business logic, persistence logic, and transaction concerns.
