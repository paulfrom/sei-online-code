package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.SpecState;
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
 * {@code @BeforeAll} 注入回显消息码的 mock 上下文（模式参照 BuildLoopServiceOptimizeTest）。
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
    private SpecService specService;

    @BeforeEach
    void setUp() {
        specDao = mock(SpecDao.class);
        projectService = mock(ProjectService.class);
        specAgentService = mock(SpecAgentService.class);
        specService = new SpecService(specDao, projectService, specAgentService);
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
        assertTrue(result.getMessage().contains("仅 DRAFTING/FAILED 状态可精炼 Spec"));
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
