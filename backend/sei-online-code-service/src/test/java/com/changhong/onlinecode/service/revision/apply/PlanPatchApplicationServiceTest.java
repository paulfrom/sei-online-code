package com.changhong.onlinecode.service.revision.apply;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.dto.revision.PlanPatchOperation;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanPatchApplicationServiceTest {

    private RequirementDao requirementDao;
    private ExecutionPlanDao executionPlanDao;
    private CodingTaskDao codingTaskDao;
    private EffectiveTaskGraphResolver graphResolver;
    private PlanPatchApplicationService service;
    private Requirement requirement;
    private ExecutionPlan basePlan;
    private CodingTask keptTask;
    private CodingTask runningTask;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        graphResolver = mock(EffectiveTaskGraphResolver.class);
        service = new PlanPatchApplicationService(requirementDao, executionPlanDao, codingTaskDao,
                graphResolver, new ObjectMapper());

        requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("project-1");
        requirement.setTitle("title");
        requirement.setActiveLoopId("loop-1");
        requirement.setRevisionSeq(2L);
        requirement.setAppliedRevisionSeq(1L);
        requirement.setRevisionState(RequirementRevisionState.APPLYING);
        requirement.setRevisionTriggerCommentId("comment-2");

        basePlan = new ExecutionPlan();
        basePlan.setId("plan-1");
        basePlan.setRequirementId("req-1");
        basePlan.setLoopId("loop-1");
        basePlan.setVersion(4);
        basePlan.setRevisionSeq(1L);

        keptTask = task("task-keep", "BE-1", CodingTaskStatus.SUCCEEDED, "backend");
        runningTask = task("task-running", "FE-1", CodingTaskStatus.RUNNING, "frontend");
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(basePlan);
        when(graphResolver.resolve(basePlan)).thenReturn(new EffectiveTaskGraph(1L,
                List.of(keptTask, runningTask),
                java.util.Map.of("BE-1", keptTask, "FE-1", runningTask)));
        when(requirementDao.applyRevisionIfCurrent(any(), any(), any(), any(), any())).thenReturn(1);

        AtomicInteger ids = new AtomicInteger();
        when(executionPlanDao.save(any(ExecutionPlan.class))).thenAnswer(invocation -> {
            ExecutionPlan plan = invocation.getArgument(0);
            if (plan.getId() == null) plan.setId("plan-new");
            return plan;
        });
        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(invocation -> {
            CodingTask task = invocation.getArgument(0);
            if (task.getId() == null) task.setId("new-task-" + ids.incrementAndGet());
            return task;
        });
    }

    @Test
    void apply_keepsStableTaskAndDefersRunningAmendSettlement() {
        PlanPatchApplyResult result = service.apply("req-1", "loop-1", 2L,
                patch(List.of(keep(), amend())));

        assertEquals(5, result.planVersion());
        assertEquals(List.of("task-keep"), result.keptTaskIds());
        assertEquals(List.of("new-task-1"), result.createdTaskIds());
        assertEquals(List.of("task-running"), result.taskIdsToSettle());
        assertTrue(result.supersededTaskIds().isEmpty());
        assertEquals(CodingTaskStatus.RUNNING, runningTask.getStatus());
        verify(requirementDao).applyRevisionIfCurrent("req-1", "loop-1", 2L,
                RequirementRevisionState.APPLYING, RequirementRevisionState.NONE);
    }

    @Test
    void apply_nonRunningAmendMarksSourceSupersededWithoutDeletingHistory() {
        runningTask.setStatus(CodingTaskStatus.FAILED);

        PlanPatchApplyResult result = service.apply("req-1", "loop-1", 2L,
                patch(List.of(keep(), amend())));

        assertEquals(CodingTaskStatus.SUPERSEDED, runningTask.getStatus());
        assertEquals(List.of("task-running"), result.supersededTaskIds());
        assertTrue(result.taskIdsToSettle().isEmpty());
        verify(codingTaskDao).save(runningTask);
    }

    @Test
    void apply_staleLoopRejectsBeforeAnyWrite() {
        assertThrows(PlanPatchApplyException.class,
                () -> service.apply("req-1", "old-loop", 2L, patch(List.of(keep(), amend()))));

        verify(requirementDao, never()).applyRevisionIfCurrent(any(), any(), any(), any(), any());
        verify(executionPlanDao, never()).save(any(ExecutionPlan.class));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
    }

    @Test
    void apply_casLossRejectsBeforePlanOrTaskWrite() {
        when(requirementDao.applyRevisionIfCurrent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(PlanPatchApplyException.class,
                () -> service.apply("req-1", "loop-1", 2L, patch(List.of(keep(), amend()))));

        verify(executionPlanDao, never()).save(any(ExecutionPlan.class));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
    }

    @Test
    void apply_isOneTransactionalUnitOfWork() throws Exception {
        Transactional annotation = PlanPatchApplicationService.class
                .getMethod("apply", String.class, String.class, long.class, PlanPatch.class)
                .getAnnotation(Transactional.class);
        assertTrue(annotation != null);
    }

    private CodingTask task(String id, String key, CodingTaskStatus status, String area) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setRequirementId("req-1");
        task.setProjectId("project-1");
        task.setLoopId("loop-1");
        task.setPlanTaskKey(key);
        task.setTitle(key);
        task.setDescription("description");
        task.setArea(area);
        task.setAssignedAgent(area + "-dev-agent");
        task.setFileScope(List.of(area + "/"));
        task.setDependsOn(List.of());
        task.setStatus(status);
        return task;
    }

    private PlanPatch patch(List<PlanPatchOperation> operations) {
        PlanPatch patch = new PlanPatch();
        patch.setRequirementId("req-1");
        patch.setLoopId("loop-1");
        patch.setRevisionSeq(2L);
        patch.setBasePlanId("plan-1");
        patch.setBasePlanVersion(4);
        patch.setSummary("adjust frontend only");
        patch.setOperations(operations);
        return patch;
    }

    private PlanPatchOperation keep() {
        return operation("BE-1", PlanPatchAction.KEEP, "task-keep", null, null, null,
                List.of(), List.of(), null);
    }

    private PlanPatchOperation amend() {
        return operation("FE-2", PlanPatchAction.AMEND, "task-running", "frontend v2",
                "adjust frontend", "frontend", List.of("frontend/src/"), List.of("BE-1"),
                "frontend-dev-agent");
    }

    private PlanPatchOperation operation(String key, PlanPatchAction action, String sourceId,
                                         String title, String description, String area,
                                         List<String> scope, List<String> dependsOn, String agent) {
        PlanPatchOperation operation = new PlanPatchOperation();
        operation.setTaskKey(key);
        operation.setAction(action);
        operation.setSourceTaskId(sourceId);
        operation.setTitle(title);
        operation.setDescription(description);
        operation.setArea(area);
        operation.setFileScope(scope);
        operation.setDependsOn(dependsOn);
        operation.setAssignedAgent(agent);
        operation.setReason("user feedback");
        return operation;
    }
}
