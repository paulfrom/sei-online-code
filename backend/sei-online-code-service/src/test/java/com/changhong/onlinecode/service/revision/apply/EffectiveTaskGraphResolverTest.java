package com.changhong.onlinecode.service.revision.apply;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.dto.revision.PlanPatchOperation;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectiveTaskGraphResolverTest {

    private ExecutionPlanDao planDao;
    private CodingTaskDao taskDao;
    private ObjectMapper mapper;
    private EffectiveTaskGraphResolver resolver;

    @BeforeEach
    void setUp() {
        planDao = mock(ExecutionPlanDao.class);
        taskDao = mock(CodingTaskDao.class);
        mapper = new ObjectMapper();
        resolver = new EffectiveTaskGraphResolver(planDao, taskDao, mapper);
    }

    @Test
    void resolve_revisionCombinesNewRowsWithExplicitKeepSourcesInPatchOrder() throws Exception {
        ExecutionPlan plan = plan(3L);
        CodingTask kept = task("old-1", "BE-1", 1L);
        CodingTask created = task("new-1", "FE-2", 3L);
        plan.setChangeSetJson(mapper.writeValueAsString(patch(List.of(
                operation("BE-1", PlanPatchAction.KEEP, "old-1"),
                operation("FE-2", PlanPatchAction.ADD, null),
                operation("OLD", PlanPatchAction.SUPERSEDE, "old-2")))));
        when(taskDao.findByRequirementIdAndLoopIdAndRevisionSeq("req-1", "loop-1", 3L))
                .thenReturn(List.of(created));
        when(taskDao.findByRequirementIdAndLoopId("req-1", "loop-1"))
                .thenReturn(List.of(kept, created));

        EffectiveTaskGraph graph = resolver.resolve(plan);

        assertEquals(List.of("old-1", "new-1"), graph.tasks().stream().map(CodingTask::getId).toList());
        assertEquals("old-1", graph.tasksByKey().get("BE-1").getId());
        assertEquals("new-1", graph.tasksByKey().get("FE-2").getId());
    }

    @Test
    void resolve_duplicateEffectiveKeyFailsClosed() throws Exception {
        ExecutionPlan plan = plan(3L);
        CodingTask kept = task("old-1", "DUP", 1L);
        CodingTask created = task("new-1", "DUP", 3L);
        plan.setChangeSetJson(mapper.writeValueAsString(patch(List.of(
                operation("DUP", PlanPatchAction.KEEP, "old-1"),
                operation("DUP", PlanPatchAction.ADD, null)))));
        when(taskDao.findByRequirementIdAndLoopIdAndRevisionSeq("req-1", "loop-1", 3L))
                .thenReturn(List.of(created));
        when(taskDao.findByRequirementIdAndLoopId("req-1", "loop-1"))
                .thenReturn(List.of(kept, created));

        assertThrows(PlanPatchApplyException.class, () -> resolver.resolve(plan));
    }

    @Test
    void resolve_revisionZeroUsesTasksAttachedToPlan() {
        ExecutionPlan plan = plan(0L);
        CodingTask task = task("task-1", "BE-1", 0L);
        when(taskDao.findByExecutionPlanId("plan-3")).thenReturn(List.of(task));

        assertEquals(List.of(task), resolver.resolve(plan).tasks());
    }

    @Test
    void resolveByIdentityRejectsMissingRevision() {
        when(planDao.findByRequirementIdAndLoopIdAndRevisionSeq("req-1", "loop-1", 9L))
                .thenReturn(Optional.empty());

        assertThrows(PlanPatchApplyException.class, () -> resolver.resolve("req-1", "loop-1", 9L));
    }

    private ExecutionPlan plan(long revisionSeq) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-3");
        plan.setRequirementId("req-1");
        plan.setLoopId("loop-1");
        plan.setRevisionSeq(revisionSeq);
        return plan;
    }

    private CodingTask task(String id, String key, long revisionSeq) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setPlanTaskKey(key);
        task.setRevisionSeq(revisionSeq);
        return task;
    }

    private PlanPatch patch(List<PlanPatchOperation> operations) {
        PlanPatch patch = new PlanPatch();
        patch.setOperations(operations);
        return patch;
    }

    private PlanPatchOperation operation(String key, PlanPatchAction action, String sourceId) {
        PlanPatchOperation operation = new PlanPatchOperation();
        operation.setTaskKey(key);
        operation.setAction(action);
        operation.setSourceTaskId(sourceId);
        return operation;
    }
}
