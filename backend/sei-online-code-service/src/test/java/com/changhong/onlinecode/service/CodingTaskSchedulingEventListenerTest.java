package com.changhong.onlinecode.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CodingTaskSchedulingEventListenerTest {

    private CodingTaskScheduler scheduler;
    private CodingTaskSchedulingEventListener listener;

    @BeforeEach
    void setUp() {
        scheduler = mock(CodingTaskScheduler.class);
        listener = new CodingTaskSchedulingEventListener(scheduler);
    }

    @Test
    void scheduleRequested_delegatesToScheduler() {
        listener.onScheduleRequested(new CodingTaskSchedulingEvents.ScheduleRequested("req-1"));

        verify(scheduler).schedule("req-1");
    }

    @Test
    void developmentFinished_delegatesToScheduler() {
        listener.onDevelopmentFinished(
                new CodingTaskSchedulingEvents.DevelopmentFinished("task-1", false, "compile error"));

        verify(scheduler).onDevelopmentRunFinished("task-1", false, "compile error");
    }
}
