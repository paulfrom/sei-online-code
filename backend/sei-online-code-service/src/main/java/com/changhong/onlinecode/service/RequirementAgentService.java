package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.entity.SkillFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Requirement PRD 代理服务。
 *
 * <p>优先通过 {@link CliRunner} 调用真实 prd-agent；当 CLI 不可用或 agent 未配置时回退到
 * 确定性本地 fallback（backend 规则 #11）。真实 LLM 集成在 {@code ANTHROPIC_API_KEY} 等密钥配置后
 * 由 CLI runner 自动启用。{</p>
 *
 * @author sei-online-code
 */
@Service
public class RequirementAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementAgentService.class);
    private static final String AGENT_NAME = "prd-agent";

    private final RequirementDao requirementDao;
    private final AgentService agentService;
    private final SkillService skillService;
    private final ProjectService projectService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final SkillMaterializer skillMaterializer;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final FailureInfoSupport failureInfoSupport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RequirementAgentService(RequirementDao requirementDao,
                                   AgentService agentService,
                                   SkillService skillService,
                                   ProjectService projectService,
                                   CliRunnerRegistry cliRunnerRegistry,
                                   SkillMaterializer skillMaterializer,
                                   BuiltInSkillRegistry builtInSkillRegistry,
                                   FailureInfoSupport failureInfoSupport) {
        this.requirementDao = requirementDao;
        this.agentService = agentService;
        this.skillService = skillService;
        this.projectService = projectService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.skillMaterializer = skillMaterializer;
        this.builtInSkillRegistry = builtInSkillRegistry;
        this.failureInfoSupport = failureInfoSupport;
    }

    /**
     * 异步启动 PRD 生成。
     *
     * @param requirementId 需求 ID
     * @param prompt        可选提示词
     */
    @Async
    public void spawnPrd(String requirementId, String prompt) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (Objects.isNull(requirement)) {
            LOGGER.warn("prd-agent: requirement 不存在 {}", requirementId);
            return;
        }
        if (requirement.getStatus() != RequirementStatus.PRD_GENERATING
                && requirement.getStatus() != RequirementStatus.FAILED) {
            LOGGER.warn("prd-agent: requirement 状态不允许生成 {}", requirement.getStatus());
            return;
        }

        Agent agent = agentService.findByName(AGENT_NAME);
        Project project = projectService.findOne(requirement.getProjectId());
        String fullPrompt = buildPrdPrompt(project, requirement, prompt);
        Path workdir = materializeSkills(agent);

        if (agent != null) {
            AgentBriefWriter.writeBrief(workdir.toString(), agent.getCliTool(),
                    agent.getName(), agent.getInstructions(),
                    agent.getModel(),
                    agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank(),
                    LOGGER);
        }

        CliRunner runner = cliRunnerRegistry.resolve(agent == null ? null : agent.getCliTool());
        CompletableFuture<String> future = runner.execute(
                requirementId, fullPrompt, workdir.toString(),
                agent == null ? null : agent.getModel(),
                agent == null ? null : agent.getMcpConfig());

        future.thenApply(json -> {
                    if (json == null || json.isBlank()) {
                        LOGGER.warn("prd-agent: CLI 返回空，使用 fallback requirementId={}", requirementId);
                        return generatePlaceholderPrd(requirement, prompt);
                    }
                    return extractJsonObject(json);
                })
                .thenAccept(content -> {
                    requirement.setPrdContent(content);
                    requirement.setStatus(RequirementStatus.PRD_REVIEW);
                    requirement.setLastRetryAt(new Date());
                    requirement.setRetryCount(Objects.requireNonNullElse(requirement.getRetryCount(), 0) + 1);
                    requirementDao.save(requirement);
                    LOGGER.info("prd-agent: requirement {} PRD 生成完成，版本 {}", requirementId, requirement.getPrdVersion());
                })
                .exceptionally(e -> {
                    LOGGER.error("prd-agent: requirement {} PRD 生成失败", requirementId, e);
                    requirement.setStatus(RequirementStatus.FAILED);
                    requirement.setFailureSummary("PRD 生成失败");
                    requirement.setFailureDetail(rootMessage(e));
                    requirement.setLastFailedAt(new Date());
                    requirementDao.save(requirement);
                    return null;
                });
    }

    /**
     * 确定性占位 PRD 生成器（backend 规则 #11 fallback）。
     *
     * @param requirement 需求实体
     * @param prompt      可选提示词
     * @return JSON 字符串
     */
    private String generatePlaceholderPrd(Requirement requirement, String prompt) {
        String hint = prompt == null ? "" : "，补充要求：" + prompt;
        return "{"
                + "\"title\":\"" + escapeJson(requirement.getTitle()) + "\","
                + "\"overview\":\"" + escapeJson(requirement.getDescription()) + "\","
                + "\"modules\":[],"
                + "\"hint\":\"" + escapeJson(hint) + "\""
                + "}";
    }

    private String buildPrdPrompt(Project project, Requirement requirement, String modifyHint) {
        String projectDesign = project == null ? "" : project.getDesign();
        String hint = modifyHint == null ? "" : modifyHint;
        return "项目描述：" + projectDesign
                + "\n需求标题：" + requirement.getTitle()
                + "\n需求描述：" + requirement.getDescription()
                + "\n修改提示：" + hint
                + "\n输出 PRD JSON 骨架：title/overview/modules[]{moduleId,title,features[]{featureId,title}}/hint"
                + "\n严格要求：只输出一个 JSON 对象，不要 markdown 围栏，不要任何解释文字；"
                + "title/overview 为字符串，modules/features 为数组，moduleId/featureId 为字符串";
    }

    private static String extractJsonObject(String raw) {
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
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private Path materializeSkills(Agent agent) {
        try {
            Path tmp = Files.createTempDirectory("agent-skills-");
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
            skillMaterializer.materialize(tmp.toString(), payloads);
            return tmp;
        } catch (Exception e) {
            LOGGER.warn("materializeSkills failed, fallback to tmp root", e);
            return Path.of(System.getProperty("java.io.tmpdir"));
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
