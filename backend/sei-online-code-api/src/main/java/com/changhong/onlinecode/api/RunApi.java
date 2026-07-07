package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RunDto;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Run 管理 API。契约 Phase 2 §2 端点 13/14。
 *
 * <ul>
 *   <li>#13 POST /run/findByPage —— FindByPageApi.findByPage（按 iterationId / taskId 过滤）</li>
 *   <li>#14 GET  /run/findOne    —— BaseEntityApi.findOne（轮询状态/exitCode）</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RunApi.PATH)
public interface RunApi extends BaseEntityApi<RunDto>, FindByPageApi<RunDto> {

    String PATH = "run";

    @GetMapping(path = "findByCodingTask")
    @Operation(summary = "按编码任务查询 Run 历史")
    ResultData<List<RunDto>> findByCodingTask(@RequestParam("codingTaskId") String codingTaskId);
}
