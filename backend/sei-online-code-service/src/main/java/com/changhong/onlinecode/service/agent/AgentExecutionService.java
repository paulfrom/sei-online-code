package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.AgentInvocationContext;
import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.entity.SkillFile;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.SkillService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 统一 Agent 执行入口。
 *
 * <p>负责解析 Agent 配置、绑定项目工作区、写 Agent brief、创建 Run、调用 CLI runner、
 * 收口 Run 终态。业务服务不要直接调用 {@link CliRunnerRegistry}。</p>
 */
@Service
@AllArgsConstructor
@Slf4j
public class AgentExecutionService {

    private final AgentService agentService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final RunDao runDao;
    private final AgentRunRecorder agentRunRecorder;
    private final SkillService skillService;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final SkillMaterializer skillMaterializer;

    public CompletableFuture<AgentExecutionResult> executeAsync(String agentName, AgentExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> execute(agentName, request));
    }

    public AgentWorkspace workspace(String projectId) {
        return cliRunnerRegistry.workspace(projectId);
    }

    public void cancel(String runId) {
        cliRunnerRegistry.cancel(runId);
    }

    public AgentExecutionResult execute(String agentName, AgentExecutionRequest request) {
        Agent agent = agentService.findByName(agentName);
        if (agent == null) {
            return AgentExecutionResult.failed(null, agentName + " 不存在，无法执行。");
        }
        AgentWorkspace workspace;
        try {
            workspace = cliRunnerRegistry.workspace(request.getProjectId());
        } catch (Exception e) {
            return AgentExecutionResult.failed(null, e.getMessage());
        }

        Run run = resolveRun(agent, request);
        if (run == null) {
            return AgentExecutionResult.failed(request.getRunId(), "Run 不存在: " + request.getRunId());
        }
        try {
            materializeSkills(agent, workspace);
            AgentBriefWriter.writeBrief(workspace.pathString(), agent.getCliTool(), agent.getName(),
                    agent.getInstructions(), agent.getModel(),
                    agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank(), null);
            String prompt = composePrompt(agent, request.getPrompt());
            CompletableFuture<CliRunResult> future = cliRunnerRegistry.executeDetailed(workspace,
                    new AgentInvocationContext(run.getId(), iterationId(request), request.getCodingTaskId(),
                            agent.getId(), agent.getName(), agent.getCliTool(), agent.getModel()),
                    prompt, agent.getMcpConfig());
            CliRunResult result = future.get(timeoutSeconds(request), TimeUnit.SECONDS);
            if (result != null && Boolean.TRUE.equals(result.isProcessSucceeded())) {
                return new AgentExecutionResult(run.getId(), result.getOutput(), true, null);
            }
            String reason = result == null ? "Agent 执行无结果" : result.getFailureReason();
            return AgentExecutionResult.failed(run.getId(), reason == null ? "Agent 执行失败" : reason);
        } catch (Exception e) {
            log.warn("agent execution failed: agentName={}, runId={}", agentName, run.getId(), e);
            cliRunnerRegistry.cancel(run.getId());
            return AgentExecutionResult.failed(run.getId(), e.getMessage());
        }
    }

    public void settleRun(String runId, RunState state, String reason) {
        Run current = runDao.findOne(runId);
        if (current == null || current.getState() != RunState.RUNNING) {
            return;
        }
        current.setState(state);
        current.setTerminalReason(terminalReason(state));
        current.setFinishedDate(new Date());
        if (state == RunState.FAILED && reason != null) {
            current.setFailureReason(reason);
        }
        runDao.save(current);
    }

    private Run createRun(Agent agent, AgentExecutionRequest request) {
        AgentRunCreateCommand command = new AgentRunCreateCommand();
        command.setTaskId(request.getTaskId());
        command.setCodingTaskId(request.getCodingTaskId());
        command.setRequirementId(request.getRequirementId());
        command.setIterationId(iterationId(request));
        command.setLoopId(request.getLoopId());
        command.setTriggerSource(request.getTriggerSource() == null ? TriggerSource.AUTO : request.getTriggerSource());
        command.setUserPrompt(composePrompt(agent, request.getPrompt()));
        command.setMemoryContextId(request.getMemoryContextId());
        command.setWorkspaceMemoryId(request.getWorkspaceMemoryId());
        command.setParentRunId(request.getParentRunId());
        command.setCompensatesRunId(request.getCompensatesRunId());
        command.setAttemptNo(request.getAttemptNo());
        command.setAgentId(agent.getId());
        command.setAgentName(agent.getName());
        command.setCliTool(agent.getCliTool());
        command.setModel(agent.getModel());
        return agentRunRecorder.createAgentRun(command);
    }

    private Run resolveRun(Agent agent, AgentExecutionRequest request) {
        if (request.getRunId() == null || request.getRunId().isBlank()) {
            return createRun(agent, request);
        }
        return runDao.findOne(request.getRunId());
    }

    private String iterationId(AgentExecutionRequest request) {
        if (request.getIterationId() != null && !request.getIterationId().isBlank()) {
            return request.getIterationId();
        }
        return request.getRequirementId();
    }

    private long timeoutSeconds(AgentExecutionRequest request) {
        return request.getTimeoutSeconds() <= 0 ? 1_800L : request.getTimeoutSeconds();
    }

    private RunTerminalReason terminalReason(RunState state) {
        if (state == RunState.SUCCEEDED) {
            return RunTerminalReason.SUCCEEDED;
        }
        if (state == RunState.CANCELLED) {
            return RunTerminalReason.CANCELLED;
        }
        return RunTerminalReason.FAILED;
    }

    private String composePrompt(Agent agent, String runtimePrompt) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "Agent Prompt Template", agent.getPromptTemplate());
        appendSection(sb, "Execution Policy", agent.getExecutionPolicy());
        appendSection(sb, "Scope Policy", agent.getScopePolicy());
        appendSection(sb, "Output Schema", agent.getOutputSchema());
        appendSection(sb, "Runtime Context", runtimePrompt);
        String prompt = sb.toString().trim();
        return prompt.isBlank() ? "" : prompt;
    }

    private void appendSection(StringBuilder sb, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append("## ").append(title).append("\n").append(content.trim());
    }

    private void materializeSkills(Agent agent, AgentWorkspace workspace) {
        try {
            List<SkillMaterializer.SkillPayload> payloads = new ArrayList<>();
            if (agent.getSkillIds() != null) {
                for (String sid : agent.getSkillIds()) {
                    if (sid == null || sid.isBlank()) {
                        continue;
                    }
                    if (sid.startsWith(BuiltInSkillRegistry.PREFIX)) {
                        builtInSkillRegistry.resolve(sid).ifPresent(payloads::add);
                        continue;
                    }
                    Skill skill = skillService.findOne(sid);
                    if (skill != null) {
                        payloads.add(new SkillMaterializer.SkillPayload(
                                skill.getName(), skill.getContent(), skill.getComputedHash(), toFileRefs(skill)));
                    }
                }
            }
            skillMaterializer.materialize(workspace.pathString(), payloads);
        } catch (Exception e) {
            throw new IllegalStateException("项目工作区技能写入失败: " + workspace.pathString(), e);
        }
    }

    private List<SkillMaterializer.SkillFileRef> toFileRefs(Skill skill) {
        List<SkillMaterializer.SkillFileRef> refs = new ArrayList<>();
        if (skill.getFiles() != null) {
            for (SkillFile file : skill.getFiles()) {
                refs.add(new SkillMaterializer.SkillFileRef(file.getPath(), file.getContent()));
            }
        }
        return refs;
    }
}
