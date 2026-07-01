package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.SpecApi;
import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.SpecDto;
import com.changhong.onlinecode.dto.request.ConfirmSpecRequest;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.onlinecode.service.IterationService;
import com.changhong.onlinecode.service.SpecService;
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
 * Spec 管理控制器。实现 {@link SpecApi}，契约 §3 端点 5/6 + Phase 4 §2 端点 30。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "SpecApi", description = "Spec 管理服务")
@RequestMapping(path = SpecApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class SpecController extends BaseEntityController<Spec, SpecDto>
        implements SpecApi {

    private final SpecService service;
    private final IterationService iterationService;

    public SpecController(SpecService service, IterationService iterationService) {
        this.service = service;
        this.iterationService = iterationService;
    }

    @Override
    public BaseEntityService<Spec> getService() {
        return service;
    }

    @Override
    public ResultData<IterationDto> confirm(ConfirmSpecRequest request) {
        OperateResultWithData<Iteration> result = iterationService.confirmSpec(request.getSpecId());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertIterationToDto(result.getData()));
    }

    @Override
    public ResultData<List<SpecDto>> findByProject(String projectId) {
        List<SpecDto> dtos = service.findByProject(projectId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResultData.success(dtos);
    }

    /**
     * Iteration 实体 → DTO。Iteration 属另一聚合，独立映射（含 Phase 4 回合溯源字段）。
     *
     * @param iteration 迭代实体
     * @return IterationDto
     */
    private IterationDto convertIterationToDto(Iteration iteration) {
        IterationDto dto = new IterationDto();
        dto.setId(iteration.getId());
        dto.setProjectId(iteration.getProjectId());
        dto.setSpecId(iteration.getSpecId());
        dto.setSpecVersion(iteration.getSpecVersion());
        dto.setRound(iteration.getRound());
        dto.setParentIterationId(iteration.getParentIterationId());
        dto.setFeedback(iteration.getFeedback());
        dto.setState(iteration.getState());
        dto.setPreviewUrl(iteration.getPreviewUrl());
        dto.setCreatedDate(iteration.getCreatedDate());
        dto.setFinishedDate(iteration.getFinishedDate());
        return dto;
    }
}
