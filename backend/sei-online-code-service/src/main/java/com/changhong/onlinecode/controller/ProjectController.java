package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.ProjectApi;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.ProjectDto;
import com.changhong.onlinecode.dto.ProjectStateDto;
import com.changhong.onlinecode.dto.request.RefineSpecRequest;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.service.FeatureDesignBuildService;
import com.changhong.onlinecode.service.ProjectService;
import com.changhong.onlinecode.service.ProjectStateService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * 项目管理控制器。实现 {@link ProjectApi}，契约 §3 端点 1/2/3/4/9。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "ProjectApi", description = "项目管理服务")
@RequestMapping(path = ProjectApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProjectController extends BaseEntityController<Project, ProjectDto>
        implements ProjectApi {

    private final ProjectService service;
    private final ProjectStateService projectStateService;
    private final FeatureDesignBuildService featureDesignBuildService;

    public ProjectController(ProjectService service,
                            ProjectStateService projectStateService,
                            FeatureDesignBuildService featureDesignBuildService) {
        this.service = service;
        this.projectStateService = projectStateService;
        this.featureDesignBuildService = featureDesignBuildService;
    }

    @Override
    public BaseEntityService<Project> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<ProjectDto>> findByPage(Search search) {
        PageResult<Project> page = service.findByPage(search);
        PageResult<ProjectDto> dtoPage = new PageResult<>(page);
        dtoPage.setRows(page.getRows().stream()
                .map(this::convertProjectToDto)
                .collect(Collectors.toList()));
        return ResultData.success(dtoPage);
    }

    @Override
    public ResultData<PlanDto> refineSpec(RefineSpecRequest request) {
        OperateResultWithData<PlanDto> result = service.refineSpec(request.getProjectId());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(result.getData());
    }

    @Override
    public ResultData<ProjectStateDto> state(String id) {
        Project project = service.findOne(id);
        if (project == null) {
            return ResultData.fail("项目不存在: " + id);
        }
        return ResultData.success(new ProjectStateDto(projectStateService.resolvePreBuildState(id)));
    }

    @Override
    public ResultData<java.util.List<FeatureDesignBuildResultDto>> build(String projectId) {
        return ResultData.success(featureDesignBuildService.buildProject(projectId));
    }

    /**
     * Project 实体 → DTO。对外暴露编码前聚合状态，而不是历史持久化生命周期列。
     *
     * @param project Project 实体
     * @return ProjectDto
     */
    private ProjectDto convertProjectToDto(Project project) {
        ProjectDto dto = new ProjectDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDesign(project.getDesign());
        dto.setGitUrl(project.getGitUrl());
        dto.setProjectCode(project.getProjectCode());
        dto.setProjectVersion(project.getProjectVersion());
        dto.setPackageName(project.getPackageName());
        dto.setWorkspacePath(project.getWorkspacePath());
        dto.setAutoRunCodingTask(project.getAutoRunCodingTask());
        dto.setMemorySeedTemplateId(project.getMemorySeedTemplateId());
        dto.setState(projectStateService.resolvePreBuildState(project.getId()));
        dto.setCurrentSpecId(project.getCurrentSpecId());
        dto.setCreatedDate(project.getCreatedDate());
        dto.setLastEditedDate(project.getLastEditedDate());
        return dto;
    }
}
