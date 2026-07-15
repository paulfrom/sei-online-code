package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.spec.SpecApiContract;
import com.changhong.onlinecode.dto.spec.SpecContent;
import com.changhong.onlinecode.dto.spec.SpecEntity;
import com.changhong.onlinecode.dto.spec.SpecPage;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.sei.core.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 需求智能体 spawn 编排。Agent 执行统一委托 {@link AgentExecutionService}，
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
@AllArgsConstructor
@Slf4j
public class SpecAgentService {

    private final SpecDao specDao;
    private final ProjectLifecycleService projectLifecycleService;
    private final AgentExecutionService agentExecutionService;
    private final FailureInfoSupport failureInfoSupport;
    private final RunDao runDao;

    /**
     * spawn 需求智能体（latest Spec 应已由 caller 置 GENERATING）。D11 链式落库 SPEC_REVIEW/FAILED。
     *
     * @param projectId 项目 id
     * @param modifyHint 修改提示（refineSpec 首次生成为 null）
     * @param specId     待填充的 GENERATING Spec id
     */
    public void spawnRequirement(String projectId, String modifyHint, String specId) {
        spawnRequirement(projectId, modifyHint, specId, TriggerSource.USER_ACTION);
    }

    public void spawnRequirement(String projectId, String modifyHint, String specId, TriggerSource triggerSource) {
        Spec spec = specDao.findById(specId).orElse(null);
        if (spec == null) {
            log.warn("spawnRequirement: no Spec specId={} for projectId={}, skip", specId, projectId);
            return;
        }
        spec.setLastTriggerSource(triggerSource);
        Project project = projectLifecycleService.findById(projectId);
        String prompt = buildSpecPrompt(project, spec, modifyHint);

        String iterationId = projectId; // 需求阶段用 projectId 作日志键
        CompletableFuture<AgentExecutionResult> future = agentExecutionService.executeAsync("requirement-agent",
                buildSpecRequest(projectId, iterationId, prompt, triggerSource));
        future.thenApply(result -> resultOutput(result, json -> {
                    // claude CLI 不可用（json==null）时走确定性 fallback（backend rule 11）。
                    if (json == null || json.isBlank()) {
                        return cannedSpec();
                    }
                    return parseJson(json, SpecContent.class);
                }))
                .thenAccept(output -> {
                    spec.setPages(output.content().getPages());
                    spec.setComponents(output.content().getComponents());
                    spec.setEntities(output.content().getEntities());
                    spec.setApiContract(output.content().getApiContract());
                    spec.setState(SpecState.SPEC_REVIEW);
                    failureInfoSupport.clearSpecFailure(spec);
                    specDao.save(spec);
                    settleRun(output.runId(), RunState.SUCCEEDED, null);
                    // refineSpec 路径项目停在 SPEC_REFINING，此处推进到 SPEC_REVIEW；
                    // regenerate 路径项目已在 SPEC_REVIEW，自环合法（见 SpecService 注释）。
                    projectLifecycleService.transitionState(projectId, LifecycleState.SPEC_REVIEW);
                })
                .exceptionally(e -> {
                    log.error("spawnRequirement failed projectId={} specId={}", projectId, specId, e);
                    spec.setState(SpecState.FAILED);
                    failureInfoSupport.markSpecFailure(spec,
                            FailureCode.AGENT_JSON_PARSE_FAILED,
                            FailureStage.SPEC,
                            "详细设计生成失败",
                            rootMessage(e),
                            triggerSource,
                            new java.util.Date());
                    specDao.save(spec);
                    projectLifecycleService.transitionState(projectId, LifecycleState.FAILED);
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
            return JsonUtils.mapper().readValue(extracted, type);
        } catch (Exception e) {
            String repaired = repairTruncatedJson(extracted);
            if (!repaired.equals(extracted)) {
                try {
                    return JsonUtils.mapper().readValue(repaired, type);
                } catch (Exception retry) {
                    e.addSuppressed(retry);
                }
            }
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

    /**
     * 仅修复“JSON 主体完整，但尾部缺少若干闭合符”的截断场景。
     * 不猜测缺失字段值；若末尾停在字符串/冒号等不完整 token，则保持原样交由上层失败。
     */
    private static String repairTruncatedJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        Deque<Character> closers = new ArrayDeque<>();
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                closers.push('}');
            } else if (c == '[') {
                closers.push(']');
            } else if ((c == '}' || c == ']') && !closers.isEmpty() && closers.peek() == c) {
                closers.pop();
            }
        }
        if (inString || closers.isEmpty()) {
            return trimmed;
        }
        int end = trimmed.length() - 1;
        while (end >= 0 && Character.isWhitespace(trimmed.charAt(end))) {
            end--;
        }
        if (end < 0) {
            return trimmed;
        }
        char last = trimmed.charAt(end);
        if (last == ':' || last == '{' || last == '[') {
            return trimmed;
        }
        StringBuilder repaired = new StringBuilder(trimmed.substring(0, end + 1));
        while (repaired.length() > 0) {
            char tail = repaired.charAt(repaired.length() - 1);
            if (Character.isWhitespace(tail)) {
                repaired.deleteCharAt(repaired.length() - 1);
                continue;
            }
            if (tail == ',') {
                repaired.deleteCharAt(repaired.length() - 1);
                continue;
            }
            break;
        }
        while (!closers.isEmpty()) {
            repaired.append(closers.pop());
        }
        return repaired.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private AgentExecutionRequest buildSpecRequest(String projectId, String iterationId, String prompt,
                                                   TriggerSource triggerSource) {
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setProjectId(projectId);
        request.setIterationId(iterationId);
        request.setTriggerSource(triggerSource == null ? TriggerSource.AUTO : triggerSource);
        request.setPrompt(prompt);
        return request;
    }

    private <T> AgentOutput<T> resultOutput(AgentExecutionResult result, Function<String, T> mapper) {
        String runId = result == null ? null : result.runId();
        if (result == null || !result.succeeded()) {
            String reason = result == null ? "Agent 执行无结果" : result.failureReason();
            settleRun(runId, RunState.FAILED, reason);
            throw new IllegalStateException(reason);
        }
        try {
            return new AgentOutput<>(runId, mapper.apply(result.output()));
        } catch (RuntimeException e) {
            settleRun(runId, RunState.FAILED, e.getMessage());
            throw e;
        }
    }

    private record AgentOutput<T>(String runId, T content) {
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
            current.setTerminalReason(state == RunState.SUCCEEDED
                    ? RunTerminalReason.SUCCEEDED : RunTerminalReason.FAILED);
            current.setFinishedDate(new java.util.Date());
            if (state == RunState.FAILED) {
                current.setFailureReason(reason);
            }
            runDao.save(current);
        } catch (Exception e) {
            log.warn("spec-agent: 更新 Run 终态失败 runId={}", runId, e);
        }
    }

    private String buildSpecPrompt(Project project, Spec spec, String modifyHint) {
        String desc = project == null ? "" : project.getDesign();
        String hint = modifyHint == null ? "" : modifyHint;
        String module = spec == null ? "" : "\n模块 id：" + nullToEmpty(spec.getModuleId())
                + "\n模块标题：" + nullToEmpty(spec.getModuleTitle())
                + "\n模块概要：" + nullToEmpty(spec.getModuleSummary());
        return "项目描述：" + desc + module + "\n修改提示：" + hint
                + "\n输出模块详细设计 JSON 骨架：pages[]{key,title,route,description}"
                + "/components[]{key,type,page,description}"
                + "/entities[]{key,fields[]{name,type,description}}"
                + "/apiContract[]{method,path,requestShape,responseShape,description}"
                + "\n要求：只围绕上述模块生成详细设计，不要展开其他模块。"
                + "\n严格要求：只输出一个 JSON 对象，不要 markdown 围栏，不要任何解释文字；"
                + "四字段均为数组，数组可为空 []；key/path 为字符串";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
