package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
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
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MemoryJob 执行器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §10.6、§12、§16。
 *
 * <p>抢占 PENDING job → 按类型执行：<ul>
 *   <li>初始化/重建/刷新：补齐 agent-memory → 扫描工作区 → 创建 WorkspaceMemory → 写入 platform-memory。</li>
 *   <li>CodingTask 后回写：采集 git 变更 → 增量更新 WorkspaceMemory → 写入 platform-memory；
 *       大范围变更或 base 过期时降级为 MEMORY_REBUILD。</li>
 * </ul>
 * 异常时标记失败并触发重试；CodingTask 回写失败不影响 CodingTask 本身成功状态。</p>
 *
 * @author sei-online-code
 */
@Component
public class MemoryJobExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryJobExecutor.class);

    /**
     * CodingTask 后增量更新降级的变更文件数阈值（契约 §16.5）。
     * 通过 {@code memory.rebuild.large-change-threshold} 配置，默认 50。
     */
    private final int largeChangeThreshold;

    /**
     * 触发全量重建的关键路径片段集合。通过 {@code memory.rebuild.critical-paths} 配置。
     *
     * <p>WHY：原硬编码集合 {@code /main/} 会命中几乎所有 Java 文件（{@code src/main/java}），
     * 导致修改三个普通后端文件就误降级为 REBUILD。这里只保留真正的构建入口、路由根与迁移文件，
     * 让普通业务代码变更不触发全量重建。</p>
     */
    private final Set<String> criticalPaths;

    /**
     * 关键路径累计命中阈值，达到即降级 REBUILD。通过 {@code memory.rebuild.critical-hits-threshold} 配置。
     */
    private final int criticalHitsThreshold;

    private final MemoryJobService memoryJobService;
    private final ProjectDao projectDao;
    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final WorkspaceMemoryDao workspaceMemoryDao;
    private final AgentMemoryTemplateService agentMemoryTemplateService;
    private final WorkspaceMemoryScannerService scannerService;
    private final WorkspaceMemoryService workspaceMemoryService;
    private final PlatformMemoryWriterService platformMemoryWriterService;
    private final CodingTaskChangeCollector changeCollector;
    private final CodingTaskMemoryUpdateAssembler updateAssembler;

    public MemoryJobExecutor(MemoryJobService memoryJobService,
                             ProjectDao projectDao,
                             CodingTaskDao codingTaskDao,
                             RunDao runDao,
                             WorkspaceMemoryDao workspaceMemoryDao,
                             AgentMemoryTemplateService agentMemoryTemplateService,
                             WorkspaceMemoryScannerService scannerService,
                             WorkspaceMemoryService workspaceMemoryService,
                             PlatformMemoryWriterService platformMemoryWriterService,
                             CodingTaskChangeCollector changeCollector,
                             CodingTaskMemoryUpdateAssembler updateAssembler,
                             @Value("${memory.rebuild.large-change-threshold:50}") int largeChangeThreshold,
                             @Value("${memory.rebuild.critical-hits-threshold:3}") int criticalHitsThreshold,
                             @Value("${memory.rebuild.critical-paths:build.gradle,settings.gradle,package.json,pnpm-workspace.yaml,bootstrap,routes/,.sql}") String criticalPathsCsv) {
        this.memoryJobService = memoryJobService;
        this.projectDao = projectDao;
        this.codingTaskDao = codingTaskDao;
        this.runDao = runDao;
        this.workspaceMemoryDao = workspaceMemoryDao;
        this.agentMemoryTemplateService = agentMemoryTemplateService;
        this.scannerService = scannerService;
        this.workspaceMemoryService = workspaceMemoryService;
        this.platformMemoryWriterService = platformMemoryWriterService;
        this.changeCollector = changeCollector;
        this.updateAssembler = updateAssembler;
        this.largeChangeThreshold = largeChangeThreshold;
        this.criticalHitsThreshold = criticalHitsThreshold;
        this.criticalPaths = parseCriticalPaths(criticalPathsCsv);
    }

    /**
     * 解析关键路径配置。空值或空白片段被忽略，结果保持插入顺序去重。
     */
    private static Set<String> parseCriticalPaths(String csv) {
        Set<String> paths = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return paths;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(paths::add);
        return paths;
    }

    /**
     * 执行单个 MemoryJob。
     *
     * @param job 待执行 job
     */
    public void execute(MemoryJob job) {
        MemoryJob claimed;
        try {
            claimed = memoryJobService.tryClaim(job.getId(), MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING);
        } catch (DataIntegrityViolationException e) {
            // 多实例可能同时抢占同一项目的不同 job；数据库 RUNNING partial unique index 负责裁决。
            LOGGER.debug("memory-job: 项目已有 RUNNING job，跳过本次抢占 jobId={}, projectId={}",
                    job.getId(), job.getProjectId());
            return;
        }
        if (Objects.isNull(claimed)) {
            LOGGER.debug("memory-job: 未能抢占 jobId={}", job.getId());
            return;
        }
        try {
            if (retryCompletedOutput(claimed)) {
                return;
            }
            if (claimed.getJobType() == MemoryJobType.MEMORY_UPDATE_AFTER_CODING_TASK) {
                executeCodingTaskUpdate(claimed);
            } else {
                executeFullScan(claimed);
            }
        } catch (Exception e) {
            LOGGER.error("memory-job: 执行失败 jobId={}", claimed.getId(), e);
            memoryJobService.markFailed(claimed.getId(), e.getMessage(), stackTrace(e));
        }
    }

    /** 已生成 DB 版本的失败任务重试时，仅补写 platform-memory 镜像。 */
    private boolean retryCompletedOutput(MemoryJob job) {
        if (job.getNewWorkspaceMemoryId() == null || job.getNewWorkspaceMemoryId().isBlank()) {
            return false;
        }
        Project project = projectDao.findOne(job.getProjectId());
        WorkspaceMemory memory = workspaceMemoryDao.findOne(job.getNewWorkspaceMemoryId());
        if (project == null || memory == null) {
            return false;
        }
        String workspacePath = project.getWorkspacePath();
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalStateException("项目工作区路径为空: " + job.getProjectId());
        }
        writePlatformMemoryOrFail(job, workspacePath, memory);
        return true;
    }

    /**
     * 全量扫描链路：初始化/重建/刷新通用。
     */
    private void executeFullScan(MemoryJob job) {
        Project project = projectDao.findOne(job.getProjectId());
        if (Objects.isNull(project)) {
            throw new IllegalStateException("项目不存在: " + job.getProjectId());
        }
        String workspacePath = project.getWorkspacePath();
        if (Objects.isNull(workspacePath) || workspacePath.isBlank()) {
            throw new IllegalStateException("项目工作区路径为空: " + job.getProjectId());
        }

        // 上次执行已经创建 DB CURRENT、仅 platform-memory 写入失败时，重试只补写镜像。
        if (job.getNewWorkspaceMemoryId() != null && !job.getNewWorkspaceMemoryId().isBlank()) {
            WorkspaceMemory existing = workspaceMemoryDao.findOne(job.getNewWorkspaceMemoryId());
            if (existing != null) {
                writePlatformMemoryOrFail(job, workspacePath, existing);
                return;
            }
        }

        agentMemoryTemplateService.ensureAgentMemory(project.getId(), workspacePath);
        Integer seedVersion = agentMemoryTemplateService.resolveSeedVersion(project.getId());
        WorkspaceMemoryScanResult scan = scannerService.scan(project.getId(), workspacePath);
        OperateResultWithData<WorkspaceMemory> result = workspaceMemoryService.createNewVersionFromScan(
                project.getId(), scan, project.getMemorySeedTemplateId(), seedVersion);
        if (result.notSuccessful() || result.getData() == null) {
            throw new IllegalStateException("创建 WorkspaceMemory 失败: " + result.getMessage());
        }
        WorkspaceMemory memory = result.getData();
        memoryJobService.recordOutput(job.getId(), memory.getId());
        writePlatformMemoryOrFail(job, workspacePath, memory);
    }

    private void writePlatformMemoryOrFail(MemoryJob job, String workspacePath, WorkspaceMemory memory) {
        if (!platformMemoryWriterService.writePlatformMemory(workspacePath, memory)) {
            throw new IllegalStateException("platform-memory latest 写入失败");
        }
        if (memory.getFreshness() == WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT) {
            workspaceMemoryService.markFreshness(memory, WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT);
        }
        memoryJobService.markSucceeded(job.getId(), memory.getId());
        LOGGER.info("memory-job: 执行成功 jobId={}, newWorkspaceMemoryId={}", job.getId(), memory.getId());
    }

    /**
     * CodingTask 成功后增量回写链路。契约 §16。
     */
    private void executeCodingTaskUpdate(MemoryJob job) {
        Project project = projectDao.findOne(job.getProjectId());
        if (Objects.isNull(project)) {
            throw new IllegalStateException("项目不存在: " + job.getProjectId());
        }
        String workspacePath = project.getWorkspacePath();
        if (Objects.isNull(workspacePath) || workspacePath.isBlank()) {
            throw new IllegalStateException("项目工作区路径为空: " + job.getProjectId());
        }

        WorkspaceMemory baseMemory = findBaseWorkspaceMemory(job);
        if (baseMemory == null || baseMemory.getStatus() != WorkspaceMemoryStatus.CURRENT) {
            LOGGER.warn("memory-job: base WorkspaceMemory 不存在或已过期，降级为 REBUILD jobId={}", job.getId());
            submitRebuildAndSucceed(job);
            return;
        }

        CodingTask task = codingTaskDao.findOne(job.getCodingTaskId());
        Run run = runDao.findOne(job.getRunId());
        if (task == null || run == null) {
            throw new IllegalStateException("CodingTask 或 Run 不存在: taskId=" + job.getCodingTaskId()
                    + ", runId=" + job.getRunId());
        }

        String worktreePath = run.getWorktreePath() != null ? run.getWorktreePath() : workspacePath;
        CodingTaskChangeResult changeResult = changeCollector.collect(worktreePath, run.getBaseCommit());
        if (!changeResult.isSuccess()) {
            LOGGER.warn("memory-job: 变更采集失败，降级为 REBUILD jobId={}, reason={}",
                    job.getId(), changeResult.getFailureReason());
            submitRebuildAndSucceed(job);
            return;
        }

        if (shouldDowngradeToRebuild(changeResult)) {
            LOGGER.info("memory-job: 变更范围过大，降级为 REBUILD jobId={}, changedFiles={}",
                    job.getId(), changeResult.getChangedFiles().size());
            submitRebuildAndSucceed(job);
            return;
        }

        try {
            WorkspaceMemoryScanResult incrementalScan = scannerService.scanIncremental(
                    project.getId(), worktreePath, changeResult.getChangedFiles());
            WorkspaceMemoryScanResult newScan = updateAssembler.assemble(baseMemory, changeResult, incrementalScan, task, run);

            OperateResultWithData<WorkspaceMemory> result = workspaceMemoryService.createNewVersionFromScan(
                    project.getId(), newScan, project.getMemorySeedTemplateId(), baseMemory.getAgentMemorySeedVersion());
            if (result.notSuccessful() || result.getData() == null) {
                throw new IllegalStateException("创建 WorkspaceMemory 失败: " + result.getMessage());
            }
            WorkspaceMemory memory = result.getData();
            memoryJobService.recordOutput(job.getId(), memory.getId());
            if (!platformMemoryWriterService.writePlatformMemory(workspacePath, memory)) {
                throw new IllegalStateException("platform-memory latest 写入失败");
            }
            memoryJobService.markSucceeded(job.getId(), memory.getId());
            LOGGER.info("memory-job: CodingTask 回写成功 jobId={}, newWorkspaceMemoryId={}",
                    job.getId(), memory.getId());
        } catch (Exception e) {
            LOGGER.warn("memory-job: 增量分析或写入失败，降级为 REBUILD jobId={}", job.getId(), e);
            submitRebuildAndSucceed(job);
        }
    }

    private WorkspaceMemory findBaseWorkspaceMemory(MemoryJob job) {
        String baseId = job.getBaseWorkspaceMemoryId();
        if (baseId == null || baseId.isBlank()) {
            return workspaceMemoryService.findCurrent(job.getProjectId());
        }
        return workspaceMemoryDao.findOne(baseId);
    }

    private boolean shouldDowngradeToRebuild(CodingTaskChangeResult changeResult) {
        List<String> files = changeResult.getChangedFiles();
        if (files.size() > largeChangeThreshold) {
            return true;
        }
        long criticalHits = files.stream().filter(this::isCriticalPath).count();
        return criticalHits >= criticalHitsThreshold;
    }

    private boolean isCriticalPath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return criticalPaths.stream().anyMatch(lower::contains);
    }

    private void submitRebuildAndSucceed(MemoryJob job) {
        String idempotencyKey = job.getProjectId() + ":MEMORY_REBUILD:" + System.currentTimeMillis();
        OperateResultWithData<MemoryJob> submitted = memoryJobService.submit(job.getProjectId(), MemoryJobType.MEMORY_REBUILD,
                MemoryJobTriggerSource.CODING_TASK_SUCCEEDED, idempotencyKey,
                job.getRequirementId(), job.getCodingTaskId(), job.getRunId(), null);
        if (submitted == null || submitted.notSuccessful()) {
            throw new IllegalStateException("降级投递 MEMORY_REBUILD 失败: "
                    + (submitted == null ? "null result" : submitted.getMessage()));
        }
        memoryJobService.markSucceeded(job.getId(), null);
    }

    private String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
