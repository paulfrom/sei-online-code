package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.StudentApi;
import com.changhong.onlinecode.dto.StudentDto;
import com.changhong.onlinecode.entity.Student;
import com.changhong.onlinecode.exception.StudentErrorCode;
import com.changhong.onlinecode.query.StudentQuery;
import com.changhong.onlinecode.service.StudentService;
import com.changhong.onlinecode.vo.StudentVo;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生管理控制器（BE-002）。契约 PRD §5.2。
 *
 * <p>继承 {@link BaseEntityController} 提供基础 CRUD 注入点，但本控制器不调用
 * {@code super.save} —— 因学生写入路径需要做学号/身份证/手机号三重唯一性 + 格式校验，
 * 必须走 {@code StudentService.create/update}，避免依赖框架默认 save 流程绕过校验。</p>
 *
 * <p>所有错误以 {@code ResultData.fail(...)} 透传业务错误码，由前端 isBusinessError
 * 守卫判断并展示具体冲突字段（FE-002 教训：禁止 catch 块吞错误文案）。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "StudentApi", description = "学生管理服务")
@RequestMapping(path = StudentApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class StudentController extends BaseEntityController<Student, StudentDto> implements StudentApi {

    private final StudentService studentService;

    @Override
    public BaseEntityService<Student> getService() {
        return studentService;
    }

    @Override
    public ResultData<StudentDto> create(StudentDto dto) {
        OperateResultWithData<StudentDto> result = studentService.create(dto);
        return result.successful() ? ResultData.success(result.getData()) : ResultData.fail(result.getMessage());
    }

    @Override
    public ResultData<StudentDto> update(String id, StudentDto dto) {
        OperateResultWithData<StudentDto> result = studentService.update(id, dto);
        return result.successful() ? ResultData.success(result.getData()) : ResultData.fail(result.getMessage());
    }

    @Override
    public ResultData<StudentDto> updateStatus(String id, StudentDto dto) {
        OperateResultWithData<StudentDto> result = studentService.updateStatus(id, dto.getStatus());
        return result.successful() ? ResultData.success(result.getData()) : ResultData.fail(result.getMessage());
    }

    @Override
    public ResultData<StudentVo> getDetail(String id) {
        StudentVo vo = studentService.getDetail(id);
        return vo == null
                ? ResultData.fail(StudentErrorCode.STUDENT_NOT_FOUND)
                : ResultData.success(vo);
    }

    @Override
    public ResultData<PageResult<StudentDto>> page(StudentQuery query) {
        return ResultData.success(studentService.page(query));
    }

    @Override
    public ResultData<Void> softDelete(String id) {
        OperateResult result = studentService.softDelete(id);
        return result.successful() ? ResultData.success(null) : ResultData.fail(result.getMessage());
    }
}
