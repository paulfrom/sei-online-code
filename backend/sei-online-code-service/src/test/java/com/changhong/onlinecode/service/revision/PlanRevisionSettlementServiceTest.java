package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.CodingTaskExecutionService;
import com.changhong.onlinecode.service.CodingTaskScheduler;
import com.changhong.onlinecode.service.revision.apply.PlanPatchApplyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PlanRevisionSettlementServiceTest {

    private RequirementDao requirementDao;
    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private TaskHandoffSnapshotService snapshotService;
    private CodingTaskExecutionService executionService;
    private CodingTaskScheduler scheduler;
    private PlanRevisionSettlementService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        snapshotService = mock(TaskHandoffSnapshotService.class);
        executionService = mock(CodingTaskExecutionService.class);
        scheduler = mock(CodingTaskScheduler.class);
        service = new PlanRevisionSettlementService(requirementDao, codingTaskDao, runDao,
                snapshotService, executionService, scheduler);
    }

    @Test
    void settlesOnlyPatchSelectedRunningTaskAndKeepsLoop() {
        Requirement requirement = requirement();
        CodingTask affected = task("affected", CodingTaskStatus.RUNNING);
        Run running = run("run-1", RunState.RUNNING);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(codingTaskDao.findOne("affected")).thenReturn(affected);
        when(runDao.findByCodingTaskId("affected")).thenReturn(List.of(running));

        service.settle(event(List.of("keep"), List.of("affected")));

        verify(snapshotService).refresh("req-1", "affected", 2L, "comment-2");
        verify(executionService).cancelRun("run-1", "comment-2");
        assertEquals(CodingTaskStatus.SUPERSEDED, affected.getStatus());
        verify(codingTaskDao).save(affected);
        verify(codingTaskDao, never()).findOne("keep");
        verify(scheduler).schedule("req-1");
        assertEquals("loop-1", requirement.getActiveLoopId());
    }

    @Test
    void duplicateAppliedEventDoesNotCancelOrSnapshotAgain() {
        Requirement requirement = requirement();
        CodingTask alreadySettled = task("affected", CodingTaskStatus.SUPERSEDED);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(codingTaskDao.findOne("affected")).thenReturn(alreadySettled);
        when(runDao.findByCodingTaskId("affected")).thenReturn(List.of());

        service.settle(event(List.of(), List.of("affected")));

        verifyNoInteractions(snapshotService, executionService);
        verify(runDao).findByCodingTaskId("affected");
        verify(codingTaskDao, never()).save(any(CodingTask.class));
        verify(scheduler).schedule("req-1");
    }

    @Test
    void ignoresEventFromOldLoop() {
        Requirement requirement = requirement();
        requirement.setActiveLoopId("loop-2");
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        service.settle(event(List.of(), List.of("affected")));

        verifyNoInteractions(codingTaskDao, runDao, snapshotService, executionService, scheduler);
    }

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setRevisionTriggerCommentId("comment-2");
        return requirement;
    }

    private CodingTask task(String id, CodingTaskStatus status) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setRequirementId("req-1");
        task.setLoopId("loop-1");
        task.setStatus(status);
        return task;
    }

    private Run run(String id, RunState state) {
        Run run = new Run();
        run.setId(id);
        run.setState(state);
        return run;
    }

    private PlanRevisionAppliedEvent event(List<String> kept, List<String> settle) {
        return new PlanRevisionAppliedEvent("req-1", "loop-1",
                new PlanPatchApplyResult("plan-2", 2, 2L, kept, List.of("new"), settle, List.of()));
    }
}
