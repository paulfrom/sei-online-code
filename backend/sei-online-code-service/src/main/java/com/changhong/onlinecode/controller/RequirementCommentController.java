package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.RequirementCommentApi;
import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.service.RequirementCommentService;
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
 * RequirementComment 控制器。
 */
@RestController
@Tag(name = "RequirementCommentApi", description = "Requirement 评论服务")
@RequestMapping(path = RequirementCommentApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class RequirementCommentController extends BaseEntityController<RequirementComment, RequirementCommentDto>
        implements RequirementCommentApi {

    private final RequirementCommentService service;

    public RequirementCommentController(RequirementCommentService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<RequirementComment> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<RequirementCommentDto>> findByPage(Search search) {
        PageResult<RequirementComment> page = service.findByPage(search);
        PageResult<RequirementCommentDto> dtoPage = new PageResult<>(page);
        dtoPage.setRows(page.getRows().stream().map(service::convertToDto).collect(Collectors.toList()));
        return ResultData.success(dtoPage);
    }

    @Override
    public ResultData<List<RequirementCommentDto>> findByRequirement(String requirementId) {
        return ResultData.success(service.findByRequirementId(requirementId).stream()
                .map(service::convertToDto)
                .collect(Collectors.toList()));
    }
}
