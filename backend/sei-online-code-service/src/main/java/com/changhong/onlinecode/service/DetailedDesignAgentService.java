package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.DetailedDesign;
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
 * DetailedDesign 代理服务。
 *
 * <p>优先通过 {@link CliRunner} 调用真实 detailed-design-agent；CLI 不可用时回退到本地占位内容。</p>
 *
 * @author sei-online-code
 */
@Service
public class DetailedDesignAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetailedDesignAgentService.class);
    private static final String AGENT_NAME = "detailed-design-agent";

    private final DetailedDesignDao detailedDesignDao;
    private final OverviewDesignDao overviewDesignDao;
    private final RequirementDao requirementDao;
    private final AgentService agentService;
    private final SkillService skillService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final SkillMaterializer skillMaterializer;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final FailureInfoSupport failureInfoSupport;

    public DetailedDesignAgentService(DetailedDesignDao detailedDesignDao,
                                      OverviewDesignDao overviewDesignDao,
                                      RequirementDao requirementDao,
                                      AgentService agentService,
                                      SkillService skillService,
                                      CliRunnerRegistry cliRunnerRegistry,
                                      SkillMaterializer skillMaterializer,
                                      BuiltInSkillRegistry builtInSkillRegistry,
                                      FailureInfoSupport failureInfoSupport) {
        this.detailedDesignDao = detailedDesignDao;
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
     * 异步生成详细设计。
     *
     * @param detailedDesignId 详细设计 ID
     * @param prompt           可选提示词
     */
    @Async
    public void spawnDetailedDesign(String detailedDesignId, String prompt) {
        DetailedDesign design = detailedDesignDao.findOne(detailedDesignId);
        if (Objects.isNull(design)) {
            LOGGER.warn("detailed-design-agent: design 不存在 {}", detailedDesignId);
            return;
        }
        OverviewDesign overview = overviewDesignDao.findOne(design.getOverviewDesignId());
        Requirement requirement = requirementDao.findOne(design.getRequirementId());
        if (Objects.isNull(requirement)) {
            LOGGER.warn("detailed-design-agent: requirement 不存在 {}", design.getRequirementId());
            return;
        }

        Agent agent = agentService.findByName(AGENT_NAME);
        String fullPrompt = buildDetailedPrompt(requirement, overview, design, prompt);
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
                detailedDesignId, fullPrompt, workdir.toString(),
                agent == null ? null : agent.getModel(),
                agent == null ? null : agent.getMcpConfig());

        future.thenApply(result -> {
                    if (result == null || result.isBlank()) {
                        LOGGER.warn("detailed-design-agent: CLI 返回空，使用 fallback designId={}", detailedDesignId);
                        return generatePlaceholderContent(design);
                    }
                    return normalizeMarkdown(result);
                })
                .thenAccept(content -> {
                    design.setContent(content);
                    design.setStatus(DetailedDesignStatus.REVIEW);
                    failureInfoSupport.clearDetailedDesignFailure(design);
                    detailedDesignDao.save(design);
                    LOGGER.info("detailed-design-agent: design {} 生成完成", detailedDesignId);
                })
                .exceptionally(e -> {
                    LOGGER.error("detailed-design-agent: design {} 生成失败", detailedDesignId, e);
                    design.setStatus(DetailedDesignStatus.FAILED);
                    failureInfoSupport.markDetailedDesignFailure(design,
                            "详细设计生成失败", rootMessage(e), TriggerSource.AUTO, new Date());
                    detailedDesignDao.save(design);
                    return null;
                });
    }

    private String buildDetailedPrompt(Requirement requirement, OverviewDesign overview,
                                       DetailedDesign design, String modifyHint) {
        String hint = modifyHint == null ? "" : modifyHint;
        return "PRD：" + requirement.getPrdContent()
                + "\n概览设计：" + (overview == null ? "" : overview.getContent())
                + "\n当前模块：" + design.getModuleTitle() + "(" + design.getModuleId() + ")"
                + "\n修改提示：" + hint
                + "\n请仅围绕当前模块输出一份完整的详细设计 Markdown 文档。"
                + "\n严格要求："
                + "\n1. 只输出 Markdown 正文，不要 JSON，不要 markdown 围栏，不要解释性前后缀。"
                + "\n2. 该文档只描述当前模块，不展开其他模块。"
                + "\n3. 至少包含：模块目标、职责边界、业务流程、接口设计、数据模型、页面/组件设计、状态流转、异常处理、测试要点、编码约束。";
    }

    private String generatePlaceholderContent(DetailedDesign design) {
        return "# 详细设计: " + design.getModuleTitle() + "\n\n"
                + "## 1. 模块目标\n\n"
                + "围绕模块 `" + design.getModuleId() + "` 的详细设计待补充。\n\n"
                + "## 2. 职责边界\n\n"
                + "- 职责：待补充\n"
                + "- 边界：待补充\n\n"
                + "## 3. 业务流程\n\n"
                + "待补充。\n\n"
                + "## 4. 接口设计\n\n"
                + "待补充。\n\n"
                + "## 5. 数据模型\n\n"
                + "待补充。\n\n"
                + "## 6. 页面与组件设计\n\n"
                + "待补充。\n\n"
                + "## 7. 测试要点\n\n"
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
