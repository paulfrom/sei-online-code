package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.service.revision.apply.PlanPatchApplyResult;

/** Hands an atomically applied plan revision to run settlement and scheduling. */
public record PlanRevisionAppliedEvent(String requirementId, String loopId,
                                       PlanPatchApplyResult applyResult) {
    public long revisionSeq() {
        return applyResult.revisionSeq();
    }
}
