package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.RequirementApi;
import com.changhong.onlinecode.dto.RequirementDto;
import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.onlinecode.dto.request.CreateRequirementCommentRequest;
import com.changhong.onlinecode.dto.request.EditPrdRequest;
import com.changhong.onlinecode.dto.request.RegeneratePrdRequest;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.RequirementAutomationService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.RequirementDeliveryService;
import com.changhong.onlinecode.service.RequirementService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * Requirement 控制器。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "RequirementApi", description = "需求管理服务")
@RequestMapping(path = RequirementApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class RequirementController extends BaseEntityController<Requirement, RequirementDto>
        implements RequirementApi {

    private final RequirementService service;
    private final RequirementAutomationService requirementAutomationService;
    private final RequirementCommentService requirementCommentService;
    private final RequirementDeliveryService requirementDeliveryService;


    @Override
    public BaseEntityService<Requirement> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<RequirementDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<RequirementDto> regeneratePrd(String id, RegeneratePrdRequest request) {
        OperateResultWithData<Requirement> result = service.regeneratePrd(id, request.getPrompt());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(service.convertToDto(result.getData()));
    }

    @Override
    public ResultData<RequirementDto> editPrd(String id, EditPrdRequest request) {
        OperateResultWithData<Requirement> result = service.editPrd(id, request.getPrdContent());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(service.convertToDto(result.getData()));
    }

    @Override
    public ResultData<RequirementDto> confirmPrd(String id) {
        OperateResultWithData<Requirement> result = service.confirmPrd(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(service.convertToDto(result.getData()));
    }

    @Override
    public ResultData<RequirementDto> confirmCompletion(String id) {
        OperateResultWithData<Requirement> result = service.confirmCompletion(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(service.convertToDto(result.getData()));
    }

    @Override
    public ResultData<RequirementCommentDto> addComment(String id, CreateRequirementCommentRequest request) {
        try {
            RequirementComment comment = requirementAutomationService.handleHumanComment(
                    id, request.getContent(), request.getMetadataJson());
            return ResultData.success(requirementCommentService.convertToDto(comment));
        } catch (Exception e) {
            return ResultData.fail(e.getMessage());
        }
    }

    @Override
    public ResultData<RequirementDto> retryMr(String id) {
        try {
            Requirement requirement = requirementDeliveryService.retry(id);
            return ResultData.success(service.convertToDto(requirement));
        } catch (Exception e) {
            return ResultData.fail(e.getMessage());
        }
    }

    @Override
    public ResultData<RequirementDto> stopAutomation(String id) {
        try {
            Requirement requirement = requirementAutomationService.stopAutomation(id);
            return ResultData.success(service.convertToDto(requirement));
        } catch (Exception e) {
            return ResultData.fail(e.getMessage());
        }
    }
}
