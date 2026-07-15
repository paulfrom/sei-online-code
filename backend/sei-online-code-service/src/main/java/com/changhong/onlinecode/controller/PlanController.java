package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.PlanApi;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.request.EditPlanRequest;
import com.changhong.onlinecode.dto.request.RegeneratePlanRequest;
import com.changhong.onlinecode.service.PlanService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 规划书控制器。实现 {@link PlanApi}，契约 §5 端点 P2–P5、P13。
 *
 * <p>PlanApi 不继承 BaseEntityApi（Plan 经 P1 项目创建内部触发，无独立 save/findByPage 端点）。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "PlanApi", description = "规划书服务")
@RequestMapping(path = PlanApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class PlanController implements PlanApi {

    private final PlanService service;

    @Override
    public ResultData<PlanDto> getLatest(String projectId) {
        PlanDto dto = service.findLatest(projectId);
        return dto == null ? ResultData.fail("规划书不存在: " + projectId) : ResultData.success(dto);
    }

    @Override
    public ResultData<PlanDto> edit(String projectId, EditPlanRequest request) {
        OperateResultWithData<PlanDto> result = service.edit(projectId, request.getContent());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<PlanDto> regenerate(String projectId, RegeneratePlanRequest request) {
        OperateResultWithData<PlanDto> result = service.regenerate(projectId, request.getModifyHint());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<Void> confirm(String projectId) {
        OperateResultWithData<PlanDto> result = service.confirm(projectId);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(null);
    }

    @Override
    public ResultData<List<PlanDto>> history(String projectId) {
        return ResultData.success(service.history(projectId));
    }
}
