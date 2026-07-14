package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Comment-driven loop compensation regression tests. */
class CompensationServiceTest {

    private RequirementDao requirementDao;
    private ExecutionPlanDao executionPlanDao;
    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private RequirementAgentService requirementAgentService;
    private RequirementAutomationService automationService;
    private RequirementDeliveryService deliveryService;
    private FailureInfoSupport failureInfoSupport;
    private CompensationLogService compensationLogService;
    private PlatformTransactionManager transactionManager;
    private CompensationService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        requirementAgentService = mock(RequirementAgentService.class);
        automationService = mock(RequirementAutomationService.class);
        deliveryService = mock(RequirementDeliveryService.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        compensationLogService = mock(CompensationLogService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        service = new CompensationService(requirementDao, executionPlanDao, codingTaskDao, runDao,
                requirementAgentService,
                automationService, deliveryService, failureInfoSupport, compensationLogService,
                transactionManager);

        when(requirementDao.save(any(Requirement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void failedPlanningWithoutPlan_retriesSameLoopAsInitialPlan() {
        Requirement requirement = requirement("req-1", RequirementAutomationStatus.FAILED);
        when(requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)).thenReturn(List.of(requirement));
        when(runDao.findByRequirementIdAndState("req-1", RunState.RUNNING)).thenReturn(List.of());
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(null);
        when(executionPlanDao.findTopByRequirementIdOrderByVersionDesc("req-1")).thenReturn(null);
        when(failureInfoSupport.canRetry(eq(requirement), any(Date.class))).thenReturn(true);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        service.compensateRequirementLoops(new Date());

        verify(failureInfoSupport).markRetrying(eq(requirement),
                eq(TriggerSource.SCHEDULED_COMPENSATION), any(Date.class));
        verify(automationService).executePreparedLoop("req-1", "loop-1", ExecutionPlanType.INITIAL,
                "补偿恢复：重新生成当前 loop 的 PM 执行计划。");
        assertEquals(RequirementAutomationStatus.PLANNING, requirement.getAutomationStatus());
    }

    @Test
    void failedPrdGeneration_keepsPreLoopCompensationBehavior() {
        Requirement requirement = new Requirement();
        requirement.setId("req-prd");
        requirement.setStatus(RequirementStatus.FAILED);
        requirement.setFailureSummary("agent process exited");
        when(requirementDao.findByStatus(RequirementStatus.FAILED)).thenReturn(List.of(requirement));
        when(requirementDao.findByStatus(RequirementStatus.PRD_GENERATING)).thenReturn(List.of());
        when(failureInfoSupport.canRetry(eq(requirement), any(Date.class))).thenReturn(true);
        when(requirementDao.updateStatusIfMatch("req-prd", RequirementStatus.FAILED,
                RequirementStatus.PRD_GENERATING)).thenReturn(1);

        service.compensatePrdGeneration(new Date());

        assertEquals(RequirementStatus.PRD_GENERATING, requirement.getStatus());
        verify(requirementAgentService).spawnPrd(eq("req-prd"), any(), eq(requirement.getGenerationToken()));
        verify(failureInfoSupport).markRetrying(eq(requirement),
                eq(TriggerSource.SCHEDULED_COMPENSATION), any(Date.class));
    }

    @Test
    void developingPlan_resumesIdempotentTaskCreationAndScheduling() {
        Requirement requirement = requirement("req-2", RequirementAutomationStatus.DEVELOPING);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-2");
        plan.setLoopId("loop-1");
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        when(requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)).thenReturn(List.of(requirement));
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-2", "loop-1"))
                .thenReturn(plan);
        when(codingTaskDao.findByRequirementId("req-2")).thenReturn(List.of());
        when(runDao.findByRequirementIdAndState("req-2", RunState.RUNNING)).thenReturn(List.of());

        service.compensateRequirementLoops(new Date());

        verify(automationService).resumeDevelopmentLoop("req-2", "loop-1");
    }

    @Test
    void timedOutDevelopmentRun_isClosedAndTaskBecomesRetryableFailure() {
        Date now = new Date();
        Run run = new Run();
        run.setId("run-1");
        run.setCodingTaskId("task-1");
        run.setState(RunState.RUNNING);
        run.setStartedDate(new Date(now.getTime() - 31 * 60_000L));
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setStatus(CodingTaskStatus.RUNNING);
        when(runDao.findByState(RunState.RUNNING)).thenReturn(List.of(run));
        when(runDao.updateStateIfMatch("run-1", RunState.RUNNING, RunState.FAILED)).thenReturn(1);
        when(codingTaskDao.findOne("task-1")).thenReturn(task);

        service.timeoutRuns(now);

        assertEquals(RunState.FAILED, run.getState());
        assertEquals(CodingTaskStatus.FAILED, task.getStatus());
        verify(failureInfoSupport).markCodingTaskFailure(eq(task), eq("运行超时"), any(),
                eq(TriggerSource.SCHEDULED_COMPENSATION), eq(now));
    }

    @Test
    void waitingHuman_isNeverAdvancedAutomatically() {
        Requirement requirement = requirement("req-3", RequirementAutomationStatus.WAITING_HUMAN);
        when(requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)).thenReturn(List.of(requirement));

        service.compensateRequirementLoops(new Date());

        verify(automationService, never()).executePreparedLoop(any(), any(), any(), any());
        verify(automationService, never()).resumeDevelopmentLoop(any(), any());
        verify(deliveryService, never()).retry(any());
    }

    private Requirement requirement(String id, RequirementAutomationStatus automationStatus) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(automationStatus);
        return requirement;
    }
}
