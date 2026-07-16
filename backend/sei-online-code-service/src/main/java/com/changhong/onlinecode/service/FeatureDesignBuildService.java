package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskState;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Task;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class FeatureDesignBuildService {

    private final FeatureDesignDao featureDesignDao;
    private final AgentService agentService;
    private final TaskService taskService;
    private final RunService runService;
    private final RunNumberService runNumberService;
    private final AgentExecutionService agentExecutionService;
    private final FailureInfoSupport failureInfoSupport;

    /**
     * 构建单个功能设计（P12a）。
     *
     * @param featureDesignId 功能设计 id
     * @return 构建结果，包含 runId
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<FeatureDesignBuildResultDto> build(String featureDesignId) {
        return build(featureDesignId, TriggerSource.USER_ACTION);
    }

    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<FeatureDesignBuildResultDto> build(String featureDesignId, TriggerSource triggerSource) {
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
            FeatureDesign latest = featureDesignDao.findLatestById(featureDesignId);
            if (latest != null) {
                failureInfoSupport.markFeatureDesignFailure(latest, FailureCode.BUILD_CONFLICT, FailureStage.BUILD,
                        "编码执行抢占失败", "该功能正在编码执行中", triggerSource, new java.util.Date());
                featureDesignDao.save(latest);
            }
            throw new ConflictException("该功能正在编码执行中");
        }
        fd.setLastTriggerSource(triggerSource);

        // 4. 查找 dev-agent
        Agent devAgent = agentService.findByName("dev-agent");
        if (devAgent == null) {
            // 回退构建状态
            failureInfoSupport.markFeatureDesignFailure(fd, FailureCode.UPSTREAM_MISSING, FailureStage.BUILD,
                    "编码执行失败", "dev-agent 未配置", triggerSource, new java.util.Date());
            rollbackBuildStatus(fd, FeatureDesignBuildStatus.IDLE);
            return OperateResultWithData.operationFailure("dev-agent 未配置");
        }

        // 5. 创建 Task（D8：task.featureDesignId = featureDesignId）
        Task task = new Task();
        task.setFeatureDesignId(featureDesignId);
        task.setTitle("编码实现：" + fd.getFeatureId());
        task.setDescription(buildTaskDescription(fd));
        task.setFileScope(fd.getContent() == null ? null : fd.getContent().getFileScope());
        task.setAssignedAgent(devAgent.getName());
        task.setState(TaskState.PENDING);
        task.setSeq(1);
        OperateResultWithData<Task> taskResult = taskService.save(task);
        if (!taskResult.successful()) {
            // 回退构建状态
            failureInfoSupport.markFeatureDesignFailure(fd, FailureCode.UNKNOWN, FailureStage.BUILD,
                    "编码执行失败", taskResult.getMessage(), triggerSource, new java.util.Date());
            rollbackBuildStatus(fd, FeatureDesignBuildStatus.IDLE);
            return OperateResultWithData.operationFailure(taskResult.getMessage());
        }
        Task savedTask = taskResult.getData();

        // 6. 创建 Run
        AgentWorkspace workspace = agentExecutionService.workspace(fd.getProjectId());
        Run run = new Run();
        run.setTaskId(savedTask.getId());
        run.setLogStreamKey(featureDesignId);
        run.setState(RunState.RUNNING);
        run.setAgentId(devAgent.getId());
        run.setAgentName(devAgent.getName());
        run.setCliTool(devAgent.getCliTool());
        run.setModel(devAgent.getModel());
        run.setWorktreePath(workspace.pathString());
        runNumberService.assign(run);
        OperateResultWithData<Run> runResult = runService.save(run);
        if (!runResult.successful()) {
            // 回退构建状态
            failureInfoSupport.markFeatureDesignFailure(fd, FailureCode.UNKNOWN, FailureStage.BUILD,
                    "编码执行失败", runResult.getMessage(), triggerSource, new java.util.Date());
            rollbackBuildStatus(fd, FeatureDesignBuildStatus.IDLE);
            return OperateResultWithData.operationFailure(runResult.getMessage());
        }
        Run savedRun = runResult.getData();

        // 7. 异步执行编码（D11：链式回调）——按 dev-agent.cliTool 选 runner
        String prompt = buildPrompt(fd);
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setRunId(savedRun.getId());
        request.setProjectId(fd.getProjectId());
        request.setLogStreamKey(featureDesignId);
        request.setTaskId(savedTask.getId());
        request.setPrompt(prompt);
        request.setTriggerSource(triggerSource);
        CompletableFuture<String> executeFuture = agentExecutionService.executeAsync(devAgent.getName(), request)
                .thenApply(AgentExecutionResult::output);
        executeFuture.thenAccept(result -> {
            // 解析结果，判断成功或失败
            boolean success = parseSuccess(result);
            updateBuildStatus(featureDesignId, success, triggerSource,
                    success ? null : "构建执行返回失败结果");
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
        updateBuildStatus(featureDesignId, success, TriggerSource.USER_ACTION, success ? null : "构建失败");
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBuildStatus(String featureDesignId, boolean success, TriggerSource triggerSource, String detail) {
        FeatureDesign fd = featureDesignDao.findLatestById(featureDesignId);
        if (fd == null) {
            return;
        }
        fd.setBuildStatus(success ? FeatureDesignBuildStatus.BUILT : FeatureDesignBuildStatus.BUILD_FAILED);
        if (success) {
            failureInfoSupport.clearFeatureDesignFailure(fd);
        } else {
            failureInfoSupport.markFeatureDesignFailure(fd, FailureCode.UNKNOWN, FailureStage.BUILD,
                    "编码执行失败", detail, triggerSource, new java.util.Date());
        }
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
        return buildTaskDescription(fd);
    }

    /**
     * 将已审批的功能设计转换为开发 agent 可执行的任务说明。
     */
    private String buildTaskDescription(FeatureDesign fd) {
        FeatureDesignContent content = fd.getContent();
        if (content == null) {
            return "请实现功能：" + fd.getFeatureId();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下功能设计执行编码。\n");
        sb.append("featureId: ").append(nullToEmpty(content.getFeatureId(), fd.getFeatureId())).append('\n');
        sb.append("目标: ").append(nullToEmpty(content.getGoal(), "")).append('\n');
        if (content.getDesign() != null) {
            sb.append("设计: ").append(content.getDesign()).append('\n');
        }
        if (content.getAcceptance() != null && !content.getAcceptance().isEmpty()) {
            sb.append("验收点:\n");
            for (String item : content.getAcceptance()) {
                sb.append("- ").append(item).append('\n');
            }
        }
        if (content.getFileScope() != null && !content.getFileScope().isEmpty()) {
            sb.append("文件边界:\n");
            for (String file : content.getFileScope()) {
                sb.append("- ").append(file).append('\n');
            }
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * 解析执行结果是否成功（内部辅助）。
     */
    private boolean parseSuccess(String result) {
        // TODO: 实际应解析 ClaudeRunner 返回的结果判断成功或失败
        return result != null && !result.contains("FAILED");
    }
}
