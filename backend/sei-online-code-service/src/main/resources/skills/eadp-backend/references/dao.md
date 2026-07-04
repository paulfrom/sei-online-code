# DAO

Patterns and conventions for DAO interfaces in EADP/SEI backend development.

## Positioning

The DAO layer is primarily JPA-based and framework-integrated. This codebase does NOT use MyBatis.

Key points:
- Standard entity DAOs extend `BaseEntityDao<Entity>`.
- Tree entity DAOs extend `BaseTreeDao<Entity>`.
- Complex custom queries are split into `XxxExtDao`.
- The main `XxxDao` usually combines the framework base DAO with the extension interface.
- Simple CRUD and derived queries stay in `XxxDao`.
- Complex paging, dynamic JPQL, and custom save behavior move into `XxxDaoImpl`.

## Base DAO Classes

| Base Class | Use When |
|---|---|
| `BaseEntityDao<T extends BaseEntity>` | Normal entity module |
| `BaseTreeDao<T extends BaseEntity>` | Tree-structured entity module |

`BaseEntityDao<T>` extends `BaseDao<T, String>` and provides these methods (no need to redeclare):

| Method | Description |
|---|---|
| `findOne(id)` | Find by ID |
| `findAll()` | Find all (respects tenant/project/soft-delete) |
| `findAllUnfrozen()` | Find all not frozen |
| `findAllWithDelete()` | Find all including soft-deleted |
| `findByProperty(property, value)` | Single property query |
| `findFirstByProperty(property, value)` | Single property, returns first match |
| `findByFilter(SearchFilter)` | Single filter query |
| `findByFilters(Search)` | Multi-filter query |
| `findByPage(Search)` | Paginated query |
| `isExistsByProperty(property, value)` | Existence check |
| `isCodeExists(code, id)` | Code uniqueness check |
| `findListByProperty(property, value)` | List by property |
| `create(entity)` | Create entity |
| `save(Collection)` | Save collection |
| `delete(Collection ids)` | Delete by ID collection |
| `evict()` / `evict(id)` / `evictAll()` | L2 cache operations |

## Core Conventions

### 1. Choose the correct base DAO

```java
public interface EmployeeDao extends BaseEntityDao<Employee>, EmployeeExtDao {
}

public interface PositionDao extends BaseEntityDao<Position>, PositionExtDao {
}

public interface OrganizationDao extends BaseTreeDao<Organization> {
}

public interface AccountDao extends BaseEntityDao<Account> {
}
```

### 2. Compose with ExtDao for non-trivial custom queries

This is a key real-world pattern:

```java
public interface EmployeeDao extends BaseEntityDao<Employee>, EmployeeExtDao {
}

public interface PositionDao extends BaseEntityDao<Position>, PositionExtDao {
}
```

Use `XxxExtDao` for:
- Dynamic paging queries
- Manual save variants
- Custom uniqueness checks
- Query shapes awkward as derived JPA methods
- JPQL assembled from runtime conditions

If no custom extension is needed, `ExtDao` can be omitted.

### 3. Keep simple queries in the main DAO interface

The main DAO interface commonly contains:
- Derived finder methods
- Small `@Query` methods
- `@Modifying` update statements

Examples:

```java
List<Employee> findByOrganizationId(String organizationId);

List<Position> findAllByOrganizationIdOrderByCode(String organizationId);

Account findByAccountAndTenantCode(String account, String tenant);

@Query("select e from Employee e where e.code in :codes and e.tenantCode = :tenantCode")
List<Employee> findByCodeInAndTenantCode(@Param("codes") Collection<String> codes,
                                         @Param("tenantCode") String tenantCode);

@Modifying
@Query("update Account a set a.password = :password where a.id = :id")
int updatePassword(@Param("id") String id, @Param("password") String password);
```

### 4. Use Spring Data JPA method naming where it stays readable

Examples:
- `findByOrganizationId`
- `findByOrganizationIdAndUserFrozenFalse`
- `findByTenantCodeAndUserUserAuthorityPolicy`
- `findByCodeAndTenantCode`
- `findAllByOrganizationIdOrderByCode`

Prefer derived methods when the query is simple and the property path is readable. Use `@Query` or `ExtDao` when the method name becomes unwieldy.

### 5. Use @Query and @Modifying for direct JPQL updates or special lookups

```java
@Modifying
@Query("update Position o set o.dimensionId = :dimensionId where o.dimensionId = '' and o.tenantCode = :tenantCode")
void updateDimensionId(@Param("dimensionId") String dimensionId,
                       @Param("tenantCode") String tenantCode);

@Query("select max(t.lastEditedDate) FROM Organization t")
Date findMaxUpdateDate();
```

Prefer JPQL. Do not assume native SQL unless the module truly requires it.

## DAO Templates

### Standard entity DAO

```java
@Repository
public interface YourDao extends BaseEntityDao<YourEntity>, YourExtDao {
    List<YourEntity> findByOrganizationId(String organizationId);
    YourEntity findByCodeAndTenantCode(String code, String tenantCode);
}
```

### Tree DAO

```java
@Repository
public interface YourTreeDao extends BaseTreeDao<YourTreeEntity> {
    YourTreeEntity findByParentIdIsNullAndId(String id);
}
```

### DAO with direct JPQL update

```java
@Repository
public interface YourDao extends BaseEntityDao<YourEntity> {
    @Modifying
    @Query("update YourEntity e set e.frozen = :frozen where e.userId = :userId")
    int updateFrozen(@Param("userId") String userId, @Param("frozen") boolean frozen);
}
```

### Extension DAO interface

```java
public interface YourExtDao {
    Boolean isCodeExist(String code, String id);
    PageResult<YourEntity> queryEntities(YourQuickQueryParam queryParam, List<String> excludeIds, String tenantCode);
}
```

## When To Use ExtDao

Put methods into `ExtDao` when they need:
- `EntityManager`
- Dynamic JPQL string assembly
- `QuerySql`
- `PageResultUtil`
- Non-standard save semantics
- Richer paging behavior than derived JPA methods provide

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Main DAO | `XxxDao` | `EmployeeDao` |
| Extension interface | `XxxExtDao` | `EmployeeExtDao` |
| Implementation class | `impl/XxxDaoImpl` | `impl/EmployeeDaoImpl` |

Method naming:
- `findBy...`, `findAllBy...` — read queries
- `is...Exist` — existence checks
- `update...` — update operations
- `query...` — complex/custom queries

## What To Prefer

1. Standard JPA-style methods stay in `XxxDao`.
2. Dynamic custom methods move to `XxxExtDao` + `impl/XxxDaoImpl`.
3. Prefer JPQL over raw SQL.
4. Reuse `BaseEntityDao` and `BaseTreeDao` instead of inventing custom DAO bases.
5. Do NOT reference MyBatis patterns — this codebase is JPA-only.

## Best Practices

1. Extend `BaseEntityDao` or `BaseTreeDao` according to entity type.
2. Compose with `XxxExtDao` for dynamic or non-trivial custom queries.
3. Keep simple finder methods in the main DAO interface.
4. Use `@Query` and `@Modifying` for concise static JPQL and updates.
5. Use Spring Data method naming when it stays readable.
6. Keep DTO-centric shaping out of ordinary DAO methods.
7. Pass tenant code explicitly where cross-tenant ambiguity matters.
8. Return `PageResult<Entity>` from extension paging methods.
9. Keep complex query assembly out of services when it clearly belongs in DAO extensions.
10. Do NOT reference MyBatis/XML implementation patterns.
