# DTO (Data Transfer Object)

Patterns and conventions for DTO design in EADP/SEI backend development.

## Base DTO

All business DTOs should extend `BaseEntityDto` (has `id` field):

```java
import com.changhong.sei.core.dto.BaseEntityDto;

public class FooDto extends BaseEntityDto {
    private String name;
    // Only include fields needed by API consumers
}
```

## Key Framework DTOs

| DTO | Purpose |
|---|---|
| `BaseEntityDto` | Base DTO with `id` field |
| `ResultData<T>` | Unified API response wrapper (success/fail/exception) |
| `PageResult<T>` | Pagination response (page, records, total, rows) |
| `PageInfo` | Pagination request (page, rows; defaults: 1, 15) |
| `Search` | Query config (filters, sortOrders, pageInfo, quickSearch) |
| `SearchFilter` | Single filter condition |
| `SearchOrder` | Sort definition |
| `TreeEntity<T>` / `TreeEntityOfAsync<T>` | Tree node wrapper |

## ResultData Usage

```java
ResultData.success(data)
ResultData.success("message", data)
ResultData.fail("error message")
ResultData.exception("exception message")
```

## DTO Patterns

### Standard Response DTO

```java
public class ContractDto extends BaseEntityDto {

    private String code;
    private String name;
    private String type;
    private BigDecimal amount;

    // Organization for BPM
    private String organizationId;
    private String organizationName;

    // Workflow
    private String flowStatus;

    // EDM attachment bindings
    private String mainAttachmentBindingId;
    private String scanAttachmentBindingId;

    // Document IDs from frontend (for save)
    private List<String> mainDocIds;
    private List<String> scanDocIds;
}
```

### Save Request DTO

```java
public class ContractSaveRequest extends BaseEntityDto {

    @NotBlank(message = "单据编号不能为空")
    @Size(max = 50, message = "单据编号长度不能超过50")
    private String code;

    @NotBlank(message = "单据名称不能为空")
    @Size(max = 200, message = "单据名称长度不能超过200")
    private String name;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remarks;

    // EDM document IDs from frontend
    private List<String> mainDocIds;
    private List<String> scanDocIds;

    // Binding IDs (for existing records)
    private String mainAttachmentBindingId;
    private String scanAttachmentBindingId;
}
```

### Query Parameter DTO

Use a dedicated parameter object for complex queries, not `extends Search`:

```java
public class ContractQuickQueryParam {
    private String code;
    private String name;
    private String type;
    private String status;
    private String flowStatus;
    private String organizationId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}
```

Convert to `Search` in the service or controller:

```java
Search search = new Search(param);
search.addFilter(new SearchFilter("organization.id", param.getOrganizationId(), SearchFilter.Operator.EQ));
return findByPage(search);
```

## DTO Conversion

DTO conversion is handled in controllers using base controller helpers:

```java
// In controller — uses ModelMapper from BaseController
convertToDto(entity)           // Entity → DTO
convertToEntity(dto)           // DTO → Entity
convertToDtos(entities)        // List<Entity> → List<DTO>
convertToDtoPageResult(page)   // PageResult<Entity> → PageResult<DTO>
```

Override `customConvertToDtoMapper()` for custom field mapping:

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

## BPM-Specific DTOs

### BpmInvokeParams

Received from BPM engine during workflow callbacks:

```java
import com.changhong.sei.bpm.dto.vo.BpmInvokeParams;

@Override
public ResultData<Void> afterStartFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    String nodeCode = invokeParams.getNodeCode();
    String nodeName = invokeParams.getNodeName();
    String userId = invokeParams.getStartUserId();
    // ... business logic
    return ResultData.success();
}
```

### BpmReturnParams

Return parameters to BPM engine:

```java
import com.changhong.sei.bpm.dto.vo.BpmReturnParams;

@Override
public ResultData<BpmReturnParams> triggerTaskService(BpmInvokeParams invokeParams) {
    BpmReturnParams returnParams = new BpmReturnParams();
    returnParams.setImmediateTriggerTask(true);
    returnParams.setReceiverUserId("SYSTEM_USER");
    returnParams.setReceiveOpinion("自动通过");
    return ResultData.success(returnParams);
}
```

### PropertiesAndValuesVo

Return entity properties to BPM:

```java
import com.changhong.sei.bpm.dto.vo.PropertiesAndValuesVo;

@Override
public ResultData<PropertiesAndValuesVo> propertiesAndValues(
    String businessEntityCode, String businessId) {
    Contract entity = contractDao.findById(businessId);
    PropertiesAndValuesVo values = new PropertiesAndValuesVo();
    values.setBusinessCode(entity.getCode());
    values.setBusinessName(entity.getName());
    values.setBusinessMoney(entity.getAmount().toString());
    values.setOrgId(entity.getOrganizationId());
    return ResultData.success(values);
}
```

### PropertiesAllExplainVo

Return property explanations to BPM:

```java
import com.changhong.sei.bpm.dto.vo.PropertiesAllExplainVo;

@Override
public ResultData<List<PropertiesAllExplainVo>> propertiesAllExplain(
    String businessEntityCode) {
    List<PropertiesAllExplainVo> explains = new ArrayList<>();
    PropertiesAllExplainVo codeExplain = new PropertiesAllExplainVo();
    codeExplain.setCode("code");
    codeExplain.setName("单据编号");
    codeExplain.setInitValue("");
    codeExplain.setRemark("业务单据的唯一标识");
    explains.add(codeExplain);
    return ResultData.success(explains);
}
```

## EDM-Specific DTOs

### DocumentResponse

```java
import com.changhong.sei.edm.dto.DocumentResponse;

@Override
public ResultData<List<DocumentResponse>> getMainAttachments(String contractId) {
    Contract entity = contractDao.findById(contractId);
    String bindingId = entity.getMainAttachmentBindingId();
    ResultData<List<DocumentResponse>> result =
        documentManager.getEntityDocumentInfos(bindingId);
    return result;
}
```

### UploadResponse

```java
import com.changhong.sei.edm.dto.UploadResponse;

// Upload from file
File file = new File("path/to/file.pdf");
UploadResponse uploadResponse = documentManager.uploadDocument(file);
String docId = uploadResponse.getDocId();
```

## SearchFilter Factory Methods

| Method | SQL |
|---|---|
| `eq(field, value)` | = |
| `ne(field, value)` | != |
| `like(field, value)` | LIKE %val% |
| `leftLike(field, value)` | LIKE val% |
| `rightLike(field, value)` | LIKE %val |
| `gt(field, value)` | > |
| `ge(field, value)` | >= |
| `lt(field, value)` | < |
| `le(field, value)` | <= |
| `in(field, collection)` | IN (...) |
| `notin(field, collection)` | NOT IN (...) |
| `isNull(field)` | IS NULL |
| `isNotNull(field)` | IS NOT NULL |
| `isBlank(field)` | IS NULL OR ='' |
| `notBlank(field)` | IS NOT NULL AND !='' |

## Best Practices

1. Extend `BaseEntityDto` for all business DTOs.
2. Use JSR-303 validation annotations on request DTOs.
3. Always use `ResultData` and `PageResult` for API responses.
4. Use controller `convertToDto`/`convertToEntity` for Entity↔DTO conversion, not `BeanMapper` directly.
5. Return binding IDs to frontend for attachment components.
6. Accept docId lists from frontend for EDM binding.
7. Use `@JsonFormat` for date fields.
8. Use dedicated query parameter objects, convert to `Search` in service.
9. Use descriptive names (e.g., `ContractSaveRequest`, `ContractQuickQueryParam`).
10. Keep request DTOs, response DTOs, and query DTOs explicit instead of one catch-all class.
