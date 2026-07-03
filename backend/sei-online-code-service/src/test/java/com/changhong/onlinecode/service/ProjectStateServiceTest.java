package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ProjectStateService 单元测试（Task 12）。
 *
 * <p>验证 WHY：契约 §4.1 聚合规则是编码前流程的总开关——只有 READY_TO_BUILD 才允许执行编码，
 * FAILED 必须阻断（D7），空 FD 不得误判为就绪（D15）。纯查询无 super.save，无需 bootstrapContext。</p>
 */
class ProjectStateServiceTest {

    private PlanDao planDao;
    private FeatureDesignDao featureDesignDao;
    private ProjectStateService service;

    @BeforeEach
    void setUp() {
        planDao = mock(PlanDao.class);
        featureDesignDao = mock(FeatureDesignDao.class);
        service = new ProjectStateService(planDao, featureDesignDao);
    }

    @Test
    void drafting_whenNoPlan() {
        when(planDao.findLatestByProjectId("p1")).thenReturn(null);
        assertEquals("DRAFTING", service.resolvePreBuildState("p1"));
    }

    @Test
    void planning_whenPlanGenerating() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.GENERATING);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        assertEquals("PLANNING", service.resolvePreBuildState("p1"));
    }

    @Test
    void planning_whenPlanDraft() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.DRAFT);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        assertEquals("PLANNING", service.resolvePreBuildState("p1"));
    }

    @Test
    void failed_whenPlanFailed() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.FAILED);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        assertEquals("FAILED", service.resolvePreBuildState("p1"));
    }

    @Test
    void designing_whenPlanConfirmedButNoFd() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.CONFIRMED);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        when(featureDesignDao.findLatestByProjectId("p1")).thenReturn(List.of());
        assertEquals("DESIGNING", service.resolvePreBuildState("p1"));
    }

    @Test
    void failed_whenAnyFdFailed() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.CONFIRMED);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        FeatureDesign fd1 = new FeatureDesign();
        fd1.setStatus(FeatureDesignStatus.DRAFT);
        FeatureDesign fd2 = new FeatureDesign();
        fd2.setStatus(FeatureDesignStatus.FAILED);
        when(featureDesignDao.findLatestByProjectId("p1")).thenReturn(List.of(fd1, fd2));
        assertEquals("FAILED", service.resolvePreBuildState("p1"));
    }

    @Test
    void designing_whenNotAllFdConfirmed() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.CONFIRMED);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        FeatureDesign fd1 = new FeatureDesign();
        fd1.setStatus(FeatureDesignStatus.CONFIRMED);
        FeatureDesign fd2 = new FeatureDesign();
        fd2.setStatus(FeatureDesignStatus.DRAFT);
        when(featureDesignDao.findLatestByProjectId("p1")).thenReturn(List.of(fd1, fd2));
        assertEquals("DESIGNING", service.resolvePreBuildState("p1"));
    }

    @Test
    void readyToBuild_whenAllFdConfirmed() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.CONFIRMED);
        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        FeatureDesign fd1 = new FeatureDesign();
        fd1.setStatus(FeatureDesignStatus.CONFIRMED);
        FeatureDesign fd2 = new FeatureDesign();
        fd2.setStatus(FeatureDesignStatus.CONFIRMED);
        when(featureDesignDao.findLatestByProjectId("p1")).thenReturn(List.of(fd1, fd2));
        assertEquals("READY_TO_BUILD", service.resolvePreBuildState("p1"));
    }
}
