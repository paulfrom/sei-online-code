package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.FeatureDesignApi;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.FeatureDesignDto;
import com.changhong.onlinecode.dto.request.ConfirmFeatureDesignsRequest;
import com.changhong.onlinecode.dto.request.EditFeatureDesignRequest;
import com.changhong.onlinecode.dto.request.RegenerateFeatureDesignRequest;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.service.FeatureDesignBuildService;
import com.changhong.onlinecode.service.FeatureDesignService;
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

import java.util.List;

/**
 * 功能设计控制器。实现 {@link FeatureDesignApi}，契约 §5 端点 P6–P11、P12a、P14。
 *
 * <p>继承 {@link BaseEntityController} 提供 BaseEntityApi/FindByPageApi 方法（save/findOne/findByPage）；
 * build 端点委托 {@link FeatureDesignBuildService}。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "FeatureDesignApi", description = "功能设计服务")
@RequestMapping(path = FeatureDesignApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class FeatureDesignController extends BaseEntityController<FeatureDesign, FeatureDesignDto>
        implements FeatureDesignApi {

    private final FeatureDesignService service;
    private final FeatureDesignBuildService buildService;

    @Override
    public BaseEntityService<FeatureDesign> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<FeatureDesignDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<FeatureDesignDto> getLatest(String id) {
        FeatureDesignDto dto = service.findOneLatest(id);
        return dto == null ? ResultData.fail("功能设计不存在: " + id) : ResultData.success(dto);
    }

    @Override
    public ResultData<FeatureDesignDto> edit(String id, EditFeatureDesignRequest request) {
        OperateResultWithData<FeatureDesignDto> result = service.edit(id, request.getContent());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<FeatureDesignDto> regenerate(String id, RegenerateFeatureDesignRequest request) {
        OperateResultWithData<FeatureDesignDto> result = service.regenerate(id, request.getModifyHint());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<List<FeatureDesignDto>> confirm(ConfirmFeatureDesignsRequest request) {
        OperateResultWithData<List<FeatureDesignDto>> result = service.confirm(request.getIds());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<FeatureDesignDto> confirmOne(String id) {
        OperateResultWithData<FeatureDesignDto> result = service.confirmOne(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<FeatureDesignBuildResultDto> build(String id) {
        OperateResultWithData<FeatureDesignBuildResultDto> result = buildService.build(id);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<List<FeatureDesignDto>> history(String id) {
        return ResultData.success(service.history(id));
    }
}
