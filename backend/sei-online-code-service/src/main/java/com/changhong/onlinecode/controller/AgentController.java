package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.AgentApi;
import com.changhong.onlinecode.dto.AgentDto;
import com.changhong.onlinecode.dto.request.AttachSkillsRequest;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.service.AgentService;
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

/**
 * Agent 管理控制器。实现 {@link AgentApi}，契约 Phase 3 §2 端点 20/21/22/23/24。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "AgentApi", description = "Agent 管理服务")
@RequestMapping(path = AgentApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class AgentController extends BaseEntityController<Agent, AgentDto>
        implements AgentApi {

    private final AgentService service;

    @Override
    public BaseEntityService<Agent> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<AgentDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<AgentDto> skills(AttachSkillsRequest request) {
        OperateResultWithData<Agent> result =
                service.attachSkills(request.getAgentId(), request.getSkillIds());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertToDto(result.getData()));
    }
}
