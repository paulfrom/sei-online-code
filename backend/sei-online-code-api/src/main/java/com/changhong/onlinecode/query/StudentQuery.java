package com.changhong.onlinecode.query;

import com.changhong.onlinecode.dto.enums.StudentStatus;
import com.changhong.sei.core.dto.serach.Search;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 学生分页查询入参（PRD §5.2 POST /api/students/page）。
 *
 * <p>前端传入的扁平条件（学号精确、姓名/手机号模糊、班级模糊、状态过滤），由 service
 * 组装为 sei-core {@link Search}。POST + body 的好处：可承载复杂筛选条件且不受 URL 长度限制。</p>
 *
 * <p>默认排序规则：{@code lastEditedDate DESC}（在 service 层组装时指定）。</p>
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "学生分页查询条件")
public class StudentQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "学号精确匹配", example = "S20240001")
    private String studentNo;

    @Schema(description = "姓名模糊匹配", example = "张")
    private String name;

    @Schema(description = "手机号模糊匹配", example = "138")
    private String mobile;

    @Schema(description = "班级名称模糊匹配", example = "三年级")
    private String className;

    @Schema(description = "启用/停用状态过滤", example = "ENABLED")
    private StudentStatus status;

    @Schema(description = "页码（1 起）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize = 20;

    @Schema(description = "排序字段列表（默认 lastEditedDate DESC）", example = "[\"lastEditedDate\"]")
    private List<String> sortFields;
}
