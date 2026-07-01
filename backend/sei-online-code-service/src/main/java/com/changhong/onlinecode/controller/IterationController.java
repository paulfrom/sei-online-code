package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.IterationApi;
import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.request.DeployIterationRequest;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.service.IterationService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 迭代管理控制器。实现 {@link IterationApi}，契约 §3 端点 7/8。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "IterationApi", description = "迭代管理服务")
@RequestMapping(path = IterationApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class IterationController extends BaseEntityController<Iteration, IterationDto>
        implements IterationApi {

    private final IterationService service;

    public IterationController(IterationService service) {
        this.service = service;
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
}
