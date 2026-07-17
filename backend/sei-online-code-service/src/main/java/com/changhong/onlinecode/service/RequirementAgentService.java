package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
@AllArgsConstructor
@Slf4j
public class RequirementAgentService {

    private static final String PRD_AGENT_NAME = "prd-agent";
    private static final String MEMORY_REVIEW_AGENT_NAME = "memory-review-agent";
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");
    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private final RequirementDao requirementDao;
    private final ProjectService projectService;
    private final AgentExecutionService agentExecutionService;
    private final FailureInfoSupport failureInfoSupport;
    private final RequirementDesignContextService requirementDesignContextService;
    private final DesignContextPromptAssembler designContextPromptAssembler;
    private final RequirementCommentService requirementCommentService;
    private final RunDao runDao;


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
            log.warn("prd-agent: requirement 不存在 {}", requirementId);
            return;
        }
        if (!matchesGenerationToken(requirement, generationToken)) {
            log.info("prd-agent: requirement {} generation token 已变化，跳过过期执行", requirementId);
            return;
        }
        if (requirement.getStatus() != RequirementStatus.PRD_GENERATING
                && requirement.getStatus() != RequirementStatus.FAILED) {
            log.warn("prd-agent: requirement 状态不允许生成 {}", requirement.getStatus());
            return;
        }

        Project project = projectService.findOne(requirement.getProjectId());
        RequirementDesignContext context = requirementDesignContextService.prepare(requirementId);
        String fullPrompt = buildPrdPrompt(project, requirement, prompt, context);
        AgentExecutionRequest request = buildRequest(requirement, fullPrompt, context);

        CompletableFuture<AgentExecutionResult> future = agentExecutionService.executeAsync(PRD_AGENT_NAME, request);

        future.thenApply(result -> resultOutput(result, RequirementAgentService::normalizeMarkdown))
                .thenAccept(output -> {
                    Requirement latest = requirementDao.findOne(requirementId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        log.info("prd-agent: requirement {} 已被新一轮生成接管，丢弃过期结果", requirementId);
                        settleRun(output.runId(), RunState.FAILED, "已被新一轮生成接管");
                        return;
                    }
                    try {
                        validatePrdContent(output.content());
                    } catch (RuntimeException ex) {
                        settleRun(output.runId(), RunState.FAILED, ex.getMessage());
                        throw ex;
                    }
                    latest.setPrdContent(output.content());
                    latest.setStatus(RequirementStatus.PRD_REVIEW);
                    latest.setDesignContextId(context.getId());
                    latest.setMemoryValidationStatus(MemoryValidationStatus.NOT_RUN);
                    latest.setMemoryValidationResultJson(null);
                    failureInfoSupport.clearRequirementFailure(latest);
                    requirementDao.save(latest);
                    settleRun(output.runId(), RunState.SUCCEEDED, null);
                    log.info("prd-agent: requirement {} PRD 生成完成，版本 {}，已提交异步记忆审阅",
                            requirementId, latest.getPrdVersion());
                    reviewMemory(requirementId, output.content(), context);
                })
                .exceptionally(e -> {
                    log.error("prd-agent: requirement {} PRD 生成失败", requirementId, e);
                    Requirement latest = requirementDao.findOne(requirementId);
                    if (Objects.isNull(latest) || !matchesGenerationToken(latest, generationToken)) {
                        log.info("prd-agent: requirement {} 已被新一轮生成接管，丢弃过期失败", requirementId);
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
            log.info("memory-review-agent: requirement {} 内容或上下文已变化，跳过过期审阅", requirementId);
            return;
        }

        AgentExecutionRequest request = buildRequest(requirement, buildMemoryReviewPrompt(content, context), context);
        CompletableFuture<AgentExecutionResult> future = agentExecutionService.executeAsync(MEMORY_REVIEW_AGENT_NAME, request);

        future.thenApply(result -> resultOutput(result, this::parseMemoryReview))
                .thenAccept(output -> {
                    settleRun(output.runId(), RunState.SUCCEEDED, null);
                    persistMemoryReview(requirementId, content, output.content());
                })
                .exceptionally(e -> {
                    log.warn("memory-review-agent: requirement {} 异步审阅失败，流程不受影响: {}",
                            requirementId, rootMessage(e));
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
            JsonNode root = readMemoryReviewJson(raw);
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

    private JsonNode readMemoryReviewJson(String raw) {
        for (String candidate : jsonCandidates(raw)) {
            try {
                JsonNode root = JsonUtils.mapper().readTree(candidate);
                if (root != null && root.isObject() && root.has("findings")) {
                    return root;
                }
            } catch (Exception ignored) {
                // Try the next candidate; LLM output often contains examples before the real JSON.
            }
        }
        String extracted = extractJsonObject(raw);
        try {
            return JsonUtils.mapper().readTree(extracted);
        } catch (Exception e) {
            throw new IllegalArgumentException("记忆审阅 agent 未返回合法 JSON", e);
        }
    }

    private void persistMemoryReview(String requirementId, String reviewedContent,
                                     DesignMemoryValidationService.ValidationResult result) {
        Requirement latest = requirementDao.findOne(requirementId);
        if (latest == null || !Objects.equals(reviewedContent, latest.getPrdContent())) {
            log.info("memory-review-agent: requirement {} 已有更新，丢弃过期审阅结果", requirementId);
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
        java.util.List<String> candidates = jsonCandidates(raw);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(raw == null || raw.isBlank()
                    ? "记忆审阅 agent 输出为空"
                    : "记忆审阅 agent 输出不包含 JSON 对象");
        }
        return candidates.get(0);
    }

    private static java.util.List<String> jsonCandidates(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> candidates = new java.util.ArrayList<>();
        java.util.regex.Matcher fence = JSON_FENCE.matcher(raw);
        while (fence.find()) {
            candidates.add(fence.group(1).trim());
        }

        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    candidates.add(raw.substring(start, i + 1).trim());
                    start = -1;
                }
            }
        }
        return candidates;
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

    private AgentExecutionRequest buildRequest(Requirement requirement, String prompt,
                                               RequirementDesignContext context) {
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setProjectId(requirement.getProjectId());
        request.setRequirementId(requirement.getId());
        request.setLogStreamKey(requirement.getId());
        request.setTriggerSource(TriggerSource.AUTO);
        request.setPrompt(prompt);
        request.setMemoryContextId(context == null ? null : context.getId());
        request.setWorkspaceMemoryId(context == null ? null : context.getWorkspaceMemoryId());
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

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
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
            current.setTerminalReason(terminalReason(state, reason));
            current.setFinishedDate(new Date());
            if (state == RunState.FAILED) {
                current.setFailureReason(reason);
            }
            runDao.save(current);
        } catch (Exception e) {
            log.warn("prd-agent: 更新 Run 终态失败 runId={}", runId, e);
        }
    }

    private RunTerminalReason terminalReason(RunState state, String reason) {
        if (state == RunState.SUCCEEDED) {
            return RunTerminalReason.SUCCEEDED;
        }
        if (state == RunState.CANCELLED) {
            return RunTerminalReason.CANCELLED;
        }
        if (reason != null && reason.contains("新一轮生成接管")) {
            return RunTerminalReason.SUPERSEDED;
        }
        return RunTerminalReason.FAILED;
    }

    private String toJson(DesignMemoryValidationService.ValidationResult result) {
        try {
            return JsonUtils.mapper().writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("记忆审阅结果序列化失败", e);
        }
    }
}
