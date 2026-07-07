package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.request.RerunCodingTaskRequest;
import com.changhong.onlinecode.dto.request.RunCodingTaskRequest;
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
 * CodingTask API。契约 §3.5。
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = CodingTaskApi.PATH)
public interface CodingTaskApi extends BaseEntityApi<CodingTaskDto>, FindByPageApi<CodingTaskDto> {

    String PATH = "coding-task";

    @PostMapping(path = "{id}/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "运行编码任务", description = "首次/手动运行，userPrompt 可空")
    ResultData<CodingTaskDto> run(@PathVariable("id") String id,
                                     @RequestBody @Valid RunCodingTaskRequest request);

    @PostMapping(path = "{id}/rerun", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重跑编码任务", description = "rerunPrompt 必填")
    ResultData<CodingTaskDto> rerun(@PathVariable("id") String id,
                                       @RequestBody @Valid RerunCodingTaskRequest request);

    @PostMapping(path = "{id}/cancel")
    @Operation(summary = "取消编码任务")
    ResultData<CodingTaskDto> cancel(@PathVariable("id") String id);
}
