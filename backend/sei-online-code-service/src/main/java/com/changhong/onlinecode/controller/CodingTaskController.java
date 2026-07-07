package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.CodingTaskApi;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.request.RerunCodingTaskRequest;
import com.changhong.onlinecode.dto.request.RunCodingTaskRequest;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.service.CodingTaskService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * CodingTask 控制器。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "CodingTaskApi", description = "编码任务服务")
@RequestMapping(path = CodingTaskApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class CodingTaskController extends BaseEntityController<CodingTask, CodingTaskDto>
        implements CodingTaskApi {

    private final CodingTaskService service;

    public CodingTaskController(CodingTaskService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<CodingTask> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<CodingTaskDto>> findByPage(Search search) {
        PageResult<CodingTask> page = service.findByPage(search);
        PageResult<CodingTaskDto> dtoPage = new PageResult<>(page);
        dtoPage.setRows(page.getRows().stream()
                .map(service::convertToDto)
                .collect(Collectors.toList()));
        return ResultData.success(dtoPage);
    }

    @Override
    public ResultData<CodingTaskDto> run(String id, RunCodingTaskRequest request) {
        return service.run(id, request.getUserPrompt());
    }

    @Override
    public ResultData<CodingTaskDto> rerun(String id, RerunCodingTaskRequest request) {
        return service.rerun(id, request.getRerunPrompt());
    }

    @Override
    public ResultData<CodingTaskDto> cancel(String id) {
        return service.cancel(id);
    }
}
