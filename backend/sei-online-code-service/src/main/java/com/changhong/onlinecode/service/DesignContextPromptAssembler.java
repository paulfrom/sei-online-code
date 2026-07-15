package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 设计上下文 prompt 组装器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §10.8。
 *
 * <p>将 {@link RequirementDesignContext} 转成统一 prompt 段，注入 PRD、概览设计、详细设计、
 * CodingTask 执行 prompt。</p>
 *
 * <p>WHY：原 {@code renderAgentMemory} 只输出 WorkspaceMemory / RequirementContext 的 id，
 * 项目自维护记忆正文从未真正进入 prompt，“项目自维护记忆优先级最高”停留在提示语而非事实。
 * 本版本通过 {@link WorkspaceMemoryDao} 反查 {@code agentMemoryMarkdown}，把正文带字符预算截断后注入。</p>
 *
 * @author sei-online-code
 */
@Component
public class DesignContextPromptAssembler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DesignContextPromptAssembler.class);

    /** agent-memory 正文注入到 prompt 的字符预算，避免占用过多上下文窗口。 */
    static final int AGENT_MEMORY_BUDGET = 4000;

    /**
     * 固定 prompt 段标题。
     */
    public static final String SECTION_PROJECT_MEMORY = "【项目自维护记忆】";
    public static final String SECTION_NORMS = "【工作区规范 NormClaim】";
    public static final String SECTION_REALITY = "【代码现状 RealityClaim】";
    public static final String SECTION_CONFLICTS = "【冲突与待确认 ConflictFinding】";
    public static final String SECTION_DESIGN_BASIS = "【设计依据 DesignBasis】";
    public static final String SECTION_MODULE_REALITY = "【当前模块相关代码现状】";

    private final WorkspaceMemoryDao workspaceMemoryDao;

    public DesignContextPromptAssembler(WorkspaceMemoryDao workspaceMemoryDao) {
        this.workspaceMemoryDao = workspaceMemoryDao;
    }

    /**
     * 从上下文组装完整 prompt 段。
     *
     * @param context RequirementDesignContext
     * @return 可直接追加到 LLM prompt 的文本
     */
    public String assemble(RequirementDesignContext context) {
        if (Objects.isNull(context)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(SECTION_PROJECT_MEMORY).append("\n");
        sb.append("以下记忆来自项目自维护文件（agent-memory）和平台规范，具有最高优先级。\n\n");
        sb.append(renderAgentMemory(context));

        sb.append("\n").append(SECTION_NORMS).append("\n");
        sb.append(renderNorms(context));

        sb.append("\n").append(SECTION_REALITY).append("\n");
        sb.append(renderReality(context));

        sb.append("\n").append(SECTION_CONFLICTS).append("\n");
        sb.append(renderConflicts(context));

        sb.append("\n").append(SECTION_DESIGN_BASIS).append("\n");
        sb.append(renderDesignBasis(context));

        return sb.toString();
    }

    /**
     * 仅组装项目自维护记忆段。
     */
    public String assembleProjectMemoryOnly(RequirementDesignContext context) {
        if (Objects.isNull(context)) {
            return "";
        }
        return SECTION_PROJECT_MEMORY + "\n" + renderAgentMemory(context) + "\n";
    }

    /**
     * 组装“当前模块相关代码现状”段。仅详细设计调用，按 moduleId / moduleTitle 裁剪需求级
     * RealityClaim 子集；命中为空时返回空串（不注入空段，避免误导模型认为无相关现状）。
     *
     * @param context      设计上下文
     * @param moduleId     当前模块 id
     * @param moduleTitle  当前模块标题
     * @return 模块相关 reality 渲染段；无命中返回空串
     */
    public String assembleModuleRealitySlice(RequirementDesignContext context, String moduleId, String moduleTitle) {
        if (Objects.isNull(context)) {
            return "";
        }
        List<MemoryRealityClaim> realities = readJson(context.getRequirementRelatedSnapshotJson(),
                new TypeReference<List<MemoryRealityClaim>>() {
                });
        if (realities == null || realities.isEmpty()) {
            return "";
        }
        List<MemoryRealityClaim> slice = realities.stream()
                .filter(r -> matchesModule(r, moduleId, moduleTitle))
                .toList();
        if (slice.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(SECTION_MODULE_REALITY).append("\n");
        sb.append("以下为与当前模块相关的代码现状 RealityClaim，仅围绕本模块生成详细设计。\n\n");
        for (MemoryRealityClaim reality : slice) {
            sb.append("- ").append("[").append(reality.getType()).append("] ")
                    .append(reality.getContent().replace("\n", " "))
                    .append(" (source: ").append(reality.getSource()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * 判断 reality 是否与当前模块相关：source 或 content 命中 moduleId 或 moduleTitle（大小写不敏感）。
     */
    private boolean matchesModule(MemoryRealityClaim reality, String moduleId, String moduleTitle) {
        if (reality == null) {
            return false;
        }
        String source = Objects.requireNonNullElse(reality.getSource(), "").toLowerCase(Locale.ROOT);
        String content = Objects.requireNonNullElse(reality.getContent(), "").toLowerCase(Locale.ROOT);
        String hay = source + " " + content;
        if (moduleId != null && !moduleId.isBlank() && hay.contains(moduleId.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return moduleTitle != null && !moduleTitle.isBlank()
                && hay.contains(moduleTitle.toLowerCase(Locale.ROOT));
    }

    private String renderAgentMemory(RequirementDesignContext context) {
        // 反查 WorkspaceMemory.agentMemoryMarkdown，把项目自维护记忆正文注入 prompt（带预算截断）。
        String markdown = resolveAgentMemoryMarkdown(context);
        StringBuilder sb = new StringBuilder();
        sb.append("- WorkspaceMemory: ").append(context.getWorkspaceMemoryId()).append("\n")
                .append("- RequirementContext: ").append(context.getId())
                .append(" (v").append(context.getVersion()).append(")\n");
        if (markdown != null && !markdown.isBlank()) {
            sb.append("\n").append(truncate(markdown, AGENT_MEMORY_BUDGET)).append("\n");
        } else {
            sb.append("\n（无可用 agent-memory 正文，请以 DesignBasis 中 Applicable Norms 为准）\n");
        }
        return sb.toString();
    }

    private String resolveAgentMemoryMarkdown(RequirementDesignContext context) {
        String id = context.getWorkspaceMemoryId();
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            WorkspaceMemory memory = workspaceMemoryDao.findOne(id);
            return memory == null ? null : memory.getAgentMemoryMarkdown();
        } catch (Exception e) {
            LOGGER.warn("设计上下文反查 agent-memory 失败 workspaceMemoryId={}", id, e);
            return null;
        }
    }

    private static String truncate(String text, int budget) {
        if (text.length() <= budget) {
            return text;
        }
        return text.substring(0, budget) + "\n…（agent-memory 正文已按预算截断）";
    }

    private String renderNorms(RequirementDesignContext context) {
        // norms 目前内嵌在 designBasis 中；后续若需要结构化可单独解析 workspace_norms_json。
        return "详见「设计依据 DesignBasis」中的 Applicable Norms 与 Hard Rules。\n";
    }

    private String renderReality(RequirementDesignContext context) {
        List<MemoryRealityClaim> realities = readJson(context.getRequirementRelatedSnapshotJson(),
                new TypeReference<List<MemoryRealityClaim>>() {
                });
        if (realities == null || realities.isEmpty()) {
            return "无直接相关的代码现状。\n";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryRealityClaim reality : realities) {
            sb.append("- ").append("[").append(reality.getType()).append("] ")
                    .append(reality.getContent().replace("\n", " "))
                    .append(" (source: ").append(reality.getSource()).append(")\n");
        }
        return sb.toString();
    }

    private String renderConflicts(RequirementDesignContext context) {
        List<MemoryConflictFinding> conflicts = readConflictReport(context.getRequirementConflictReportJson());
        if (conflicts == null || conflicts.isEmpty()) {
            return "无高/中/低严重度冲突。\n";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryConflictFinding conflict : conflicts) {
            sb.append("- ").append("[").append(conflict.getSeverity()).append("] ")
                    .append(conflict.getSummary())
                    .append(" [建议处理: ").append(conflict.getRecommendedHandling()).append("]\n");
        }
        return sb.toString();
    }

    private List<MemoryConflictFinding> readConflictReport(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JsonUtils.mapper().readTree(json);
            if (root.isArray()) {
                return JsonUtils.mapper().convertValue(root, new TypeReference<List<MemoryConflictFinding>>() { });
            }
            List<MemoryConflictFinding> result = new java.util.ArrayList<>();
            for (String level : List.of("high", "medium", "low")) {
                JsonNode values = root.get(level);
                if (values != null && values.isArray()) {
                    result.addAll(JsonUtils.mapper().convertValue(values,
                            new TypeReference<List<MemoryConflictFinding>>() { }));
                }
            }
            return result;
        } catch (IllegalArgumentException | IOException e) {
            LOGGER.warn("设计上下文冲突报告反序列化失败", e);
            return List.of();
        }
    }

    private String renderDesignBasis(RequirementDesignContext context) {
        String basis = context.getDesignBasis();
        if (basis == null || basis.isBlank()) {
            return "暂无设计依据。\n";
        }
        return basis;
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.mapper().readValue(json, typeReference);
        } catch (IOException e) {
            LOGGER.warn("设计上下文 JSON 反序列化失败", e);
            return null;
        }
    }
}
