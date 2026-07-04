# Service & Query System

Patterns and conventions for service implementations and the search/query system in EADP/SEI backend development.

## Positioning

The service layer is the main place for business rules, data validation, persistence orchestration, cascading operations, cache handling, and transaction boundaries.

Key points:
- Services extend `BaseEntityService<T>` (or `BaseTreeService<T>`), they do NOT implement the API interface.
- Controllers implement the API contract and call services.
- Services mostly work with entities, framework result types, `Search`, and DAO objects.
- DTO conversion is handled in controllers, not in services.
- Services return `OperateResult`/`OperateResultWithData` for writes; controllers adapt to `ResultData`.

## Core Conventions

### 1. Extend the framework base service

```java
@Service
public class EmployeeService extends BaseEntityService<Employee> {
}

@Service
public class OrganizationService extends BaseTreeService<Organization> {
}

@Service
public class ProjectService extends BaseEntityService<Project> {
}
```

Use:
- `BaseEntityService<T>` for normal entity modules
- `BaseTreeService<T>` for tree-structured entities

Do NOT default to `implements XxxApi` — that is the controller's job.

### 2. Override getDao()

Standard services wire their main DAO into the framework:

```java
@Override
protected BaseEntityDao<Employee> getDao() {
    return employeeDao;
}

@Override
protected BaseTreeDao<Organization> getDao() {
    return organizationDao;
}
```

This is a required integration point for framework CRUD, query, and tree behavior.

### 3. Keep service dependencies domain-oriented

Real services depend on:
- Their main DAO
- Related DAOs
- Sibling services
- Infrastructure helpers (SerialGenerator, AsyncRunUtil, etc.)
- Cache, async, and integration managers when needed

### 4. Put write operations under transactions

```java
@Transactional(rollbackFor = Exception.class)
public OperateResultWithData<Project> save(Project entity) {
    // validate and enrich
    return super.save(entity);
}
```

Use transactions on data-changing methods. Read methods normally do not require them.

## Return Type Conventions

| Method Type | Return Type | Example |
|---|---|---|
| Save | `OperateResultWithData<T>` | `save(entity)` |
| Delete, move, copy | `OperateResult` | `delete(id)` |
| Framework wrapper | `ResponseData<T>` | When caller expects that wrapper |
| API-facing | `ResultData<T>` | When method directly serves controller output |

Do NOT force every service method to return `ResultData` — use `OperateResult` or `OperateResultWithData` for framework-style write flows.

```java
public OperateResultWithData<Employee> save(Employee entity) { ... }
public OperateResult delete(String projectId) { ... }
public ResponseData<List<Organization>> findByCorpCode(String corporationCode) { ... }
public ResultData<ProjectInfoDto> getProjectInfo(String projectId) { ... }
```

## Lifecycle Hooks

Override these in the service instead of scattering logic everywhere:

### preInsert — Before Create

```java
@Override
protected OperateResultWithData<FooEntity> preInsert(FooEntity entity) {
    if (getService().isExistsByProperty("code", entity.getCode())) {
        return OperateResultWithData.operationFailureWithData(entity, "Code already exists");
    }
    return super.preInsert(entity);
}
```

Default behavior: checks `ICodeUnique` if implemented.

### preUpdate — Before Update

Default behavior: checks `ICodeUnique` if implemented.

### preDelete — Before Delete

Common pattern for referential and business checks:

```java
@Override
protected OperateResult preDelete(String id) {
    if (positionService.isExistsByProperty("organization.id", id)) {
        return OperateResult.operationFailure("00042");
    }
    return super.preDelete(id);
}
```

### Override save

Use to:
- Generate codes or serial numbers
- Enforce uniqueness
- Initialize defaults
- Perform parent/child state checks
- Trigger side effects after successful save

### Override delete

Use when deletion requires extra cleanup, cascades, cache eviction, or integration callbacks.

## Validation Patterns

### Validate before calling super.save

```java
@Override
@Transactional(rollbackFor = Exception.class)
public OperateResultWithData<Project> save(Project entity) {
    Project existProject = dao.findFirstByProperty(Project.FIELD_NAME, entity.getName());
    if (Objects.nonNull(existProject) && !StringUtils.equals(existProject.getId(), entity.getId())) {
        return OperateResultWithData.operationFailure("project_031", entity.getName());
    }
    return super.save(entity);
}
```

### Prefer framework result failures over ad hoc exceptions

```java
return OperateResult.operationFailure("00042");
return OperateResultWithData.operationFailure("project_001");
return ResultData.fail(ContextUtil.getMessage("account_0016"));
```

Throw exceptions only when the failure is truly exceptional.

## Search & Query System

### Search Object

`Search` is the unified query configuration object.

```java
Search search = Search.of()
    .addFilter(SearchFilter.eq("status", "ACTIVE"))
    .addFilter(SearchFilter.like("name", keyword))
    .addSortOrder(new SearchOrder("createdDate", SearchOrder.Direction.DESC))
    .setPageInfo(PageInfo.of(1, 20));
```

Search fields:
- `quickSearchProperties` — Collection of field names for quick search
- `quickSearchValue` — Quick search keyword (applies LIKE to all quickSearchProperties)
- `filters` — List of `SearchFilter` conditions
- `sortOrders` — List of `SearchOrder` definitions
- `pageInfo` — `PageInfo` with page/rows

### SearchFilter Operators

| Operator | SQL Equivalent | Factory Method |
|---|---|---|
| EQ | = | `SearchFilter.eq(field, value)` |
| NE | != | `SearchFilter.ne(field, value)` |
| LK | LIKE %val% | `SearchFilter.like(field, value)` |
| LLK | LIKE val% | `SearchFilter.leftLike(field, value)` |
| RLK | LIKE %val | `SearchFilter.rightLike(field, value)` |
| NC | NOT LIKE %val% | — |
| GT | > | `SearchFilter.gt(field, value)` |
| GE | >= | `SearchFilter.ge(field, value)` |
| LT | < | `SearchFilter.lt(field, value)` |
| LE | <= | `SearchFilter.le(field, value)` |
| IN | IN (...) | `SearchFilter.in(field, collection)` |
| NOTIN | NOT IN (...) | `SearchFilter.notin(field, collection)` |
| BT | BETWEEN a AND b | — (pass array/collection of 2 values) |
| NU | IS NULL | `SearchFilter.isNull(field)` |
| NN | IS NOT NULL | `SearchFilter.isNotNull(field)` |
| BK | IS NULL OR ='' | `SearchFilter.isBlank(field)` |
| NB | IS NOT NULL AND !='' | `SearchFilter.notBlank(field)` |

Constructor shortcut: `new SearchFilter("name", "value")` defaults to EQ operator.

### Date Handling in Queries

BaseDaoImpl handles date queries specially:
- If the date has no time component (00:00:00), EQ becomes a range: `>= date AND < date+1`
- LE with zero-time becomes `< date+1` (includes the full day)
- Supports `java.util.Date`, `LocalDate`, `LocalDateTime` type conversion

### Sorting

Default sort order (when no `SearchOrder` specified):
1. If implements `IRank`: ascending by `rank`
2. If extends `BaseAuditableEntity`: descending by `createdDate`
3. If extends `BaseEntity`: descending by `id`

When `SearchOrder` is provided, `IRank` sort is still appended as secondary.

### Pagination

```java
// Request
PageInfo pageInfo = PageInfo.of(1, 20);  // page 1, 20 rows
search.setPageInfo(pageInfo);

// Response
PageResult<FooEntity> result = service.findByPage(search);
result.getPage();     // current page
result.getRecords();  // total count
result.getTotal();    // total pages
result.getRows();     // ArrayList<FooEntity> data
```

### SearchParam and QuickSearchParam

Front-end oriented parameter objects:
- `SearchParam` — advanced search with filters/sortOrders/pageInfo
- `QuickSearchParam` — quick search with quickSearchValue/quickSearchProperties/sortOrders/pageInfo
- Both can be passed to `new Search(param)` constructor for conversion

### Query Usage in Services

```java
Search search = new Search(param);
search.addFilter(new SearchFilter("organization.id", param.getOrganizationId(), SearchFilter.Operator.EQ));
return findByPage(search);
```

### Service Layer Query Methods (inherited from BaseService)

| Method | Description | Returns |
|---|---|---|
| `save(entity)` | Create or update (checks `isNew()`) | OperateResultWithData |
| `delete(id)` | Delete by ID (soft if `ISoftDelete`) | OperateResult |
| `findOne(id)` | Find by ID | T or null |
| `findAll()` | Find all (respects tenant/project/soft-delete) | List<T> |
| `findByIds(ids)` | Find by ID collection (filters soft-deleted) | List<T> |
| `findAllUnfrozen()` | Find all not frozen | List<T> |
| `findByProperty(prop, val)` | Single property EQ, fails if multiple | T or null |
| `findFirstByProperty(prop, val)` | Single property EQ, returns first match | T or null |
| `findListByProperty(prop, val)` | Single property EQ | List<T> |
| `isExistsByProperty(prop, val)` | Existence check | boolean |
| `findByFilter(SearchFilter)` | Single filter | List<T> |
| `findByFilters(Search)` | Multi-filter with sort | List<T> |
| `findOneByFilters(Search)` | Multi-filter, fails if multiple | T or null |
| `findFirstByFilters(Search)` | Multi-filter, returns first | T or null |
| `findByPage(Search)` | Paginated query | PageResult<T> |
| `count(Search)` | Count with filters | long |

### Data permission methods (in BaseEntityService)

- `getUserAuthorizedEntities(featureCode)` — current user's authorized entities
- `findAllAuthEntityData()` — all data permission entities
- `getAuthEntityDataByParentEntityId(parentId)` — cascading data permission

## EDM Integration

### Bind documents on save

```java
@Override
@Transactional(rollbackFor = Exception.class)
public OperateResultWithData<Contract> save(Contract entity) {
    OperateResultWithData<Contract> result = super.save(entity);
    if (result.successful()) {
        if (StringUtils.isNotBlank(entity.getMainAttachmentBindingId())) {
            documentManager.bindDocuments(entity.getMainAttachmentBindingId(), entity.getMainDocIds());
        }
    }
    return result;
}
```

### Unbind documents on delete

```java
@Override
protected OperateResult preDelete(String id) {
    Contract entity = getDao().findOne(id);
    if (entity != null && StringUtils.isNotBlank(entity.getMainAttachmentBindingId())) {
        documentManager.unbindAllDocuments(entity.getMainAttachmentBindingId());
    }
    return super.preDelete(id);
}
```

## BPM Integration

### beforeStartFlow — Validate business data before workflow starts

```java
@Override
public ResultData<Void> beforeStartFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    Contract entity = getDao().findOne(businessId);
    if (entity.getAmount() == null || entity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        return ResultData.fail("金额必须大于0才能发起审批");
    }
    return ResultData.success();
}
```

### afterStartFlow / afterEndFlow — Update status in callbacks

```java
@Override
public ResultData<Void> afterStartFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    Contract entity = getDao().findOne(businessId);
    entity.setFlowStatus(FlowStatus.InReview);
    getDao().save(entity);
    return ResultData.success();
}

@Override
public ResultData<Void> afterEndFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    Contract entity = getDao().findOne(businessId);
    entity.setFlowStatus(FlowStatus.End);
    getDao().save(entity);
    return ResultData.success();
}
```

## Cache and After-Commit Handling

### Use framework cache helpers

```java
return cacheBuilder.get(CACHE_NAME_PROJECT, projectId, () -> { ... });
cacheBuilder.evict(CACHE_NAME_PROJECT, projectId);
```

### Prefer after-commit callbacks for cache eviction

```java
TransactionUtil.afterCommit(() -> {
    cacheBuilder.evict(SeiDefaultCacheKey.CACHE_NAME_PROJECT, projectId);
});
```

## Entity Enrichment Patterns

Services often enrich entities after DAO reads:
- Load profile fields
- Fill display or remark fields
- Attach related account or organization information
- Normalize missing attribute value rows

## Cascading and Multi-Service Orchestration

Service methods frequently orchestrate multiple services and DAOs in one transaction:
- Saving employee data also creates/updates user, profile, and account records
- Deleting a project also removes project members
- Copying a position optionally copies feature-role relations

## Service Responsibility Boundaries

Services should:
- Own core business validation
- Coordinate multiple repositories or services
- Define transaction boundaries
- Build framework queries
- Perform cascade maintenance
- Manage cache and integration side effects

Services should NOT:
- Expose HTTP annotations
- Define request mappings
- Depend on controller concerns
- Perform DTO transport shaping as their main purpose

## Best Practices

1. Extend `BaseEntityService` or `BaseTreeService` for standard business entities.
2. Override `getDao()` to connect the service to the framework.
3. Put write operations under `@Transactional(rollbackFor = Exception.class)`.
4. Use lifecycle hooks (`preInsert`, `preUpdate`, `preDelete`) for validation.
5. Use `OperateResult` and `OperateResultWithData` for framework-style write flows.
6. Use `Search`, `SearchFilter`, and `PageResult` for business query composition.
7. Keep DTO conversion primarily in controllers, not in services.
8. Handle cache eviction and after-commit side effects explicitly when the module uses caching.
9. Allow pragmatic orchestration across multiple services and DAOs.
10. Keep transport concerns out of the service layer.
