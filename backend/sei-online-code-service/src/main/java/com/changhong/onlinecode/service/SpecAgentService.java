package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.spec.SpecApiContract;
import com.changhong.onlinecode.dto.spec.SpecComponent;
import com.changhong.onlinecode.dto.spec.SpecContent;
import com.changhong.onlinecode.dto.spec.SpecEntity;
import com.changhong.onlinecode.dto.spec.SpecPage;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.entity.SkillFile;
import com.changhong.onlinecode.entity.Spec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 需求智能体 spawn 编排。复用 {@link CliRunner} + {@link SkillMaterializer}，
 * 镜像 {@link PlanAgentService} 的链式落库（GENERATING → SPEC_REVIEW / FAILED）。
 *
 * <p>D11 链式落库：{@code CliRunner.execute} 返 {@link CompletableFuture}，{@code .thenApply} 解析 JSON
 * → {@code .thenAccept} 持久化 SPEC_REVIEW+content 并推进项目到 SPEC_REVIEW，
 * {@code .exceptionally} 落 FAILED 并推进项目到 FAILED。无独立 onRunFinished 钩子。</p>
 *
 * <p>caller（{@link SpecService#refineSpec}/{@link SpecService#regenerate}）负责预置 GENERATING 行
 * 与项目入口态推进；本类只处理 latest Spec 行与异步回调里的状态收口。并发守卫归 caller。</p>
 *
 * @author sei-online-code
 */
@Service
public class SpecAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpecAgentService.class);

    private final SpecDao specDao;
    private final AgentService agentService;
    private final SkillService skillService;
    private final ProjectService projectService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final SkillMaterializer skillMaterializer;
    private final BuiltInSkillRegistry builtInSkillRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpecAgentService(SpecDao specDao, AgentService agentService, SkillService skillService,
                            ProjectService projectService, CliRunnerRegistry cliRunnerRegistry,
                            SkillMaterializer skillMaterializer, BuiltInSkillRegistry builtInSkillRegistry) {
        this.specDao = specDao;
        this.agentService = agentService;
        this.skillService = skillService;
        this.projectService = projectService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.skillMaterializer = skillMaterializer;
        this.builtInSkillRegistry = builtInSkillRegistry;
    }

    /**
     * spawn 需求智能体（latest Spec 应已由 caller 置 GENERATING）。D11 链式落库 SPEC_REVIEW/FAILED。
     *
     * @param projectId 项目 id
     * @param modifyHint 修改提示（refineSpec 首次生成为 null）
     * @param specId     待填充的 GENERATING Spec id
     */
    public void spawnRequirement(String projectId, String modifyHint, String specId) {
        Spec spec = specDao.findById(specId).orElse(null);
        if (spec == null) {
            LOGGER.warn("spawnRequirement: no Spec specId={} for projectId={}, skip", specId, projectId);
            return;
        }
        Agent agent = agentService.findByName("requirement-agent");
        Project project = projectService.findOne(projectId);
        String prompt = buildSpecPrompt(project, modifyHint);
        Path workdir = materializeSkills(agent);

        String iterationId = projectId; // 需求阶段无 Run，用 projectId 作日志键
        CliRunner runner = cliRunnerRegistry.resolve(agent == null ? null : agent.getCliTool());
        if (agent != null) {
            AgentBriefWriter.writeBrief(workdir.toString(), agent.getCliTool(),
                    agent.getName(), agent.getInstructions(),
                    agent.getModel(),
                    agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank(),
                    LOGGER);
        }
        CompletableFuture<String> future = runner.execute(iterationId, prompt, workdir.toString(),
                agent == null ? null : agent.getModel(),
                agent == null ? null : agent.getMcpConfig());
        future.thenApply(json -> {
                    // claude CLI 不可用（json==null）时走确定性 fallback（backend rule 11）。
                    if (json == null || json.isBlank()) {
                        return cannedSpec();
                    }
                    return parseJson(json, SpecContent.class);
                })
                .thenAccept(content -> {
                    spec.setPages(content.getPages());
                    spec.setComponents(content.getComponents());
                    spec.setEntities(content.getEntities());
                    spec.setApiContract(content.getApiContract());
                    spec.setState(SpecState.SPEC_REVIEW);
                    specDao.save(spec);
                    // refineSpec 路径项目停在 SPEC_REFINING，此处推进到 SPEC_REVIEW；
                    // regenerate 路径项目已在 SPEC_REVIEW，自环合法（见 SpecService 注释）。
                    projectService.transitionState(projectId, LifecycleState.SPEC_REVIEW);
                })
                .exceptionally(e -> {
                    LOGGER.error("spawnRequirement failed projectId={} specId={}", projectId, specId, e);
                    spec.setState(SpecState.FAILED);
                    specDao.save(spec);
                    projectService.transitionState(projectId, LifecycleState.FAILED);
                    return null;
                });
    }

    /**
     * claude CLI 不可用时的确定性 fallback 内容（backend rule 11）。
     * 仅本地无 claude 环境跑通链路时触发；真实环境 claude 返回有效 JSON 时不触发。
     */
    private SpecContent cannedSpec() {
        SpecContent c = new SpecContent();
        SpecPage page = new SpecPage();
        page.setKey("fallback");
        page.setTitle("fallback: claude CLI 不可用");
        page.setRoute("/fallback");
        page.setDescription("fallback 占位页");
        c.setPages(List.of(page));
        c.setComponents(List.of());
        SpecEntity entity = new SpecEntity();
        entity.setKey("Fallback");
        entity.setFields(new ArrayList<>());
        c.setEntities(List.of(entity));
        SpecApiContract api = new SpecApiContract();
        api.setMethod("GET");
        api.setPath("/api/fallback/findOne");
        api.setRequestShape("-");
        api.setResponseShape("ResultData<FallbackDto>");
        api.setDescription("fallback 占位契约");
        c.setApiContract(List.of(api));
        return c;
    }

    private <T> T parseJson(String json, Class<T> type) {
        String extracted = extractJsonObject(json);
        try {
            return objectMapper.readValue(extracted, type);
        } catch (Exception e) {
            throw new RuntimeException("parse " + type.getSimpleName() + " failed, rawHead="
                    + (json == null ? "null" : json.substring(0, Math.min(json.length(), 200))), e);
        }
    }

    /**
     * 从 LLM 输出中抽取首个 JSON 对象：剥离前言散文、markdown 围栏、尾随文字。
     * 模型即使被要求"只输出 JSON"仍可能带 "前提假设：…" 之类前言，此处兜底。
     * 无 {@code {}} 时原样返回，交由 {@code readValue} 抛出可读错误。
     */
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

    private Path materializeSkills(Agent agent) {
        try {
            Path tmp = Files.createTempDirectory("agent-skills-");
            List<SkillMaterializer.SkillPayload> payloads = new ArrayList<>();
            if (agent != null && agent.getSkillIds() != null) {
                for (String sid : agent.getSkillIds()) {
                    // builtin:<name> synthetic id → classpath registry；其余 → DB（SkillService.findOne populate files）
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

    /** Skill 辅助文件 → materializer SkillFileRef（与 PlanAgentService 一致）。 */
    private static List<SkillMaterializer.SkillFileRef> toFileRefs(Skill skill) {
        List<SkillMaterializer.SkillFileRef> refs = new ArrayList<>();
        if (skill.getFiles() != null) {
            for (SkillFile f : skill.getFiles()) {
                refs.add(new SkillMaterializer.SkillFileRef(f.getPath(), f.getContent()));
            }
        }
        return refs;
    }

    private String buildSpecPrompt(Project project, String modifyHint) {
        String desc = project == null ? "" : project.getDesign();
        String hint = modifyHint == null ? "" : modifyHint;
        return "项目描述：" + desc + "\n修改提示：" + hint
                + "\n输出 Spec JSON 骨架：pages[]{key,title,route,description}"
                + "/components[]{key,type,page,description}"
                + "/entities[]{key,fields[]{name,type,description}}"
                + "/apiContract[]{method,path,requestShape,responseShape,description}"
                + "\n严格要求：只输出一个 JSON 对象，不要 markdown 围栏，不要任何解释文字；"
                + "四字段均为数组，数组可为空 []；key/path 为字符串";
    }
}
