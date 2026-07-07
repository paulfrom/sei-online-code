package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanModule;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Spec;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompensationServiceTest {

    private PlanDao planDao;
    private SpecDao specDao;
    private FeatureDesignDao featureDesignDao;
    private PlanService planService;
    private SpecService specService;
    private PlanAgentService planAgentService;
    private SpecAgentService specAgentService;
    private FeatureDesignBuildService featureDesignBuildService;
    private FailureInfoSupport failureInfoSupport;
    private CompensationLogService compensationLogService;
    private CompensationService compensationService;

    @BeforeEach
    void setUp() throws Exception {
        planDao = mock(PlanDao.class);
        specDao = mock(SpecDao.class);
        featureDesignDao = mock(FeatureDesignDao.class);
        planService = mock(PlanService.class);
        specService = mock(SpecService.class);
        planAgentService = mock(PlanAgentService.class);
        specAgentService = mock(SpecAgentService.class);
        featureDesignBuildService = mock(FeatureDesignBuildService.class);
        failureInfoSupport = spy(new FailureInfoSupport());
        compensationLogService = mock(CompensationLogService.class);
        compensationService = new CompensationService(planDao, specDao, featureDesignDao,
                planService, specService, planAgentService, specAgentService, featureDesignBuildService,
                failureInfoSupport, compensationLogService);
        setField(compensationService, "autoBuildEnabled", true);
        setField(compensationService, "buildTimeoutMinutes", 30L);
    }

    @Test
    void compensateFailedPlans_retriesEligiblePlan() {
        Plan plan = new Plan();
        plan.setId("plan1");
        plan.setProjectId("project1");
        plan.setStatus(PlanStatus.FAILED);
        plan.setFailureSummary("json parse failed");
        plan.setRetryCount(0);
        plan.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        when(planDao.findByStatusAndIsLatestTrue(PlanStatus.FAILED)).thenReturn(List.of(plan));

        compensationService.compensateFailedPlans();

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(captor.capture());
        assertEquals(PlanStatus.GENERATING, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        verify(planAgentService).spawnPlanning(eq("project1"), anyString(), eq(TriggerSource.SCHEDULED_COMPENSATION));
    }

    @Test
    void compensateMissingSpecs_createsOnlyMissingModuleSpec() {
        PlanModule existingModule = new PlanModule();
        existingModule.setModuleId("mod-existing");
        existingModule.setTitle("已存在模块");
        PlanModule missingModule = new PlanModule();
        missingModule.setModuleId("mod-missing");
        missingModule.setTitle("缺失模块");

        Plan plan = new Plan();
        plan.setId("plan1");
        plan.setProjectId("project1");
        plan.setStatus(PlanStatus.CONFIRMED);
        plan.setContent(new PlanContent());

        Spec existing = new Spec();
        existing.setId("spec1");
        existing.setProjectId("project1");
        existing.setVersion(1);
        existing.setModuleId("mod-existing");
        existing.setModuleTitle("已存在模块");

        when(planDao.findByStatusAndIsLatestTrue(PlanStatus.CONFIRMED)).thenReturn(List.of(plan));
        when(planService.modulesOrFallback(plan.getContent())).thenReturn(List.of(existingModule, missingModule));
        when(specDao.findByProjectId("project1")).thenReturn(List.of(existing));
        when(specDao.save(any(Spec.class))).thenAnswer(inv -> {
            Spec spec = inv.getArgument(0);
            spec.setId("spec-new");
            return spec;
        });

        compensationService.compensateMissingSpecs();

        ArgumentCaptor<Spec> captor = ArgumentCaptor.forClass(Spec.class);
        verify(specDao).save(captor.capture());
        assertEquals("mod-missing", captor.getValue().getModuleId());
        assertEquals(SpecState.GENERATING, captor.getValue().getState());
        verify(specAgentService).spawnRequirement(eq("project1"), eq(null), eq("spec-new"),
                eq(TriggerSource.CHAIN_COMPENSATION));
    }

    @Test
    void timeoutBuildingFeatureDesigns_marksTimedOutBuildFailed() {
        FeatureDesign design = new FeatureDesign();
        design.setId("fd1");
        design.setProjectId("project1");
        design.setFeatureId("feature1");
        design.setStatus(FeatureDesignStatus.CONFIRMED);
        design.setBuildStatus(FeatureDesignBuildStatus.BUILDING);
        design.setLastEditedDate(new Date(System.currentTimeMillis() - 31L * 60_000L));
        when(featureDesignDao.findByBuildStatusAndIsLatestTrue(FeatureDesignBuildStatus.BUILDING))
                .thenReturn(List.of(design));

        compensationService.timeoutBuildingFeatureDesigns();

        ArgumentCaptor<FeatureDesign> captor = ArgumentCaptor.forClass(FeatureDesign.class);
        verify(featureDesignDao).save(captor.capture());
        assertEquals(FeatureDesignBuildStatus.BUILD_FAILED, captor.getValue().getBuildStatus());
        assertEquals(FailureCode.BUILD_TIMEOUT, captor.getValue().getFailureCode());
        assertNotNull(captor.getValue().getNextRetryAt());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
