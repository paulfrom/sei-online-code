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
import java.util.regex.Pattern;

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
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");

    private final RequirementDao requirementDao;
    private final AgentService agentService;
    private final SkillService skillService;
    private final ProjectService projectService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final SkillMaterializer skillMaterializer;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final FailureInfoSupport failureInfoSupport;

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

        future.thenApply(RequirementAgentService::normalizeMarkdown)
                .thenAccept(content -> {
                    Requirement latest = requirementDao.findOne(requirementId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        LOGGER.info("prd-agent: requirement {} 已被新一轮生成接管，丢弃过期结果", requirementId);
                        return;
                    }
                    validatePrdContent(content);
                    latest.setPrdContent(content);
                    latest.setStatus(RequirementStatus.PRD_REVIEW);
                    failureInfoSupport.clearRequirementFailure(latest);
                    requirementDao.save(latest);
                    LOGGER.info("prd-agent: requirement {} PRD 生成完成，版本 {}", requirementId, latest.getPrdVersion());
                })
                .exceptionally(e -> {
                    LOGGER.error("prd-agent: requirement {} PRD 生成失败", requirementId, e);
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

    private String buildPrdPrompt(Project project, Requirement requirement, String modifyHint) {
        String projectDesign = project == null ? "" : project.getDesign();
        String hint = modifyHint == null ? "" : modifyHint;
        return "项目描述：" + projectDesign
                + "\n需求标题：" + requirement.getTitle()
                + "\n需求描述：" + requirement.getDescription()
                + "\n修改提示：" + hint
                + "\n请输出一个完整的 PRD Markdown 文档。"
                + "\n严格要求："
                + "\n1. 只输出 Markdown 正文，不要 JSON，不要 markdown 围栏，不要解释性前后缀。"
                + "\n2. 至少包含：需求概述、业务目标、范围、用户场景、功能需求、非功能需求、验收标准、风险与待确认项。"
                + "\n3. 文档内容要可直接进入评审，而不是提纲或骨架。";
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
