package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * RequirementComment API。
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RequirementCommentApi.PATH)
public interface RequirementCommentApi extends BaseEntityApi<RequirementCommentDto>, FindByPageApi<RequirementCommentDto> {

    String PATH = "requirementComment";

    @GetMapping(path = "requirement/{requirementId}")
    @Operation(summary = "按 Requirement 查询评论")
    ResultData<List<RequirementCommentDto>> findByRequirement(@PathVariable("requirementId") String requirementId);
}
