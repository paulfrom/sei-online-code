package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.AgentMemoryTemplateService;
import com.changhong.onlinecode.service.MemoryJobService;
import com.changhong.onlinecode.service.PlatformMemoryWriterService;
import com.changhong.onlinecode.service.WorkspaceMemoryService;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemoryJobExecutor 单元测试。
 *
 * <p>WHY：MEMORY_INITIALIZE 是工作区记忆能力的入口，必须验证完整链路：抢占 job → 补齐 agent-memory →
 * 扫描 → 创建 WorkspaceMemory → 写入 platform-memory → 标记成功。任何环节异常都应进入失败重试，
 * 且不应因 tryClaim 失败而重复执行。CodingTask 后回写需验证增量更新、降级与失败隔离。</p>
 *
 * @author sei-online-code
 */
class MemoryJobExecutorTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private MemoryJobService memoryJobService;
    private ProjectDao projectDao;
    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private WorkspaceMemoryDao workspaceMemoryDao;
    private AgentMemoryTemplateService agentMemoryTemplateService;
    private WorkspaceMemoryScannerService scannerService;
    private WorkspaceMemoryService workspaceMemoryService;
    private PlatformMemoryWriterService writerService;
    private CodingTaskChangeCollector changeCollector;
    private CodingTaskMemoryUpdateAssembler updateAssembler;
    private MemoryJobExecutor executor;

    @BeforeEach
    void setUp() {
        memoryJobService = mock(MemoryJobService.class);
        projectDao = mock(ProjectDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        workspaceMemoryDao = mock(WorkspaceMemoryDao.class);
        agentMemoryTemplateService = mock(AgentMemoryTemplateService.class);
        scannerService = mock(WorkspaceMemoryScannerService.class);
        workspaceMemoryService = mock(WorkspaceMemoryService.class);
        writerService = mock(PlatformMemoryWriterService.class);
        changeCollector = mock(CodingTaskChangeCollector.class);
        updateAssembler = mock(CodingTaskMemoryUpdateAssembler.class);
        executor = new MemoryJobExecutor(memoryJobService, projectDao, codingTaskDao, runDao, workspaceMemoryDao,
                agentMemoryTemplateService, scannerService, workspaceMemoryService, writerService,
                changeCollector, updateAssembler, 50, 3,
                "build.gradle,settings.gradle,package.json,pnpm-workspace.yaml,bootstrap,routes/,.sql");
    }

    @Test
    void execute_initializeJob_createsMemoryAndMarksSucceeded() {
        // WHY：正常初始化链路应完成扫描、持久化和平台镜像写入。
        MemoryJob job = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        MemoryJob running = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        Project project = project("proj-1", "/workspace/proj-1");
        project.setMemorySeedTemplateId("seed-1");
        when(projectDao.findOne("proj-1")).thenReturn(project);
        when(agentMemoryTemplateService.ensureAgentMemory("proj-1", "/workspace/proj-1"))
                .thenReturn(List.of("agent-memory/project-memory.md"));
        when(agentMemoryTemplateService.resolveSeedVersion("proj-1")).thenReturn(3);

        WorkspaceMemoryScanResult scan = new WorkspaceMemoryScanResult();
        scan.setNormClaims(List.of());
        scan.setRealityClaims(List.of());
        when(scannerService.scan("proj-1", "/workspace/proj-1")).thenReturn(scan);

        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setId("wm-1");
        OperateResultWithData<WorkspaceMemory> success = OperateResultWithData.operationSuccessWithData(memory);
        when(workspaceMemoryService.createNewVersionFromScan(eq("proj-1"), any(WorkspaceMemoryScanResult.class),
                any(), any())).thenReturn(success);
        when(writerService.writePlatformMemory("/workspace/proj-1", memory)).thenReturn(true);

        executor.execute(job);

        verify(workspaceMemoryService).createNewVersionFromScan(eq("proj-1"), eq(scan), eq("seed-1"), eq(3));
        verify(memoryJobService).recordOutput("job-1", "wm-1");
        verify(memoryJobService).markSucceeded("job-1", "wm-1");
        verify(writerService).writePlatformMemory("/workspace/proj-1", memory);
    }

    @Test
    void execute_retryAfterMirrorFailure_reusesCreatedWorkspaceMemory() {
        MemoryJob job = pendingJob("job-retry", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        job.setNewWorkspaceMemoryId("wm-existing");
        MemoryJob running = pendingJob("job-retry", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        running.setStatus(MemoryJobStatus.RUNNING);
        running.setNewWorkspaceMemoryId("wm-existing");
        when(memoryJobService.tryClaim("job-retry", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);
        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory existing = new WorkspaceMemory();
        existing.setId("wm-existing");
        when(workspaceMemoryDao.findOne("wm-existing")).thenReturn(existing);
        when(writerService.writePlatformMemory("/workspace/proj-1", existing)).thenReturn(true);

        executor.execute(job);

        verify(writerService).writePlatformMemory("/workspace/proj-1", existing);
        verify(memoryJobService).markSucceeded("job-retry", "wm-existing");
        verify(scannerService, never()).scan(anyString(), anyString());
        verify(workspaceMemoryService, never()).createNewVersionFromScan(anyString(), any(), any(), any());
    }

    @Test
    void execute_claimFailed_skipsExecution() {
        // WHY：tryClaim 是 CAS，失败说明被其他执行器抢占，当前执行器应直接返回。
        MemoryJob job = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        when(memoryJobService.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(null);

        executor.execute(job);

        verify(projectDao, never()).findOne(anyString());
        verify(memoryJobService, never()).markSucceeded(anyString(), anyString());
        verify(memoryJobService, never()).markFailed(anyString(), anyString(), anyString());
    }

    @Test
    void execute_projectAlreadyRunning_skipsAfterUniqueConstraintConflict() {
        MemoryJob job = pendingJob("job-unique", "proj-1", MemoryJobType.MEMORY_REBUILD);
        when(memoryJobService.tryClaim("job-unique", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenThrow(new DataIntegrityViolationException("uk_memory_job_running_project"));

        assertDoesNotThrow(() -> executor.execute(job));

        verify(projectDao, never()).findOne(anyString());
        verify(memoryJobService, never()).markFailed(anyString(), anyString(), anyString());
    }

    @Test
    void execute_scannerFailure_marksFailed() {
        // WHY：扫描异常必须被捕获并进入 MemoryJob 重试流程，不能抛回调度器导致无限异常循环。
        MemoryJob job = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        MemoryJob running = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);
        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        when(scannerService.scan("proj-1", "/workspace/proj-1"))
                .thenThrow(new IllegalStateException("scan boom"));

        executor.execute(job);

        verify(memoryJobService).markFailed(eq("job-1"), eq("scan boom"), argThat((String detail) -> detail != null && detail.contains("IllegalStateException")));
        verify(memoryJobService, never()).markSucceeded(anyString(), anyString());
    }

    @Test
    void execute_platformMemoryWriteFailure_marksFailedForRetry() {
        MemoryJob job = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        MemoryJob running = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);
        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));

        WorkspaceMemoryScanResult scan = new WorkspaceMemoryScanResult();
        when(scannerService.scan("proj-1", "/workspace/proj-1")).thenReturn(scan);
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setId("wm-1");
        OperateResultWithData<WorkspaceMemory> saved = OperateResultWithData.operationSuccessWithData(memory);
        when(workspaceMemoryService.createNewVersionFromScan(eq("proj-1"), eq(scan), any(), any()))
                .thenReturn(saved);
        when(writerService.writePlatformMemory("/workspace/proj-1", memory)).thenReturn(false);

        executor.execute(job);

        verify(memoryJobService).markFailed(eq("job-1"), contains("platform-memory"), anyString());
        verify(memoryJobService, never()).markSucceeded(anyString(), anyString());
    }

    @Test
    void execute_platformMemoryDrift_persistsFreshness() {
        MemoryJob job = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        MemoryJob running = pendingJob("job-1", "proj-1", MemoryJobType.MEMORY_INITIALIZE);
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);
        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemoryScanResult scan = new WorkspaceMemoryScanResult();
        when(scannerService.scan("proj-1", "/workspace/proj-1")).thenReturn(scan);
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setId("wm-1");
        OperateResultWithData<WorkspaceMemory> saved = OperateResultWithData.operationSuccessWithData(memory);
        when(workspaceMemoryService.createNewVersionFromScan(eq("proj-1"), eq(scan), any(), any()))
                .thenReturn(saved);
        when(writerService.writePlatformMemory("/workspace/proj-1", memory)).thenAnswer(invocation -> {
            memory.setFreshness(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT);
            return true;
        });

        executor.execute(job);

        verify(workspaceMemoryService).markFreshness(memory, WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT);
        verify(memoryJobService).markSucceeded("job-1", "wm-1");
    }

    @Test
    void execute_codingTaskUpdate_createsNewMemoryVersion() {
        // WHY：CodingTask 成功后应基于增量变更生成新 WorkspaceMemory，不阻塞主流程。
        MemoryJob job = codingTaskJob("job-ct-1", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-1", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        Project project = project("proj-1", "/workspace/proj-1");
        when(projectDao.findOne("proj-1")).thenReturn(project);

        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.CURRENT);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("proj-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        run.setWorktreePath("/workspace/proj-1");
        run.setBaseCommit("base-commit-1");
        when(runDao.findOne("run-1")).thenReturn(run);

        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/main/java/A.java"));
        when(changeCollector.collect("/workspace/proj-1", "base-commit-1")).thenReturn(changeResult);

        WorkspaceMemoryScanResult incrementalScan = new WorkspaceMemoryScanResult();
        WorkspaceMemoryScanResult assembledScan = new WorkspaceMemoryScanResult();
        assembledScan.setNormClaims(List.of());
        assembledScan.setRealityClaims(List.of());
        when(scannerService.scanIncremental("proj-1", "/workspace/proj-1", changeResult.getChangedFiles()))
                .thenReturn(incrementalScan);
        when(updateAssembler.assemble(base, changeResult, incrementalScan, task, run)).thenReturn(assembledScan);

        WorkspaceMemory newMemory = new WorkspaceMemory();
        newMemory.setId("wm-new-1");
        OperateResultWithData<WorkspaceMemory> saved = OperateResultWithData.operationSuccessWithData(newMemory);
        when(workspaceMemoryService.createNewVersionFromScan(eq("proj-1"), eq(assembledScan), any(), any()))
                .thenReturn(saved);
        when(writerService.writePlatformMemory("/workspace/proj-1", newMemory)).thenReturn(true);

        executor.execute(job);

        verify(changeCollector).collect("/workspace/proj-1", "base-commit-1");
        verify(memoryJobService).markSucceeded("job-ct-1", "wm-new-1");
        verify(writerService).writePlatformMemory("/workspace/proj-1", newMemory);
    }

    @Test
    void execute_codingTaskUpdate_baseNotCurrent_downgradesToRebuild() {
        // WHY：base WorkspaceMemory 过期意味着增量基准不可靠，必须降级为全量重建；降级投递失败必须被标记失败而不是静默成功。
        MemoryJob job = codingTaskJob("job-ct-2", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-2", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-2", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.ARCHIVED);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("proj-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        when(runDao.findOne("run-1")).thenReturn(run);
        OperateResultWithData<MemoryJob> rebuildSubmitted =
                OperateResultWithData.operationSuccessWithData(new MemoryJob());
        when(memoryJobService.submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull()))
                .thenReturn(rebuildSubmitted);

        executor.execute(job);

        verify(memoryJobService).submit(eq("proj-1"), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull());
        verify(memoryJobService).markSucceeded("job-ct-2", null);
        verify(scannerService, never()).scan(anyString(), anyString());
    }

    @Test
    void execute_codingTaskUpdate_rebuildSubmissionFailure_marksFailed() {
        // WHY：降级重建 job 投递失败时，原增量 job 必须进入失败重试，不能静默成功。
        MemoryJob job = codingTaskJob("job-ct-2b", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-2b", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-2b", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.ARCHIVED);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("proj-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        when(runDao.findOne("run-1")).thenReturn(run);
        OperateResultWithData<MemoryJob> rebuildFailed = OperateResultWithData.operationFailure("db conflict");
        when(memoryJobService.submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull()))
                .thenReturn(rebuildFailed);

        executor.execute(job);

        verify(memoryJobService).markFailed(eq("job-ct-2b"), contains("MEMORY_REBUILD"), anyString());
        verify(memoryJobService, never()).markSucceeded(eq("job-ct-2b"), any());
    }

    @Test
    void execute_codingTaskUpdate_tooManyChanges_downgradesToRebuild() {
        // WHY：大范围变更下增量更新成本与风险高于全量重建，按 §16.5 降级。
        MemoryJob job = codingTaskJob("job-ct-3", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-3", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-3", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.CURRENT);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        when(runDao.findOne("run-1")).thenReturn(run);

        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(java.util.Collections.nCopies(51, "src/main/java/X.java"));
        when(changeCollector.collect(anyString(), isNull())).thenReturn(changeResult);
        OperateResultWithData<MemoryJob> rebuildSubmitted =
                OperateResultWithData.operationSuccessWithData(new MemoryJob());
        when(memoryJobService.submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull()))
                .thenReturn(rebuildSubmitted);

        executor.execute(job);

        verify(memoryJobService).submit(eq("proj-1"), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull());
        verify(memoryJobService).markSucceeded("job-ct-3", null);
    }

    @Test
    void execute_codingTaskUpdate_collectorFailure_downgradesToRebuild() {
        // WHY：变更采集失败时不应抛异常中断调度，应降级为全量重建。
        MemoryJob job = codingTaskJob("job-ct-4", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-4", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-4", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.CURRENT);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        when(runDao.findOne("run-1")).thenReturn(run);

        when(changeCollector.collect(anyString(), isNull()))
                .thenReturn(CodingTaskChangeResult.failure("git not found"));
        OperateResultWithData<MemoryJob> rebuildSubmitted =
                OperateResultWithData.operationSuccessWithData(new MemoryJob());
        when(memoryJobService.submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull()))
                .thenReturn(rebuildSubmitted);

        executor.execute(job);

        verify(memoryJobService).submit(eq("proj-1"), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull());
        verify(memoryJobService).markSucceeded("job-ct-4", null);
    }

    @Test
    void execute_codingTaskUpdate_threeSrcMainJavaFiles_doesNotDowngrade() {
        // WHY：原 CRITICAL_PATHS 含 /main/，src/main/java 下任意三个文件就会误降级 REBUILD。
        // 移除 /main/ 后三个普通后端文件必须走增量更新，不应投递 MEMORY_REBUILD。
        MemoryJob job = codingTaskJob("job-ct-1b", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-1b", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-1b", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));

        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.CURRENT);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("proj-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        run.setWorktreePath("/workspace/proj-1");
        when(runDao.findOne("run-1")).thenReturn(run);

        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of(
                "src/main/java/A.java", "src/main/java/B.java", "src/main/java/C.java"));
        when(changeCollector.collect("/workspace/proj-1", null)).thenReturn(changeResult);

        WorkspaceMemoryScanResult incrementalScan = new WorkspaceMemoryScanResult();
        WorkspaceMemoryScanResult assembledScan = new WorkspaceMemoryScanResult();
        assembledScan.setNormClaims(List.of());
        assembledScan.setRealityClaims(List.of());
        when(scannerService.scanIncremental("proj-1", "/workspace/proj-1", changeResult.getChangedFiles()))
                .thenReturn(incrementalScan);
        when(updateAssembler.assemble(base, changeResult, incrementalScan, task, run)).thenReturn(assembledScan);

        WorkspaceMemory newMemory = new WorkspaceMemory();
        newMemory.setId("wm-new-1b");
        OperateResultWithData<WorkspaceMemory> saved = OperateResultWithData.operationSuccessWithData(newMemory);
        when(workspaceMemoryService.createNewVersionFromScan(eq("proj-1"), eq(assembledScan), any(), any()))
                .thenReturn(saved);
        when(writerService.writePlatformMemory("/workspace/proj-1", newMemory)).thenReturn(true);

        executor.execute(job);

        verify(memoryJobService, never()).submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                any(), anyString(), any(), any(), any(), any());
        verify(memoryJobService).markSucceeded("job-ct-1b", "wm-new-1b");
    }

    @Test
    void execute_codingTaskUpdate_buildEntriesThreeTimes_downgradesToRebuild() {
        // WHY：build.gradle 等 构建入口仍是真正的关键路径，命中三次应降级 REBUILD。
        MemoryJob job = codingTaskJob("job-ct-1c", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-1c", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-1c", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.CURRENT);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        when(runDao.findOne("run-1")).thenReturn(run);

        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of(
                "build.gradle", "settings.gradle", "package.json"));
        when(changeCollector.collect(anyString(), isNull())).thenReturn(changeResult);
        OperateResultWithData<MemoryJob> rebuildSubmitted =
                OperateResultWithData.operationSuccessWithData(new MemoryJob());
        when(memoryJobService.submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull()))
                .thenReturn(rebuildSubmitted);

        executor.execute(job);

        verify(memoryJobService).submit(eq("proj-1"), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull());
        verify(memoryJobService).markSucceeded("job-ct-1c", null);
    }

    @Test
    void execute_codingTaskUpdate_scannerException_downgradesToRebuild() {
        // WHY：增量扫描或组装异常时按 §16.5 必须降级为全量 REBUILD，而不是直接 markFailed 中断链路。
        MemoryJob job = codingTaskJob("job-ct-5", "proj-1", "task-1", "run-1", "wm-base-1");
        MemoryJob running = codingTaskJob("job-ct-5", "proj-1", "task-1", "run-1", "wm-base-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(memoryJobService.tryClaim("job-ct-5", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING))
                .thenReturn(running);

        when(projectDao.findOne("proj-1")).thenReturn(project("proj-1", "/workspace/proj-1"));
        WorkspaceMemory base = new WorkspaceMemory();
        base.setId("wm-base-1");
        base.setStatus(WorkspaceMemoryStatus.CURRENT);
        when(workspaceMemoryDao.findOne("wm-base-1")).thenReturn(base);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        Run run = new Run();
        run.setId("run-1");
        run.setWorktreePath("/workspace/proj-1");
        when(runDao.findOne("run-1")).thenReturn(run);

        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/main/java/A.java"));
        when(changeCollector.collect("/workspace/proj-1", null)).thenReturn(changeResult);

        when(scannerService.scanIncremental("proj-1", "/workspace/proj-1", changeResult.getChangedFiles()))
                .thenThrow(new IllegalStateException("incremental scan boom"));

        OperateResultWithData<MemoryJob> rebuildSubmitted = OperateResultWithData.operationSuccessWithData(new MemoryJob());
        when(memoryJobService.submit(anyString(), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull()))
                .thenReturn(rebuildSubmitted);

        executor.execute(job);

        verify(memoryJobService).submit(eq("proj-1"), eq(MemoryJobType.MEMORY_REBUILD),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                isNull(), eq("task-1"), eq("run-1"), isNull());
        verify(memoryJobService).markSucceeded("job-ct-5", null);
        verify(memoryJobService, never()).markFailed(eq("job-ct-5"), anyString(), anyString());
    }

    private MemoryJob pendingJob(String id, String projectId, MemoryJobType type) {
        MemoryJob job = new MemoryJob();
        job.setId(id);
        job.setProjectId(projectId);
        job.setJobType(type);
        job.setStatus(MemoryJobStatus.PENDING);
        return job;
    }

    private MemoryJob codingTaskJob(String id, String projectId, String codingTaskId, String runId, String baseMemoryId) {
        MemoryJob job = pendingJob(id, projectId, MemoryJobType.MEMORY_UPDATE_AFTER_CODING_TASK);
        job.setCodingTaskId(codingTaskId);
        job.setRunId(runId);
        job.setBaseWorkspaceMemoryId(baseMemoryId);
        return job;
    }

    private Project project(String id, String workspacePath) {
        Project p = new Project();
        p.setId(id);
        p.setWorkspacePath(workspacePath);
        return p;
    }
}
