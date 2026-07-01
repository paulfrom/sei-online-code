package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RunDto;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;

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
}
