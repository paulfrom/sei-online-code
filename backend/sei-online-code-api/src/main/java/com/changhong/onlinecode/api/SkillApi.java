package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.SkillDto;
import com.changhong.onlinecode.dto.request.ImportSkillRequest;
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
 * Skill 管理 API。契约 Phase 3 §2 端点 16/17/18/19。
 *
 * <ul>
 *   <li>#16 POST   /skill/import     —— 导入，以 name 去重，同名已存在返回 409</li>
 *   <li>#17 POST   /skill/findByPage —— FindByPageApi.findByPage</li>
 *   <li>#18 GET    /skill/findOne    —— BaseEntityApi.findOne</li>
 *   <li>#19 DELETE /skill/delete     —— BaseEntityApi.delete（绑定到任一 agent 则拒绝）</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = SkillApi.PATH)
public interface SkillApi extends BaseEntityApi<SkillDto>, FindByPageApi<SkillDto> {

    String PATH = "skill";

    @PostMapping(path = "import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "导入技能", description = "导入技能，以 name 为去重键；同名已存在抛 ConflictException 返回 409。computedHash 由服务端运行时计算返回")
    ResultData<SkillDto> importSkill(@RequestBody @Valid ImportSkillRequest request);
}
