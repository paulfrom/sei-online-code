# DAO Implementation

Patterns and conventions for DAO extension implementations in EADP/SEI backend development.

## Positioning

`DaoImpl` classes implement `XxxExtDao`, NOT the main `XxxDao` interface. Most DAOs do NOT need a `DaoImpl` — only write one when custom JPQL/EntityManager logic is truly needed.

Key points:
- `XxxDao` is the primary Spring Data JPA repository interface — no manual implementation needed.
- `XxxExtDao` declares custom extension methods.
- `impl/XxxDaoImpl` implements only the extension interface.
- `DaoImpl` extends `BaseEntityDaoImpl<Entity>` and uses `EntityManager`, `QuerySql`, `PageResultUtil`.
- This codebase is JPA-only — do NOT reference MyBatis patterns.

## Core Conventions

### 1. Implement XxxExtDao, not XxxDao

```java
public class EmployeeDaoImpl extends BaseEntityDaoImpl<Employee> implements EmployeeExtDao {
}

public class PositionDaoImpl extends BaseEntityDaoImpl<Position> implements PositionExtDao {
}
```

The main `XxxDao` remains a Spring Data repository interface and does not need a manual implementation class.

### 2. Extend BaseEntityDaoImpl<Entity>

This provides:
- Access to the shared `entityManager`
- Framework helper methods such as `findFirstByFilters`
- Entity metadata integration

### 3. Use constructor injection with EntityManager

```java
public EmployeeDaoImpl(EntityManager entityManager) {
    super(Employee.class, entityManager);
}
```

## Recommended DaoImpl Template

```java
package com.changhong.sei.xxx.dao.impl;

import com.changhong.sei.core.dao.impl.BaseEntityDaoImpl;
import com.changhong.sei.core.dao.impl.PageResultUtil;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.entity.search.QuerySql;
import com.changhong.sei.xxx.dao.YourExtDao;
import com.changhong.sei.xxx.dto.search.YourQuickQueryParam;
import com.changhong.sei.xxx.entity.YourEntity;
import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.Map;

public class YourDaoImpl extends BaseEntityDaoImpl<YourEntity> implements YourExtDao {

    public YourDaoImpl(EntityManager entityManager) {
        super(YourEntity.class, entityManager);
    }

    @Override
    public PageResult<YourEntity> query(YourQuickQueryParam queryParam) {
        String select = "select e ";
        String fromAndWhere = "from YourEntity e where e.tenantCode = :tenantCode ";
        Map<String, Object> params = new HashMap<>();
        params.put("tenantCode", queryParam.getTenantCode());

        QuerySql querySql = new QuerySql(select, fromAndWhere);
        querySql.setOrderBy("order by e.code");
        return PageResultUtil.getResult(entityManager, querySql, params, queryParam);
    }
}
```

## Common Implementation Patterns

### Dynamic page query with QuerySql

Most common extension pattern:

```java
String select = "select e ";
String fromAndWhere = "from Employee e where e.tenantCode = :tenantCode ";
Map<String, Object> sqlParams = new HashMap<>();
sqlParams.put("tenantCode", ContextUtil.getTenantCode());

if (StringUtils.isNotBlank(quickSearchValue)) {
    fromAndWhere += "and (e.code like :quickSearchValue or e.user.userName like :quickSearchValue) ";
    sqlParams.put("quickSearchValue", "%" + quickSearchValue + "%");
}

QuerySql querySql = new QuerySql(select, fromAndWhere);
querySql.setOrderBy("order by e.code");
return PageResultUtil.getResult(entityManager, querySql, sqlParams, queryParam);
```

Use this when:
- Filters are conditional
- Sorting needs defaults
- The result is paged
- Query parameters are assembled from a query object

### Uniqueness and existence checks with manual JPQL

```java
String sql = "select r.id from User r " +
        "where r.account = :account " +
        "and r.tenantCode = :tenantCode " +
        "and r.id <> :id ";
Query query = entityManager.createQuery(sql);
query.setParameter("account", account);
query.setParameter("tenantCode", ContextUtil.getTenantCode());
query.setParameter("id", id);
List results = query.getResultList();
return !results.isEmpty();
```

### Custom save behavior

```java
if (isNew) {
    entityManager.persist(entity);
    return entity;
} else {
    return entityManager.merge(entity);
}
```

Use this only when the module truly needs persistence behavior beyond the framework defaults.

### DTO/read-model projection queries

```java
String select = "select new com.changhong.sei.basic.dto.EmployeeBriefInfo(e.id, e.code, u.userName, o.name) ";
```

Acceptable for tightly scoped read models where returning entities would be wasteful.

## Framework Helpers

### PageResultUtil

Standard helper for dynamic paged JPQL execution:

```java
PageResultUtil.getResult(entityManager, querySql, sqlParams, queryParam)
```

### QuerySql

Separates `select`, `from + where`, and optional `order by` to keep dynamic JPQL assembly manageable.

### Inherited helper methods from BaseEntityDaoImpl

- `findFirstByFilters(search)`

Prefer these helpers over rebuilding the same persistence logic manually.

## Tenant and Context Handling

Many extension DAOs are tenant-aware:

```java
sqlParams.put("tenantCode", ContextUtil.getTenantCode());
entity.setTenantCode(ContextUtil.getTenantCode());
entity.setLastEditorId(ContextUtil.getUserId());
```

This is acceptable in the DAO extension layer when persistence logic depends on framework context.

## BaseEntityDaoImpl Auto-Handles

- ID generation (`IdGenerator.nextIdStr()`)
- Audit fields auto-fill (creatorId, createdDate, etc.)
- Tenant code auto-fill from `ContextUtil.getTenantCode()`
- Project ID auto-fill from `ContextUtil.getProjectId()`
- Soft delete (if `ISoftDelete` is implemented)
- Tenant isolation (auto-appends `tenant_code = ?` filter)
- Project isolation (auto-appends `project_id = ?` filter)
- Optimistic lock exception handling

## What DaoImpl Should Handle

Good responsibilities:
- Dynamic JPQL assembly
- Custom paging queries
- Uniqueness checks with exclusion logic
- Specialized custom save behavior
- Projection queries

Avoid putting into DaoImpl:
- HTTP or controller logic
- Business workflows that belong in services
- Transaction boundary decisions
- DTO transport concerns outside tightly scoped projections

## Naming and Packaging

| Type | Convention | Example |
|---|---|---|
| Extension interface | `XxxExtDao` | `EmployeeExtDao` |
| Implementation | `impl/XxxDaoImpl` | `impl/EmployeeDaoImpl` |
| Package | `dao.impl` | `com.changhong.sei.xxx.dao.impl` |

The implementation class name matches the main DAO name, even though it technically implements the extension interface.

## Best Practices

1. Implement `XxxExtDao` in `DaoImpl`, not the main `XxxDao`.
2. Extend `BaseEntityDaoImpl<Entity>` for standard custom DAO implementations.
3. Use constructor injection with `EntityManager`.
4. Use `QuerySql` and `PageResultUtil` for dynamic paged JPQL queries.
5. Use manual JPQL with `EntityManager` for custom uniqueness and existence checks.
6. Keep simple repository methods in the main DAO interface.
7. Use context-aware tenant and audit filling only when the module requires it.
8. Keep service-level business rules out of `DaoImpl`.
9. Return entities or focused read-model projections, not transport-layer DTO contracts.
10. Do NOT reference MyBatis/XML implementation patterns — this codebase is JPA-only.
