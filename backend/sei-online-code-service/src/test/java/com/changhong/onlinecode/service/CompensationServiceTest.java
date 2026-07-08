package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.DetailedDesignDto;
import com.changhong.onlinecode.dto.OverviewDesignDto;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dto.ResultData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新流程补偿服务单元测试。
 */
class CompensationServiceTest {

    private RequirementDao requirementDao;
    private OverviewDesignDao overviewDesignDao;
    private DetailedDesignDao detailedDesignDao;
    private CodingTaskDao codingTaskDao;
    private RunDao runDao;

    private RequirementAgentService requirementAgentService;
    private OverviewDesignService overviewDesignService;
    private DetailedDesignService detailedDesignService;
    private CodingTaskService codingTaskService;

    private FailureInfoSupport failureInfoSupport;
    private CompensationLogService compensationLogService;
    private CompensationService compensationService;

    @BeforeEach
    void setUp() throws Exception {
        requirementDao = mock(RequirementDao.class);
        overviewDesignDao = mock(OverviewDesignDao.class);
        detailedDesignDao = mock(DetailedDesignDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);

        requirementAgentService = mock(RequirementAgentService.class);
        overviewDesignService = mock(OverviewDesignService.class);
        detailedDesignService = mock(DetailedDesignService.class);
        codingTaskService = mock(CodingTaskService.class);

        failureInfoSupport = spy(new FailureInfoSupport());
        compensationLogService = mock(CompensationLogService.class);

        compensationService = new CompensationService(
                requirementDao, overviewDesignDao, detailedDesignDao, codingTaskDao, runDao,
                requirementAgentService, overviewDesignService, detailedDesignService, codingTaskService,
                failureInfoSupport, compensationLogService);
        setField(compensationService, "autoRunEnabled", true);
        setField(compensationService, "runTimeoutMinutes", 30L);
    }

    @Test
    void compensateFailedRequirements_retriesEligibleRequirement() {
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setProjectId("proj1");
        requirement.setStatus(RequirementStatus.FAILED);
        requirement.setFailureSummary("json parse failed");
        requirement.setRetryCount(0);
        requirement.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        when(requirementDao.findByStatus(RequirementStatus.FAILED)).thenReturn(List.of(requirement));

        compensationService.compensateFailedRequirements(new Date());

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementDao).save(captor.capture());
        assertEquals(RequirementStatus.PRD_GENERATING, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        verify(requirementAgentService).spawnPrd(eq("req1"), anyString());
    }

    @Test
    void compensateFailedRequirements_skipsWhenNotRetryable() {
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setStatus(RequirementStatus.FAILED);
        requirement.setRetryCount(3);
        requirement.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        when(requirementDao.findByStatus(RequirementStatus.FAILED)).thenReturn(List.of(requirement));

        compensationService.compensateFailedRequirements(new Date());

        verify(requirementDao, never()).save(any(Requirement.class));
        verify(requirementAgentService, never()).spawnPrd(anyString(), anyString());
    }

    @Test
    void compensateMissingOverviewDesigns_createsOverviewForConfirmedRequirement() {
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setProjectId("proj1");
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        when(requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)).thenReturn(List.of(requirement));
        when(overviewDesignDao.findByRequirementId("req1")).thenReturn(null);

        compensationService.compensateMissingOverviewDesigns(new Date());

        verify(overviewDesignService).createGeneratingOverview(requirement);
    }

    @Test
    void compensateFailedOverviewDesigns_retriesEligibleOverview() {
        OverviewDesign overview = new OverviewDesign();
        overview.setId("ov1");
        overview.setRequirementId("req1");
        overview.setStatus(OverviewDesignStatus.FAILED);
        overview.setFailureSummary("agent timeout");
        overview.setRetryCount(0);
        overview.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        when(overviewDesignDao.findByStatus(OverviewDesignStatus.FAILED)).thenReturn(List.of(overview));
        when(overviewDesignService.regenerate(eq("ov1"), anyString()))
                .thenReturn(ResultData.success(new OverviewDesignDto()));

        compensationService.compensateFailedOverviewDesigns(new Date());

        ArgumentCaptor<OverviewDesign> captor = ArgumentCaptor.forClass(OverviewDesign.class);
        verify(overviewDesignDao).save(captor.capture());
        assertEquals(1, captor.getValue().getRetryCount());
        verify(overviewDesignService).regenerate(eq("ov1"), anyString());
    }

    @Test
    void compensateMissingDetailedDesigns_createsAllWhenNoneExist() {
        OverviewDesign overview = new OverviewDesign();
        overview.setId("ov1");
        overview.setRequirementId("req1");
        overview.setStatus(OverviewDesignStatus.CONFIRMED);
        when(overviewDesignDao.findByStatus(OverviewDesignStatus.CONFIRMED)).thenReturn(List.of(overview));
        when(detailedDesignDao.findByOverviewDesignId("ov1")).thenReturn(List.of());

        compensationService.compensateMissingDetailedDesigns(new Date());

        verify(detailedDesignService).createFromOverviewDesign(overview);
    }

    @Test
    void compensateFailedDetailedDesigns_retriesEligibleDesign() {
        DetailedDesign design = new DetailedDesign();
        design.setId("dd1");
        design.setOverviewDesignId("ov1");
        design.setStatus(DetailedDesignStatus.FAILED);
        design.setFailureSummary("parse error");
        design.setRetryCount(0);
        design.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        when(detailedDesignDao.findByStatus(DetailedDesignStatus.FAILED)).thenReturn(List.of(design));
        when(detailedDesignService.regenerate(eq("dd1"), anyString()))
                .thenReturn(ResultData.success(new DetailedDesignDto()));

        compensationService.compensateFailedDetailedDesigns(new Date());

        ArgumentCaptor<DetailedDesign> captor = ArgumentCaptor.forClass(DetailedDesign.class);
        verify(detailedDesignDao).save(captor.capture());
        assertEquals(1, captor.getValue().getRetryCount());
        verify(detailedDesignService).regenerate(eq("dd1"), anyString());
    }

    @Test
    void compensateMissingCodingTasks_createsTaskForConfirmedDesign() {
        DetailedDesign design = new DetailedDesign();
        design.setId("dd1");
        design.setProjectId("proj1");
        design.setStatus(DetailedDesignStatus.CONFIRMED);
        when(detailedDesignDao.findByStatus(DetailedDesignStatus.CONFIRMED)).thenReturn(List.of(design));
        when(codingTaskDao.findByDetailedDesignId("dd1")).thenReturn(List.of());

        compensationService.compensateMissingCodingTasks(new Date());

        verify(codingTaskService).createFromDetailedDesign(design);
    }

    @Test
    void compensateFailedCodingTasks_retriesEligibleTask() {
        CodingTask task = new CodingTask();
        task.setId("ct1");
        task.setDetailedDesignId("dd1");
        task.setStatus(CodingTaskStatus.FAILED);
        task.setFailureSummary("run failed");
        task.setRetryCount(0);
        task.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        when(codingTaskDao.findByStatus(CodingTaskStatus.FAILED)).thenReturn(List.of(task));
        when(codingTaskService.rerun(eq("ct1"), anyString()))
                .thenReturn(ResultData.success(new CodingTaskDto()));

        compensationService.compensateFailedCodingTasks(new Date());

        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao).save(captor.capture());
        assertEquals(1, captor.getValue().getRetryCount());
        verify(codingTaskService).rerun(eq("ct1"), anyString());
    }

    @Test
    void timeoutRunningRuns_marksTimedOutRunFailed() {
        Run run = new Run();
        run.setId("run1");
        run.setCodingTaskId("ct1");
        run.setState(RunState.RUNNING);
        run.setStartedDate(new Date(System.currentTimeMillis() - 31L * 60_000L));
        when(runDao.findByState(RunState.RUNNING)).thenReturn(List.of(run));

        CodingTask task = new CodingTask();
        task.setId("ct1");
        task.setStatus(CodingTaskStatus.RUNNING);
        when(codingTaskDao.findOne("ct1")).thenReturn(task);

        compensationService.timeoutRunningRuns(new Date());

        ArgumentCaptor<Run> runCaptor = ArgumentCaptor.forClass(Run.class);
        verify(runDao).save(runCaptor.capture());
        assertEquals(RunState.FAILED, runCaptor.getValue().getState());
        assertNotNull(runCaptor.getValue().getFinishedDate());

        ArgumentCaptor<CodingTask> taskCaptor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao).save(taskCaptor.capture());
        assertEquals(CodingTaskStatus.FAILED, taskCaptor.getValue().getStatus());
        verify(failureInfoSupport).markCodingTaskFailure(eq(task), anyString(), anyString(), eq(TriggerSource.SCHEDULED_COMPENSATION), any(Date.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
