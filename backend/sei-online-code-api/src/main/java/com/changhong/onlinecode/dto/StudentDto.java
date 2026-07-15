package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.StudentGender;
import com.changhong.onlinecode.dto.enums.StudentStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学生主数据 DTO（PRD §5.1）。
 *
 * <p>对外承载学生创建/修改/列表/详情所需的全部业务字段与审计字段。审计时间字段命名遵循
 * BaseAuditableEntity 约定（{@code createdDate / lastEditedDate}）。</p>
 *
 * <p>本类仅作数据传输载体，唯一性与格式校验在 {@code StudentService} 内执行
 * （详见 StudentErrorCode / StudentValidator），不在 DTO 层使用 JSR-303 注解以避免
 * 与业务错误码脱钩。</p>
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "学生 DTO")
public class StudentDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "学号，全局唯一", example = "S20240001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String studentNo;

    @Schema(description = "姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "性别：MALE / FEMALE / UNKNOWN", example = "MALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private StudentGender gender;

    @Schema(description = "出生日期，不得晚于今天", example = "2010-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate birthDate;

    @Schema(description = "身份证号 18 位；非空时全局唯一", example = "11010120100115001X")
    private String idCardNo;

    @Schema(description = "手机号 11 位中国大陆号段；非空时全局唯一", example = "13800138000")
    private String mobile;

    @Schema(description = "班级名称，本期以字符串承载，不做主数据关联", example = "三年级二班")
    private String className;

    @Schema(description = "入学日期", example = "2024-09-01")
    private LocalDate enrollmentDate;

    @Schema(description = "启用/停用状态：ENABLED / DISABLED", example = "ENABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    private StudentStatus status;

    @Schema(description = "头像 URL，长度不超过 512")
    private String avatarUrl;

    @Schema(description = "备注，长度不超过 500")
    private String remark;

    @Schema(description = "创建时间")
    private java.util.Date createdDate;

    @Schema(description = "最后修改时间")
    private java.util.Date lastEditedDate;
}
