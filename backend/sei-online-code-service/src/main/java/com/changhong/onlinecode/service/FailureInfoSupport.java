package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Spec;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 失败信息与重试窗口支持。
 */
@Service
public class FailureInfoSupport {

    private static final int MAX_RETRY = 3;
    private static final long FIRST_DELAY_MS = 60_000L;
    private static final long LATER_DELAY_MS = 600_000L;

    public int maxRetry() {
        return MAX_RETRY;
    }

    public boolean canRetry(Plan plan, Date now) {
        return canRetry(plan.getRetryCount(), plan.getNextRetryAt(), now);
    }

    public boolean canRetry(Spec spec, Date now) {
        return canRetry(spec.getRetryCount(), spec.getNextRetryAt(), now);
    }

    public boolean canRetry(FeatureDesign design, Date now) {
        return canRetry(design.getRetryCount(), design.getNextRetryAt(), now);
    }

    private boolean canRetry(Integer retryCount, Date nextRetryAt, Date now) {
        int count = retryCount == null ? 0 : retryCount;
        if (count >= MAX_RETRY) {
            return false;
        }
        return nextRetryAt == null || !nextRetryAt.after(now);
    }

    public void markRetrying(Plan plan, TriggerSource source, Date now) {
        plan.setRetryCount(defaultRetryCount(plan.getRetryCount()) + 1);
        plan.setLastRetryAt(now);
        plan.setLastTriggerSource(source);
    }

    public void markRetrying(Spec spec, TriggerSource source, Date now) {
        spec.setRetryCount(defaultRetryCount(spec.getRetryCount()) + 1);
        spec.setLastRetryAt(now);
        spec.setLastTriggerSource(source);
    }

    public void markRetrying(FeatureDesign design, TriggerSource source, Date now) {
        design.setRetryCount(defaultRetryCount(design.getRetryCount()) + 1);
        design.setLastRetryAt(now);
        design.setLastTriggerSource(source);
    }

    public void markPlanFailure(Plan plan, FailureCode code, FailureStage stage,
                                String summary, String detail, TriggerSource source, Date now) {
        plan.setFailureCode(code);
        plan.setFailureStage(stage);
        plan.setFailureSummary(summary);
        plan.setFailureDetail(detail);
        plan.setLastFailedAt(now);
        plan.setNextRetryAt(nextRetryAt(plan.getRetryCount(), now));
        plan.setLastTriggerSource(source);
    }

    public void markSpecFailure(Spec spec, FailureCode code, FailureStage stage,
                                String summary, String detail, TriggerSource source, Date now) {
        spec.setFailureCode(code);
        spec.setFailureStage(stage);
        spec.setFailureSummary(summary);
        spec.setFailureDetail(detail);
        spec.setLastFailedAt(now);
        spec.setNextRetryAt(nextRetryAt(spec.getRetryCount(), now));
        spec.setLastTriggerSource(source);
    }

    public void markFeatureDesignFailure(FeatureDesign design, FailureCode code, FailureStage stage,
                                         String summary, String detail, TriggerSource source, Date now) {
        design.setFailureCode(code);
        design.setFailureStage(stage);
        design.setFailureSummary(summary);
        design.setFailureDetail(detail);
        design.setLastFailedAt(now);
        design.setNextRetryAt(nextRetryAt(design.getRetryCount(), now));
        design.setLastTriggerSource(source);
    }

    public void clearPlanFailure(Plan plan) {
        plan.setFailureCode(null);
        plan.setFailureStage(null);
        plan.setFailureSummary(null);
        plan.setFailureDetail(null);
        plan.setLastFailedAt(null);
        plan.setLastRetryAt(null);
        plan.setRetryCount(0);
        plan.setNextRetryAt(null);
    }

    public void clearSpecFailure(Spec spec) {
        spec.setFailureCode(null);
        spec.setFailureStage(null);
        spec.setFailureSummary(null);
        spec.setFailureDetail(null);
        spec.setLastFailedAt(null);
        spec.setLastRetryAt(null);
        spec.setRetryCount(0);
        spec.setNextRetryAt(null);
    }

    public void clearFeatureDesignFailure(FeatureDesign design) {
        design.setFailureCode(null);
        design.setFailureStage(null);
        design.setFailureSummary(null);
        design.setFailureDetail(null);
        design.setLastFailedAt(null);
        design.setLastRetryAt(null);
        design.setRetryCount(0);
        design.setNextRetryAt(null);
    }

    private Date nextRetryAt(Integer retryCount, Date now) {
        int count = defaultRetryCount(retryCount);
        long delay = count <= 0 ? FIRST_DELAY_MS : LATER_DELAY_MS;
        return new Date(now.getTime() + delay);
    }

    private int defaultRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }
}
