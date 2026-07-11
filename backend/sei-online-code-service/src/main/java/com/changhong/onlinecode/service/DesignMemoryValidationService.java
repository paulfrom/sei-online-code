package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryNormClaim;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.onlinecode.service.memory.WorkspaceNorms;
import com.changhong.onlinecode.service.memory.WorkspaceSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 设计记忆校验服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §10.9、§15.4。
 *
 * <p>WHY：原实现各项 check 多为占位式启发——forbidden choices 只识 Vue/antd、复用模块只判 JSON 是否为空、
 * source-backed impact 只判含不含斜杠、high severity 冲突用 summary 前 40 字符做 substring 匹配。
 * 这会让校验既易误报也易漏报，无法真正约束生成文档遵循项目记忆。本版本改为解析
 * {@link WorkspaceMemory} 中的结构化 NormClaim/RealityClaim/Snapshot，做键级比对。</p>
 *
 * <p>第一版校验项：必填章节、RequirementDesignContext 引用、forbidden choices、
 * high severity 冲突遗漏、复用不存在模块/接口、source-backed 影响点。</p>
 *
 * @author sei-online-code
 */
@Service
public class DesignMemoryValidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DesignMemoryValidationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 文档类型。 */
    public enum DocumentType {
        PRD,
        OVERVIEW,
        DETAILED,
        CODING_TASK
    }

    /**
     * 校验结果。
     */
    public static class ValidationResult {
        private MemoryValidationStatus status;
        private final List<ValidationFinding> findings = new ArrayList<>();

        public MemoryValidationStatus getStatus() { return status; }
        public void setStatus(MemoryValidationStatus status) { this.status = status; }
        public List<ValidationFinding> getFindings() { return findings; }
    }

    /**
     * 校验发现项。
     */
    public static class ValidationFinding {
        private String severity;
        private String message;
        private String suggestedAction;

        public ValidationFinding(String severity, String message, String suggestedAction) {
            this.severity = severity;
            this.message = message;
            this.suggestedAction = suggestedAction;
        }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSuggestedAction() { return suggestedAction; }
        public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    }

    private final WorkspaceMemoryDao workspaceMemoryDao;

    public DesignMemoryValidationService(WorkspaceMemoryDao workspaceMemoryDao) {
        this.workspaceMemoryDao = workspaceMemoryDao;
    }

    /**
     * 校验生成后的文档。
     *
     * @param documentType 文档类型
     * @param content      文档正文
     * @param context      设计上下文
     * @return 校验结果
     */
    public ValidationResult validate(DocumentType documentType, String content, RequirementDesignContext context) {
        ValidationResult result = new ValidationResult();
        if (content == null || content.isBlank()) {
            result.getFindings().add(new ValidationFinding("HIGH", "文档内容为空", "重新生成文档"));
            result.setStatus(MemoryValidationStatus.FAILED);
            return result;
        }
        String lower = content.toLowerCase(Locale.ROOT);

        // 反查结构化记忆：用 NormClaim 中的 forbidden choices、RealityClaim 的 source、snapshot 的模块/路径。
        WorkspaceMemory memory = resolveWorkspaceMemory(context);
        WorkspaceNorms norms = readJson(memory == null ? null : memory.getWorkspaceNormsJson(), WorkspaceNorms.class);
        List<MemoryRealityClaim> realities = readJsonList(
                memory == null ? null : memory.getRealityClaimsJson(), MemoryRealityClaim.class);
        WorkspaceSnapshot snapshot = readJson(memory == null ? null : memory.getWorkspaceSnapshotJson(),
                WorkspaceSnapshot.class);

        checkRequiredSections(documentType, lower, result);
        checkContextReference(lower, context, result);
        checkForbiddenChoices(lower, norms, result);
        checkHighSeverityConflicts(lower, context, result);
        checkReusedModules(lower, snapshot, realities, result);
        checkSourceBackedImpact(lower, realities, result);

        if (result.getFindings().stream().anyMatch(f -> "HIGH".equals(f.getSeverity()))) {
            result.setStatus(MemoryValidationStatus.FAILED);
        } else if (result.getFindings().stream().anyMatch(f -> "WARNING".equals(f.getSeverity()))) {
            result.setStatus(MemoryValidationStatus.WARNING);
        } else {
            result.setStatus(MemoryValidationStatus.PASSED);
        }
        return result;
    }

    private WorkspaceMemory resolveWorkspaceMemory(RequirementDesignContext context) {
        if (context == null || context.getWorkspaceMemoryId() == null || context.getWorkspaceMemoryId().isBlank()) {
            return null;
        }
        try {
            return workspaceMemoryDao.findOne(context.getWorkspaceMemoryId());
        } catch (Exception e) {
            LOGGER.warn("校验服务反查 WorkspaceMemory 失败 id={}", context.getWorkspaceMemoryId(), e);
            return null;
        }
    }

    private void checkRequiredSections(DocumentType type, String lower, ValidationResult result) {
        switch (type) {
            case PRD -> {
                requireSection(lower, result, "与现有系统关系", "HIGH");
                requireSection(lower, result, "复用/扩展/重构范围", "HIGH");
                requireSection(lower, result, "冲突与待确认项", "HIGH");
                requireSection(lower, result, "非目标", "WARNING");
                requireSection(lower, result, "验收标准", "HIGH");
                requireSection(lower, result, "规范符合性", "WARNING");
            }
            case OVERVIEW -> {
                requireSection(lower, result, "模块清单", "HIGH");
                requireSection(lower, result, "模块与现有代码映射", "HIGH");
                requireSection(lower, result, "新增/复用/调整模块", "HIGH");
                requireSection(lower, result, "架构影响", "WARNING");
                requireSection(lower, result, "接口/页面/数据影响范围", "WARNING");
                requireSection(lower, result, "风险与待确认", "WARNING");
            }
            case DETAILED -> {
                requireSection(lower, result, "当前模块目标", "HIGH");
                requireSection(lower, result, "职责边界", "HIGH");
                requireSection(lower, result, "现有代码影响点", "HIGH");
                requireSection(lower, result, "新增/修改文件建议", "HIGH");
                requireSection(lower, result, "接口设计", "WARNING");
                requireSection(lower, result, "数据模型影响", "WARNING");
                requireSection(lower, result, "测试要点", "WARNING");
            }
            case CODING_TASK -> {
                // CodingTask 文档较自由，仅检查冲突引用
            }
        }
    }

    private void requireSection(String lower, ValidationResult result, String keyword, String severity) {
        if (!lower.contains(keyword.toLowerCase(Locale.ROOT))) {
            result.getFindings().add(new ValidationFinding(severity,
                    "缺少必填章节或关键词：" + keyword,
                    "在文档中补充 " + keyword + " 章节"));
        }
    }

    private void checkContextReference(String lower, RequirementDesignContext context, ValidationResult result) {
        if (context == null) {
            result.getFindings().add(new ValidationFinding("HIGH",
                    "文档未关联 RequirementDesignContext", "重新生成以绑定上下文"));
            return;
        }
        if (!lower.contains(context.getId().toLowerCase(Locale.ROOT))
                && !lower.contains("design basis")
                && !lower.contains("设计依据")) {
            result.getFindings().add(new ValidationFinding("WARNING",
                    "文档未显式引用设计上下文或 DesignBasis",
                    "在文档中说明依据的设计上下文"));
        }
    }

    /**
     * 检查文档是否使用了被结构化标记为 forbidden 的选择。
     *
     * <p>WHY：原实现硬编码识别 Vue/antd，无法识别结构化 NormClaim 中的任意禁止项。
     * 改为遍历 {@link WorkspaceNorms#getForbiddenChoices()} 每条 claim，从其 content 提取关键 token
     * （非中文章节标题外的英文/技术词），命中文档即报 HIGH。</p>
     */
    private void checkForbiddenChoices(String lower, WorkspaceNorms norms, ValidationResult result) {
        if (norms == null || norms.getForbiddenChoices() == null || norms.getForbiddenChoices().isEmpty()) {
            return;
        }
        for (MemoryNormClaim forbidden : norms.getForbiddenChoices()) {
            if (forbidden == null || forbidden.getContent() == null) {
                continue;
            }
            for (String token : extractForbiddenTokens(forbidden.getContent())) {
                if (lower.contains(token)) {
                    result.getFindings().add(new ValidationFinding("HIGH",
                            "文档使用了 Forbidden Choice：" + token,
                            "改用项目规定技术栈或澄清例外"));
                    break; // 同一 forbidden claim 只报一次
                }
            }
        }
    }

    /**
     * 从 forbidden claim content 中提取可作为命中键的 token：长度 &gt; 1 的英文/技术词。
     * 中文章节标题（如“禁止使用”）不作为命中键，避免把“禁止”本身误判为命中。
     */
    private List<String> extractForbiddenTokens(String content) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("[A-Za-z][A-Za-z0-9._-]+").matcher(content);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * 检查文档是否遗漏 high severity 冲突。
     *
     * <p>WHY：原实现用 summary 前 40 字符做 substring 匹配，summary 措辞微调即漏判。
     * 改为以冲突引用键（conflict id 或 normClaimIds）为准：文档需显式提及任一引用键，
     * 否则视为遗漏。引用键由生成方在文档中以 `conflict-xxx` 或 `norm-xxx` 形式写入。</p>
     */
    private void checkHighSeverityConflicts(String lower, RequirementDesignContext context, ValidationResult result) {
        if (context == null) {
            return;
        }
        List<MemoryConflictFinding> conflicts = readConflictReport(context.getRequirementConflictReportJson());
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }
        for (MemoryConflictFinding conflict : conflicts) {
            if (!"HIGH".equalsIgnoreCase(conflict.getSeverity())) {
                continue;
            }
            if (!referencesConflict(lower, conflict)) {
                result.getFindings().add(new ValidationFinding("HIGH",
                        "遗漏 high severity 冲突：" + conflict.getSummary(),
                        "在文档中显式处理或以 " + conflict.getId() + " 标注确认该冲突"));
            }
        }
    }

    private boolean referencesConflict(String lower, MemoryConflictFinding conflict) {
        if (conflict.getId() != null && lower.contains(conflict.getId().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (conflict.getNormClaimIds() != null) {
            for (String normId : conflict.getNormClaimIds()) {
                if (normId != null && lower.contains(normId.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查声称复用的模块是否真实存在于代码现状。
     *
     * <p>WHY：原实现只判上下文 JSON 是否为空，无法识别“复用了不存在的模块”。
     * 改为：解析文档中“复用 X”短语提取被复用项 X，与 snapshot modules、reality sources、
     * apiSurface 对照，X 未命中任一即 WARNING。</p>
     */
    private void checkReusedModules(String lower, WorkspaceSnapshot snapshot,
                                      List<MemoryRealityClaim> realities, ValidationResult result) {
        if (!lower.contains("复用")) {
            return;
        }
        Set<String> existing = collectExistingModuleKeys(snapshot, realities);
        if (existing.isEmpty()) {
            // 无任何模块/路径可对照时不误判（上下文本身空时不报）
            return;
        }
        for (String reused : extractReusedTargets(lower)) {
            // 命中放行规则：被复用项作为子串出现在任一真实模块/路径中（支持只写类名、写全路径两种情形）。
            boolean found = existing.stream().anyMatch(key -> key.contains(reused) || reused.contains(key));
            if (!found) {
                result.getFindings().add(new ValidationFinding("WARNING",
                        "声称复用但在代码现状中未找到依据：" + reused,
                        "补充复用依据或确认该模块为新增"));
            }
        }
    }

    private Set<String> collectExistingModuleKeys(WorkspaceSnapshot snapshot, List<MemoryRealityClaim> realities) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (snapshot != null) {
            addAll(keys, snapshot.getModules());
            addAll(keys, snapshot.getApiSurface());
            addAll(keys, snapshot.getUiSurface());
        }
        if (realities != null) {
            for (MemoryRealityClaim r : realities) {
                if (r.getSource() != null) {
                    keys.add(r.getSource().toLowerCase(Locale.ROOT));
                }
            }
        }
        return keys;
    }

    private void addAll(Set<String> keys, List<String> values) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            if (v != null) {
                keys.add(v.toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * 从文档中提取“复用 X”短语中的 X。
     * 用负向 lookbehind 排除“不复用 X”——明确否定复用不应判定为复用幻觉。
     */
    private List<String> extractReusedTargets(String lower) {
        Matcher matcher = Pattern.compile("(?<!不)复用\\s+([A-Za-z][A-Za-z0-9/_.-]+)").matcher(lower);
        List<String> targets = new ArrayList<>();
        while (matcher.find()) {
            targets.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return targets;
    }

    /**
     * 检查“影响点”章节是否给出真实 source-backed 的文件路径示例。
     *
     * <p>WHY：原实现只判正文含不含 `/` 或 `\`，任意斜杠即放行，与 RealityClaim.source 无关。
     * 改为：仅在文档出现“影响点”时，要求文档至少命中一条真实 RealityClaim.source 的路径。</p>
     */
    private void checkSourceBackedImpact(String lower, List<MemoryRealityClaim> realities, ValidationResult result) {
        if (!lower.contains("影响点")) {
            return;
        }
        if (realities == null || realities.isEmpty()) {
            return;
        }
        List<String> sources = realities.stream()
                .map(MemoryRealityClaim::getSource)
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        if (sources.isEmpty()) {
            return;
        }
        boolean hit = false;
        for (String source : sources) {
            if (lower.contains(source)) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            result.getFindings().add(new ValidationFinding("WARNING",
                    "影响点未命中任何真实代码 source",
                    "补充受影响的真实代码文件路径（须与代码现状中的 RealityClaim.source 一致）"));
        }
    }

    private List<MemoryConflictFinding> readConflictReport(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                return MAPPER.convertValue(root, new TypeReference<List<MemoryConflictFinding>>() { });
            }
            List<MemoryConflictFinding> result = new ArrayList<>();
            for (String level : List.of("high", "medium", "low")) {
                JsonNode values = root.get(level);
                if (values != null && values.isArray()) {
                    result.addAll(MAPPER.convertValue(values,
                            new TypeReference<List<MemoryConflictFinding>>() { }));
                }
            }
            return result;
        } catch (IllegalArgumentException | IOException e) {
            LOGGER.warn("校验服务冲突报告反序列化失败", e);
            return List.of();
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            LOGGER.warn("校验服务 JSON 反序列化失败", e);
            return null;
        }
    }

    private <T> List<T> readJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, elementType));
        } catch (IOException e) {
            LOGGER.warn("校验服务 JSON 列表反序列化失败", e);
            return List.of();
        }
    }
}