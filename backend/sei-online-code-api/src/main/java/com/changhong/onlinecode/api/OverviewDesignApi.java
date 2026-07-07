package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.OverviewDesignDto;
import com.changhong.onlinecode.dto.request.EditOverviewRequest;
import com.changhong.onlinecode.dto.request.RegenerateOverviewRequest;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OverviewDesign API。契约 §3.3。
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = OverviewDesignApi.PATH)
public interface OverviewDesignApi {

    String PATH = "overview-design";

    @GetMapping(path = "findOne")
    @Operation(summary = "查询概览设计", description = "按需求 ID 查询当前概览设计")
    ResultData<OverviewDesignDto> findOne(@RequestParam("requirementId") String requirementId);

    @PostMapping(path = "{id}/regenerate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重生成概览设计")
    ResultData<OverviewDesignDto> regenerate(@PathVariable("id") String id,
                                                  @RequestBody @Valid RegenerateOverviewRequest request);

    @PostMapping(path = "{id}/edit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编辑概览设计")
    ResultData<OverviewDesignDto> edit(@PathVariable("id") String id,
                                          @RequestBody @Valid EditOverviewRequest request);

    @PostMapping(path = "{id}/confirm")
    @Operation(summary = "确认概览设计", description = "确认后拆分为 feature 级详细设计")
    ResultData<OverviewDesignDto> confirm(@PathVariable("id") String id);
}
