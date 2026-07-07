package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import com.changhong.onlinecode.dto.plan.PlanModule;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PlanService 单元测试
 *
 * <p>OperateResult 构造经 ApplicationContextHolder 解析 i18n，单测缺容器会 NPE；
 * {@code @BeforeAll} 注入回显消息码的 mock 上下文。</p>
 */
class PlanServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private PlanDao planDao;
    private FeatureDesignDao featureDesignDao;
    private SpecDao specDao;
    private SpecAgentService specAgentService;
    private PlanAgentService planAgentService;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planDao = mock(PlanDao.class);
        featureDesignDao = mock(FeatureDesignDao.class);
        specDao = mock(SpecDao.class);
        specAgentService = mock(SpecAgentService.class);
        planAgentService = mock(PlanAgentService.class);
        planService = new PlanService(planDao, featureDesignDao, specDao, specAgentService, planAgentService);
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

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
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
    void confirm_successCreatesModuleSpecs() {
        // 准备
        PlanService service = spy(planService);
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
        PlanModule module = new PlanModule();
        module.setModuleId("mod-inventory");
        module.setTitle("库存模块");
        module.setSummary("管理库存出入库");
        module.setFeatures(List.of(f1, f2));
        content.setModules(List.of(module));
        existing.setContent(content);

        when(planDao.findLatestByProjectId(projectId)).thenReturn(existing);
        doReturn(OperateResultWithData.operationSuccessWithData(existing)).when(service).savePlan(existing);
        when(specDao.findByProjectIdOrderByVersionDesc(projectId)).thenReturn(List.of());
        when(specDao.save(any(Spec.class))).thenAnswer(inv -> {
            Spec s = inv.getArgument(0);
            s.setId("spec-" + s.getModuleId());
            return s;
        });

        // 执行
        OperateResultWithData<PlanDto> result = service.confirm(projectId);

        // 验证
        assertTrue(result.successful());
        assertEquals(PlanStatus.CONFIRMED, existing.getStatus());

        ArgumentCaptor<Spec> specCaptor = ArgumentCaptor.forClass(Spec.class);
        verify(specDao).save(specCaptor.capture());
        Spec savedSpec = specCaptor.getValue();
        assertEquals(projectId, savedSpec.getProjectId());
        assertEquals(1, savedSpec.getVersion());
        assertEquals(SpecState.GENERATING, savedSpec.getState());
        assertEquals("mod-inventory", savedSpec.getModuleId());
        assertEquals("库存模块", savedSpec.getModuleTitle());
        assertEquals("管理库存出入库", savedSpec.getModuleSummary());
        verify(specAgentService).spawnRequirement(projectId, null, "spec-mod-inventory");
        verify(planAgentService, never()).spawnFeatureDesigns(anyString(), any());
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

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
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
