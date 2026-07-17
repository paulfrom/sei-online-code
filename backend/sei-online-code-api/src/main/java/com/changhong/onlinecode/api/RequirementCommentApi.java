package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * RequirementComment API——仅追加（append-only），不暴露更新/删除端点。
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RequirementCommentApi.PATH)
public interface RequirementCommentApi {

    String PATH = "requirementComment";

    @GetMapping(path = "requirement/{requirementId}")
    @Operation(summary = "按 Requirement 查询评论")
    ResultData<List<RequirementCommentDto>> findByRequirement(@PathVariable("requirementId") String requirementId);
}
