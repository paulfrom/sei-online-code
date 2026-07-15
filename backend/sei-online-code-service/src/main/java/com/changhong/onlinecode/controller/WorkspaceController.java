package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.api.WorkspaceApi;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区解析控制器（B35）。实现 {@link WorkspaceApi}，契约 Phase 5 §2 端点 33 / §3。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "WorkspaceApi", description = "工作区解析服务")
@RequestMapping(path = WorkspaceApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class WorkspaceController implements WorkspaceApi {

    private final WorkspaceManager workspaceManager;

    @Override
    public ResultData<WorkspaceResolveResult> resolve(String projectId) {
        return ResultData.success(workspaceManager.resolve(projectId));
    }
}
