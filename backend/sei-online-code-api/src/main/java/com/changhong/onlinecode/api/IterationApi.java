package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.TaskDto;
import com.changhong.onlinecode.dto.request.AcceptIterationRequest;
import com.changhong.onlinecode.dto.request.CancelIterationRequest;
import com.changhong.onlinecode.dto.request.DeployIterationRequest;
import com.changhong.onlinecode.dto.request.DispatchIterationRequest;
import com.changhong.onlinecode.dto.request.MergeIterationRequest;
import com.changhong.onlinecode.dto.request.RetryIterationRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 迭代管理 API。契约 §3 端点 7/8 + Phase 2 §2 端点 10/15 + Phase 4 §2 端点 25/27/28/29。
 *
 * <ul>
 *   <li>#7  POST /iteration/deploy   —— Deploy Agent：vite build → previewUrl</li>
 *   <li>#8  GET  /iteration/findOne  —— BaseEntityApi.findOne，轮询迭代状态/previewUrl</li>
 *   <li>#10 POST /iteration/dispatch —— Dispatch Agent：确认后的 Spec → 非重叠 Task[]</li>
 *   <li>#15 POST /iteration/merge    —— 各 Task worktree 合并回主干</li>
 *   <li>#25 POST /iteration/accept   —— 验收：PREVIEW → ACCEPTED（置 finishedDate）</li>
 *   <li>#27 POST /iteration/findByPage —— 时间线：按 projectId 过滤、round 排序</li>
 *   <li>#28 POST /iteration/cancel   —— 中止：非终态 → CANCELLED（级联 RUNNING task/run）</li>
 *   <li>#29 POST /iteration/retry    —— 重试：FAILED → DISPATCHING，重新派发同版 Spec</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = IterationApi.PATH)
public interface IterationApi extends BaseEntityApi<IterationDto>, FindByPageApi<IterationDto> {

    String PATH = "iteration";

    @PostMapping(path = "deploy", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "部署迭代", description = "Deploy Agent：构建前端产物并生成 previewUrl，状态流转至 PREVIEW")
    ResultData<IterationDto> deploy(@RequestBody @Valid DeployIterationRequest request);

    @PostMapping(path = "dispatch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "派发迭代", description = "Dispatch Agent：将确认后的 Spec 按 fileScope 切分为非重叠 Task[]，状态 DISPATCHING→DEVELOPING")
    ResultData<List<TaskDto>> dispatch(@RequestBody @Valid DispatchIterationRequest request);

    @PostMapping(path = "merge", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "合并迭代", description = "将各 Task 的 worktree 合并回主干，状态 MERGING→DEPLOYING")
    ResultData<IterationDto> merge(@RequestBody @Valid MergeIterationRequest request);

    @PostMapping(path = "accept", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "验收迭代", description = "用户签收：PREVIEW → ACCEPTED（终态），置 finishedDate")
    ResultData<IterationDto> accept(@RequestBody @Valid AcceptIterationRequest request);

    @PostMapping(path = "cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "取消迭代", description = "非终态 → CANCELLED（终态），同一事务内级联 RUNNING task/run → CANCELLED")
    ResultData<IterationDto> cancel(@RequestBody @Valid CancelIterationRequest request);

    @PostMapping(path = "retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重试迭代", description = "FAILED → DISPATCHING：以同版 Spec 重新派发（新 task/run）")
    ResultData<IterationDto> retry(@RequestBody @Valid RetryIterationRequest request);
}
