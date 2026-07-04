# Controller

Patterns and conventions for controller implementations in EADP/SEI backend development.

## Positioning

Controllers implement the API interface directly and delegate business logic to services. They handle DTO conversion, result adaptation, and lightweight request-context decisions.

Key points:
- Implement the `XxxApi` interface directly — HTTP annotations come from the interface.
- Extend `BaseEntityController<Entity, Dto>` or `BaseTreeController<Entity, Dto>` for standard modules.
- DTO conversion uses `convertToDto`/`convertToEntity` from the base controller (ModelMapper-based).
- Services do NOT implement the API; controllers bridge API ↔ Service.

## Base Controllers

| Base Class | Use When |
|---|---|
| `BaseEntityController<E, D>` | Standard entity CRUD module |
| `BaseTreeController<E, D>` | Tree-structured entity module |

### BaseController<E, D> provides:

- `convertToDto(entity)` / `convertToEntity(dto)` — ModelMapper-based conversion
- `convertToDtos(entities)` / `convertToEntities(dtos)` — batch conversion
- `convertToDtoPageResult(pageResult)` — pagination result conversion
- `checkDto(dto)` — null check
- `customConvertToEntityMapper()` / `customConvertToDtoMapper()` — override for custom mapping

## Core Conventions

### 1. Implement the API interface directly

```java
@RestController
@Tag(name = "ProjectApi", description = "项目管理服务")
@RequestMapping(path = ProjectApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProjectController extends BaseEntityController<Project, ProjectDto>
        implements ProjectApi {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<Project> getService() {
        return service;
    }
}
```

Because the controller implements the API interface, the HTTP annotations declared on the API methods are reused by Spring MVC.

### 2. Implement getService()

This is required when extending `BaseEntityController` or `BaseTreeController`:

```java
@Override
public BaseEntityService<Project> getService() {
    return service;
}

@Override
public BaseTreeService<Organization> getService() {
    return service;
}
```

### 3. Match class-level mapping to API root path

```java
@RequestMapping(path = ProjectApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
```

- Reuse `XxxApi.PATH` when the API interface defines it.
- Set `produces = MediaType.APPLICATION_JSON_VALUE`.
- Add `@Tag` for OpenAPI grouping.

### 4. Inject services, not API interfaces

Controllers inject domain services. They do NOT inject the matching `XxxApi` interface.

## DTO Conversion

### Use base controller conversion helpers

```java
// Single conversion
return ResultData.success(convertToDto(service.findByCode(code)));

// List conversion
return ResultData.success(convertToDtos(service.findByIds(ids)));

// Page result conversion
return convertToDtoPageResult(service.findByPage(search));
```

### Customize mapping when necessary

```java
@Override
protected void customConvertToDtoMapper() {
    PropertyMap<Employee, EmployeeDto> propertyMap = new PropertyMap<>() {
        @Override
        protected void configure() {
            map().setOrganizationId(source.getOrganizationId());
            map().setUserAccount(source.getUser().getAccount());
        }
    };
    dtoModelMapper.addMappings(propertyMap);
}
```

### Override conversion methods when needed

Some controllers override `convertToDto` directly for richer custom assembly. This is appropriate when:
- Nested associations need special flattening
- Null handling is non-trivial
- The default mapper is not enough

## Result Adaptation

### Return ResultData consistently

Most controller methods return:
- `ResultData<Dto>`
- `ResultData<List<Dto>>`
- `ResultData<PageResult<Dto>>`
- `ResultData<Void>`

### Adapt service-layer result objects

Services return `OperateResult`/`OperateResultWithData`. Controllers adapt:

```java
return ResultDataUtil.convertFromOperateResult(service.copyToEmployees(copyParam));

return ResultDataUtil.convertFromResponseData(responseData, convertToDtos(responseData.getData()));
```

Do not force every service to already return the final DTO-shaped `ResultData` — that is the controller's job.

## Controller Templates

### Standard entity controller

```java
@RestController
@Tag(name = "YourApi", description = "业务服务")
@RequestMapping(path = YourApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class YourController extends BaseEntityController<YourEntity, YourDto>
        implements YourApi {

    private final YourService service;

    public YourController(YourService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<YourEntity> getService() {
        return service;
    }

    @Override
    public ResultData<YourDto> findByCode(String code) {
        return ResultData.success(convertToDto(service.findByCode(code)));
    }
}
```

### Tree controller

```java
@RestController
@Tag(name = "OrganizationApi", description = "组织机构服务")
@RequestMapping(path = OrganizationApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class OrganizationController extends BaseTreeController<Organization, OrganizationDto>
        implements OrganizationApi {

    @Override
    public BaseTreeService<Organization> getService() {
        return service;
    }
}
```

### Non-entity protocol controller

```java
@RestController
@Tag(name = "AuthenticationApi", description = "账户认证服务")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthenticationController implements AuthenticationApi {
}
```

## Audit and Cross-Cutting Concerns

### Audit integration

```java
public class ProjectController extends BaseEntityController<Project, ProjectDto>
        implements ProjectApi, AuditDtoApi<ProjectDto> {
}

@EnableAudit(id = "#dto.id", description = "保存")
public ResultData<ProjectDto> save(ProjectDto dto) {
    return super.save(dto);
}
```

### Access log integration

```java
@AccessLog(AccessLog.FilterReply.DENY)
public ResultData<SessionUserResponse> login(LoginRequest loginRequest) {
    ...
}
```

## Export-Related Patterns

Entity controllers with export capability commonly override base hooks:

- `constructExportDataFields`
- `sortExportTableData`
- `exportTableData(List<DataField> dataFields)`
- `convertField`

## Controller Responsibility Boundaries

Controllers may:
- Convert entities to DTOs
- Call multiple services for composition
- Adapt `OperateResult` or `ResponseData`
- Apply request-context checks (current tenant, current user)
- Enforce lightweight permission gates
- Enrich export field definitions

Controllers should NOT:
- Access DAO/repository classes directly
- Manage transactions
- Implement low-level persistence logic
- Contain broad domain workflows that belong in services

## Best Practices

1. Implement the API interface directly instead of duplicating endpoint contracts.
2. Reuse API interface mappings rather than rewriting method annotations in controllers.
3. Extend `BaseEntityController` or `BaseTreeController` when the entity type fits.
4. Set class-level `@RequestMapping(..., produces = MediaType.APPLICATION_JSON_VALUE)`.
5. Use `@Tag` for OpenAPI grouping on public controllers.
6. Inject domain services, not the matching API interface.
7. Use base controller conversion helpers (`convertToDto`, `convertToDtoPageResult`) for DTO transformation.
8. Adapt framework result objects with `ResultDataUtil` when necessary.
9. Keep DAO access and transaction management out of controllers.
10. Override `customConvertToDtoMapper()` for custom field mapping, don't use `BeanMapper` directly.
