package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 增量计划修订领域契约的默认值和动作集合测试。
 */
class IncrementalPlanRevisionDomainTest {

    @Test
    void newRequirementStartsWithoutPendingRevision() {
        Requirement requirement = new Requirement();

        assertEquals(0L, requirement.getRevisionSeq());
        assertEquals(0L, requirement.getAppliedRevisionSeq());
        assertEquals(RequirementRevisionState.NONE, requirement.getRevisionState());
    }

    @Test
    void newPlanAndTaskRemainCompatibleWithHistoricalRevisionZero() {
        ExecutionPlan plan = new ExecutionPlan();
        CodingTask task = new CodingTask();

        assertEquals(0L, plan.getRevisionSeq());
        assertEquals(0L, task.getRevisionSeq());
    }

    @Test
    void patchContractContainsEverySupportedTaskDisposition() {
        assertEquals(EnumSet.of(
                        PlanPatchAction.KEEP,
                        PlanPatchAction.AMEND,
                        PlanPatchAction.ADD,
                        PlanPatchAction.SUPERSEDE,
                        PlanPatchAction.REVALIDATE),
                EnumSet.allOf(PlanPatchAction.class));
        assertTrue(EnumSet.allOf(CodingTaskStatus.class).contains(CodingTaskStatus.SUPERSEDED));
    }
}
