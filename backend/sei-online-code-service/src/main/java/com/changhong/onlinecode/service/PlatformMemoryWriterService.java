package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryNormClaim;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.onlinecode.service.memory.WorkspaceNorms;
import com.changhong.onlinecode.service.memory.WorkspaceSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 平台记忆写入服务（基础）。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §3.2、§5、§10.7、§22.4。
 *
 * <p>将 DB 结构化记忆渲染为 {@code platform-memory/} Markdown 与 {@code metadata.json}，写到工作区。
 * 检测 {@code PLATFORM_MEMORY_DRIFT}（被发现已手动编辑）；重建失败时不覆盖 latest。</p>
 *
 * <p>第一版仅实现最小写入骨架：六个固定 Markdown 文件按计划 §5 模板生成章节骨架，
 * {@code metadata.json} 写入权威字段。实际的 WorkspaceNorms/WorkspaceSnapshot/ConflictReport/DesignBasis
 * 渲染正文在 Phase 2/3 由扫描器与上下文服务产出后填充。</p>
 *
 * @author sei-online-code
 */
@Service
public class PlatformMemoryWriterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformMemoryWriterService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** 工作区内 platform-memory 目录相对路径。 */
    public static final String PLATFORM_MEMORY_DIR = "platform-memory";

    private static final List<String> MEMORY_MD_FILES = List.of(
            "workspace-norms.md",
            "workspace-snapshot.md",
            "requirement-conflict-report.md",
            "design-basis.md",
            "decision-log.md");

    /**
     * 将一版 WorkspaceMemory 的平台镜像写入工作区 platform-memory 目录。
     *
     * <p>重建失败（status=FAILED）时不覆盖 latest（契约 §10.7、§22.4）。</p>
     *
     * @param workspacePath 工作区根目录绝对路径
     * @param memory        WorkspaceMemory 实体
     * @return 是否写入成功（DRIFT 或写入异常返回 false 但不抛断主流程）
     */
    public boolean writePlatformMemory(String workspacePath, WorkspaceMemory memory) {
        if (workspacePath == null || workspacePath.isBlank()) {
            LOGGER.warn("platform-memory: 工作区路径为空，跳过写入 memoryId={}", memory.getId());
            return false;
        }
        Path dir = Path.of(workspacePath, PLATFORM_MEMORY_DIR);
        try {
            Files.createDirectories(dir);
            // 漂移检测：对比当前磁盘各 md 文件指纹与上一版平台记录的指纹，识别正文人工改动（契约 §3.2、§10.7）。
            // 仅当 doNotEdit 仍为 true 且 md 正文未被改动时才视为未漂移。
            if (detectDrift(dir)) {
                LOGGER.warn("platform-memory: 检测到 DRIFT，memoryId={}, 标记但不覆盖", memory.getId());
                memory.setFreshness(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT);
            }
            Map<String, String> mdContents = new LinkedHashMap<>();
            for (String md : MEMORY_MD_FILES) {
                mdContents.put(md, renderMarkdown(md, memory));
            }
            Map<String, String> fingerprints = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : mdContents.entrySet()) {
                fingerprints.put(e.getKey(), sha256(e.getValue()));
                Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
            }
            Files.writeString(dir.resolve("metadata.json"), renderMetadata(memory, fingerprints), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.error("platform-memory: 写入失败 memoryId={}, dir={}", memory.getId(), dir, e);
            return false;
        }
    }

    /**
     * 漂移检测：platform-memory/metadata.json 不存在视为首次写入（无漂移）。
     * 存在则读取 doNotEdit 与 mdFileFingerprints：
     * <ul>
     *   <li>doNotEdit 非 true → 人工已撤"禁止编辑"标记 → DRIFT。</li>
     *   <li>任一 md 文件当前磁盘指纹与上次平台记录指纹不一致 → 正文被人工改 → DRIFT。</li>
     *   <li>新增的 md 文件（上次未记录）不视为漂移。</li>
     * </ul>
     * 相比只查 doNotEdit 字符串，可识别正文人工改动（契约 §3.2 PLATFORM_MEMORY_DRIFT 触发条件）。
     */
    private boolean detectDrift(Path dir) {
        Path metadata = dir.resolve("metadata.json");
        if (!Files.exists(metadata)) {
            return false;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(Files.readString(metadata, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("platform-memory: metadata.json 读取失败，跳过 drift 检测 dir={}", dir, e);
            return false;
        }
        JsonNode doNotEdit = root.get("doNotEdit");
        if (doNotEdit == null || !doNotEdit.asBoolean()) {
            return true;
        }
        JsonNode recorded = root.get("mdFileFingerprints");
        if (recorded == null || !recorded.isObject()) {
            // 旧版 metadata 无指纹记录：退化到只看 doNotEdit，已为 true 不报漂移
            return false;
        }
        Set<String> checked = new HashSet<>();
        for (String md : MEMORY_MD_FILES) {
            JsonNode expected = recorded.get(md);
            if (expected == null) {
                continue;
            }
            checked.add(md);
            Path file = dir.resolve(md);
            if (!Files.exists(file)) {
                // 上次有记录、本次文件缺失：视为人工删除 → DRIFT
                return true;
            }
            try {
                String current = sha256(Files.readString(file, StandardCharsets.UTF_8));
                if (!current.equals(expected.asText())) {
                    return true;
                }
            } catch (IOException e) {
                LOGGER.warn("platform-memory: 读取 md 文件失败，视为漂移 file={}", file, e);
                return true;
            }
        }
        return false;
    }

    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算 SHA-256 失败", e);
        }
    }

    /**
     * 渲染 platform-memory Markdown（§5 模板章节 + Phase 2 实际 claim 内容）。
     */
    private String renderMarkdown(String fileName, WorkspaceMemory memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("generatedBy: sei-online-code\n");
        sb.append("workspaceMemoryId: ").append(memory.getId()).append("\n");
        sb.append("memorySpecVersion: ").append(memory.getMemorySpecVersion()).append("\n");
        sb.append("freshness: ").append(memory.getFreshness()).append("\n");
        sb.append("source: platform\n");
        sb.append("doNotEdit: true\n");
        sb.append("---\n\n");
        switch (fileName) {
            case "workspace-norms.md" -> renderWorkspaceNorms(sb, memory);
            case "workspace-snapshot.md" -> renderWorkspaceSnapshot(sb, memory);
            case "requirement-conflict-report.md" -> renderConflictReport(sb, memory);
            case "design-basis.md" -> renderDesignBasisSkeleton(sb);
            case "decision-log.md" -> renderDecisionLog(sb, memory);
            default -> sb.append("# ").append(fileName).append("\n");
        }
        return sb.toString();
    }

    private void renderWorkspaceNorms(StringBuilder sb, WorkspaceMemory memory) {
        sb.append("# Workspace Norms\n\n");
        WorkspaceNorms norms = readJson(memory.getWorkspaceNormsJson(), new TypeReference<>() {
        });
        if (norms == null) {
            sb.append("## Project Memory Overrides\n\n## Hard Rules\n\n## Preferred Direction\n\n")
                    .append("## Forbidden Choices\n\n## Documentation Rules\n\n")
                    .append("## Testing And Delivery Rules\n\n## Source Files\n");
            return;
        }
        sb.append("## Project Memory Overrides\n").append(renderClaims(norms.getProjectMemoryOverrides())).append("\n");
        sb.append("## Hard Rules\n").append(renderClaims(norms.getHardRules())).append("\n");
        sb.append("## Preferred Direction\n").append(renderClaims(norms.getPreferredDirection())).append("\n");
        sb.append("## Forbidden Choices\n").append(renderClaims(norms.getForbiddenChoices())).append("\n");
        sb.append("## Documentation Rules\n").append(renderClaims(norms.getDocumentationRules())).append("\n");
        sb.append("## Testing And Delivery Rules\n").append(renderClaims(norms.getTestingAndDeliveryRules())).append("\n");
        sb.append("## Source Files\n").append(renderStrings(norms.getSourceFiles())).append("\n");
    }

    private void renderWorkspaceSnapshot(StringBuilder sb, WorkspaceMemory memory) {
        sb.append("# Workspace Snapshot\n\n");
        WorkspaceSnapshot snapshot = readJson(memory.getWorkspaceSnapshotJson(), new TypeReference<>() {
        });
        if (snapshot == null) {
            sb.append("## Modules\n\n## Entrypoints\n\n## API Surface\n\n")
                    .append("## Data Model\n\n## UI Surface\n\n## State Model\n\n")
                    .append("## Integration Points\n\n## Scan Limits\n\n## Source Files\n");
            return;
        }
        sb.append("## Modules\n").append(renderStrings(snapshot.getModules())).append("\n");
        sb.append("## Entrypoints\n").append(renderStrings(snapshot.getEntrypoints())).append("\n");
        sb.append("## API Surface\n").append(renderStrings(snapshot.getApiSurface())).append("\n");
        sb.append("## Data Model\n").append(renderStrings(snapshot.getDataModel())).append("\n");
        sb.append("## UI Surface\n").append(renderStrings(snapshot.getUiSurface())).append("\n");
        sb.append("## State Model\n").append(renderStrings(snapshot.getStateModel())).append("\n");
        sb.append("## Integration Points\n").append(renderStrings(snapshot.getIntegrationPoints())).append("\n");
        sb.append("## Scan Limits\n").append(renderMap(snapshot.getScanLimits())).append("\n");
        sb.append("## Source Files\n").append(renderSourceFiles(snapshot.getSourceFiles())).append("\n");
    }

    private void renderConflictReport(StringBuilder sb, WorkspaceMemory memory) {
        sb.append("# Requirement Conflict Report\n\n");
        List<MemoryConflictFinding> conflicts = readJson(memory.getConflictFindingsJson(),
                new TypeReference<List<MemoryConflictFinding>>() { });
        List<MemoryRealityClaim> realities = readJson(memory.getRealityClaimsJson(),
                new TypeReference<List<MemoryRealityClaim>>() { });
        Map<String, String> realityIdToSource = new LinkedHashMap<>();
        if (realities != null) {
            for (MemoryRealityClaim r : realities) {
                if (r.getId() != null) {
                    realityIdToSource.put(r.getId(), r.getSource());
                }
            }
        }

        List<MemoryConflictFinding> high = filterBySeverity(conflicts, "HIGH");
        List<MemoryConflictFinding> medium = filterBySeverity(conflicts, "MEDIUM");
        List<MemoryConflictFinding> low = filterBySeverity(conflicts, "LOW");

        sb.append("## High Severity\n");
        renderFindings(sb, high);
        sb.append("## Medium Severity\n");
        renderFindings(sb, medium);
        sb.append("## Low Severity\n");
        renderFindings(sb, low);

        sb.append("## Open Questions\n");
        if (high.isEmpty() && medium.isEmpty()) {
            sb.append("- 无待确认问题\n");
        } else {
            sb.append("- 请确认以上 MEDIUM/HIGH 冲突的处理方式\n");
        }
        sb.append("\n");

        sb.append("## Assumptions\n");
        sb.append("- 当前冲突检测基于结构化 NormClaim 与 RealityClaim 比对\n\n");

        sb.append("## Source Files\n");
        Set<String> sources = collectConflictSources(conflicts, realityIdToSource);
        if (sources.isEmpty()) {
            sb.append("- 无相关源文件\n");
        } else {
            for (String source : sources) {
                sb.append("- ").append(source).append("\n");
            }
        }
    }

    private List<MemoryConflictFinding> filterBySeverity(List<MemoryConflictFinding> conflicts, String severity) {
        if (conflicts == null) {
            return Collections.emptyList();
        }
        return conflicts.stream()
                .filter(c -> severity.equalsIgnoreCase(c.getSeverity()))
                .toList();
    }

    private void renderFindings(StringBuilder sb, List<MemoryConflictFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            sb.append("- 无\n");
            return;
        }
        for (MemoryConflictFinding finding : findings) {
            sb.append("- **[").append(finding.getSeverity()).append("]** ")
                    .append(finding.getSummary())
                    .append(" [id: ").append(finding.getId()).append("]");
            if (finding.getRecommendedHandling() != null) {
                sb.append(" [建议处理: ").append(finding.getRecommendedHandling()).append("]");
            }
            sb.append("\n");
        }
    }

    private Set<String> collectConflictSources(List<MemoryConflictFinding> conflicts,
                                                Map<String, String> realityIdToSource) {
        Set<String> sources = new LinkedHashSet<>();
        if (conflicts == null) {
            return sources;
        }
        for (MemoryConflictFinding conflict : conflicts) {
            if (conflict.getRealityClaimIds() == null) {
                continue;
            }
            for (String id : conflict.getRealityClaimIds()) {
                String source = realityIdToSource.get(id);
                if (source != null && !source.isBlank()) {
                    sources.add(source);
                }
            }
        }
        return sources;
    }

    private void renderDesignBasisSkeleton(StringBuilder sb) {
        sb.append("# Design Basis\n\n")
                .append("## Requirement\n\n## Active Workspace Memory\n\n## Applicable Norms\n\n")
                .append("## Relevant Current Reality\n\n## Conflicts To Address\n\n## Decisions\n\n")
                .append("## Constraints For PRD\n\n## Constraints For Overview Design\n\n")
                .append("## Constraints For Detailed Design\n\n## Constraints For Coding\n");
    }

    private void renderDecisionLog(StringBuilder sb, WorkspaceMemory memory) {
        sb.append("# Decision Log\n\n");
        List<MemoryNormClaim> decisions = readJson(memory.getNormClaimsJson(), new TypeReference<List<MemoryNormClaim>>() {
        });
        if (decisions == null) {
            decisions = Collections.emptyList();
        }
        sb.append("## Confirmed Decisions\n");
        decisions.stream()
                .filter(c -> "decision".equals(c.getType()) || "confirmed".equals(c.getType()))
                .forEach(c -> sb.append("- ").append(c.getContent().replace("\n", " ")).append("\n"));
        sb.append("\n## Generated Suggestions\n");
        List<MemoryNormClaim> suggestions = decisions.stream()
                .filter(c -> "generated_suggestion".equals(c.getType()))
                .toList();
        if (suggestions.isEmpty()) {
            sb.append("\n");
        } else {
            suggestions.forEach(c -> sb.append("- ").append(c.getContent().replace("\n", " ")).append("\n"));
        }
        sb.append("\n## Coding Task Updates\n");
        renderCodingTaskUpdates(sb, memory);
        sb.append("\n## Open Decisions\n");
    }

    private void renderCodingTaskUpdates(StringBuilder sb, WorkspaceMemory memory) {
        WorkspaceSnapshot snapshot = readJson(memory.getWorkspaceSnapshotJson(), new TypeReference<>() {
        });
        if (snapshot == null || snapshot.getScanLimits() == null) {
            sb.append("\n");
            return;
        }
        Map<String, Object> limits = snapshot.getScanLimits();
        Object changedFiles = limits.get("codingTaskChangedFiles");
        Object headCommit = limits.get("codingTaskHeadCommit");
        Object baseCommit = limits.get("codingTaskBaseCommit");
        if (changedFiles == null) {
            sb.append("\n");
            return;
        }
        sb.append("- 本次 CodingTask 变更文件数: ").append(changedFiles).append("\n");
        if (headCommit != null) {
            sb.append("- HEAD commit: ").append(headCommit).append("\n");
        }
        if (baseCommit != null) {
            sb.append("- base commit: ").append(baseCommit).append("\n");
        }
        List<MemoryConflictFinding> conflicts = readJson(memory.getConflictFindingsJson(), new TypeReference<List<MemoryConflictFinding>>() {
        });
        if (conflicts != null && !conflicts.isEmpty()) {
            long high = conflicts.stream().filter(c -> "HIGH".equals(c.getSeverity())).count();
            long medium = conflicts.stream().filter(c -> "MEDIUM".equals(c.getSeverity())).count();
            sb.append("- 冲突更新: HIGH=").append(high).append(", MEDIUM=").append(medium).append("\n");
        }
    }

    private String renderClaims(List<MemoryNormClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return "\n";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryNormClaim claim : claims) {
            sb.append("- **").append(claim.getPriority()).append("** ")
                    .append(claim.getContent().replace("\n", " "))
                    .append(" *(").append(claim.getSource()).append(")*\n");
        }
        return sb.toString();
    }

    private String renderStrings(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "\n";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("- ").append(item).append("\n");
        }
        return sb.toString();
    }

    private String renderMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "\n";
        }
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    private String renderSourceFiles(List<?> files) {
        if (files == null || files.isEmpty()) {
            return "\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Object file : files) {
            if (file instanceof Map<?, ?> m) {
                sb.append("- ").append(m.get("path")).append(" (").append(m.get("fingerprint")).append(")\n");
            } else {
                sb.append("- ").append(file.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (IOException e) {
            LOGGER.warn("platform-memory: 反序列化 JSON 失败，退回到空值", e);
            return null;
        }
    }

    /**
     * 渲染 metadata.json（§5.6）。Phase 2/3 产出指纹与 sourceFingerprints 后补全。
     * {@code mdFileFingerprints} 为平台内部漂移检测用扩展字段，记录本次写入的各 md 正文指纹。
     */
    private String renderMetadata(WorkspaceMemory memory, Map<String, String> mdFileFingerprints) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workspaceMemoryId", memory.getId());
        meta.put("memorySpecVersion", memory.getMemorySpecVersion());
        meta.put("status", memory.getStatus().name());
        meta.put("freshness", memory.getFreshness().name());
        meta.put("generatedAt", memory.getGeneratedAt() != null ? memory.getGeneratedAt() : new Date());
        meta.put("agentMemoryFingerprint", memory.getAgentMemoryFingerprint());
        meta.put("projectRuleFingerprint", memory.getProjectRuleFingerprint());
        meta.put("sourceFingerprints", memory.getSourceFingerprintsJson());
        meta.put("scanTruncated", false);
        meta.put("doNotEdit", true);
        meta.put("mdFileFingerprints", mdFileFingerprints);
        try {
            return MAPPER.writeValueAsString(meta);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 platform-memory metadata.json 失败", e);
        }
    }
}