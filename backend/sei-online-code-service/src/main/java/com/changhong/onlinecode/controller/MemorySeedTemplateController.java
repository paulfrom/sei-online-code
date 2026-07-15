package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.MemorySeedTemplateApi;
import com.changhong.onlinecode.dto.MemorySeedTemplateDto;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.onlinecode.service.MemorySeedTemplateService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 平台 seed 记忆模板配置控制器。实现 {@link MemorySeedTemplateApi}，
 * 契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §17.1。
 *
 * <p>plain 控制器、手工 DTO 映射（不经 BaseEntityController）。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "MemorySeedTemplateApi", description = "平台 seed 记忆模板配置服务")
@RequestMapping(path = MemorySeedTemplateApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class MemorySeedTemplateController implements MemorySeedTemplateApi {

    private final MemorySeedTemplateService service;

    @Override
    public ResultData<List<MemorySeedTemplateDto>> list() {
        return ResultData.success(service.findActiveTemplates().stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> activeDefault() {
        MemorySeedTemplate template = service.bootstrapDefaultIfAbsent();
        return ResultData.success(toDto(template));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> get(String id) {
        MemorySeedTemplate template = service.findOne(id);
        if (Objects.isNull(template)) {
            return ResultData.fail("seed 模板不存在: " + id);
        }
        return ResultData.success(toDto(template));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> saveDraft(MemorySeedTemplateDto dto) {
        MemorySeedTemplate draft = new MemorySeedTemplate();
        draft.setId(dto.getId());
        draft.setCode(dto.getCode());
        draft.setName(dto.getName());
        draft.setDescription(dto.getDescription());
        draft.setProjectMemoryTemplate(dto.getProjectMemoryTemplate());
        draft.setMemoryRulesTemplate(dto.getMemoryRulesTemplate());
        draft.setDecisionsTemplate(dto.getDecisionsTemplate());
        draft.setModulesTemplate(dto.getModulesTemplate());
        MemorySeedTemplate saved;
        if (Objects.isNull(dto.getId())) {
            OperateResultWithData<MemorySeedTemplate> result = service.save(draft);
            if (result.notSuccessful()) {
                return ResultData.fail(result.getMessage());
            }
            saved = result.getData();
        } else {
            OperateResultWithData<MemorySeedTemplate> result = service.saveDraft(dto.getId(), draft);
            if (result.notSuccessful()) {
                return ResultData.fail(result.getMessage());
            }
            saved = result.getData();
        }
        return ResultData.success(toDto(saved));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> publish(String id) {
        OperateResultWithData<MemorySeedTemplate> result = service.publish(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(toDto(result.getData()));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> setDefault(String id) {
        OperateResultWithData<MemorySeedTemplate> result = service.setDefault(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(toDto(result.getData()));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> archive(String id) {
        OperateResultWithData<MemorySeedTemplate> result = service.archive(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(toDto(result.getData()));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> bootstrapDefault() {
        return ResultData.success(toDto(service.bootstrapDefaultIfAbsent()));
    }

    @Override
    public ResultData<MemorySeedTemplateDto> resolve(String memorySeedTemplateId) {
        MemorySeedTemplate template = service.resolveForProject(memorySeedTemplateId);
        if (Objects.isNull(template)) {
            template = service.bootstrapDefaultIfAbsent();
        }
        return ResultData.success(toDto(template));
    }

    /**
     * 实体 → DTO。
     */
    private MemorySeedTemplateDto toDto(MemorySeedTemplate template) {
        if (template == null) {
            return null;
        }
        MemorySeedTemplateDto dto = new MemorySeedTemplateDto();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setVersion(template.getVersion());
        dto.setStatus(template.getStatus());
        dto.setIsDefault(template.getIsDefault());
        dto.setSourceType(template.getSourceType());
        dto.setProjectMemoryTemplate(template.getProjectMemoryTemplate());
        dto.setMemoryRulesTemplate(template.getMemoryRulesTemplate());
        dto.setDecisionsTemplate(template.getDecisionsTemplate());
        dto.setModulesTemplate(template.getModulesTemplate());
        dto.setPublishedAt(template.getPublishedAt());
        dto.setArchivedAt(template.getArchivedAt());
        return dto;
    }
}