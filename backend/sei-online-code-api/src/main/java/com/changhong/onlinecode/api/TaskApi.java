package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.TaskDto;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Task 管理 API。契约 Phase 2 §2 端点 11/12。
 *
 * <ul>
 *   <li>#11 POST /task/findByPage —— FindByPageApi.findByPage（按 featureDesignId 过滤）</li>
 *   <li>#12 GET  /task/findOne    —— BaseEntityApi.findOne</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = TaskApi.PATH)
public interface TaskApi extends BaseEntityApi<TaskDto>, FindByPageApi<TaskDto> {

    String PATH = "task";
}
