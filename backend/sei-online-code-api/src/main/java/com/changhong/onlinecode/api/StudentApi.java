package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.StudentDto;
import com.changhong.onlinecode.query.StudentQuery;
import com.changhong.onlinecode.vo.StudentVo;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 学生管理 API。契约 PRD §5.2 接口清单。
 *
 * <ul>
 *   <li>{@code POST   /students}                  —— 新增学生（AC-01 必填校验）</li>
 *   <li>{@code DELETE /students/{id}}             —— 软删除（写 deletedBy/deletedDate，列表过滤）</li>
 *   <li>{@code PUT    /students/{id}}             —— 全量修改（写 lastEditedBy/lastEditedDate）</li>
 *   <li>{@code PATCH  /students/{id}/status}      —— 启用/停用（写 lastEditedBy/lastEditedDate）</li>
 *   <li>{@code GET    /students/{id}}             —— 详情（已软删返回 STUDENT_NOT_FOUND）</li>
 *   <li>{@code POST   /students/page}             —— 分页查询（PageResult 严格三字段语义）</li>
 * </ul>
 *
 * <p>所有写接口均使用 {@code @Valid} 触发 DTO 校验；唯一性与格式校验在 service 层由
 * {@code StudentValidator} 完成，结果以 {@code ResultData.fail(StudentErrorCode.*)} 透传。</p>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = StudentApi.PATH)
public interface StudentApi {

    String PATH = "students";

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "新增学生", description = "学号/姓名/性别/出生日期/状态 必填；学号/身份证/手机号 唯一性二次校验")
    ResultData<StudentDto> create(@RequestBody @Valid StudentDto dto);

    @PutMapping(path = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "全量修改学生", description = "写 lastEditedBy/lastEditedDate；已软删记录不可修改")
    ResultData<StudentDto> update(@PathVariable("id") String id, @RequestBody @Valid StudentDto dto);

    @PatchMapping(path = "{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "启用/停用", description = "写 lastEditedBy/lastEditedDate；已软删记录不可启停")
    ResultData<StudentDto> updateStatus(@PathVariable("id") String id, @RequestBody @Valid StudentDto dto);

    @GetMapping(path = "{id}")
    @Operation(summary = "学生详情", description = "返回包含审计扩展字段的 VO；已软删记录返回 STUDENT_NOT_FOUND")
    ResultData<StudentVo> getDetail(@PathVariable("id") String id);

    @PostMapping(path = "page", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "分页查询", description = "严格遵循 sei-core PageResult 契约：records=总记录数、total=总页数、rows=当前页")
    ResultData<PageResult<StudentDto>> page(@RequestBody @Valid StudentQuery query);

    /**
     * 软删除。PRD §5.2 明确定义为 {@code DELETE /students/{id}}。
     *
     * <p>{@code BaseEntityApi.delete} 默认走 {@code DELETE /{entityName}/delete}，与 PRD
     * 路径冲突，所以 StudentApi 不继承 BaseEntityApi 而是在此独立声明，保证路径与契约一致。</p>
     */
    @DeleteMapping(path = "{id}")
    @Operation(summary = "软删除", description = "写 deletedBy/deletedDate；常规查询自动过滤；已软删返回 STUDENT_NOT_FOUND")
    ResultData<Void> softDelete(@PathVariable("id") String id);
}
