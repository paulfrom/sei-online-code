package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.agent.AgentUsage;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.UsageStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RunNumberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Agent Run 记录器。
 *
 * <p>负责在独立事务中创建 Agent Run 并写入 Agent 快照，以及定向更新 token usage。
 * 必须通过 Spring 注入调用，以保证 {@code REQUIRES_NEW} 事务传播生效。</p>
 *
 * @author sei-online-code
 */
@Component
public class AgentRunRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRunRecorder.class);

    private final RunDao runDao;
    private final RunNumberService runNumberService;
    private static final List<RunState> TERMINAL_RETRYABLE_STATES = List.of(
            RunState.FAILED, RunState.CANCELLED);

    public AgentRunRecorder(RunDao runDao, RunNumberService runNumberService) {
        this.runDao = runDao;
        this.runNumberService = runNumberService;
    }

    /**
     * 创建并提交 Agent Run，写入 Agent 快照。
     *
     * <p>在启动 CLI 前调用，确保 Run 在独立事务中提交。</p>
     *
     * @param command 创建命令
     * @return 已保存的 Run
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Run createAgentRun(AgentRunCreateCommand command) {
        Run run = new Run();
        run.setTaskId(command.getTaskId());
        run.setCodingTaskId(command.getCodingTaskId());
        run.setRequirementId(command.getRequirementId());
        run.setIterationId(command.getIterationId());
        run.setLoopId(command.getLoopId());
        run.setRunType(command.getRunType() == null ? RunType.AGENT : command.getRunType());
        bindAttemptLineage(run, command);
        run.setTriggerSource(command.getTriggerSource());
        run.setUserPrompt(command.getUserPrompt());
        run.setMemoryContextId(command.getMemoryContextId());
        run.setWorkspaceMemoryId(command.getWorkspaceMemoryId());
        run.setWorktreePath(command.getWorktreePath());
        run.setBaseCommit(command.getBaseCommit());
        run.setAgentId(command.getAgentId());
        run.setAgentName(command.getAgentName());
        run.setCliTool(command.getCliTool());
        run.setModel(command.getModel());
        run.setState(com.changhong.onlinecode.dto.enums.RunState.RUNNING);
        run.setStartedDate(new Date());
        run.setUsageStatus(UsageStatus.UNAVAILABLE);
        runNumberService.assign(run);
        Run saved = runDao.save(run);
        LOGGER.info("Agent Run 已创建: runId={}, agentName={}",
                saved.getId(), saved.getAgentName());
        return saved;
    }

    private void bindAttemptLineage(Run run, AgentRunCreateCommand command) {
        run.setParentRunId(command.getParentRunId());
        run.setCompensatesRunId(command.getCompensatesRunId());
        run.setAttemptNo(command.getAttemptNo() == null || command.getAttemptNo() < 1
                ? 1 : command.getAttemptNo());

        if (!isCompensation(command.getTriggerSource())
                || run.getParentRunId() != null
                || run.getCompensatesRunId() != null) {
            return;
        }

        Run previous = findPreviousAttempt(command);
        if (previous == null) {
            return;
        }
        run.setParentRunId(previous.getId());
        run.setCompensatesRunId(previous.getId());
        run.setAttemptNo(previous.getAttemptNo() == null ? 2 : previous.getAttemptNo() + 1);
    }

    private boolean isCompensation(TriggerSource triggerSource) {
        return triggerSource == TriggerSource.SCHEDULED_COMPENSATION
                || triggerSource == TriggerSource.CHAIN_COMPENSATION
                || triggerSource == TriggerSource.RETRY
                || triggerSource == TriggerSource.REMEDIATION;
    }

    private Run findPreviousAttempt(AgentRunCreateCommand command) {
        if (command.getCodingTaskId() != null && !command.getCodingTaskId().isBlank()) {
            return runDao.findTopByCodingTaskIdAndStateInOrderByCreatedDateDesc(
                    command.getCodingTaskId(), TERMINAL_RETRYABLE_STATES).orElse(null);
        }
        if (command.getRequirementId() != null && !command.getRequirementId().isBlank()
                && command.getLoopId() != null && !command.getLoopId().isBlank()) {
            return runDao.findTopByRequirementIdAndLoopIdAndStateInOrderByCreatedDateDesc(
                    command.getRequirementId(), command.getLoopId(), TERMINAL_RETRYABLE_STATES).orElse(null);
        }
        if (command.getRequirementId() != null && !command.getRequirementId().isBlank()) {
            return runDao.findTopByRequirementIdAndStateInOrderByCreatedDateDesc(
                    command.getRequirementId(), TERMINAL_RETRYABLE_STATES).orElse(null);
        }
        return null;
    }

    /**
     * 定向更新 usage 列，不覆盖业务终态字段。
     *
     * <p>更新行数为 0 时记录错误和监控指标，但不把 usage 落库失败转换成 Agent 输出失败。</p>
     *
     * @param runId 运行 ID
     * @param usage 归一化 usage
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateUsage(String runId, AgentUsage usage) {
        if (usage == null) {
            return;
        }
        UsageStatus status = usage.getStatus() != null ? usage.getStatus() : UsageStatus.UNAVAILABLE;
        int rows = runDao.updateUsage(
                runId,
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCacheReadTokens(),
                usage.getCacheWriteTokens(),
                usage.getTotalTokens(),
                status,
                usage.getRawUsageJson());
        if (rows == 0) {
            LOGGER.error("Agent Run usage 更新失败，未找到 runId={}", runId);
        } else {
            LOGGER.debug("Agent Run usage 已更新: runId={}, status={}", runId, status);
        }
    }
}
