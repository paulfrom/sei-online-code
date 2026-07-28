package com.changhong.onlinecode.service.evidence;

import com.changhong.onlinecode.dao.AgentBehaviorMemoryDao;
import com.changhong.onlinecode.dao.RunBehaviorMemoryDao;
import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
import com.changhong.onlinecode.dto.enums.BehaviorMemoryOutcome;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.evidence.AgentBehaviorMemoryDto;
import com.changhong.onlinecode.entity.AgentBehaviorMemory;
import com.changhong.onlinecode.entity.RunBehaviorMemory;
import com.changhong.onlinecode.service.agent.PmDeliveryDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 证据支持、作用域受限且可评估效果的 Agent 长期行为记忆。
 */
@Service
@RequiredArgsConstructor
public class AgentBehaviorMemoryService {

    private final AgentBehaviorMemoryDao memoryDao;
    private final RunBehaviorMemoryDao runMemoryDao;

    @Transactional
    public void recordPmDecision(String projectId, String agentName, String reviewId,
                                 String evidenceId, String deliveryRunId,
                                 PmDeliveryDecision decision) {
        evaluateAppliedMemories(deliveryRunId, evidenceId,
                decision != null && decision.decision() == TaskDeliveryReviewDecision.APPROVE);
        if (decision == null || decision.behaviorMemoryCandidates().isEmpty()
                || projectId == null || projectId.isBlank()) {
            return;
        }
        for (PmDeliveryDecision.BehaviorMemoryCandidate candidate
                : decision.behaviorMemoryCandidates()) {
            AgentBehaviorMemory memory = memoryDao
                    .findFirstByProjectIdAndScopeKeyAndRuleText(
                            projectId, candidate.scopeKey(), candidate.rule())
                    .orElseGet(AgentBehaviorMemory::new);
            boolean existing = memory.getId() != null;
            memory.setProjectId(projectId);
            memory.setAgentName(agentName);
            memory.setArea(candidate.area());
            memory.setScopeKey(candidate.scopeKey());
            memory.setRuleText(candidate.rule());
            memory.setRationale(candidate.rationale());
            memory.setSourceReviewId(reviewId);
            memory.setSourceEvidenceId(evidenceId);
            memory.setOccurrenceCount(existing
                    ? Objects.requireNonNullElse(memory.getOccurrenceCount(), 0) + 1 : 1);
            if (!existing) {
                memory.setStatus(AgentBehaviorMemoryStatus.CANDIDATE);
            }
            if (memory.getStatus() == AgentBehaviorMemoryStatus.CANDIDATE
                    && memory.getOccurrenceCount() >= 2
                    && canAutoPromote(memory)) {
                memory.setStatus(AgentBehaviorMemoryStatus.ACTIVE);
            }
            memoryDao.save(memory);
        }
    }

    @Transactional(readOnly = true)
    public List<AgentBehaviorMemory> findActive(String projectId, String agentName, String area) {
        List<AgentBehaviorMemory> candidates = agentName == null || agentName.isBlank()
                ? memoryDao.findByProjectIdAndStatusOrderByLastEditedDateDesc(
                        projectId, AgentBehaviorMemoryStatus.ACTIVE)
                : memoryDao.findByProjectIdAndAgentNameAndStatusOrderByLastEditedDateDesc(
                        projectId, agentName, AgentBehaviorMemoryStatus.ACTIVE);
        Date now = new Date();
        return candidates.stream()
                .filter(memory -> memory.getExpiresAt() == null || memory.getExpiresAt().after(now))
                .filter(memory -> area == null || area.isBlank()
                        || memory.getArea() == null || memory.getArea().isBlank()
                        || area.equalsIgnoreCase(memory.getArea())
                        || "full-stack".equalsIgnoreCase(memory.getArea()))
                .limit(20)
                .toList();
    }

    @Transactional
    public String renderAndLink(String runId, String projectId, String agentName, String area) {
        if (projectId == null || projectId.isBlank()) {
            return "";
        }
        List<AgentBehaviorMemory> memories = findActive(projectId, agentName, area);
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 已验证的长期行为改进\n");
        prompt.append("以下规则来自历史 PM 证据，只在当前作用域适用；如与当前事实冲突，"
                + "必须报告冲突，不得静默覆盖当前验收标准。\n");
        for (AgentBehaviorMemory memory : memories) {
            prompt.append("- [").append(memory.getScopeKey()).append("] ")
                    .append(memory.getRuleText()).append(" (memoryId=")
                    .append(memory.getId()).append(")\n");
            if (runId != null && !runId.isBlank()) {
                RunBehaviorMemory link = new RunBehaviorMemory();
                link.setRunId(runId);
                link.setBehaviorMemoryId(memory.getId());
                link.setOutcome(BehaviorMemoryOutcome.UNKNOWN);
                runMemoryDao.save(link);
                memory.setLastAppliedAt(new Date());
                memoryDao.save(memory);
            }
        }
        return prompt.toString();
    }

    @Transactional(readOnly = true)
    public List<AgentBehaviorMemoryDto> findByProject(String projectId,
                                                      AgentBehaviorMemoryStatus status) {
        return memoryDao.findByProjectIdAndStatusOrderByLastEditedDateDesc(projectId, status)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public AgentBehaviorMemoryDto updateStatus(String memoryId, AgentBehaviorMemoryStatus status) {
        AgentBehaviorMemory memory = memoryDao.findOne(memoryId);
        if (memory == null) {
            return null;
        }
        memory.setStatus(status);
        return toDto(memoryDao.save(memory));
    }

    @Transactional(readOnly = true)
    public List<AgentBehaviorMemoryDto> findApplied(String runId) {
        List<AgentBehaviorMemoryDto> values = new ArrayList<>();
        for (RunBehaviorMemory link : runMemoryDao.findByRunId(runId)) {
            AgentBehaviorMemory memory = memoryDao.findOne(link.getBehaviorMemoryId());
            if (memory != null) {
                AgentBehaviorMemoryDto dto = toDto(memory);
                dto.setLastOutcome(link.getOutcome());
                values.add(dto);
            }
        }
        return values;
    }

    private void evaluateAppliedMemories(String runId, String evidenceId, boolean approved) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        for (RunBehaviorMemory link : runMemoryDao.findByRunId(runId)) {
            if (link.getOutcome() != BehaviorMemoryOutcome.UNKNOWN) {
                continue;
            }
            AgentBehaviorMemory memory = memoryDao.findOne(link.getBehaviorMemoryId());
            if (memory == null) {
                continue;
            }
            BehaviorMemoryOutcome outcome = approved
                    ? BehaviorMemoryOutcome.HELPFUL : BehaviorMemoryOutcome.INEFFECTIVE;
            link.setOutcome(outcome);
            link.setEvaluationEvidenceId(evidenceId);
            runMemoryDao.save(link);
            memory.setLastOutcome(outcome);
            if (approved) {
                memory.setHelpfulCount(Objects.requireNonNullElse(memory.getHelpfulCount(), 0) + 1);
            } else {
                memory.setIneffectiveCount(Objects.requireNonNullElse(memory.getIneffectiveCount(), 0) + 1);
                if (memory.getIneffectiveCount() >= 2) {
                    memory.setStatus(AgentBehaviorMemoryStatus.DISABLED);
                }
            }
            memoryDao.save(memory);
        }
    }

    private boolean canAutoPromote(AgentBehaviorMemory memory) {
        String scope = Objects.toString(memory.getScopeKey(), "").toLowerCase(Locale.ROOT);
        return !scope.contains("security")
                && !scope.contains("auth")
                && !scope.contains("secret")
                && !scope.contains("permission");
    }

    private AgentBehaviorMemoryDto toDto(AgentBehaviorMemory memory) {
        AgentBehaviorMemoryDto dto = new AgentBehaviorMemoryDto();
        dto.setId(memory.getId());
        dto.setProjectId(memory.getProjectId());
        dto.setAgentName(memory.getAgentName());
        dto.setArea(memory.getArea());
        dto.setScopeKey(memory.getScopeKey());
        dto.setRuleText(memory.getRuleText());
        dto.setRationale(memory.getRationale());
        dto.setSourceReviewId(memory.getSourceReviewId());
        dto.setSourceEvidenceId(memory.getSourceEvidenceId());
        dto.setStatus(memory.getStatus());
        dto.setOccurrenceCount(memory.getOccurrenceCount());
        dto.setHelpfulCount(memory.getHelpfulCount());
        dto.setIneffectiveCount(memory.getIneffectiveCount());
        dto.setLastOutcome(memory.getLastOutcome());
        dto.setLastAppliedAt(memory.getLastAppliedAt());
        dto.setExpiresAt(memory.getExpiresAt());
        return dto;
    }
}
