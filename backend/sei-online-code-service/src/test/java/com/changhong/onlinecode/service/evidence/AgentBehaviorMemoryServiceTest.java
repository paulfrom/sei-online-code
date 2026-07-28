package com.changhong.onlinecode.service.evidence;

import com.changhong.onlinecode.dao.AgentBehaviorMemoryDao;
import com.changhong.onlinecode.dao.RunBehaviorMemoryDao;
import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.entity.AgentBehaviorMemory;
import com.changhong.onlinecode.service.agent.PmDeliveryDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBehaviorMemoryServiceTest {

    @Test
    void recordPmDecision_promotesRepeatedEvidenceBackedCandidateButNotSecurityRule() {
        AgentBehaviorMemoryDao memoryDao = mock(AgentBehaviorMemoryDao.class);
        RunBehaviorMemoryDao runMemoryDao = mock(RunBehaviorMemoryDao.class);
        AtomicReference<AgentBehaviorMemory> stored = new AtomicReference<>();
        when(memoryDao.findFirstByProjectIdAndScopeKeyAndRuleText(any(), any(), any()))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(memoryDao.save(any(AgentBehaviorMemory.class))).thenAnswer(invocation -> {
            AgentBehaviorMemory memory = invocation.getArgument(0);
            if (memory.getId() == null) {
                memory.setId("memory-1");
            }
            stored.set(memory);
            return memory;
        });
        when(runMemoryDao.findByRunId(any())).thenReturn(List.of());
        AgentBehaviorMemoryService service = new AgentBehaviorMemoryService(memoryDao, runMemoryDao);
        PmDeliveryDecision decision = decision(candidate(
                "backend.validation-command", "报告实际命令与 exitCode"));

        service.recordPmDecision("project-1", "backend-dev-agent", "review-1",
                "evidence-1", "run-1", decision);
        assertEquals(AgentBehaviorMemoryStatus.CANDIDATE, stored.get().getStatus());

        service.recordPmDecision("project-1", "backend-dev-agent", "review-2",
                "evidence-2", "run-2", decision);
        assertEquals(AgentBehaviorMemoryStatus.ACTIVE, stored.get().getStatus());
        assertEquals(2, stored.get().getOccurrenceCount());

        stored.set(null);
        PmDeliveryDecision securityDecision = decision(candidate(
                "security.auth-token", "检查鉴权令牌边界"));
        service.recordPmDecision("project-1", "backend-dev-agent", "review-3",
                "evidence-3", "run-3", securityDecision);
        service.recordPmDecision("project-1", "backend-dev-agent", "review-4",
                "evidence-4", "run-4", securityDecision);
        assertEquals(AgentBehaviorMemoryStatus.CANDIDATE, stored.get().getStatus());
    }

    private PmDeliveryDecision decision(PmDeliveryDecision.BehaviorMemoryCandidate candidate) {
        return new PmDeliveryDecision(
                TaskDeliveryReviewDecision.RETRY,
                "fix",
                DeliveryFailureCategory.VALIDATION_FAILED,
                List.of("finding"),
                "retry",
                List.of(),
                null,
                List.of(candidate));
    }

    private PmDeliveryDecision.BehaviorMemoryCandidate candidate(String scope, String rule) {
        return new PmDeliveryDecision.BehaviorMemoryCandidate(
                scope, "backend", rule, "prevents recurrence", List.of("evidence-1"));
    }
}
