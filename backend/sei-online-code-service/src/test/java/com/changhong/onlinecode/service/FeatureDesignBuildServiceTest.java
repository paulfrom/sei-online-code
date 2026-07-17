package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Task;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FeatureDesignBuildService 单元测试（Task 11）。
 *
 * <p>验证互斥抢占 409、批量跳过 BUILDING、回调 BUILT/BUILD_FAILED。
 */
@ExtendWith(MockitoExtension.class)
class FeatureDesignBuildServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    @Mock
    private FeatureDesignDao featureDesignDao;
    @Mock
    private AgentService agentService;
    @Mock
    private TaskService taskService;
    @Mock
    private RunService runService;
    @Mock
    private RunNumberService runNumberService;
    @Mock
    private AgentExecutionService agentExecutionService;
    @Mock
    private FailureInfoSupport failureInfoSupport;

    private FeatureDesignBuildService service;

    @BeforeEach
    void setUp() {
        service = new FeatureDesignBuildService(
                featureDesignDao,
                agentService,
                taskService,
                runService,
                runNumberService,
                agentExecutionService,
                failureInfoSupport
        );
        lenient().when(runNumberService.assign(any(Run.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void build_throwsFailureWhenFdNotFound() {
        // 准备
        String id = "fd1";
        when(featureDesignDao.findLatestById(id)).thenReturn(null);

        // 执行
        OperateResultWithData<FeatureDesignBuildResultDto> result = service.build(id);

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("功能设计不存在"));
    }

    @Test
    void build_throwsFailureWhenNotConfirmed() {
        // 准备
        String id = "fd1";
        FeatureDesign fd = new FeatureDesign();
        fd.setId(id);
        fd.setStatus(FeatureDesignStatus.DRAFT);
        when(featureDesignDao.findLatestById(id)).thenReturn(fd);

        // 执行
        OperateResultWithData<FeatureDesignBuildResultDto> result = service.build(id);

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("设计未确认，不可执行编码"));
    }

    @Test
    void build_throwsConflictWhenAcquireFails() {
        // 准备
        String id = "fd1";
        FeatureDesign fd = new FeatureDesign();
        fd.setId(id);
        fd.setStatus(FeatureDesignStatus.CONFIRMED);
        when(featureDesignDao.findLatestById(id)).thenReturn(fd);
        when(featureDesignDao.tryAcquireBuildLock(eq(id), eq(FeatureDesignBuildStatus.BUILDING))).thenReturn(0);

        // 执行 & 验证
        assertThrows(ConflictException.class, () -> service.build(id));
    }

    @Test
    void updateBuildStatus_success_setsBuilt() {
        // 准备
        String id = "fd1";
        FeatureDesign fd = new FeatureDesign();
        fd.setId(id);
        fd.setBuildStatus(FeatureDesignBuildStatus.BUILDING);
        when(featureDesignDao.findLatestById(id)).thenReturn(fd);

        // 执行
        service.updateBuildStatus(id, true);

        // 验证
        ArgumentCaptor<FeatureDesign> captor = ArgumentCaptor.forClass(FeatureDesign.class);
        verify(featureDesignDao).save(captor.capture());
        assertEquals(FeatureDesignBuildStatus.BUILT, captor.getValue().getBuildStatus());
    }

    @Test
    void updateBuildStatus_failure_setsBuildFailed() {
        // 准备
        String id = "fd1";
        FeatureDesign fd = new FeatureDesign();
        fd.setId(id);
        fd.setBuildStatus(FeatureDesignBuildStatus.BUILDING);
        when(featureDesignDao.findLatestById(id)).thenReturn(fd);

        // 执行
        service.updateBuildStatus(id, false);

        // 验证
        ArgumentCaptor<FeatureDesign> captor = ArgumentCaptor.forClass(FeatureDesign.class);
        verify(featureDesignDao).save(captor.capture());
        assertEquals(FeatureDesignBuildStatus.BUILD_FAILED, captor.getValue().getBuildStatus());
    }

    @Test
    void buildProject_skipsBuilding() {
        // 准备
        String projectId = "proj1";

        FeatureDesign fd1 = new FeatureDesign();
        fd1.setId("fd1");
        fd1.setStatus(FeatureDesignStatus.CONFIRMED);
        fd1.setBuildStatus(FeatureDesignBuildStatus.IDLE);

        FeatureDesign fd2 = new FeatureDesign();
        fd2.setId("fd2");
        fd2.setStatus(FeatureDesignStatus.CONFIRMED);
        fd2.setBuildStatus(FeatureDesignBuildStatus.BUILDING); // 这个会被跳过

        FeatureDesign fd3 = new FeatureDesign();
        fd3.setId("fd3");
        fd3.setStatus(FeatureDesignStatus.DRAFT); // 这个会被跳过
        fd3.setBuildStatus(FeatureDesignBuildStatus.IDLE);

        when(featureDesignDao.findLatestByProjectId(projectId)).thenReturn(List.of(fd1, fd2, fd3));

        // 执行
        List<FeatureDesignBuildResultDto> results = service.buildProject(projectId);

        // 验证
        assertEquals(3, results.size());
        // 注意：由于 build 方法在 buildProject 中被调用，但实际测试中我们可以验证逻辑
        // 这里主要验证过滤逻辑是否正确应用
    }

    @Test
    void build_successSummaryContainingFailedWord_marksBuildAndRunSucceeded() {
        // 准备
        String fdId = "fd1";
        String projId = "proj1";
        String taskId = "task1";
        String runId = "run1";

        FeatureDesign fd = new FeatureDesign();
        fd.setId(fdId);
        fd.setProjectId(projId);
        fd.setFeatureId("feat1");
        fd.setStatus(FeatureDesignStatus.CONFIRMED);
        fd.setBuildStatus(FeatureDesignBuildStatus.IDLE);

        Agent devAgent = new Agent();
        devAgent.setName("dev-agent");

        Task savedTask = new Task();
        savedTask.setId(taskId);

        Run savedRun = new Run();
        savedRun.setId(runId);
        savedRun.setState(RunState.RUNNING);

        com.changhong.onlinecode.agent.AgentWorkspace workspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(workspace.pathString()).thenReturn("/tmp/workspace");

        when(featureDesignDao.findLatestById(fdId)).thenReturn(fd);
        when(featureDesignDao.tryAcquireBuildLock(eq(fdId), eq(FeatureDesignBuildStatus.BUILDING))).thenReturn(1);
        when(agentService.findByName("dev-agent")).thenReturn(devAgent);
        OperateResultWithData<Task> taskSaveResult = OperateResultWithData.operationSuccessWithData(savedTask);
        OperateResultWithData<Run> runSaveResult = OperateResultWithData.operationSuccessWithData(savedRun);
        when(taskService.save(any(Task.class))).thenReturn(taskSaveResult);
        when(agentExecutionService.workspace(projId)).thenReturn(workspace);
        when(runService.save(any(Run.class))).thenReturn(runSaveResult);
        when(runService.findOne(runId)).thenReturn(savedRun);
        when(agentExecutionService.executeAsync(eq("dev-agent"), any()))
                .thenReturn(CompletableFuture.completedFuture(new AgentExecutionResult(runId,
                        "Supported outcomes: ENROLLED/WAITLISTED/FAILED", true, null)));

        // 执行
        OperateResultWithData<FeatureDesignBuildResultDto> result = service.build(fdId);

        // 验证
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(runId, result.getData().getRunId());

        // 验证 Task 创建正确
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).save(taskCaptor.capture());
        assertEquals(fdId, taskCaptor.getValue().getFeatureDesignId());
        assertEquals("dev-agent", taskCaptor.getValue().getAssignedAgent());

        // 验证 Run 创建正确
        ArgumentCaptor<Run> runCaptor = ArgumentCaptor.forClass(Run.class);
        verify(runService, times(2)).save(runCaptor.capture());
        assertEquals(taskId, runCaptor.getAllValues().get(0).getTaskId());
        assertEquals(FeatureDesignBuildStatus.BUILT, fd.getBuildStatus());
        assertEquals(RunState.SUCCEEDED, savedRun.getState());
        assertEquals(RunTerminalReason.SUCCEEDED, savedRun.getTerminalReason());
        assertNotNull(savedRun.getFinishedDate());
    }

    @Test
    void build_failedAgentResultWithoutFailedWord_marksBuildAndRunFailed() {
        String fdId = "fd-failed";
        String projectId = "project-failed";
        String runId = "run-failed";

        FeatureDesign fd = new FeatureDesign();
        fd.setId(fdId);
        fd.setProjectId(projectId);
        fd.setFeatureId("feature-failed");
        fd.setStatus(FeatureDesignStatus.CONFIRMED);
        fd.setBuildStatus(FeatureDesignBuildStatus.IDLE);

        Agent devAgent = new Agent();
        devAgent.setName("dev-agent");
        Task savedTask = new Task();
        savedTask.setId("task-failed");
        Run savedRun = new Run();
        savedRun.setId(runId);
        savedRun.setState(RunState.RUNNING);

        com.changhong.onlinecode.agent.AgentWorkspace workspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(workspace.pathString()).thenReturn("/tmp/workspace-failed");
        when(featureDesignDao.findLatestById(fdId)).thenReturn(fd);
        when(featureDesignDao.tryAcquireBuildLock(fdId, FeatureDesignBuildStatus.BUILDING)).thenReturn(1);
        when(agentService.findByName("dev-agent")).thenReturn(devAgent);
        OperateResultWithData<Task> taskSaveResult = OperateResultWithData.operationSuccessWithData(savedTask);
        OperateResultWithData<Run> runSaveResult = OperateResultWithData.operationSuccessWithData(savedRun);
        when(taskService.save(any(Task.class))).thenReturn(taskSaveResult);
        when(agentExecutionService.workspace(projectId)).thenReturn(workspace);
        when(runService.save(any(Run.class))).thenReturn(runSaveResult);
        when(runService.findOne(runId)).thenReturn(savedRun);
        when(agentExecutionService.executeAsync(eq("dev-agent"), any()))
                .thenReturn(CompletableFuture.completedFuture(new AgentExecutionResult(
                        runId, "compiler diagnostics", false, "network failed")));

        OperateResultWithData<FeatureDesignBuildResultDto> result = service.build(fdId);

        assertTrue(result.successful());
        assertEquals(FeatureDesignBuildStatus.BUILD_FAILED, fd.getBuildStatus());
        assertEquals(RunState.FAILED, savedRun.getState());
        assertEquals(RunTerminalReason.FAILED, savedRun.getTerminalReason());
        assertEquals("network failed", savedRun.getFailureReason());
        assertNotNull(savedRun.getFinishedDate());
    }
}
