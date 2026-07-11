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
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
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
    private final RequirementDesignContextService requirementDesignContextService;
    private final DesignContextPromptAssembler designContextPromptAssembler;
    private final DesignMemoryValidationService designMemoryValidationService;
    private final ObjectMapper objectMapper;

    public DetailedDesignAgentService(DetailedDesignDao detailedDesignDao,
                                      OverviewDesignDao overviewDesignDao,
                                      RequirementDao requirementDao,
                                      AgentService agentService,
                                      SkillService skillService,
                                      CliRunnerRegistry cliRunnerRegistry,
                                      SkillMaterializer skillMaterializer,
                                      BuiltInSkillRegistry builtInSkillRegistry,
                                      FailureInfoSupport failureInfoSupport,
                                      RequirementDesignContextService requirementDesignContextService,
                                      DesignContextPromptAssembler designContextPromptAssembler,
                                      DesignMemoryValidationService designMemoryValidationService,
                                      ObjectMapper objectMapper) {
        this.detailedDesignDao = detailedDesignDao;
        this.overviewDesignDao = overviewDesignDao;
        this.requirementDao = requirementDao;
        this.agentService = agentService;
        this.skillService = skillService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.skillMaterializer = skillMaterializer;
        this.builtInSkillRegistry = builtInSkillRegistry;
        this.failureInfoSupport = failureInfoSupport;
        this.requirementDesignContextService = requirementDesignContextService;
        this.designContextPromptAssembler = designContextPromptAssembler;
        this.designMemoryValidationService = designMemoryValidationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步生成详细设计。
     *
     * @param detailedDesignId 详细设计 ID
     * @param prompt           可选提示词
     */
    @Async
    public void spawnDetailedDesign(String detailedDesignId, String prompt, String generationToken) {
        DetailedDesign design = detailedDesignDao.findOne(detailedDesignId);
        if (Objects.isNull(design)) {
            LOGGER.warn("detailed-design-agent: design 不存在 {}", detailedDesignId);
            return;
        }
        if (!matchesGenerationToken(design, generationToken)) {
            LOGGER.info("detailed-design-agent: design {} generation token 已变化，跳过过期执行", detailedDesignId);
            return;
        }
        OverviewDesign overview = overviewDesignDao.findOne(design.getOverviewDesignId());
        Requirement requirement = requirementDao.findOne(design.getRequirementId());
        if (Objects.isNull(requirement)) {
            LOGGER.warn("detailed-design-agent: requirement 不存在 {}", design.getRequirementId());
            return;
        }

        Agent agent = agentService.findByName(AGENT_NAME);
        RequirementDesignContext context = resolveContext(requirement, overview);
        String fullPrompt = buildDetailedPrompt(requirement, overview, design, prompt, context);
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

        future.thenApply(DetailedDesignAgentService::normalizeMarkdown)
                .thenAccept(content -> {
                    DetailedDesign latest = detailedDesignDao.findOne(detailedDesignId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        LOGGER.info("detailed-design-agent: design {} 已被新一轮生成接管，丢弃过期结果",
                                detailedDesignId);
                        return;
                    }
                    validateDetailedContent(content);
                    latest.setContent(content);
                    latest.setStatus(DetailedDesignStatus.REVIEW);
                    latest.setDesignContextId(context.getId());
                    DesignMemoryValidationService.ValidationResult validation = designMemoryValidationService.validate(
                            DesignMemoryValidationService.DocumentType.DETAILED, content, context);
                    latest.setMemoryValidationStatus(validation.getStatus());
                    latest.setMemoryValidationResultJson(toJson(validation));
                    failureInfoSupport.clearDetailedDesignFailure(latest);
                    detailedDesignDao.save(latest);
                    LOGGER.info("detailed-design-agent: design {} 生成完成，校验 {}",
                            detailedDesignId, validation.getStatus());
                })
                .exceptionally(e -> {
                    LOGGER.error("detailed-design-agent: design {} 生成失败", detailedDesignId, e);
                    DetailedDesign latest = detailedDesignDao.findOne(detailedDesignId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        LOGGER.info("detailed-design-agent: design {} 已被新一轮生成接管，丢弃过期失败",
                                detailedDesignId);
                        return null;
                    }
                    latest.setStatus(DetailedDesignStatus.FAILED);
                    failureInfoSupport.markDetailedDesignFailure(latest,
                            "详细设计生成失败", rootMessage(e), TriggerSource.AUTO, new Date());
                    detailedDesignDao.save(latest);
                    return null;
                });
    }

    private String buildDetailedPrompt(Requirement requirement, OverviewDesign overview,
                                       DetailedDesign design, String modifyHint,
                                       RequirementDesignContext context) {
        String hint = modifyHint == null ? "" : modifyHint;
        String designContextSection = designContextPromptAssembler.assemble(context);
        // 注入当前模块相关 RealityClaim 切片，让详细设计只围绕本模块相关现状展开（契约 §10.8）。
        String moduleRealitySlice = designContextPromptAssembler.assembleModuleRealitySlice(
                context, design.getModuleId(), design.getModuleTitle());
        return "PRD：" + requirement.getPrdContent()
                + "\n概览设计：" + (overview == null ? "" : overview.getContent())
                + "\n当前模块：" + design.getModuleTitle() + "(" + design.getModuleId() + ")"
                + "\n修改提示：" + hint
                + "\n\n" + designContextSection
                + (moduleRealitySlice == null || moduleRealitySlice.isBlank() ? "" : "\n" + moduleRealitySlice)
                + "\n请仅围绕当前模块输出一份完整的详细设计 Markdown 文档。"
                + "\n严格要求："
                + "\n1. 只输出 Markdown 正文，不要 JSON，不要 markdown 围栏，不要解释性前后缀。"
                + "\n2. 该文档只描述当前模块，不展开其他模块。"
                + "\n3. 至少包含：模块目标、职责边界、业务流程、接口设计、数据模型、页面/组件设计、状态流转、异常处理、测试要点、编码约束。"
                + "\n4. 必须包含当前模块目标、职责边界、现有代码影响点、新增/修改文件建议、接口设计、数据模型影响、页面/组件设计、状态流转、异常处理、测试要点、兼容和回归风险。";
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

    private void validateDetailedContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("详细设计输出为空");
        }
        requireKeyword(content, "模块目标");
        requireKeyword(content, "接口设计");
        requireKeyword(content, "测试要点");
    }

    private static void requireKeyword(String content, String keyword) {
        if (!content.contains(keyword)) {
            throw new IllegalArgumentException("详细设计缺少关键章节: " + keyword);
        }
    }

    private static boolean matchesGenerationToken(DetailedDesign design, String generationToken) {
        return generationToken != null && generationToken.equals(design.getGenerationToken());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private RequirementDesignContext resolveContext(Requirement requirement, OverviewDesign overview) {
        RequirementDesignContext context = null;
        String contextId = overview == null ? null : overview.getDesignContextId();
        if (contextId == null || contextId.isBlank()) {
            contextId = requirement.getDesignContextId();
        }
        if (contextId != null && !contextId.isBlank()) {
            context = requirementDesignContextService.findCurrentByRequirement(requirement.getId());
        }
        if (isReady(context)) {
            return context;
        }
        return requirementDesignContextService.prepare(requirement.getId());
    }

    private boolean isReady(RequirementDesignContext context) {
        return context != null
                && context.getContextStatus() == RequirementDesignContextStatus.READY;
    }

    private String toJson(DesignMemoryValidationService.ValidationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            LOGGER.warn("详细设计校验结果序列化失败", e);
            return "{\"status\":\"FAILED\",\"findings\":[{\"severity\":\"HIGH\",\"message\":\"校验结果序列化失败\"}]}";
        }
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
