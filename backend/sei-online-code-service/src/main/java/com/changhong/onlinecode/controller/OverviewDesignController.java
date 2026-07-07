package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.OverviewDesignApi;
import com.changhong.onlinecode.dto.OverviewDesignDto;
import com.changhong.onlinecode.dto.request.EditOverviewRequest;
import com.changhong.onlinecode.dto.request.RegenerateOverviewRequest;
import com.changhong.onlinecode.service.OverviewDesignService;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OverviewDesign 控制器。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "OverviewDesignApi", description = "概览设计服务")
@RequestMapping(path = OverviewDesignApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class OverviewDesignController implements OverviewDesignApi {

    private final OverviewDesignService service;

    public OverviewDesignController(OverviewDesignService service) {
        this.service = service;
    }

    @Override
    public ResultData<OverviewDesignDto> findOne(String requirementId) {
        OverviewDesignDto dto = service.findByRequirementId(requirementId);
        return ResultData.success(dto);
    }

    @Override
    public ResultData<OverviewDesignDto> regenerate(String id, RegenerateOverviewRequest request) {
        return service.regenerate(id, request.getPrompt());
    }

    @Override
    public ResultData<OverviewDesignDto> edit(String id, EditOverviewRequest request) {
        return service.edit(id, request.getContent());
    }

    @Override
    public ResultData<OverviewDesignDto> confirm(String id) {
        return service.confirm(id);
    }
}
