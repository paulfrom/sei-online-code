package com.changhong.onlinecode.service.revision.apply;

/** Raised when a plan patch is stale or cannot be applied to the persisted aggregate. */
public class PlanPatchApplyException extends IllegalStateException {

    public PlanPatchApplyException(String message) {
        super(message);
    }
}
