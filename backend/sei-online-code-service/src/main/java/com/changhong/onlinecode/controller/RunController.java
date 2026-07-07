package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.RunApi;
import com.changhong.onlinecode.dto.RunDto;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RunService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Run 管理控制器。实现 {@link RunApi}，契约 Phase 2 §2 端点 13/14。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "RunApi", description = "运行记录服务")
@RequestMapping(path = RunApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class RunController extends BaseEntityController<Run, RunDto>
        implements RunApi {

    private final RunService service;

    public RunController(RunService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<Run> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<RunDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<List<RunDto>> findByCodingTask(String codingTaskId) {
        List<Run> runs = service.findByCodingTaskId(codingTaskId);
        List<RunDto> dtos = runs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResultData.success(dtos);
    }
}
