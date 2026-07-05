package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskState;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Task;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 功能设计构建服务（Task 11）。
 *
 * <p>职责：编码执行互斥（BUILDING 态抛 409 ConflictException）、创建 Task/Run、
 * 调用 ClaudeRunner 异步执行编码、回调更新 build_status。
 */
@Service
public class FeatureDesignBuildService {

    private final FeatureDesignDao featureDesignDao;
    private final AgentService agentService;
    private final TaskService taskService;
    private final RunService runService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final WorkspaceManager workspaceManager;

    public FeatureDesignBuildService(
            FeatureDesignDao featureDesignDao,
            AgentService agentService,
            TaskService taskService,
            RunService runService,
            CliRunnerRegistry cliRunnerRegistry,
            WorkspaceManager workspaceManager
    ) {
        this.featureDesignDao = featureDesignDao;
        this.agentService = agentService;
        this.taskService = taskService;
        this.runService = runService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.workspaceManager = workspaceManager;
    }

    /**
     * 构建单个功能设计（P12a）。
     *
     * @param featureDesignId 功能设计 id
     * @return 构建结果，包含 runId
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<FeatureDesignBuildResultDto> build(String featureDesignId) {
        // 1. 查找最新版 FD
        FeatureDesign fd = featureDesignDao.findLatestById(featureDesignId);
        if (fd == null) {
            return OperateResultWithData.operationFailure("功能设计不存在");
        }

        // 2. 校验状态为 CONFIRMED
        if (fd.getStatus() != FeatureDesignStatus.CONFIRMED) {
            return OperateResultWithData.operationFailure("设计未确认，不可执行编码");
        }

        // 3. 抢占构建锁
        int acquired = featureDesignDao.tryAcquireBuildLock(featureDesignId, FeatureDesignBuildStatus.BUILDING);
        if (acquired == 0) {
            throw new ConflictException("该功能正在构建中");
        }

        // 4. 查找 dev-agent
        Agent devAgent = agentService.findByName("dev-agent");
        if (devAgent == null) {
            // 回退构建状态
            rollbackBuildStatus(fd, FeatureDesignBuildStatus.IDLE);
            return OperateResultWithData.operationFailure("dev-agent 未配置");
        }

        // 5. 创建 Task（D8：task.featureDesignId = featureDesignId）
        Task task = new Task();
        task.setIterationId(featureDesignId); // 临时用 featureDesignId 作为 iterationId
        task.setFeatureDesignId(featureDesignId);
        task.setTitle("编码实现：" + fd.getFeatureId());
        task.setAssignedAgent(devAgent.getName());
        task.setState(TaskState.PENDING);
        task.setSeq(1);
        OperateResultWithData<Task> taskResult = taskService.save(task);
        if (!taskResult.successful()) {
            // 回退构建状态
            rollbackBuildStatus(fd, FeatureDesignBuildStatus.IDLE);
            return OperateResultWithData.operationFailure(taskResult.getMessage());
        }
        Task savedTask = taskResult.getData();

        // 6. 创建 Run
        WorkspaceResolveResult workspace = workspaceManager.resolve(fd.getProjectId());
        Run run = new Run();
        run.setTaskId(savedTask.getId());
        run.setIterationId(savedTask.getIterationId());
        run.setState(RunState.RUNNING);
        run.setWorktreePath(workspace.getPath());
        OperateResultWithData<Run> runResult = runService.save(run);
        if (!runResult.successful()) {
            // 回退构建状态
            rollbackBuildStatus(fd, FeatureDesignBuildStatus.IDLE);
            return OperateResultWithData.operationFailure(runResult.getMessage());
        }
        Run savedRun = runResult.getData();

        // 7. 异步执行编码（D11：链式回调）——按 dev-agent.cliTool 选 runner
        String prompt = buildPrompt(fd);
        CliRunner runner = cliRunnerRegistry.resolve(devAgent.getCliTool());
        AgentBriefWriter.writeBrief(workspace.getPath(), devAgent.getCliTool(),
                devAgent.getName(), devAgent.getInstructions(), null);
        CompletableFuture<String> executeFuture = runner.execute(
                savedTask.getIterationId(),
                savedTask.getId(),
                savedRun.getId(),
                prompt,
                workspace.getPath(),
                devAgent.getModel(),
                devAgent.getMcpConfig()
        );
        executeFuture.thenAccept(result -> {
            // 解析结果，判断成功或失败
            boolean success = parseSuccess(result);
            updateBuildStatus(featureDesignId, success);
        });

        // 8. 返回结果
        FeatureDesignBuildResultDto resultDto = new FeatureDesignBuildResultDto();
        resultDto.setRunId(savedRun.getId());
        return OperateResultWithData.operationSuccessWithData(resultDto);
    }

    /**
     * 批量构建项目下所有可构建的功能设计（P12）。
     *
     * @param projectId 项目 id
     * @return 构建结果列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<FeatureDesignBuildResultDto> buildProject(String projectId) {
        List<FeatureDesign> designs = featureDesignDao.findLatestByProjectId(projectId);
        List<FeatureDesignBuildResultDto> results = new ArrayList<>();

        for (FeatureDesign fd : designs) {
            // 过滤：status == CONFIRMED 且 build_status != BUILDING
            if (fd.getStatus() != FeatureDesignStatus.CONFIRMED
                    || fd.getBuildStatus() == FeatureDesignBuildStatus.BUILDING) {
                // 记录跳过
                FeatureDesignBuildResultDto skipped = new FeatureDesignBuildResultDto();
                // 这里我们用一个特殊标记表示跳过，实际可扩展字段
                results.add(skipped);
                continue;
            }

            // 执行构建（捕获 ConflictException 跳过）
            try {
                OperateResultWithData<FeatureDesignBuildResultDto> result = build(fd.getId());
                if (result.successful()) {
                    results.add(result.getData());
                } else {
                    FeatureDesignBuildResultDto failed = new FeatureDesignBuildResultDto();
                    results.add(failed);
                }
            } catch (ConflictException e) {
                // 跳过正在构建的
                FeatureDesignBuildResultDto skipped = new FeatureDesignBuildResultDto();
                results.add(skipped);
            }
        }

        return results;
    }

    /**
     * 更新构建状态（D11 回调）。
     *
     * @param featureDesignId 功能设计 id
     * @param success 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBuildStatus(String featureDesignId, boolean success) {
        FeatureDesign fd = featureDesignDao.findLatestById(featureDesignId);
        if (fd == null) {
            return;
        }
        fd.setBuildStatus(success ? FeatureDesignBuildStatus.BUILT : FeatureDesignBuildStatus.BUILD_FAILED);
        // 直接用 DAO save，不走 super.save，避免 validateUniqueCode
        featureDesignDao.save(fd);
    }

    /**
     * 回退构建状态（内部辅助）。
     */
    private void rollbackBuildStatus(FeatureDesign fd, FeatureDesignBuildStatus status) {
        fd.setBuildStatus(status);
        featureDesignDao.save(fd);
    }

    /**
     * 构建编码提示词（内部辅助）。
     */
    private String buildPrompt(FeatureDesign fd) {
        // TODO: 实际应从 FeatureDesignContent 构建完整提示词
        return "请实现功能：" + fd.getFeatureId();
    }

    /**
     * 解析执行结果是否成功（内部辅助）。
     */
    private boolean parseSuccess(String result) {
        // TODO: 实际应解析 ClaudeRunner 返回的结果判断成功或失败
        return result != null && !result.contains("FAILED");
    }
}
