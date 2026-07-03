package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PlanService 单元测试
 */
@Disabled("deferred: sei-core OperateResultWithData/ResponseData 构造需 Spring ApplicationContext（ApplicationContextHolder），纯 Mockito 单测无法构造；super.save 的 BaseService.applySaveHooks 同因。需 @SpringBootTest + Testcontainers 测试基座（与 T8 DAO 测试同根因），待测试基建专项")
class PlanServiceTest {

    private PlanDao planDao;
    private FeatureDesignDao featureDesignDao;
    private PlanAgentService planAgentService;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planDao = mock(PlanDao.class);
        featureDesignDao = mock(FeatureDesignDao.class);
        planAgentService = mock(PlanAgentService.class);
        planService = new PlanService(planDao, featureDesignDao, planAgentService);
    }

    @Test
    void edit_rejectsWhenGenerating() {
        // 准备
        String projectId = "proj1";
        Plan existing = new Plan();
        existing.setProjectId(projectId);
        existing.setStatus(PlanStatus.GENERATING);
        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);

        // 执行
        OperateResultWithData<PlanDto> result = planService.edit(projectId, new PlanContent());

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("正在生成中"));
        verify(planDao, never()).markNonLatest(any());
        verify(featureDesignDao, never()).cascadeStale(any());
    }

    @Test
    void edit_success() {
        // 准备
        String projectId = "proj1";
        Plan existing = new Plan();
        existing.setId("plan1");
        existing.setProjectId(projectId);
        existing.setVersion(1);
        existing.setStatus(PlanStatus.DRAFT);
        existing.setIsLatest(true);

        PlanContent newContent = new PlanContent();
        newContent.setSummary("new summary");

        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);
        when(planDao.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setId("plan2");
            return p;
        });

        // 执行
        OperateResultWithData<PlanDto> result = planService.edit(projectId, newContent);

        // 验证
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getVersion());
        assertTrue(result.getData().getIsLatest());
        assertEquals(PlanStatus.DRAFT, result.getData().getStatus());

        verify(planDao).markNonLatest(projectId);
        verify(featureDesignDao).cascadeStale(projectId);

        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(planCaptor.capture());
        assertEquals("new summary", planCaptor.getValue().getContent().getSummary());
    }

    @Test
    void confirm_rejectsWhenNotDraft() {
        // 准备
        String projectId = "proj1";
        Plan existing = new Plan();
        existing.setProjectId(projectId);
        existing.setStatus(PlanStatus.CONFIRMED);
        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);

        // 执行
        OperateResultWithData<PlanDto> result = planService.confirm(projectId);

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("仅草稿状态可确认"));
    }

    @Test
    void confirm_successSpawnsFeatureDesigns() {
        // 准备
        String projectId = "proj1";
        Plan existing = new Plan();
        existing.setId("plan1");
        existing.setProjectId(projectId);
        existing.setStatus(PlanStatus.DRAFT);

        PlanContent content = new PlanContent();
        PlanFeature f1 = new PlanFeature();
        f1.setFeatureId("feat1");
        f1.setTitle("Feature 1");
        PlanFeature f2 = new PlanFeature();
        f2.setFeatureId("feat2");
        f2.setTitle("Feature 2");
        content.setFeatures(List.of(f1, f2));
        existing.setContent(content);

        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);
        when(planDao.save(any(Plan.class))).thenReturn(existing);

        // feat1 已有 FD，feat2 无
        FeatureDesign fd1 = new FeatureDesign();
        fd1.setFeatureId("feat1");
        when(featureDesignDao.findLatestByProjectId(projectId)).thenReturn(List.of(fd1));

        // 执行
        OperateResultWithData<PlanDto> result = planService.confirm(projectId);

        // 验证
        assertTrue(result.successful());
        assertEquals(PlanStatus.CONFIRMED, existing.getStatus());

        ArgumentCaptor<List<PlanFeature>> featuresCaptor = ArgumentCaptor.forClass(List.class);
        verify(planAgentService).spawnFeatureDesigns(eq(projectId), featuresCaptor.capture());
        assertEquals(1, featuresCaptor.getValue().size());
        assertEquals("feat2", featuresCaptor.getValue().get(0).getFeatureId());
    }

    @Test
    void regenerate_rejectsWhenGenerating() {
        // 准备
        String projectId = "proj1";
        Plan existing = new Plan();
        existing.setProjectId(projectId);
        existing.setStatus(PlanStatus.GENERATING);
        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);

        // 执行
        OperateResultWithData<PlanDto> result = planService.regenerate(projectId, "modify");

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("正在生成中"));
        verify(planDao, never()).markNonLatest(any());
        verify(planAgentService, never()).spawnPlanning(any(), any());
    }

    @Test
    void regenerate_success() {
        // 准备
        String projectId = "proj1";
        Plan existing = new Plan();
        existing.setId("plan1");
        existing.setProjectId(projectId);
        existing.setVersion(1);
        existing.setStatus(PlanStatus.DRAFT);
        existing.setIsLatest(true);

        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);
        when(planDao.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setId("plan2");
            return p;
        });

        // 执行
        OperateResultWithData<PlanDto> result = planService.regenerate(projectId, "modify hint");

        // 验证
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getVersion());
        assertTrue(result.getData().getIsLatest());
        assertEquals(PlanStatus.GENERATING, result.getData().getStatus());
        assertEquals("modify hint", result.getData().getModifyHint());

        verify(planDao).markNonLatest(projectId);
        verify(planAgentService).spawnPlanning(projectId, "modify hint");
    }
}
