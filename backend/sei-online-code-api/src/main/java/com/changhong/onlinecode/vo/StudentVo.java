package com.changhong.onlinecode.vo;

import com.changhong.onlinecode.dto.enums.StudentGender;
import com.changhong.onlinecode.dto.enums.StudentStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学生详情 VO（PRD §5.3 详情页）。
 *
 * <p>在 {@link com.changhong.onlinecode.dto.StudentDto} 基础上额外携带审计操作人的姓名
 * 与账号（来自 BaseAuditableEntity 的 creatorAccount / lastEditorAccount / creatorName /
 * lastEditorName），便于前端展示「由谁在何时创建/修改」。</p>
 *
 * <p>本视图与 DTO 解耦：DTO 走写路径与列表，VO 仅供详情读取；避免在写路径上过度加载
 * 审计扩展字段。</p>
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "学生详情 VO")
public class StudentVo extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "学号", example = "S20240001")
    private String studentNo;

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "性别", example = "MALE")
    private StudentGender gender;

    @Schema(description = "出生日期", example = "2010-01-15")
    private LocalDate birthDate;

    @Schema(description = "身份证号", example = "11010120100115001X")
    private String idCardNo;

    @Schema(description = "手机号", example = "13800138000")
    private String mobile;

    @Schema(description = "班级名称", example = "三年级二班")
    private String className;

    @Schema(description = "入学日期", example = "2024-09-01")
    private LocalDate enrollmentDate;

    @Schema(description = "启用/停用状态", example = "ENABLED")
    private StudentStatus status;

    @Schema(description = "头像 URL")
    private String avatarUrl;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人账号")
    private String creatorAccount;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "创建时间")
    private java.util.Date createdDate;

    @Schema(description = "最后修改人账号")
    private String lastEditorAccount;

    @Schema(description = "最后修改人姓名")
    private String lastEditorName;

    @Schema(description = "最后修改时间")
    private java.util.Date lastEditedDate;
}
