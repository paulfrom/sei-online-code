package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.FeatureDesignDto;
import com.changhong.onlinecode.dto.request.ConfirmFeatureDesignsRequest;
import com.changhong.onlinecode.dto.request.EditFeatureDesignRequest;
import com.changhong.onlinecode.dto.request.RegenerateFeatureDesignRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 功能设计 API。契约 §5 端点 P6–P11、P12a、P14。
 *
 * <ul>
 *   <li>P6   POST /featureDesign/findByPage      —— FindByPageApi.findByPage（继承，D5 不重声明；projectId filter）</li>
 *   <li>P7   GET  /featureDesign/{id}             —— 取单条最新版</li>
 *   <li>P8   PUT  /featureDesign/{id}             —— 编辑（BUILDING 时 409 拒绝）</li>
 *   <li>P9   POST /featureDesign/{id}/regenerate   —— 重生（BUILDING 时 409 拒绝）</li>
 *   <li>P10  POST /featureDesign/confirm          —— 批量确认</li>
 *   <li>P11  POST /featureDesign/{id}/confirm     —— 单条确认（P10 便捷版）</li>
 *   <li>P12a POST /featureDesign/{id}/build       —— 单 feature 执行编码（条件 UPDATE 抢占，返 runId）</li>
 *   <li>P14  GET  /featureDesign/{id}/history     —— 历史版本列表</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = FeatureDesignApi.PATH)
public interface FeatureDesignApi extends BaseEntityApi<FeatureDesignDto>, FindByPageApi<FeatureDesignDto> {

    String PATH = "featureDesign";

    @GetMapping(path = "{id}")
    @Operation(summary = "取功能设计最新版", description = "按 id 取该 feature 最新版本")
    ResultData<FeatureDesignDto> getLatest(@PathVariable("id") String id);

    @PutMapping(path = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编辑功能设计", description = "该条→DRAFT；build∈{BUILT,BUILD_FAILED}→STALE；BUILDING 时 409 拒绝")
    ResultData<FeatureDesignDto> edit(@PathVariable("id") String id,
                                      @RequestBody @Valid EditFeatureDesignRequest request);

    @PostMapping(path = "{id}/regenerate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重新生成功能设计", description = "version+1；→GENERATING；BUILDING 时 409 拒绝；完成后 build_status→STALE（若原 BUILT/BUILD_FAILED）")
    ResultData<FeatureDesignDto> regenerate(@PathVariable("id") String id,
                                            @RequestBody @Valid RegenerateFeatureDesignRequest request);

    @PostMapping(path = "confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量确认功能设计", description = "逐条→CONFIRMED；全部 CONFIRMED 则 Project=READY_TO_BUILD")
    ResultData<List<FeatureDesignDto>> confirm(@RequestBody @Valid ConfirmFeatureDesignsRequest request);

    @PostMapping(path = "{id}/confirm")
    @Operation(summary = "单条确认功能设计", description = "该条→CONFIRMED；全部 CONFIRMED 则 Project=READY_TO_BUILD")
    ResultData<FeatureDesignDto> confirmOne(@PathVariable("id") String id);

    @PostMapping(path = "{id}/build")
    @Operation(summary = "单 feature 执行编码", description = "校验 design=CONFIRMED 且 build≠BUILDING；条件 UPDATE 抢占→BUILDING；返 runId；已在 BUILDING 时 409")
    ResultData<FeatureDesignBuildResultDto> build(@PathVariable("id") String id);

    @GetMapping(path = "{id}/history")
    @Operation(summary = "功能设计历史版本", description = "按 id 返回该 feature 全部历史版本")
    ResultData<List<FeatureDesignDto>> history(@PathVariable("id") String id);
}
