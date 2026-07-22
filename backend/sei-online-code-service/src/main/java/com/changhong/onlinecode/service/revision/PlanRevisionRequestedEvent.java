package com.changhong.onlinecode.service.revision;

/** Requests asynchronous processing of one immutable requirement revision token. */
public record PlanRevisionRequestedEvent(String requirementId, String loopId, long revisionSeq) {
}
