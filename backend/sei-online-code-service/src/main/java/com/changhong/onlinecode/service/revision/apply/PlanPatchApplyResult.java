package com.changhong.onlinecode.service.revision.apply;

import java.util.List;

/** Immutable hand-off from atomic patch application to run settlement and scheduling. */
public record PlanPatchApplyResult(
        String executionPlanId,
        int planVersion,
        long revisionSeq,
        List<String> keptTaskIds,
        List<String> createdTaskIds,
        List<String> taskIdsToSettle,
        List<String> supersededTaskIds) {

    public PlanPatchApplyResult {
        keptTaskIds = List.copyOf(keptTaskIds);
        createdTaskIds = List.copyOf(createdTaskIds);
        taskIdsToSettle = List.copyOf(taskIdsToSettle);
        supersededTaskIds = List.copyOf(supersededTaskIds);
    }
}
