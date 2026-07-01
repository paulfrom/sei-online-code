package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BuildLoopService#optimize} 单元测试（B27）。
 *
 * <p>验证 WHY：契约 §1/§3 规定——每个 Build Loop 回合必须产出一个 <b>新</b> Spec 版本
 * （既有版本不可变，构成可 diff 历史），并开启一个 <b>子</b> 迭代，其 round 在上一回合基础上 +1、
 * parentIterationId 指向上一回合、feedback 记录本回合诉求。若 optimize 误改上一回合 Spec 或
 * 复用上一回合迭代，时间线父链与版本历史都会被破坏。因此断言：optimize 触发了下一版本 Spec 的
 * 精炼，且新建迭代携带 round+1 / parent / feedback / 新版本号。</p>
 *
 * @author sei-online-code
 */
@ExtendWith(MockitoExtension.class)
class BuildLoopServiceOptimizeTest {

    /**
     * OperateResult 构造时会经 ApplicationContextHolder 解析 i18n 消息，单测环境缺容器会 NPE。
     * 此处注入一个「回显消息码」的 mock 上下文，令框架 BO 在纯单测下可构造。
     */
    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    @Mock
    private IterationService iterationService;
    @Mock
    private SpecService specService;
    @Mock
    private ProjectService projectService;
    @Mock
    private TaskService taskService;
    @Mock
    private RunService runService;
    @Mock
    private DispatchService dispatchService;

    @Test
    void optimize_createsNewSpecVersion_andChildIterationWithRoundPlusOne() {
        String projectId = "PRJ0001";
        String priorIterId = "ITER0001";
        String feedback = "把库存列表加上导出按钮";

        BuildLoopService service = new BuildLoopService(
                iterationService, specService, projectService, taskService, runService, dispatchService);

        // 项目当前指向第 1 回合迭代（PREVIEW、round=1、挂 Spec v1）
        Project project = new Project();
        project.setState(LifecycleState.PREVIEW);
        project.setCurrentIterationId(priorIterId);
        when(projectService.findOne(projectId)).thenReturn(project);

        Iteration prior = new Iteration();
        prior.setProjectId(projectId);
        prior.setState(LifecycleState.PREVIEW);
        prior.setRound(1);
        prior.setSpecVersion(1);
        prior.setId(priorIterId);
        when(iterationService.findOne(priorIterId)).thenReturn(prior);

        // 增量精炼产出新版本 Spec（version = prior+1 = 2）
        Spec nextSpec = new Spec();
        nextSpec.setId("SPEC0002");
        nextSpec.setProjectId(projectId);
        nextSpec.setVersion(2);
        nextSpec.setState(SpecState.SPEC_REVIEW);
        // 预构造 BO（其构造会触发 ctx.getMessage）——避免在 when(...) 参数位内构造导致
        // Mockito UnfinishedStubbingException。
        OperateResultWithData<Spec> specOk =
                OperateResultWithData.operationSuccessWithData(nextSpec);
        OperateResultWithData<Project> projectOk =
                OperateResultWithData.operationSuccessWithData(project);
        when(specService.refineNextVersion(projectId, feedback)).thenReturn(specOk);

        // 状态回退再入均放行
        lenient().when(projectService.transitionState(eq(projectId), any())).thenReturn(projectOk);

        // 迭代保存：回显入参
        when(iterationService.save(any(Iteration.class)))
                .thenAnswer(inv -> OperateResultWithData.operationSuccessWithData(inv.getArgument(0)));
        lenient().when(projectService.save(any(Project.class))).thenReturn(projectOk);

        OperateResultWithData<Iteration> result = service.optimize(projectId, feedback);

        assertTrue(result.successful(), "optimize 应成功");

        // 断言：确实触发了下一版本 Spec 精炼（新版本，不改旧版）
        verify(specService).refineNextVersion(projectId, feedback);

        // 捕获新建的子迭代，断言回合溯源
        ArgumentCaptor<Iteration> captor = ArgumentCaptor.forClass(Iteration.class);
        verify(iterationService).save(captor.capture());
        Iteration child = captor.getValue();

        assertEquals(2, child.getRound(), "子迭代 round 应为上一回合 +1");
        assertEquals(priorIterId, child.getParentIterationId(), "子迭代 parent 应指向上一回合");
        assertEquals(feedback, child.getFeedback(), "子迭代应记录本回合 feedback");
        assertEquals("SPEC0002", child.getSpecId(), "子迭代应挂新版本 Spec");
        assertEquals(2, child.getSpecVersion(), "子迭代 specVersion 应为新版本号（prior+1）");
        assertEquals(LifecycleState.SPEC_REVIEW, child.getState(), "子迭代应进入 SPEC_REVIEW");
    }

    @Test
    void optimize_rejectsEmptyFeedback() {
        BuildLoopService service = new BuildLoopService(
                iterationService, specService, projectService, taskService, runService, dispatchService);

        OperateResultWithData<Iteration> result = service.optimize("PRJ0001", "  ");

        assertTrue(result.notSuccessful(), "空 feedback 必须被拒（契约 §3：feedback 不得绕过 Spec）");
        verify(specService, org.mockito.Mockito.never()).refineNextVersion(anyString(), anyString());
    }
}
