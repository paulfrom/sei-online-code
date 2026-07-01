package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.AgentDto;
import com.changhong.onlinecode.dto.request.AttachSkillsRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Agent 管理 API。契约 Phase 3 §2 端点 20/21/22/23/24。
 *
 * <ul>
 *   <li>#20 POST   /agent/save      —— BaseEntityApi.save（无 id 即创建）</li>
 *   <li>#21 POST   /agent/findByPage—— FindByPageApi.findByPage（内置 + 自定义）</li>
 *   <li>#22 GET    /agent/findOne   —— BaseEntityApi.findOne</li>
 *   <li>#23 DELETE /agent/delete    —— BaseEntityApi.delete（内置拒绝删除）</li>
 *   <li>#24 POST   /agent/skills    —— 附加/替换绑定技能</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = AgentApi.PATH)
public interface AgentApi extends BaseEntityApi<AgentDto>, FindByPageApi<AgentDto> {

    String PATH = "agent";

    @PostMapping(path = "skills", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "附加 agent 技能", description = "整体替换 agent 绑定的技能 id 列表（两步式 create→attach 第二步）")
    ResultData<AgentDto> skills(@RequestBody @Valid AttachSkillsRequest request);
}
