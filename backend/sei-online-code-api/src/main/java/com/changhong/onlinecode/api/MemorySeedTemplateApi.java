package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.MemorySeedTemplateDto;
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
 * 平台 seed 记忆模板配置 API。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §17.1。
 *
 * <ul>
 *   <li>GET  /api/memory/seed-templates                —— 列出全部模板</li>
 *   <li>GET  /api/memory/seed-templates/active-default —— 当前全局默认模板</li>
 *   <li>GET  /api/memory/seed-templates/{id}           —— 查询单个模板</li>
 *   <li>POST /api/memory/seed-templates                —— 保存草稿（新建/更新）</li>
 *   <li>POST /api/memory/seed-templates/{id}/save-draft —— 保存草稿内容</li>
 *   <li>POST /api/memory/seed-templates/{id}/publish   —— 发布新版本</li>
 *   <li>POST /api/memory/seed-templates/{id}/set-default —— 设为全局默认</li>
 *   <li>POST /api/memory/seed-templates/{id}/archive   —— 归档</li>
 *   <li>POST /api/memory/seed-templates/bootstrap-default —— 从 classpath 重建默认</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = MemorySeedTemplateApi.PATH)
public interface MemorySeedTemplateApi {

    String PATH = "api/memory/seed-templates";

    @GetMapping
    @Operation(summary = "列出全部 seed 模板", description = "返回全部 seed 模板（按状态与版本）")
    ResultData<List<MemorySeedTemplateDto>> list();

    @GetMapping(path = "active-default")
    @Operation(summary = "查询全局默认模板", description = "返回当前 ACTIVE + is_default=true 模板；缺失则 bootstrap")
    ResultData<MemorySeedTemplateDto> activeDefault();

    @GetMapping(path = "{id}")
    @Operation(summary = "查询单个模板", description = "按 id 查询 seed 模板")
    ResultData<MemorySeedTemplateDto> get(@PathVariable("id") String id);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "保存草稿", description = "新建或更新草稿态模板；不影响新项目")
    ResultData<MemorySeedTemplateDto> saveDraft(@RequestBody @Valid MemorySeedTemplateDto dto);

    @PostMapping(path = "{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "发布模板", description = "发布新版本 ACTIVE；不自动设为默认")
    ResultData<MemorySeedTemplateDto> publish(@PathVariable("id") String id);

    @PostMapping(path = "{id}/set-default")
    @Operation(summary = "设为全局默认", description = "事务切换全局唯一默认模板；仅接受 ACTIVE 模板")
    ResultData<MemorySeedTemplateDto> setDefault(@PathVariable("id") String id);

    @PostMapping(path = "{id}/archive")
    @Operation(summary = "归档模板", description = "归档非默认 ACTIVE 模板；默认模板需先切换")
    ResultData<MemorySeedTemplateDto> archive(@PathVariable("id") String id);

    @PostMapping(path = "bootstrap-default")
    @Operation(summary = "bootstrap 默认模板", description = "DB 缺失时从 classpath 重建默认模板")
    ResultData<MemorySeedTemplateDto> bootstrapDefault();

    /**
     * 查询项目实际使用的 seed 模板（显式优先，未绑定取默认）。供项目创建流程调用。
     *
     * @param memorySeedTemplateId 项目已绑定模板 id（可空）
     * @return 解析出的模板
     */
    @GetMapping(path = "resolve")
    @Operation(summary = "解析项目使用的 seed 模板", description = "项目绑定优先，未绑定取全局默认")
    ResultData<MemorySeedTemplateDto> resolve(@RequestParam(value = "memorySeedTemplateId", required = false)
                                              String memorySeedTemplateId);
}