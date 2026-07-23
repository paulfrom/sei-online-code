package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.service.RequirementAutomationService;

import java.util.List;

/**
 * pm-agent 对单次任务交付的审阅决策契约（方案 §4.3）。
 *
 * <p>由 {@code PmAgentClient.reviewDelivery} 解析 agent 返回的 JSON 得到。
 * 非法决策组合（如 {@code FAILED + APPROVE}）由服务端在解析后拒绝并转为 {@code WAIT_HUMAN}。</p>
 *
 * @param decision         APPROVE / RETRY / REPLAN / WAIT_HUMAN
 * @param summary          审阅结论
 * @param failureCategory  失败分类，NONE 表示任务成功
 * @param findings         事实与证据
 * @param retryReason      仅 RETRY 时必填的原因
 * @param remediationTasks 仅 REPLAN 时必填的补救任务（与计划级 remediationTasks 同契约）
 */
public record PmDeliveryDecision(TaskDeliveryReviewDecision decision,
                                 String summary,
                                 DeliveryFailureCategory failureCategory,
                                 List<String> findings,
                                 String retryReason,
                                 List<RequirementAutomationService.PlanTask> remediationTasks) {

    public PmDeliveryDecision {
        findings = findings == null ? List.of() : List.copyOf(findings);
        remediationTasks = remediationTasks == null ? List.of() : List.copyOf(remediationTasks);
    }

    public static PmDeliveryDecision waitingHuman(String summary) {
        return new PmDeliveryDecision(TaskDeliveryReviewDecision.WAIT_HUMAN, summary,
                DeliveryFailureCategory.NONE, List.of(), null, List.of());
    }
}
