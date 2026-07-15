package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.StudentDao;
import com.changhong.onlinecode.dto.StudentDto;
import com.changhong.onlinecode.dto.enums.StudentStatus;
import com.changhong.onlinecode.entity.Student;
import com.changhong.onlinecode.exception.StudentErrorCode;
import com.changhong.onlinecode.exception.StudentValidator;
import com.changhong.onlinecode.query.StudentQuery;
import com.changhong.onlinecode.vo.StudentVo;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生管理业务服务（BE-002）。
 *
 * <p>契约 PRD §5.2。负责业务校验（学号/身份证号/手机号唯一性 + 身份证校验位 + 手机号格式 +
 * 出生日期非空且不晚于今天）、审计字段写入（由 sei-core BaseAuditableEntity 接入点维护）、分页
 * 查询遵循 sei-core PageResult 契约（{@code records}=总记录数、{@code total}=总页数、
 * {@code rows}=当前页数据）。</p>
 *
 * <p>异常与状态码：所有校验失败以 {@code OperateResult.operationFailure(StudentErrorCode.*)}
 * 形式返回，由 controller 转译为 {@code ResultData.fail(...)}，避免 raw exception 抛出与全
 * 局处理器耦合。</p>
 *
 * @author sei-online-code
 */
@Service
@AllArgsConstructor
public class StudentService extends BaseEntityService<Student> {

    private final StudentDao studentDao;

    @Override
    protected BaseEntityDao<Student> getDao() {
        return studentDao;
    }

    /**
     * 新增学生。校验：必填字段、学号/身份证号/手机号唯一性、身份证校验位、手机号格式、
     * 出生日期不晚于今天。审计写入由 {@code BaseAuditableEntity} 自动填充。
     *
     * @param dto 学生 DTO
     * @return 操作结果，成功时 {@code .getData()} 为持久化后的实体
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<StudentDto> create(StudentDto dto) {
        OperateResult validate = validate(dto, true);
        if (!validate.successful()) {
            return OperateResultWithData.operationFailure(validate.getMessage());
        }
        Student entity = toEntity(dto);
        OperateResultWithData<Student> saved = super.save(entity);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }
        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 全量修改。已软删记录返回 STUDENT_NOT_FOUND。
     *
     * @param id  学生 id
     * @param dto 修改 DTO（id 字段会被忽略，以路径参数为准）
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<StudentDto> update(String id, StudentDto dto) {
        Student existing = studentDao.findOne(id);
        if (existing == null || existing.getDeleted() != null && existing.getDeleted() != 0L) {
            return OperateResultWithData.operationFailure(StudentErrorCode.STUDENT_NOT_FOUND);
        }
        OperateResult validate = validate(dto, false);
        if (!validate.successful()) {
            return OperateResultWithData.operationFailure(validate.getMessage());
        }
        applyUpdate(existing, dto);
        OperateResultWithData<Student> saved = super.save(existing);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }
        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 启用/停用。已软删记录返回 STUDENT_NOT_FOUND。审计字段由 BaseAuditableEntity 维护。
     *
     * @param id     学生 id
     * @param status 目标状态（{@code ENABLED}/{@code DISABLED}）
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<StudentDto> updateStatus(String id, StudentStatus status) {
        Student existing = studentDao.findOne(id);
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() != 0L)) {
            return OperateResultWithData.operationFailure(StudentErrorCode.STUDENT_NOT_FOUND);
        }
        if (!StudentValidator.isValidStatus(status)) {
            return OperateResultWithData.operationFailure(StudentErrorCode.STUDENT_INVALID_STATUS);
        }
        existing.setStatus(status);
        OperateResultWithData<Student> saved = super.save(existing);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }
        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 详情查询。已软删记录返回 STUDENT_NOT_FOUND。
     *
     * @param id 学生 id
     * @return 学生 VO（携带审计操作人姓名/账号），不存在时返回 null
     */
    public StudentVo getDetail(String id) {
        Student entity = studentDao.findOne(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() != 0L)) {
            return null;
        }
        return toVo(entity);
    }

    /**
     * 软删除。已软删记录返回 STUDENT_NOT_FOUND。
     *
     * <p>sei-core ISoftDelete 约定：删除时间戳写在 {@code deleted} 字段（epoch ms）；本服务额外
     * 通过 sei-core 提供的 {@code delete(String)} 完成软删语义（不持久化 raw cascade）。</p>
     *
     * @param id 学生 id
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResult softDelete(String id) {
        Student existing = studentDao.findOne(id);
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() != 0L)) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_NOT_FOUND);
        }
        // 调用框架软删方法（写入 deleted = epoch ms）
        OperateResult result = super.delete(id);
        if (!result.successful()) {
            return result;
        }
        // 重新加载以获取 sei-core 写入的 deleted 字段，然后回填 deletedBy
        Student after = studentDao.findOne(id);
        if (after != null) {
            after.setDeletedBy(CurrentUserSupport.tryGetCurrentUserId());
            studentDao.save(after);
        }
        return OperateResult.operationSuccess();
    }

    /**
     * 分页查询，严格遵循 sei-core PageResult 三字段契约：
     * {@code records} 为总记录数、{@code total} 为总页数、{@code rows} 为当前页数据。
     *
     * <p>默认按 {@code lastEditedDate DESC} 排序；无需返回被软删记录，由 BaseEntityDao 软删过滤保证。</p>
     *
     * @param query 查询条件
     * @return PageResult
     */
    public PageResult<StudentDto> page(StudentQuery query) {
        com.changhong.sei.core.dto.serach.Search search = buildSearch(query);
        PageResult<Student> result = studentDao.findByPage(search);
        return convertPageResult(result);
    }

    // region ============ 内部工具 ============

    private OperateResult validate(StudentDto dto, boolean isCreate) {
        // 必填字段
        if (!StudentValidator.isValidStudentNo(dto.getStudentNo())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_FIELD_REQUIRED + ":studentNo");
        }
        if (!StudentValidator.isValidName(dto.getName())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_FIELD_REQUIRED + ":name");
        }
        if (!StudentValidator.isValidGender(dto.getGender())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_INVALID_GENDER);
        }
        if (!StudentValidator.isValidBirthDate(dto.getBirthDate())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_INVALID_BIRTHDATE);
        }
        if (!StudentValidator.isValidStatus(dto.getStatus())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_INVALID_STATUS);
        }
        // 可选字段格式
        if (dto.getIdCardNo() != null && !dto.getIdCardNo().isEmpty()
                && !StudentValidator.isValidIdCard(dto.getIdCardNo())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_INVALID_IDCARD + ":" + dto.getIdCardNo());
        }
        if (dto.getMobile() != null && !dto.getMobile().isEmpty()
                && !StudentValidator.isValidMobile(dto.getMobile())) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_INVALID_MOBILE + ":" + dto.getMobile());
        }
        // 唯一性校验（排除自身）
        String excludeId = isCreate ? null : dto.getId();
        if (isDuplicateStudentNo(dto.getStudentNo(), excludeId)) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_DUPLICATE_NO + ":" + dto.getStudentNo());
        }
        if (dto.getIdCardNo() != null && !dto.getIdCardNo().isEmpty()
                && isDuplicateIdCard(dto.getIdCardNo(), excludeId)) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_DUPLICATE_IDCARD + ":" + dto.getIdCardNo());
        }
        if (dto.getMobile() != null && !dto.getMobile().isEmpty()
                && isDuplicateMobile(dto.getMobile(), excludeId)) {
            return OperateResult.operationFailure(StudentErrorCode.STUDENT_DUPLICATE_MOBILE + ":" + dto.getMobile());
        }
        return OperateResult.operationSuccess();
    }

    private boolean isDuplicateStudentNo(String studentNo, String excludeId) {
        return existsByField("studentNo", studentNo, excludeId);
    }

    private boolean isDuplicateIdCard(String idCard, String excludeId) {
        Student exist = studentDao.findFirstByProperty("idCardNo", idCard);
        return exist != null && !exist.getId().equals(excludeId);
    }

    private boolean isDuplicateMobile(String mobile, String excludeId) {
        Student exist = studentDao.findFirstByProperty("mobile", mobile);
        return exist != null && !exist.getId().equals(excludeId);
    }

    private boolean existsByField(String field, String value, String excludeId) {
        Student exist = studentDao.findFirstByProperty(field, value);
        return exist != null && !exist.getId().equals(excludeId);
    }

    private Student toEntity(StudentDto dto) {
        Student entity = new Student();
        entity.setId(dto.getId());
        entity.setStudentNo(dto.getStudentNo());
        entity.setName(dto.getName());
        entity.setGender(dto.getGender());
        entity.setBirthDate(dto.getBirthDate());
        entity.setIdCardNo(dto.getIdCardNo());
        entity.setMobile(dto.getMobile());
        entity.setClassName(dto.getClassName());
        entity.setEnrollmentDate(dto.getEnrollmentDate());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : StudentStatus.ENABLED);
        entity.setAvatarUrl(dto.getAvatarUrl());
        entity.setRemark(dto.getRemark());
        return entity;
    }

    private void applyUpdate(Student existing, StudentDto dto) {
        existing.setStudentNo(dto.getStudentNo());
        existing.setName(dto.getName());
        existing.setGender(dto.getGender());
        existing.setBirthDate(dto.getBirthDate());
        existing.setIdCardNo(dto.getIdCardNo());
        existing.setMobile(dto.getMobile());
        existing.setClassName(dto.getClassName());
        existing.setEnrollmentDate(dto.getEnrollmentDate());
        existing.setStatus(dto.getStatus());
        existing.setAvatarUrl(dto.getAvatarUrl());
        existing.setRemark(dto.getRemark());
    }

    private StudentDto toDto(Student entity) {
        StudentDto dto = new StudentDto();
        dto.setId(entity.getId());
        dto.setStudentNo(entity.getStudentNo());
        dto.setName(entity.getName());
        dto.setGender(entity.getGender());
        dto.setBirthDate(entity.getBirthDate());
        dto.setIdCardNo(entity.getIdCardNo());
        dto.setMobile(entity.getMobile());
        dto.setClassName(entity.getClassName());
        dto.setEnrollmentDate(entity.getEnrollmentDate());
        dto.setStatus(entity.getStatus());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setRemark(entity.getRemark());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastEditedDate(entity.getLastEditedDate());
        return dto;
    }

    private StudentVo toVo(Student entity) {
        StudentVo vo = new StudentVo();
        vo.setId(entity.getId());
        vo.setStudentNo(entity.getStudentNo());
        vo.setName(entity.getName());
        vo.setGender(entity.getGender());
        vo.setBirthDate(entity.getBirthDate());
        vo.setIdCardNo(entity.getIdCardNo());
        vo.setMobile(entity.getMobile());
        vo.setClassName(entity.getClassName());
        vo.setEnrollmentDate(entity.getEnrollmentDate());
        vo.setStatus(entity.getStatus());
        vo.setAvatarUrl(entity.getAvatarUrl());
        vo.setRemark(entity.getRemark());
        vo.setCreatorAccount(entity.getCreatorAccount());
        vo.setCreatorName(entity.getCreatorName());
        vo.setCreatedDate(entity.getCreatedDate());
        vo.setLastEditorAccount(entity.getLastEditorAccount());
        vo.setLastEditorName(entity.getLastEditorName());
        vo.setLastEditedDate(entity.getLastEditedDate());
        return vo;
    }

    private PageResult<StudentDto> convertPageResult(PageResult<Student> result) {
        PageResult<StudentDto> dtoResult = new PageResult<>();
        dtoResult.setPage(result.getPage());
        dtoResult.setRecords(result.getRecords());
        dtoResult.setTotal(result.getTotal());
        dtoResult.setRows(result.getRows().stream().map(this::toDto).collect(java.util.stream.Collectors.toList()));
        return dtoResult;
    }

    private com.changhong.sei.core.dto.serach.Search buildSearch(StudentQuery query) {
        com.changhong.sei.core.dto.serach.Search search = new com.changhong.sei.core.dto.serach.Search();
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageRows = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        search.setPageInfo(com.changhong.sei.core.dto.serach.PageInfo.of(pageNum, pageRows));
        if (query.getStudentNo() != null && !query.getStudentNo().isEmpty()) {
            search.addFilter(new com.changhong.sei.core.dto.serach.SearchFilter(
                    "studentNo", query.getStudentNo(),
                    com.changhong.sei.core.dto.serach.SearchFilter.Operator.EQ));
        }
        if (query.getName() != null && !query.getName().isEmpty()) {
            search.addFilter(new com.changhong.sei.core.dto.serach.SearchFilter(
                    "name", query.getName(),
                    com.changhong.sei.core.dto.serach.SearchFilter.Operator.LK));
        }
        if (query.getMobile() != null && !query.getMobile().isEmpty()) {
            search.addFilter(new com.changhong.sei.core.dto.serach.SearchFilter(
                    "mobile", query.getMobile(),
                    com.changhong.sei.core.dto.serach.SearchFilter.Operator.LK));
        }
        if (query.getClassName() != null && !query.getClassName().isEmpty()) {
            search.addFilter(new com.changhong.sei.core.dto.serach.SearchFilter(
                    "className", query.getClassName(),
                    com.changhong.sei.core.dto.serach.SearchFilter.Operator.LK));
        }
        if (query.getStatus() != null) {
            search.addFilter(new com.changhong.sei.core.dto.serach.SearchFilter(
                    "status", query.getStatus(),
                    com.changhong.sei.core.dto.serach.SearchFilter.Operator.EQ));
        }
        search.addSortOrder(new com.changhong.sei.core.dto.serach.SearchOrder(
                "lastEditedDate",
                com.changhong.sei.core.dto.serach.SearchOrder.Direction.DESC));
        return search;
    }

    // endregion
}
