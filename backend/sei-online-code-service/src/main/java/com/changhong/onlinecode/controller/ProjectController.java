package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.ProjectApi;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.ProjectDto;
import com.changhong.onlinecode.dto.ProjectStateDto;
import com.changhong.onlinecode.dto.SpecDto;
import com.changhong.onlinecode.dto.request.OptimizeProjectRequest;
import com.changhong.onlinecode.dto.request.RefineSpecRequest;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.onlinecode.service.BuildLoopService;
import com.changhong.onlinecode.service.FeatureDesignBuildService;
import com.changhong.onlinecode.service.ProjectService;
import com.changhong.onlinecode.service.SpecService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.ResultDataUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final SpecService specService;
    private final BuildLoopService buildLoopService;
    private final FeatureDesignBuildService featureDesignBuildService;

    public ProjectController(ProjectService service,
                            SpecService specService,
                            BuildLoopService buildLoopService,
                            FeatureDesignBuildService featureDesignBuildService) {
        this.service = service;
        this.specService = specService;
        this.buildLoopService = buildLoopService;
        this.featureDesignBuildService = featureDesignBuildService;
    }

    @Override
    public BaseEntityService<Project> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<ProjectDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<SpecDto> refineSpec(RefineSpecRequest request) {
        OperateResultWithData<Spec> result = specService.refineSpec(request.getProjectId());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertSpecToDto(result.getData()));
    }

    @Override
    public ResultData<IterationDto> optimize(OptimizeProjectRequest request) {
        OperateResultWithData<Iteration> result =
                buildLoopService.optimize(request.getProjectId(), request.getFeedback());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertIterationToDto(result.getData()));
    }

    @Override
    public ResultData<ProjectStateDto> state(String id) {
        Project project = service.findOne(id);
        if (project == null) {
            return ResultData.fail("项目不存在: " + id);
        }
        return ResultData.success(
                new ProjectStateDto(project.getState(), project.getCurrentIterationId()));
    }

    @Override
    public ResultData<java.util.List<FeatureDesignBuildResultDto>> build(String projectId) {
        return ResultData.success(featureDesignBuildService.buildProject(projectId));
    }

    /**
     * Spec 实体 → DTO。Spec 与 Project 属不同聚合，此处独立映射避免污染默认 ModelMapper。
     *
     * @param spec Spec 实体
     * @return SpecDto
     */
    private SpecDto convertSpecToDto(Spec spec) {
        SpecDto dto = new SpecDto();
        dto.setId(spec.getId());
        dto.setProjectId(spec.getProjectId());
        dto.setVersion(spec.getVersion());
        dto.setState(spec.getState());
        dto.setPages(spec.getPages());
        dto.setComponents(spec.getComponents());
        dto.setEntities(spec.getEntities());
        dto.setApiContract(spec.getApiContract());
        dto.setCreatedDate(spec.getCreatedDate());
        return dto;
    }

    /**
     * Iteration 实体 → DTO。Iteration 属另一聚合，独立映射以携带 Phase 4 回合溯源字段。
     *
     * @param iteration 迭代实体
     * @return IterationDto
     */
    private IterationDto convertIterationToDto(Iteration iteration) {
        IterationDto dto = new IterationDto();
        dto.setId(iteration.getId());
        dto.setProjectId(iteration.getProjectId());
        dto.setSpecId(iteration.getSpecId());
        dto.setSpecVersion(iteration.getSpecVersion());
        dto.setRound(iteration.getRound());
        dto.setParentIterationId(iteration.getParentIterationId());
        dto.setFeedback(iteration.getFeedback());
        dto.setState(iteration.getState());
        dto.setPreviewUrl(iteration.getPreviewUrl());
        dto.setCreatedDate(iteration.getCreatedDate());
        dto.setFinishedDate(iteration.getFinishedDate());
        return dto;
    }
}
