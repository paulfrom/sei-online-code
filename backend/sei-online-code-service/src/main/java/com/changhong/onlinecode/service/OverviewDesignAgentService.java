package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.OverviewDesign;
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

/**
 * OverviewDesign 代理服务。
 *
 * <p>优先通过 {@link CliRunner} 调用真实 overview-design-agent；CLI 不可用时回退到本地占位内容。</p>
 *
 * @author sei-online-code
 */
@Service
public class OverviewDesignAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverviewDesignAgentService.class);
    private static final String AGENT_NAME = "overview-design-agent";

    private final OverviewDesignDao overviewDesignDao;
    private final RequirementDao requirementDao;
    private final AgentService agentService;
    private final SkillService skillService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final SkillMaterializer skillMaterializer;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final FailureInfoSupport failureInfoSupport;

    public OverviewDesignAgentService(OverviewDesignDao overviewDesignDao,
                                      RequirementDao requirementDao,
                                      AgentService agentService,
                                      SkillService skillService,
                                      CliRunnerRegistry cliRunnerRegistry,
                                      SkillMaterializer skillMaterializer,
                                      BuiltInSkillRegistry builtInSkillRegistry,
                                      FailureInfoSupport failureInfoSupport) {
        this.overviewDesignDao = overviewDesignDao;
        this.requirementDao = requirementDao;
        this.agentService = agentService;
        this.skillService = skillService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.skillMaterializer = skillMaterializer;
        this.builtInSkillRegistry = builtInSkillRegistry;
        this.failureInfoSupport = failureInfoSupport;
    }

    /**
     * 异步生成概览设计。
     *
     * @param overviewDesignId 概览设计 ID
     * @param prompt           可选提示词
     */
    @Async
    public void spawnOverviewDesign(String overviewDesignId, String prompt) {
        OverviewDesign overview = overviewDesignDao.findOne(overviewDesignId);
        if (Objects.isNull(overview)) {
            LOGGER.warn("overview-design-agent: overview 不存在 {}", overviewDesignId);
            return;
        }
        Requirement requirement = requirementDao.findOne(overview.getRequirementId());
        if (Objects.isNull(requirement)) {
            LOGGER.warn("overview-design-agent: requirement 不存在 {}", overview.getRequirementId());
            return;
        }

        Agent agent = agentService.findByName(AGENT_NAME);
        String fullPrompt = buildOverviewPrompt(requirement, prompt);
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
                overviewDesignId, fullPrompt, workdir.toString(),
                agent == null ? null : agent.getModel(),
                agent == null ? null : agent.getMcpConfig());

        future.thenApply(result -> {
                    if (result == null || result.isBlank()) {
                        LOGGER.warn("overview-design-agent: CLI 返回空，使用 fallback overviewId={}", overviewDesignId);
                        return generatePlaceholderContent(requirement);
                    }
                    return normalizeMarkdown(result);
                })
                .thenAccept(content -> {
                    overview.setContent(content);
                    overview.setStatus(OverviewDesignStatus.DRAFT);
                    failureInfoSupport.clearOverviewDesignFailure(overview);
                    overviewDesignDao.save(overview);
                    LOGGER.info("overview-design-agent: overview {} 生成完成", overviewDesignId);
                })
                .exceptionally(e -> {
                    LOGGER.error("overview-design-agent: overview {} 生成失败", overviewDesignId, e);
                    overview.setStatus(OverviewDesignStatus.FAILED);
                    failureInfoSupport.markOverviewDesignFailure(overview,
                            "概览设计生成失败", rootMessage(e), TriggerSource.AUTO, new Date());
                    overviewDesignDao.save(overview);
                    return null;
                });
    }

    private String buildOverviewPrompt(Requirement requirement, String modifyHint) {
        String hint = modifyHint == null ? "" : modifyHint;
        return "PRD：" + requirement.getPrdContent()
                + "\n修改提示：" + hint
                + "\n请基于 PRD 输出完整的概览设计 Markdown 文档。"
                + "\n严格要求："
                + "\n1. 只输出 Markdown 正文，不要 JSON，不要 markdown 围栏，不要解释性前后缀。"
                + "\n2. 文档必须包含一个模块清单表格，列名固定为：| moduleId | moduleTitle | summary |。"
                + "\n3. 模块清单表格中的每一行代表一个后续需要单独生成详细设计的模块。"
                + "\n4. 除模块清单外，还需补充总体架构、模块职责、关键流程、接口协作、数据边界、风险与约束。";
    }

    private String generatePlaceholderContent(Requirement requirement) {
        return "# 概览设计\n\n"
                + "## 1. 设计目标\n\n"
                + "基于 PRD 将需求拆分为可独立设计和编码的模块。\n\n"
                + "## 2. 模块清单\n\n"
                + "| moduleId | moduleTitle | summary |\n"
                + "| --- | --- | --- |\n"
                + "| default-module | 默认模块 | 需要进一步补充的默认模块 |\n\n"
                + "## 3. 总体架构\n\n"
                + "待补充。\n\n"
                + "## 4. 模块职责说明\n\n"
                + "### default-module 默认模块\n\n"
                + "- 职责：待补充\n"
                + "- 边界：待补充\n"
                + "- 依赖：待补充\n\n"
                + "## 5. 关键流程\n\n"
                + "待补充。\n\n"
                + "## 6. 风险与约束\n\n"
                + "待补充。\n";
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
