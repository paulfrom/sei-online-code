package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.MemoryRequirementContextApi;
import com.changhong.onlinecode.dto.RequirementDesignContextDto;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.service.RequirementDesignContextService;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 需求级设计上下文控制器。实现 {@link MemoryRequirementContextApi}，
 * 契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §17.3。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "MemoryRequirementContextApi", description = "需求级设计上下文服务")
@RequestMapping(path = MemoryRequirementContextApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class MemoryRequirementContextController implements MemoryRequirementContextApi {

    private final RequirementDesignContextService requirementDesignContextService;

    public MemoryRequirementContextController(RequirementDesignContextService requirementDesignContextService) {
        this.requirementDesignContextService = requirementDesignContextService;
    }

    @Override
    public ResultData<RequirementDesignContextDto> current(String requirementId) {
        RequirementDesignContext context = requirementDesignContextService.findCurrentByRequirement(requirementId);
        return ResultData.success(toDto(context));
    }

    @Override
    public ResultData<RequirementDesignContextDto> prepare(String requirementId) {
        RequirementDesignContext context = requirementDesignContextService.prepare(requirementId);
        return ResultData.success(toDto(context));
    }

    private RequirementDesignContextDto toDto(RequirementDesignContext context) {
        if (Objects.isNull(context)) {
            return null;
        }
        RequirementDesignContextDto dto = new RequirementDesignContextDto();
        dto.setId(context.getId());
        dto.setProjectId(context.getProjectId());
        dto.setRequirementId(context.getRequirementId());
        dto.setWorkspaceMemoryId(context.getWorkspaceMemoryId());
        dto.setVersion(context.getVersion());
        dto.setStatus(context.getStatus());
        dto.setContextStatus(context.getContextStatus());
        dto.setRequirementFingerprint(context.getRequirementFingerprint());
        dto.setRequirementRelatedSnapshotJson(context.getRequirementRelatedSnapshotJson());
        dto.setRequirementConflictReportJson(context.getRequirementConflictReportJson());
        dto.setDesignBasis(context.getDesignBasis());
        dto.setValidationResultJson(context.getValidationResultJson());
        dto.setSourceFingerprintsJson(context.getSourceFingerprintsJson());
        dto.setFailureSummary(context.getFailureSummary());
        dto.setFailureDetail(context.getFailureDetail());
        dto.setGeneratedAt(context.getGeneratedAt());
        return dto;
    }
}
