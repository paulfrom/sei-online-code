package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.IterationApi;
import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.TaskDto;
import com.changhong.onlinecode.dto.request.DeployIterationRequest;
import com.changhong.onlinecode.dto.request.DispatchIterationRequest;
import com.changhong.onlinecode.dto.request.MergeIterationRequest;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Task;
import com.changhong.onlinecode.service.DispatchService;
import com.changhong.onlinecode.service.IterationService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 迭代管理控制器。实现 {@link IterationApi}，契约 §3 端点 7/8 + Phase 2 §2 端点 10/15。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "IterationApi", description = "迭代管理服务")
@RequestMapping(path = IterationApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class IterationController extends BaseEntityController<Iteration, IterationDto>
        implements IterationApi {

    private final IterationService service;
    private final DispatchService dispatchService;

    public IterationController(IterationService service, DispatchService dispatchService) {
        this.service = service;
        this.dispatchService = dispatchService;
    }

    @Override
    public BaseEntityService<Iteration> getService() {
        return service;
    }

    @Override
    public ResultData<IterationDto> deploy(DeployIterationRequest request) {
        OperateResultWithData<Iteration> result = service.deploy(request.getIterationId());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertToDto(result.getData()));
    }

    @Override
    public ResultData<List<TaskDto>> dispatch(DispatchIterationRequest request) {
        OperateResultWithData<List<Task>> result = dispatchService.dispatch(request.getIterationId());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        List<TaskDto> dtos = result.getData().stream()
                .map(this::convertTaskToDto)
                .collect(Collectors.toList());
        return ResultData.success(dtos);
    }

    @Override
    public ResultData<IterationDto> merge(MergeIterationRequest request) {
        OperateResultWithData<Iteration> result = dispatchService.merge(request.getIterationId());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertToDto(result.getData()));
    }

    /**
     * Task 实体 → DTO。Task 与 Iteration 属不同聚合，独立映射以携带 fileScope 等自由结构字段。
     *
     * @param task Task 实体
     * @return TaskDto
     */
    private TaskDto convertTaskToDto(Task task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setIterationId(task.getIterationId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setFileScope(task.getFileScope());
        dto.setAssignedAgent(task.getAssignedAgent());
        dto.setState(task.getState());
        dto.setWorktreeBranch(task.getWorktreeBranch());
        dto.setSeq(task.getSeq());
        dto.setCreatedDate(task.getCreatedDate());
        return dto;
    }
}
