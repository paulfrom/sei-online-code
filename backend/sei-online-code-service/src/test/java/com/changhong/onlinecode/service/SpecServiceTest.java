package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import com.changhong.onlinecode.dto.plan.PlanModule;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SpecService 单元测试
 *
 * <p>OperateResult 构造经 ApplicationContextHolder 解析 i18n，单测缺容器会 NPE；
 * {@code @BeforeAll} 注入回显消息码的 mock 上下文。
 */
class SpecServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private SpecDao specDao;
    private ProjectService projectService;
    private SpecAgentService specAgentService;
    private PlanService planService;
    private PlanAgentService planAgentService;
    private FailureInfoSupport failureInfoSupport;
    private SpecService specService;

    @BeforeEach
    void setUp() {
        specDao = mock(SpecDao.class);
        projectService = mock(ProjectService.class);
        specAgentService = mock(SpecAgentService.class);
        planService = mock(PlanService.class);
        planAgentService = mock(PlanAgentService.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        specService = new SpecService(specDao, projectService, specAgentService, planService, planAgentService,
                failureInfoSupport);
    }

    @Test
    void confirmSpec_spawnsFeatureDesignsForMatchingModule() {
        // 准备
        SpecService service = spy(specService);
        Spec spec = new Spec();
        spec.setId("spec1");
        spec.setProjectId("project1");
        spec.setVersion(1);
        spec.setState(SpecState.SPEC_REVIEW);
        spec.setModuleId("mod-inventory");
        doReturn(spec).when(service).findOne("spec1");
        OperateResultWithData<Spec> savedSpec = OperateResultWithData.operationSuccessWithData(spec);
        doReturn(savedSpec).when(service).save(spec);

        PlanFeature f1 = new PlanFeature();
        f1.setFeatureId("feat1");
        f1.setTitle("入库");
        PlanFeature f2 = new PlanFeature();
        f2.setFeatureId("feat2");
        f2.setTitle("出库");
        PlanModule module = new PlanModule();
        module.setModuleId("mod-inventory");
        module.setTitle("库存模块");
        module.setFeatures(List.of(f1, f2));
        PlanContent content = new PlanContent();
        content.setModules(List.of(module));
        PlanDto confirmedPlan = new PlanDto();
        confirmedPlan.setProjectId("project1");
        confirmedPlan.setStatus(PlanStatus.CONFIRMED);
        confirmedPlan.setContent(content);
        when(planService.findLatest("project1")).thenReturn(confirmedPlan);

        // 执行
        OperateResultWithData<Spec> result = service.confirmSpec("spec1");

        // 验证
        assertTrue(result.successful());
        assertEquals(SpecState.CONFIRMED, spec.getState());
        verify(service).save(spec);
        ArgumentCaptor<List<PlanFeature>> featuresCaptor = ArgumentCaptor.forClass(List.class);
        verify(planAgentService).spawnFeatureDesigns(eq("project1"), featuresCaptor.capture());
        assertEquals(List.of("feat1", "feat2"), featuresCaptor.getValue().stream()
                .map(PlanFeature::getFeatureId)
                .toList());
        verify(planService, never()).regenerate(anyString(), any());
    }

    @Test
    void confirmSpec_spawnsFeatureDesignsFromLegacyFeaturesWhenPlanHasNoModules() {
        // 准备
        SpecService service = spy(specService);
        Spec spec = new Spec();
        spec.setId("spec1");
        spec.setProjectId("project1");
        spec.setVersion(1);
        spec.setState(SpecState.SPEC_REVIEW);
        doReturn(spec).when(service).findOne("spec1");
        doReturn(OperateResultWithData.operationSuccessWithData(spec)).when(service).save(spec);

        PlanFeature feature = new PlanFeature();
        feature.setFeatureId("legacy-feat");
        feature.setTitle("旧功能");
        PlanContent content = new PlanContent();
        content.setFeatures(List.of(feature));
        PlanDto confirmedPlan = new PlanDto();
        confirmedPlan.setProjectId("project1");
        confirmedPlan.setStatus(PlanStatus.CONFIRMED);
        confirmedPlan.setContent(content);
        when(planService.findLatest("project1")).thenReturn(confirmedPlan);

        // 执行
        OperateResultWithData<Spec> result = service.confirmSpec("spec1");

        // 验证
        assertTrue(result.successful());
        ArgumentCaptor<List<PlanFeature>> featuresCaptor = ArgumentCaptor.forClass(List.class);
        verify(planAgentService).spawnFeatureDesigns(eq("project1"), featuresCaptor.capture());
        assertEquals(1, featuresCaptor.getValue().size());
        assertEquals("legacy-feat", featuresCaptor.getValue().get(0).getFeatureId());
    }

    @Test
    void confirmSpec_spawnsAllFeatureDesignsWhenSpecHasNoModuleIdButPlanUsesModules() {
        // 准备：兼容旧详细设计入口，Spec 未绑定 moduleId，但概要设计已按 modules 存储
        SpecService service = spy(specService);
        Spec spec = new Spec();
        spec.setId("spec1");
        spec.setProjectId("project1");
        spec.setVersion(1);
        spec.setState(SpecState.SPEC_REVIEW);
        doReturn(spec).when(service).findOne("spec1");
        doReturn(OperateResultWithData.operationSuccessWithData(spec)).when(service).save(spec);

        PlanFeature inventoryFeature = new PlanFeature();
        inventoryFeature.setFeatureId("feat-inventory");
        inventoryFeature.setTitle("库存查询");
        PlanModule inventoryModule = new PlanModule();
        inventoryModule.setModuleId("mod-inventory");
        inventoryModule.setTitle("库存模块");
        inventoryModule.setFeatures(List.of(inventoryFeature));

        PlanFeature orderFeature = new PlanFeature();
        orderFeature.setFeatureId("feat-order");
        orderFeature.setTitle("订单查询");
        PlanModule orderModule = new PlanModule();
        orderModule.setModuleId("mod-order");
        orderModule.setTitle("订单模块");
        orderModule.setFeatures(List.of(orderFeature));

        PlanContent content = new PlanContent();
        content.setModules(List.of(inventoryModule, orderModule));
        PlanDto confirmedPlan = new PlanDto();
        confirmedPlan.setProjectId("project1");
        confirmedPlan.setStatus(PlanStatus.CONFIRMED);
        confirmedPlan.setContent(content);
        when(planService.findLatest("project1")).thenReturn(confirmedPlan);

        // 执行
        OperateResultWithData<Spec> result = service.confirmSpec("spec1");

        // 验证
        assertTrue(result.successful());
        ArgumentCaptor<List<PlanFeature>> featuresCaptor = ArgumentCaptor.forClass(List.class);
        verify(planAgentService).spawnFeatureDesigns(eq("project1"), featuresCaptor.capture());
        assertEquals(List.of("feat-inventory", "feat-order"), featuresCaptor.getValue().stream()
                .map(PlanFeature::getFeatureId)
                .toList());
    }

    @Test
    void confirmSpec_rejectsWhenSpecNotExists() {
        // 准备
        SpecService service = spy(specService);
        doReturn(null).when(service).findOne("missing");

        // 执行
        OperateResultWithData<Spec> result = service.confirmSpec("missing");

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("Spec 不存在"));
        verify(service, never()).save(any(Spec.class));
        verify(planService, never()).regenerate(anyString(), any());
        verify(planAgentService, never()).spawnFeatureDesigns(anyString(), any());
    }

    @Test
    void confirmSpec_rejectsWhenNotSpecReview() {
        // 准备
        SpecService service = spy(specService);
        Spec spec = new Spec();
        spec.setId("spec1");
        spec.setProjectId("project1");
        spec.setState(SpecState.GENERATING);
        doReturn(spec).when(service).findOne("spec1");

        // 执行
        OperateResultWithData<Spec> result = service.confirmSpec("spec1");

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("仅 SPEC_REVIEW 状态可确认 Spec"));
        verify(service, never()).save(any(Spec.class));
        verify(planService, never()).regenerate(anyString(), any());
        verify(planAgentService, never()).spawnFeatureDesigns(anyString(), any());
    }

    @Test
    void regenerate_rejectsWhenProjectNotExists() {
        // 准备
        String projectId = "proj1";
        when(projectService.findOne(projectId)).thenReturn(null);

        // 执行
        OperateResultWithData<Spec> result = specService.regenerate(projectId, "modify");

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("项目不存在"));
        verify(specDao, never()).save(any(Spec.class));
    }

    @Test
    void regenerate_rejectsWhenNotSpecReview() {
        // 准备
        String projectId = "proj1";
        Project project = new Project();
        project.setState(LifecycleState.DRAFTING);
        when(projectService.findOne(projectId)).thenReturn(project);

        // 执行
        OperateResultWithData<Spec> result = specService.regenerate(projectId, "modify");

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("仅 SPEC_REVIEW 状态可重新生成 Spec"));
        verify(specDao, never()).save(any(Spec.class));
    }

    @Test
    void regenerate_rejectsWhenGenerating() {
        // 准备：latest Spec 处于 GENERATING —— 并发守卫应拒绝，避免连建多个空版本
        String projectId = "proj1";
        Project project = new Project();
        project.setId(projectId);
        project.setState(LifecycleState.SPEC_REVIEW);

        Spec generating = new Spec();
        generating.setProjectId(projectId);
        generating.setVersion(1);
        generating.setState(SpecState.GENERATING);
        when(specDao.findByProjectIdOrderByVersionDesc(projectId)).thenReturn(List.of(generating));
        when(projectService.findOne(projectId)).thenReturn(project);

        // 执行
        OperateResultWithData<Spec> result = specService.regenerate(projectId, "modify");

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("正在生成中"));
        verify(specDao, never()).save(any(Spec.class));
        verify(specAgentService, never()).spawnRequirement(anyString(), anyString(), anyString());
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
    @Test
    void regenerate_success() {
        // 准备
        String projectId = "proj1";
        Project project = new Project();
        project.setId(projectId);
        project.setState(LifecycleState.SPEC_REVIEW);

        Spec existingSpec = new Spec();
        existingSpec.setProjectId(projectId);
        existingSpec.setVersion(1);
        existingSpec.setState(SpecState.SPEC_REVIEW);
        when(specDao.findByProjectIdOrderByVersionDesc(projectId)).thenReturn(List.of(existingSpec));

        when(projectService.findOne(projectId)).thenReturn(project);
        when(specDao.save(any(Spec.class))).thenAnswer(inv -> {
            Spec s = inv.getArgument(0);
            s.setId("spec2");
            return s;
        });

        // 执行
        OperateResultWithData<Spec> result = specService.regenerate(projectId, "modify hint");

        // 验证：建 GENERATING 行 + spawn Requirement Agent（异步回调收口到 SPEC_REVIEW 由 SpecAgentService 负责）
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getVersion());
        assertEquals(SpecState.GENERATING, result.getData().getState());
        assertEquals("modify hint", result.getData().getModifyHint());

        ArgumentCaptor<Spec> specCaptor = ArgumentCaptor.forClass(Spec.class);
        verify(specDao).save(specCaptor.capture());
        assertEquals(projectId, specCaptor.getValue().getProjectId());
        assertEquals(2, specCaptor.getValue().getVersion());
        assertEquals(SpecState.GENERATING, specCaptor.getValue().getState());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectService).save(projectCaptor.capture());
        assertEquals("spec2", projectCaptor.getValue().getCurrentSpecId());

        verify(specAgentService).spawnRequirement(eq(projectId), eq("modify hint"), eq("spec2"));
    }

    @Test
    void refineSpec_rejectsWhenProjectNotExists() {
        // 准备
        String projectId = "proj1";
        when(projectService.findOne(projectId)).thenReturn(null);

        // 执行
        OperateResultWithData<Spec> result = specService.refineSpec(projectId);

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("项目不存在"));
        verify(specDao, never()).save(any(Spec.class));
    }

    @Test
    void refineSpec_rejectsFromNonDraftableState() {
        // 准备
        String projectId = "proj1";
        Project project = new Project();
        project.setState(LifecycleState.DISPATCHING);
        when(projectService.findOne(projectId)).thenReturn(project);

        // 执行
        OperateResultWithData<Spec> result = specService.refineSpec(projectId);

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("仅 DRAFTING/FAILED 状态可生成模块详细设计"));
        verify(specDao, never()).save(any(Spec.class));
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，FAILED 入口转换序列待集成测试")
    @Test
    void refineSpec_retriesFromFailed() {
        // 准备
        String projectId = "proj1";
        Project project = new Project();
        project.setId(projectId);
        project.setState(LifecycleState.FAILED);

        Spec existingSpec = new Spec();
        existingSpec.setProjectId(projectId);
        existingSpec.setVersion(1);
        when(specDao.findByProjectIdOrderByVersionDesc(projectId)).thenReturn(List.of(existingSpec));

        when(projectService.findOne(projectId)).thenReturn(project);
        when(projectService.transitionState(eq(projectId), any(LifecycleState.class)))
                .thenAnswer(inv -> {
                    LifecycleState target = inv.getArgument(1);
                    project.setState(target);
                    return OperateResultWithData.operationSuccessWithData(project);
                });
        when(specDao.save(any(Spec.class))).thenAnswer(inv -> {
            Spec s = inv.getArgument(0);
            s.setId("spec2");
            return s;
        });

        // 执行
        OperateResultWithData<Spec> result = specService.refineSpec(projectId);

        // 验证：建 GENERATING 行；项目同步推进 DRAFTING→SPEC_REFINING 后停下，
        // SPEC_REVIEW 收口交给 SpecAgentService 异步回调（此处不验证）
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getVersion());
        assertEquals(SpecState.GENERATING, result.getData().getState());

        InOrder inOrder = inOrder(projectService);
        inOrder.verify(projectService).transitionState(projectId, LifecycleState.DRAFTING);
        inOrder.verify(projectService).transitionState(projectId, LifecycleState.SPEC_REFINING);
        inOrder.verify(projectService).save(any(Project.class));
        verify(projectService, never()).transitionState(projectId, LifecycleState.SPEC_REVIEW);

        verify(specAgentService).spawnRequirement(eq(projectId), eq(null), eq("spec2"));
    }
}
