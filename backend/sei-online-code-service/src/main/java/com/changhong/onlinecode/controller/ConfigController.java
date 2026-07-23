package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.ConfigApi;
import com.changhong.onlinecode.dto.PlatformConfigDto;
import com.changhong.onlinecode.dto.request.SavePlatformConfigRequest;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.service.ConfigService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台配置控制器（B34）。实现 {@link ConfigApi}，契约 Phase 5 §2 端点 31/32。
 *
 * <p>单例配置行不走标准实体 CRUD，故为 plain 控制器、手工 DTO 映射（不经 BaseEntityController）。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "ConfigApi", description = "平台配置服务")
@RequestMapping(path = ConfigApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ConfigController implements ConfigApi {

    private final ConfigService service;

    @Override
    public ResultData<PlatformConfigDto> get() {
        return ResultData.success(toDto(service.get()));
    }

    @Override
    public ResultData<PlatformConfigDto> save(SavePlatformConfigRequest request) {
        OperateResultWithData<PlatformConfig> result =
                service.save(request.getWorkspaceRoot(), request.getTemplateGitlabUrl(),
                        request.getGitlabHost(), request.getGitlabToken(),
                        request.getGitlabProjectId(), request.getGitlabTargetBranch());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(toDto(result.getData()));
    }

    /**
     * PlatformConfig 实体 → DTO。单例配置独立映射，不污染默认 ModelMapper。
     *
     * @param config 配置实体
     * @return PlatformConfigDto
     */
    private PlatformConfigDto toDto(PlatformConfig config) {
        PlatformConfigDto dto = new PlatformConfigDto();
        dto.setId(config.getId());
        dto.setWorkspaceRoot(config.getWorkspaceRoot());
        dto.setTemplateGitlabUrl(config.getTemplateGitlabUrl());
        dto.setGitlabHost(config.getGitlabHost());
        dto.setGitlabProjectId(config.getGitlabProjectId());
        dto.setGitlabTargetBranch(config.getGitlabTargetBranch());
        dto.setCreatedDate(config.getCreatedDate());
        return dto;
    }
}
