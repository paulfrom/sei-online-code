package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.DetailedDesignDto;
import com.changhong.onlinecode.dto.request.BatchConfirmDetailedDesignRequest;
import com.changhong.onlinecode.dto.request.EditDetailedDesignRequest;
import com.changhong.onlinecode.dto.request.RegenerateDetailedDesignRequest;
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

import java.util.List;

/**
 * DetailedDesign API。契约 §3.4。
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = DetailedDesignApi.PATH)
public interface DetailedDesignApi {

    String PATH = "detailed-design";

    @GetMapping(path = "findOne")
    @Operation(summary = "查询详细设计")
    ResultData<DetailedDesignDto> findOne(@RequestParam("id") String id);

    @GetMapping(path = "findByOverview")
    @Operation(summary = "按概览设计查询")
    ResultData<List<DetailedDesignDto>> findByOverview(@RequestParam("overviewDesignId") String overviewDesignId);

    @PostMapping(path = "{id}/regenerate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重生成详细设计")
    ResultData<DetailedDesignDto> regenerate(@PathVariable("id") String id,
                                                   @RequestBody @Valid RegenerateDetailedDesignRequest request);

    @PostMapping(path = "{id}/edit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编辑详细设计")
    ResultData<DetailedDesignDto> edit(@PathVariable("id") String id,
                                          @RequestBody @Valid EditDetailedDesignRequest request);

    @PostMapping(path = "{id}/confirm")
    @Operation(summary = "确认详细设计", description = "确认后创建 CodingTask")
    ResultData<DetailedDesignDto> confirm(@PathVariable("id") String id);

    @PostMapping(path = "batchConfirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量确认详细设计")
    ResultData<List<DetailedDesignDto>> batchConfirm(@RequestBody @Valid BatchConfirmDetailedDesignRequest request);
}
