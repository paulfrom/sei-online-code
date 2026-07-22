package com.changhong.onlinecode.service.revision.contract;

/** Indicates that pm-agent returned a syntactically valid but unsafe plan patch. */
public class PlanPatchValidationException extends IllegalArgumentException {

    public PlanPatchValidationException(String message) {
        super(message);
    }
}
