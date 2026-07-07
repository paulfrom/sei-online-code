package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RequirementDto;
import com.changhong.onlinecode.dto.request.EditPrdRequest;
import com.changhong.onlinecode.dto.request.RegeneratePrdRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Requirement API。契约 §3.2。
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RequirementApi.PATH)
public interface RequirementApi extends BaseEntityApi<RequirementDto>, FindByPageApi<RequirementDto> {

    String PATH = "requirement";

    @PostMapping(path = "{id}/regeneratePrd", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重生成 PRD", description = "PRD_REVIEW 或 FAILED 状态下可重生成")
    ResultData<RequirementDto> regeneratePrd(@PathVariable("id") String id,
                                                   @RequestBody @Valid RegeneratePrdRequest request);

    @PostMapping(path = "{id}/editPrd", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编辑 PRD", description = "PRD_REVIEW 状态下可编辑")
    ResultData<RequirementDto> editPrd(@PathVariable("id") String id,
                                          @RequestBody @Valid EditPrdRequest request);

    @PostMapping(path = "{id}/confirmPrd")
    @Operation(summary = "确认 PRD", description = "确认后冻结 PRD 并创建概览设计")
    ResultData<RequirementDto> confirmPrd(@PathVariable("id") String id);
}
