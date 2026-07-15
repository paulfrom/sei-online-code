package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.TaskApi;
import com.changhong.onlinecode.dto.TaskDto;
import com.changhong.onlinecode.entity.Task;
import com.changhong.onlinecode.service.TaskService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 管理控制器。实现 {@link TaskApi}，契约 Phase 2 §2 端点 11/12。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "TaskApi", description = "任务管理服务")
@RequestMapping(path = TaskApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class TaskController extends BaseEntityController<Task, TaskDto>
        implements TaskApi {

    private final TaskService service;

    @Override
    public BaseEntityService<Task> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<TaskDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }
}
