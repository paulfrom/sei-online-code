package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.AgentInvocationContext;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.enums.UsageStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.entity.SkillFile;
import com.changhong.onlinecode.service.agent.AgentRunCreateCommand;
import com.changhong.onlinecode.service.agent.AgentRunRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Requirement PRD 代理服务。
 *
 * <p>优先通过工作区绑定 runner 调用真实 prd-agent；当 CLI 不可用或 agent 未配置时回退到
 * 确定性本地 fallback（backend 规则 #11）。真实 LLM 集成在 {@code ANTHROPIC_API_KEY} 等密钥配置后
 * 由 CLI runner 自动启用。{</p>
 *
 * @author sei-online-code
 */
@Service
public class RequirementAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementAgentService.class);
    private static final String PRD_AGENT_NAME = "prd-agent";
    private static final String MEMORY_REVIEW_AGENT_NAME = "memory-review-agent";
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");

    private final RequirementDao requirementDao;
    private final AgentService agentService;
    private final SkillService skillService;
    private final ProjectService projectService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final SkillMaterializer skillMaterializer;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final FailureInfoSupport failureInfoSupport;
    private final RequirementDesignContextService requirementDesignContextService;
    private final DesignContextPromptAssembler designContextPromptAssembler;
    private final RequirementCommentService requirementCommentService;
    private final AgentRunRecorder agentRunRecorder;
    private final com.changhong.onlinecode.dao.RunDao runDao;
    private final ObjectMapper objectMapper;

    public RequirementAgentService(RequirementDao requirementDao,
                                   AgentService agentService,
                                   SkillService skillService,
                                   ProjectService projectService,
                                   CliRunnerRegistry cliRunnerRegistry,
                                   SkillMaterializer skillMaterializer,
                                   BuiltInSkillRegistry builtInSkillRegistry,
                                   FailureInfoSupport failureInfoSupport,
                                   RequirementDesignContextService requirementDesignContextService,
                                   DesignContextPromptAssembler designContextPromptAssembler,
                                   RequirementCommentService requirementCommentService,
                                   AgentRunRecorder agentRunRecorder,
                                   com.changhong.onlinecode.dao.RunDao runDao,
                                   ObjectMapper objectMapper) {
        this.requirementDao = requirementDao;
        this.agentService = agentService;
        this.skillService = skillService;
        this.projectService = projectService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.skillMaterializer = skillMaterializer;
        this.builtInSkillRegistry = builtInSkillRegistry;
        this.failureInfoSupport = failureInfoSupport;
        this.requirementDesignContextService = requirementDesignContextService;
        this.designContextPromptAssembler = designContextPromptAssembler;
        this.requirementCommentService = requirementCommentService;
        this.agentRunRecorder = agentRunRecorder;
        this.runDao = runDao;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步启动 PRD 生成。
     *
     * @param requirementId 需求 ID
     * @param prompt        可选提示词
     */
    @Async
    public void spawnPrd(String requirementId, String prompt, String generationToken) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (Objects.isNull(requirement)) {
            LOGGER.warn("prd-agent: requirement 不存在 {}", requirementId);
            return;
        }
        if (!matchesGenerationToken(requirement, generationToken)) {
            LOGGER.info("prd-agent: requirement {} generation token 已变化，跳过过期执行", requirementId);
            return;
        }
        if (requirement.getStatus() != RequirementStatus.PRD_GENERATING
                && requirement.getStatus() != RequirementStatus.FAILED) {
            LOGGER.warn("prd-agent: requirement 状态不允许生成 {}", requirement.getStatus());
            return;
        }

        Agent agent = agentService.findByName(PRD_AGENT_NAME);
        Project project = projectService.findOne(requirement.getProjectId());
        RequirementDesignContext context = requirementDesignContextService.prepare(requirementId);
        String fullPrompt = buildPrdPrompt(project, requirement, prompt, context);
        AgentWorkspace workspace = cliRunnerRegistry.workspace(requirement.getProjectId());
        Path workdir = materializeSkills(agent, workspace.path());

        if (agent != null) {
            AgentBriefWriter.writeBrief(workdir.toString(), agent.getCliTool(),
                    agent.getName(), agent.getInstructions(),
                    agent.getModel(),
                    agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank(),
                    LOGGER);
        }

        Run run = agentRunRecorder.createAgentRun(buildRunCommand(
                requirement, prompt, agent, context));
        final String runId = run.getId();

        CompletableFuture<String> future = cliRunnerRegistry.executeDetailed(workspace,
                new AgentInvocationContext(runId, requirementId, null,
                        agent == null ? null : agent.getId(),
                        agent == null ? null : agent.getName(),
                        agent == null ? null : agent.getCliTool(),
                        agent == null ? null : agent.getModel()),
                fullPrompt, agent == null ? null : agent.getMcpConfig())
                .thenApply(CliRunResult::getOutput);

        future.thenApply(RequirementAgentService::normalizeMarkdown)
                .thenAccept(content -> {
                    Requirement latest = requirementDao.findOne(requirementId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        LOGGER.info("prd-agent: requirement {} 已被新一轮生成接管，丢弃过期结果", requirementId);
                        settleRun(runId, RunState.FAILED, "已被新一轮生成接管");
                        return;
                    }
                    try {
                        validatePrdContent(content);
                    } catch (RuntimeException ex) {
                        settleRun(runId, RunState.FAILED, ex.getMessage());
                        throw ex;
                    }
                    latest.setPrdContent(content);
                    latest.setStatus(RequirementStatus.PRD_REVIEW);
                    latest.setDesignContextId(context.getId());
                    latest.setMemoryValidationStatus(MemoryValidationStatus.NOT_RUN);
                    latest.setMemoryValidationResultJson(null);
                    failureInfoSupport.clearRequirementFailure(latest);
                    requirementDao.save(latest);
                    settleRun(runId, RunState.SUCCEEDED, null);
                    LOGGER.info("prd-agent: requirement {} PRD 生成完成，版本 {}，已提交异步记忆审阅",
                            requirementId, latest.getPrdVersion());
                    reviewMemory(requirementId, content, context);
                })
                .exceptionally(e -> {
                    LOGGER.error("prd-agent: requirement {} PRD 生成失败", requirementId, e);
                    settleRun(runId, RunState.FAILED, rootMessage(e));
                    Requirement latest = requirementDao.findOne(requirementId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        LOGGER.info("prd-agent: requirement {} 已被新一轮生成接管，丢弃过期失败", requirementId);
                        return null;
                    }
                    latest.setStatus(RequirementStatus.FAILED);
                    failureInfoSupport.markRequirementFailure(latest, FailureCode.AGENT_EXECUTION_FAILED,
                            FailureStage.PRD_GENERATION, "PRD 生成失败", rootMessage(e),
                            TriggerSource.AUTO, new Date());
                    requirementDao.save(latest);
                    return null;
                });
    }

    /**
     * 由 agent 异步审阅 PRD 与项目记忆之间的差异。
     *
     * <p>审阅用于提醒后续设计并形成可沉淀的记忆更新建议，不是确认门禁。任何发现项都只会
     * 形成 {@link MemoryValidationStatus#WARNING}；agent 失败也不会改变需求流程状态。</p>
     */
    @Async
    public void reviewMemory(String requirementId, String content, RequirementDesignContext context) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null || context == null || !Objects.equals(content, requirement.getPrdContent())) {
            LOGGER.info("memory-review-agent: requirement {} 内容或上下文已变化，跳过过期审阅", requirementId);
            return;
        }

        Agent agent = agentService.findByName(MEMORY_REVIEW_AGENT_NAME);
        AgentWorkspace workspace = cliRunnerRegistry.workspace(requirement.getProjectId());
        materializeSkills(agent, workspace.path());
        Run run = agentRunRecorder.createAgentRun(buildRunCommand(
                requirement, content, agent, context));
        final String runId = run.getId();
        String iterationId = requirementId + "-memory-review-" + UUID.randomUUID();
        CompletableFuture<String> future = cliRunnerRegistry.executeDetailed(workspace,
                new AgentInvocationContext(runId, iterationId, null,
                        agent == null ? null : agent.getId(),
                        agent == null ? null : agent.getName(),
                        agent == null ? null : agent.getCliTool(),
                        agent == null ? null : agent.getModel()),
                buildMemoryReviewPrompt(content, context),
                agent == null ? null : agent.getMcpConfig())
                .thenApply(CliRunResult::getOutput);

        future.thenApply(this::parseMemoryReview)
                .thenAccept(result -> {
                    settleRun(runId, RunState.SUCCEEDED, null);
                    persistMemoryReview(requirementId, content, result);
                })
                .exceptionally(e -> {
                    LOGGER.warn("memory-review-agent: requirement {} 异步审阅失败，流程不受影响",
                            requirementId, e);
                    settleRun(runId, RunState.FAILED, rootMessage(e));
                    return null;
                });
    }

    private String buildMemoryReviewPrompt(String content, RequirementDesignContext context) {
        return "你正在进行项目记忆审阅。目标是帮助项目持续更新和沉淀，而不是证明设计与既有记忆一致。"
                + "\n请对照项目记忆指出值得用户和后续 agent 在设计时考虑的差异、遗漏或新决策，并给出沉淀建议。"
                + "\n差异不是必须修复项，不得据此否决、阻断或要求重试 PRD。"
                + "\n只输出 JSON：{\"findings\":[{\"severity\":\"INFO|WARNING\","
                + "\"message\":\"差异或新增信息\",\"suggestedAction\":\"设计提醒或后续记忆沉淀建议\"}]}。"
                + "\n没有值得提醒的差异时输出 {\"findings\":[]}。"
                + "\n\n项目记忆与设计上下文：\n" + designContextPromptAssembler.assemble(context)
                + "\n\n待审阅 PRD：\n" + content;
    }

    private DesignMemoryValidationService.ValidationResult parseMemoryReview(String raw) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            DesignMemoryValidationService.ValidationResult result =
                    new DesignMemoryValidationService.ValidationResult();
            JsonNode findings = root.path("findings");
            if (findings.isArray()) {
                int count = 0;
                for (JsonNode finding : findings) {
                    if (count++ >= 20) {
                        break;
                    }
                    String message = finding.path("message").asText("").trim();
                    if (message.isEmpty()) {
                        continue;
                    }
                    String severity = "INFO".equalsIgnoreCase(finding.path("severity").asText())
                            ? "INFO" : "WARNING";
                    result.getFindings().add(new DesignMemoryValidationService.ValidationFinding(
                            severity, message, finding.path("suggestedAction").asText("").trim()));
                }
            }
            result.setStatus(result.getFindings().isEmpty()
                    ? MemoryValidationStatus.PASSED : MemoryValidationStatus.WARNING);
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("记忆审阅 agent 未返回合法 JSON", e);
        }
    }

    private void persistMemoryReview(String requirementId, String reviewedContent,
                                     DesignMemoryValidationService.ValidationResult result) {
        Requirement latest = requirementDao.findOne(requirementId);
        if (latest == null || !Objects.equals(reviewedContent, latest.getPrdContent())) {
            LOGGER.info("memory-review-agent: requirement {} 已有更新，丢弃过期审阅结果", requirementId);
            return;
        }
        String resultJson = toJson(result);
        latest.setMemoryValidationStatus(result.getStatus());
        latest.setMemoryValidationResultJson(resultJson);
        requirementDao.save(latest);
        if (!result.getFindings().isEmpty()) {
            String summary = result.getFindings().stream()
                    .map(DesignMemoryValidationService.ValidationFinding::getMessage)
                    .collect(java.util.stream.Collectors.joining("；"));
            requirementCommentService.append(
                    requirementId, latest.getActiveLoopId(), RequirementCommentAuthorType.SYSTEM,
                    "记忆审阅 agent", RequirementCommentType.VALIDATION_RESULT,
                    "以下差异仅用于提醒设计并支持后续项目记忆沉淀，不是必须校验项：" + summary,
                    resultJson);
        }
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("记忆审阅 agent 输出为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("记忆审阅 agent 输出不包含 JSON 对象");
        }
        return raw.substring(start, end + 1);
    }

    private String buildPrdPrompt(Project project, Requirement requirement, String modifyHint,
                                  RequirementDesignContext context) {
        String projectDesign = project == null ? "" : project.getDesign();
        String hint = modifyHint == null ? "" : modifyHint;
        String designContextSection = designContextPromptAssembler.assemble(context);
        return "项目描述：" + projectDesign
                + "\n需求标题：" + requirement.getTitle()
                + "\n需求描述：" + requirement.getDescription()
                + "\n修改提示：" + hint
                + "\n\n" + designContextSection
                + "\n请输出一个完整的 PRD Markdown 文档。"
                + "\n严格要求："
                + "\n1. 只输出 Markdown 正文，不要 JSON，不要 markdown 围栏，不要解释性前后缀。"
                + "\n2. 至少包含：需求概述、业务目标、范围、用户场景、功能需求、非功能需求、验收标准、风险与待确认项。"
                + "\n3. 必须包含与现有系统关系、复用/扩展/重构范围、冲突与待确认项、非目标、验收标准、规范符合性说明。"
                + "\n4. 文档内容要可直接进入评审，而不是提纲或骨架。";
    }

    private static String normalizeMarkdown(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t;
    }

    private void validatePrdContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("PRD 输出为空");
        }
        if (!MARKDOWN_HEADING.matcher(content).find()) {
            throw new IllegalArgumentException("PRD 输出缺少 Markdown 标题");
        }
        requireKeyword(content, "需求概述");
        requireKeyword(content, "业务目标");
        requireKeyword(content, "功能需求");
    }

    private static void requireKeyword(String content, String keyword) {
        if (!content.contains(keyword)) {
            throw new IllegalArgumentException("PRD 输出缺少关键章节: " + keyword);
        }
    }

    private static boolean matchesGenerationToken(Requirement requirement, String generationToken) {
        return generationToken != null && generationToken.equals(requirement.getGenerationToken());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    /**
     * 构造 AgentRunCreateCommand，写入 Agent 快照。
     */
    private AgentRunCreateCommand buildRunCommand(Requirement requirement, String prompt,
                                                  Agent agent,
                                                  RequirementDesignContext context) {
        AgentRunCreateCommand command = new AgentRunCreateCommand();
        command.setRequirementId(requirement.getId());
        command.setIterationId(requirement.getId());
        command.setTriggerSource(TriggerSource.AUTO);
        command.setUserPrompt(prompt);
        command.setMemoryContextId(context == null ? null : context.getId());
        command.setWorkspaceMemoryId(context == null ? null : context.getWorkspaceMemoryId());
        if (agent != null) {
            command.setAgentId(agent.getId());
            command.setAgentName(agent.getName());
            command.setCliTool(agent.getCliTool());
            command.setModel(agent.getModel());
        }
        return command;
    }

    /**
     * 更新 Run 终态。重新加载 Run 实体避免覆盖 usage 列。
     */
    private void settleRun(String runId, RunState state, String reason) {
        try {
            Run current = runDao.findOne(runId);
            if (current == null || current.getState() != RunState.RUNNING) {
                return;
            }
            current.setState(state);
            current.setFinishedDate(new Date());
            if (state == RunState.FAILED) {
                current.setFailureReason(reason);
            }
            runDao.save(current);
        } catch (Exception e) {
            LOGGER.warn("prd-agent: 更新 Run 终态失败 runId={}", runId, e);
        }
    }

    private String toJson(DesignMemoryValidationService.ValidationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("记忆审阅结果序列化失败", e);
        }
    }

    private Path materializeSkills(Agent agent, Path workdir) {
        try {
            List<SkillMaterializer.SkillPayload> payloads = new ArrayList<>();
            if (agent != null && agent.getSkillIds() != null) {
                for (String sid : agent.getSkillIds()) {
                    if (sid.startsWith(BuiltInSkillRegistry.PREFIX)) {
                        builtInSkillRegistry.resolve(sid).ifPresent(payloads::add);
                        continue;
                    }
                    Skill s = skillService.findOne(sid);
                    if (s != null) {
                        payloads.add(new SkillMaterializer.SkillPayload(
                                s.getName(), s.getContent(), s.getComputedHash(), toFileRefs(s)));
                    }
                }
            }
            skillMaterializer.materialize(workdir.toString(), payloads);
            return workdir;
        } catch (Exception e) {
            throw new IllegalStateException("项目工作区技能写入失败: " + workdir, e);
        }
    }

    private static List<SkillMaterializer.SkillFileRef> toFileRefs(Skill skill) {
        List<SkillMaterializer.SkillFileRef> refs = new ArrayList<>();
        if (skill.getFiles() != null) {
            for (SkillFile f : skill.getFiles()) {
                refs.add(new SkillMaterializer.SkillFileRef(f.getPath(), f.getContent()));
            }
        }
        return refs;
    }
}
