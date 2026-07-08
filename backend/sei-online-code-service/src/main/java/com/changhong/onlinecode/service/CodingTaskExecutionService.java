package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.dto.DetailedDesignDto;
import com.changhong.onlinecode.dto.OverviewDesignDto;
import com.changhong.sei.core.dto.ResultData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * CodingTask 执行服务。
 *
 * <p>优先通过 {@link CliRunner} 调用真实 dev-agent 执行编码；CLI 不可用时回退到本地占位执行。</p>
 *
 * @author sei-online-code
 */
@Service
public class CodingTaskExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodingTaskExecutionService.class);
    private static final String AGENT_NAME = "dev-agent";

    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final RequirementService requirementService;
    private final OverviewDesignService overviewDesignService;
    private final DetailedDesignService detailedDesignService;
    private final WorkspaceManager workspaceManager;
    private final AgentService agentService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final FailureInfoSupport failureInfoSupport;

    public CodingTaskExecutionService(CodingTaskDao codingTaskDao,
                                      RunDao runDao,
                                      RequirementService requirementService,
                                      OverviewDesignService overviewDesignService,
                                      DetailedDesignService detailedDesignService,
                                      WorkspaceManager workspaceManager,
                                      AgentService agentService,
                                      CliRunnerRegistry cliRunnerRegistry,
                                      FailureInfoSupport failureInfoSupport) {
        this.codingTaskDao = codingTaskDao;
        this.runDao = runDao;
        this.requirementService = requirementService;
        this.overviewDesignService = overviewDesignService;
        this.detailedDesignService = detailedDesignService;
        this.workspaceManager = workspaceManager;
        this.agentService = agentService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.failureInfoSupport = failureInfoSupport;
    }

    /**
     * 执行任务。
     *
     * @param id     任务 ID
     * @param prompt 用户提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> execute(String id, String prompt) {
        CodingTask task = codingTaskDao.findOne(id);
        if (Objects.isNull(task)) {
            return ResultData.fail("编码任务不存在: " + id);
        }
        if (hasActiveRun(id)) {
            return ResultData.fail("任务已有正在执行的 Run");
        }

        task.setStatus(CodingTaskStatus.RUNNING);
        codingTaskDao.save(task);

        Run run = new Run();
        run.setCodingTaskId(id);
        run.setRunNo(nextRunNo(id));
        run.setTriggerSource(prompt == null ? TriggerSource.AUTO : TriggerSource.USER_ACTION);
        run.setUserPrompt(prompt);
        run.setState(RunState.RUNNING);
        run.setStartedDate(new Date());
        runDao.save(run);

        WorkspaceResolveResult workspace = workspaceManager.resolve(task.getProjectId());
        run.setWorktreePath(workspace.getPath());
        runDao.save(run);

        Agent agent = agentService.findByName(AGENT_NAME);
        String fullPrompt = buildExecutionPrompt(task, prompt);

        if (agent != null) {
            AgentBriefWriter.writeBrief(workspace.getPath(), agent.getCliTool(),
                    agent.getName(), agent.getInstructions(),
                    agent.getModel(),
                    agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank(),
                    null);
        }

        CliRunner runner = cliRunnerRegistry.resolve(agent == null ? null : agent.getCliTool());
        CompletableFuture<String> future = runner.execute(
                task.getRequirementId(),
                task.getId(),
                run.getId(),
                fullPrompt,
                workspace.getPath(),
                agent == null ? null : agent.getModel(),
                agent == null ? null : agent.getMcpConfig());

        final Run trackedRun = run;
        future.thenAccept(result -> {
            boolean success = result != null && !result.contains("FAILED");
            finishRun(trackedRun, task, success, success ? null : "执行返回失败结果");
        }).exceptionally(e -> {
            LOGGER.error("coding-task execute failed taskId={}", id, e);
            finishRun(trackedRun, task, false, rootMessage(e));
            return null;
        });

        CodingTaskDto dto = new CodingTaskDto();
        dto.setId(task.getId());
        dto.setStatus(task.getStatus());
        return ResultData.success(dto);
    }

    private void finishRun(Run run, CodingTask task, boolean success, String failureReason) {
        Date now = new Date();
        Run persistedRun = runDao.findOne(run.getId());
        CodingTask persistedTask = codingTaskDao.findOne(task.getId());
        if (persistedRun == null || persistedTask == null) {
            LOGGER.warn("finishRun skipped because run/task disappeared. runId={}, taskId={}",
                    run.getId(), task.getId());
            return;
        }
        if (persistedRun.getState() != RunState.RUNNING || persistedTask.getStatus() != CodingTaskStatus.RUNNING) {
            LOGGER.info("finishRun skipped because run/task already settled. runId={}, runState={}, taskId={}, taskStatus={}",
                    persistedRun.getId(), persistedRun.getState(), persistedTask.getId(), persistedTask.getStatus());
            return;
        }

        persistedRun.setState(success ? RunState.SUCCEEDED : RunState.FAILED);
        persistedRun.setFinishedDate(now);
        if (success) {
            persistedRun.setFailureSummary(null);
            persistedRun.setFailureReason(null);
            failureInfoSupport.clearCodingTaskFailure(persistedTask);
        } else {
            persistedRun.setFailureSummary("编码执行失败");
            persistedRun.setFailureReason(failureReason);
            failureInfoSupport.markCodingTaskFailure(persistedTask, "编码执行失败",
                    failureReason, persistedRun.getTriggerSource(), now);
        }
        persistedTask.setStatus(success ? CodingTaskStatus.SUCCEEDED : CodingTaskStatus.FAILED);
        runDao.save(persistedRun);
        codingTaskDao.save(persistedTask);
    }

    private String buildExecutionPrompt(CodingTask task, String userPrompt) {
        Requirement requirement = requirementService.findOne(task.getRequirementId());
        OverviewDesignDto overview = overviewDesignService.findByRequirementId(task.getRequirementId());
        DetailedDesignDto detailedDesign = detailedDesignService.findOneDto(task.getDetailedDesignId());

        StringBuilder sb = new StringBuilder();
        sb.append("PRD：").append(requirement == null ? "" : requirement.getPrdContent()).append('\n');
        sb.append("概览设计：").append(overview == null ? "" : overview.getContent()).append('\n');
        sb.append("详细设计：").append(detailedDesign == null ? "" : detailedDesign.getContent()).append('\n');
        sb.append("编码任务：").append(task.getTitle()).append('\n');
        sb.append("任务描述：").append(task.getDescription()).append('\n');

        Run lastFailed = findLastFailedRun(task.getId());
        if (lastFailed != null && lastFailed.getFailureReason() != null) {
            sb.append("上一次失败原因：").append(lastFailed.getFailureReason()).append('\n');
        }
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("用户补充提示：").append(userPrompt).append('\n');
        }
        sb.append("请在已解析的工作区中按上述设计执行编码，只修改任务范围内的文件。");
        return sb.toString();
    }

    private Run findLastFailedRun(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        return runs.stream()
                .filter(r -> r.getState() == RunState.FAILED)
                .reduce((a, b) -> a.getRunNo() > b.getRunNo() ? a : b)
                .orElse(null);
    }

    private boolean hasActiveRun(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        return runs.stream().anyMatch(r -> r.getState() == RunState.RUNNING);
    }

    private Integer nextRunNo(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        return runs.stream().mapToInt(r -> Objects.requireNonNullElse(r.getRunNo(), 0)).max().orElse(0) + 1;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
