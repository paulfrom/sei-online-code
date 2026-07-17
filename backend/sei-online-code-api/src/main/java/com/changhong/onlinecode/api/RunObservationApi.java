package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.progress.AppendManualRunObservationRequest;
import com.changhong.onlinecode.dto.progress.RunObservationDto;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Run observation 查询与人工追加 API。契约 ADR-001 §9 / 计划 §3。
 *
 * <p>人工 observation 只追加，不能直接改 step/effect 状态；状态变化须经受控命令并另行追加 checkpoint。</p>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RunObservationApi.PATH)
public interface RunObservationApi {

    String PATH = "runObservation";

    @GetMapping("findByRun")
    @Operation(summary = "Run observation 分页（按 observedAt 倒序）")
    ResultData<PageResult<RunObservationDto>> findByRun(@RequestParam("runId") String runId,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "rows", defaultValue = "20") int rows);

    @PostMapping(path = "appendManual", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "人工追加 observation",
            description = "仅授权用户可调用；只追加 observation，不直接修改 step/effect 状态。")
    ResultData<RunObservationDto> appendManual(@RequestBody @Valid AppendManualRunObservationRequest request);
}
