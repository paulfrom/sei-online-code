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
 * @param remediationBrief 下一次 Agent 直接消费的权威修复说明
 * @param behaviorMemoryCandidates 可跨 Run 复用、但仍需晋升审核的行为规则候选
 */
public record PmDeliveryDecision(TaskDeliveryReviewDecision decision,
                                 String summary,
                                 DeliveryFailureCategory failureCategory,
                                 List<String> findings,
                                 String retryReason,
                                 List<RequirementAutomationService.PlanTask> remediationTasks,
                                 RemediationBrief remediationBrief,
                                 List<BehaviorMemoryCandidate> behaviorMemoryCandidates) {

    public PmDeliveryDecision {
        findings = findings == null ? List.of() : List.copyOf(findings);
        remediationTasks = remediationTasks == null ? List.of() : List.copyOf(remediationTasks);
        behaviorMemoryCandidates = behaviorMemoryCandidates == null
                ? List.of() : List.copyOf(behaviorMemoryCandidates);
    }

    public PmDeliveryDecision(TaskDeliveryReviewDecision decision,
                              String summary,
                              DeliveryFailureCategory failureCategory,
                              List<String> findings,
                              String retryReason,
                              List<RequirementAutomationService.PlanTask> remediationTasks) {
        this(decision, summary, failureCategory, findings, retryReason,
                remediationTasks, null, List.of());
    }

    public static PmDeliveryDecision waitingHuman(String summary) {
        return new PmDeliveryDecision(TaskDeliveryReviewDecision.WAIT_HUMAN, summary,
                DeliveryFailureCategory.NONE, List.of(), null, List.of(), null, List.of());
    }

    public record RemediationBrief(String goal,
                                   List<String> rootCauses,
                                   List<String> requiredChanges,
                                   List<String> verificationSteps,
                                   List<String> evidenceRefs) {
        public RemediationBrief {
            rootCauses = rootCauses == null ? List.of() : List.copyOf(rootCauses);
            requiredChanges = requiredChanges == null ? List.of() : List.copyOf(requiredChanges);
            verificationSteps = verificationSteps == null ? List.of() : List.copyOf(verificationSteps);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    public record BehaviorMemoryCandidate(String scopeKey,
                                          String area,
                                          String rule,
                                          String rationale,
                                          List<String> evidenceRefs) {
        public BehaviorMemoryCandidate {
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }
}
