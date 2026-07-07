package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.DetailedDesignApi;
import com.changhong.onlinecode.dto.DetailedDesignDto;
import com.changhong.onlinecode.dto.request.BatchConfirmDetailedDesignRequest;
import com.changhong.onlinecode.dto.request.EditDetailedDesignRequest;
import com.changhong.onlinecode.dto.request.RegenerateDetailedDesignRequest;
import com.changhong.onlinecode.service.DetailedDesignService;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DetailedDesign 控制器。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "DetailedDesignApi", description = "详细设计服务")
@RequestMapping(path = DetailedDesignApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class DetailedDesignController implements DetailedDesignApi {

    private final DetailedDesignService service;

    public DetailedDesignController(DetailedDesignService service) {
        this.service = service;
    }

    @Override
    public ResultData<DetailedDesignDto> findOne(String id) {
        return ResultData.success(service.findOneDto(id));
    }

    @Override
    public ResultData<List<DetailedDesignDto>> findByOverview(String overviewDesignId) {
        return ResultData.success(service.findByOverviewDesignId(overviewDesignId));
    }

    @Override
    public ResultData<DetailedDesignDto> regenerate(String id, RegenerateDetailedDesignRequest request) {
        return service.regenerate(id, request.getPrompt());
    }

    @Override
    public ResultData<DetailedDesignDto> edit(String id, EditDetailedDesignRequest request) {
        return service.edit(id, request.getContent());
    }

    @Override
    public ResultData<DetailedDesignDto> confirm(String id) {
        return service.confirm(id);
    }

    @Override
    public ResultData<List<DetailedDesignDto>> batchConfirm(BatchConfirmDetailedDesignRequest request) {
        return service.batchConfirm(request.getIds());
    }
}
