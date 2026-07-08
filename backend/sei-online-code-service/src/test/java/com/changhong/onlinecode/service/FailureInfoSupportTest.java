package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void markRetrying_incrementsCountAndSetsLastRetryAt() {
        OverviewDesign overview = new OverviewDesign();
        overview.setRetryCount(0);
        Date now = new Date();

        support.markRetrying(overview, TriggerSource.SCHEDULED_COMPENSATION, now);

        assertEquals(1, overview.getRetryCount());
        assertEquals(now, overview.getLastRetryAt());
        assertEquals(TriggerSource.SCHEDULED_COMPENSATION, overview.getLastTriggerSource());
    }

    @Test
    void markDetailedDesignFailure_setsFailureInfoAndBackoff() {
        DetailedDesign design = new DetailedDesign();
        design.setRetryCount(1);
        Date now = new Date();

        support.markDetailedDesignFailure(design, "summary", "detail", TriggerSource.SCHEDULED_COMPENSATION, now);

        assertEquals("summary", design.getFailureSummary());
        assertEquals("detail", design.getFailureDetail());
        assertEquals(now, design.getLastFailedAt());
        assertEquals(TriggerSource.SCHEDULED_COMPENSATION, design.getLastTriggerSource());
        assertNotNull(design.getNextRetryAt());
        assertTrue(design.getNextRetryAt().after(now));
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
