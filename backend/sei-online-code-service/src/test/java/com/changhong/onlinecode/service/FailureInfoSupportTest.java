package com.changhong.onlinecode.service;

import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FailureInfoSupport 对新流程实体的重试与失败信息支持测试。
 */
class FailureInfoSupportTest {

    private final FailureInfoSupport support = new FailureInfoSupport();

    @Test
    void canRetry_allowsWhenRetryCountBelowMaxAndWindowPassed() {
        Requirement requirement = new Requirement();
        requirement.setRetryCount(1);
        requirement.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));

        assertTrue(support.canRetry(requirement, new Date()));
    }

    @Test
    void canRetry_blocksWhenRetryCountExceedsMax() {
        Requirement requirement = new Requirement();
        requirement.setRetryCount(3);
        requirement.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));

        assertFalse(support.canRetry(requirement, new Date()));
    }

    @Test
    void canRetry_blocksUntilNextRetryAt() {
        Requirement requirement = new Requirement();
        requirement.setRetryCount(1);
        requirement.setNextRetryAt(new Date(System.currentTimeMillis() + 60_000L));

        assertFalse(support.canRetry(requirement, new Date()));
    }

    @Test
    void clearCodingTaskFailure_resetsAllFailureFields() {
        CodingTask task = new CodingTask();
        task.setFailureSummary("fail");
        task.setFailureDetail("detail");
        task.setLastFailedAt(new Date());
        task.setLastRetryAt(new Date());
        task.setRetryCount(2);
        task.setNextRetryAt(new Date());

        support.clearCodingTaskFailure(task);

        assertNull(task.getFailureSummary());
        assertNull(task.getFailureDetail());
        assertNull(task.getLastFailedAt());
        assertNull(task.getLastRetryAt());
        assertEquals(0, task.getRetryCount());
        assertNull(task.getNextRetryAt());
    }
}
