package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.RequirementCommentApi;
import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RequirementComment 控制器——仅查询，不暴露更新/删除端点。
 */
@RestController
@Tag(name = "RequirementCommentApi", description = "Requirement 评论服务（append-only）")
@RequestMapping(path = RequirementCommentApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class RequirementCommentController implements RequirementCommentApi {

    private final RequirementCommentService service;

    @Override
    public ResultData<List<RequirementCommentDto>> findByRequirement(String requirementId) {
        return ResultData.success(service.findByRequirementId(requirementId).stream()
                .map(service::convertToDto)
                .collect(Collectors.toList()));
    }
}
