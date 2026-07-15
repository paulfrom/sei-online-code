package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * CodingTask 后增量记忆组装器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §16.3、§16.4。
 *
 * <p>基于 base {@link WorkspaceMemory}、git 变更结果与增量扫描结果，生成新的
 * {@link WorkspaceMemoryScanResult}。核心行为：合并 reality claims、生成 Generated Suggestions、
 * 更新冲突 findings、同步 workspace snapshot。不修改 {@code agent-memory/}。</p>
 *
 * @author sei-online-code
 */
@Component
@AllArgsConstructor
@Slf4j
public class CodingTaskMemoryUpdateAssembler {


    private final WorkspaceNormsBuilder normsBuilder;
    private final ConflictDetectionService conflictDetectionService;


    /**
     * 组装新的 WorkspaceMemoryScanResult。
     *
     * @param base            基准 WorkspaceMemory
     * @param changeResult    git 变更采集结果
     * @param incrementalScan 对变更文件重新扫描的结果
     * @param task            CodingTask
     * @param run             成功结束的 Run
     * @return 新的扫描结果
     */
    public WorkspaceMemoryScanResult assemble(WorkspaceMemory base,
                                              CodingTaskChangeResult changeResult,
                                              WorkspaceMemoryScanResult incrementalScan,
                                              CodingTask task,
                                              Run run) {
        List<MemoryNormClaim> baseNorms = readJsonList(base.getNormClaimsJson(), MemoryNormClaim.class);
        List<MemoryRealityClaim> baseRealities = readJsonList(base.getRealityClaimsJson(), MemoryRealityClaim.class);
        List<MemoryConflictFinding> baseConflicts = readJsonList(base.getConflictFindingsJson(), MemoryConflictFinding.class);
        WorkspaceNorms baseNormsAggregate = readJson(base.getWorkspaceNormsJson(), WorkspaceNorms.class);
        WorkspaceSnapshot baseSnapshot = readJson(base.getWorkspaceSnapshotJson(), WorkspaceSnapshot.class);

        AtomicInteger idSeq = new AtomicInteger(nextId(baseNorms, baseRealities, baseConflicts));

        List<MemoryRealityClaim> mergedRealities = mergeRealityClaims(baseRealities,
                incrementalScan == null ? List.of() : incrementalScan.getRealityClaims(),
                changeResult == null ? Set.of() : changeResult.deletedPaths());

        List<MemoryNormClaim> suggestions = generateSuggestions(changeResult, mergedRealities, task, idSeq);
        List<MemoryNormClaim> mergedNorms = mergeNorms(baseNorms, suggestions);

        List<MemoryConflictFinding> updatedConflicts = updateConflicts(mergedNorms, mergedRealities,
                baseConflicts, idSeq);

        WorkspaceNorms normsAggregate = normsBuilder.build(mergedNorms);
        WorkspaceSnapshot snapshot = updateWorkspaceSnapshot(baseSnapshot, mergedRealities,
                incrementalScan == null || incrementalScan.getWorkspaceSnapshot() == null
                        ? List.of() : incrementalScan.getWorkspaceSnapshot().getSourceFiles(),
                changeResult);

        WorkspaceMemoryScanResult result = new WorkspaceMemoryScanResult();
        result.setAgentMemoryFingerprint(base.getAgentMemoryFingerprint());
        result.setAgentMemoryMarkdown(base.getAgentMemoryMarkdown());
        result.setProjectRuleFingerprint(base.getProjectRuleFingerprint());
        result.setProjectRuleMarkdown(base.getProjectRuleMarkdown());
        result.setSourceFingerprintsJson(toJson(extractFingerprints(mergedRealities)));
        result.setNormClaims(List.copyOf(mergedNorms));
        result.setRealityClaims(List.copyOf(mergedRealities));
        result.setConflictFindings(List.copyOf(updatedConflicts));
        result.setWorkspaceNorms(normsAggregate);
        result.setWorkspaceSnapshot(snapshot);
        result.setScanTruncated(baseSnapshot != null && baseSnapshot.getScanLimits() != null
                && Boolean.TRUE.equals(baseSnapshot.getScanLimits().get("truncated")));
        result.setTruncatedReason(null);
        return result;
    }

    // ============================ merge helpers ============================

    /**
     * 合并 base 与增量扫描的 RealityClaim。
     *
     * <p>WHY：增量回写不能只靠 source 覆盖，否则删除或重命名文件会留下永久残留的旧事实，
     * 让“代码现状”随多轮回写逐渐失真。因此先按 {@code deletedPaths} 移除 base 中对应 source 的 claim，
     * 再用 incremental 按 source 覆盖更新。</p>
     *
     * @param base          基准 RealityClaim 列表
     * @param incremental   本次增量扫描的 RealityClaim 列表
     * @param deletedPaths  本次变更中被删除或重命名前的旧路径集合
     * @return 合并后的 RealityClaim 列表
     */
    private List<MemoryRealityClaim> mergeRealityClaims(List<MemoryRealityClaim> base,
                                                          List<MemoryRealityClaim> incremental,
                                                          Set<String> deletedPaths) {
        Map<String, MemoryRealityClaim> bySource = new LinkedHashMap<>();
        if (base != null) {
            for (MemoryRealityClaim claim : base) {
                if (deletedPaths != null && deletedPaths.contains(claim.getSource())) {
                    // 删除/重命名：旧 source 的代码事实已不存在，必须移除而非保留。
                    continue;
                }
                bySource.put(claim.getSource(), claim);
            }
        }
        if (incremental != null) {
            for (MemoryRealityClaim claim : incremental) {
                bySource.put(claim.getSource(), claim);
            }
        }
        return new ArrayList<>(bySource.values());
    }

    private List<MemoryNormClaim> mergeNorms(List<MemoryNormClaim> base, List<MemoryNormClaim> suggestions) {
        List<MemoryNormClaim> merged = new ArrayList<>();
        if (base != null) {
            merged.addAll(base);
        }
        if (suggestions != null) {
            merged.addAll(suggestions);
        }
        return merged;
    }

    // ============================ suggestion generation ============================

    private List<MemoryNormClaim> generateSuggestions(CodingTaskChangeResult changeResult,
                                                         List<MemoryRealityClaim> realities,
                                                         CodingTask task,
                                                         AtomicInteger idSeq) {
        List<MemoryNormClaim> suggestions = new ArrayList<>();
        if (changeResult == null || changeResult.getChangedFiles() == null) {
            return suggestions;
        }
        Set<String> changed = Set.copyOf(changeResult.getChangedFiles());

        boolean hasNewController = realities.stream()
                .anyMatch(r -> isNewFile(changed, r.getSource()) && r.getSource().contains("/controller/"));
        boolean hasNewService = realities.stream()
                .anyMatch(r -> isNewFile(changed, r.getSource()) && r.getSource().contains("/service/"));
        boolean hasNewEntity = realities.stream()
                .anyMatch(r -> isNewFile(changed, r.getSource()) && r.getSource().contains("/entity/"));
        boolean hasNewPage = realities.stream()
                .anyMatch(r -> isNewFile(changed, r.getSource())
                        && (r.getSource().contains("/pages/") || r.getSource().contains("/components/")));
        boolean hasNewMigration = realities.stream()
                .anyMatch(r -> isNewFile(changed, r.getSource()) && r.getSource().endsWith(".sql"));
        boolean hasTestChange = changed.stream().anyMatch(p -> p.contains("/test/") || p.contains(".test."));
        boolean hasBuildChange = changed.stream().anyMatch(p ->
                p.endsWith("build.gradle") || p.endsWith("settings.gradle")
                        || p.endsWith("package.json") || p.endsWith("pnpm-workspace.yaml"));

        if (hasNewController || hasNewService || hasNewEntity) {
            suggestions.add(suggestion(idSeq,
                    "新增后端模块（controller/service/entity），建议确认后在 agent-memory/modules.md 中更新模块边界与集成点。"));
        }
        if (hasNewPage) {
            suggestions.add(suggestion(idSeq,
                    "新增前端页面或组件，建议确认后在 agent-memory/modules.md 中更新 UI 模块清单。"));
        }
        if (hasNewMigration) {
            suggestions.add(suggestion(idSeq,
                    "新增数据库迁移，建议确认后在 agent-memory/project-memory.md 中更新数据模型约束。"));
        }
        if (hasTestChange) {
            suggestions.add(suggestion(idSeq,
                    "测试文件发生变更，建议确认后在 agent-memory/memory-rules.md 中补充测试规则。"));
        }
        if (hasBuildChange) {
            suggestions.add(suggestion(idSeq,
                    "构建配置发生变更，建议确认后在 agent-memory/project-memory.md 中更新技术栈与构建规则。"));
        }
        if (task != null && task.getTitle() != null) {
            suggestions.add(suggestion(idSeq,
                    "CodingTask '" + task.getTitle() + "' 已完成，建议审阅 Generated Suggestions 并迁移到 agent-memory。"));
        }
        return suggestions;
    }

    private boolean isNewFile(Set<String> changedFiles, String source) {
        return changedFiles.contains(source);
    }

    private MemoryNormClaim suggestion(AtomicInteger idSeq, String content) {
        MemoryNormClaim claim = new MemoryNormClaim();
        claim.setId("norm-" + idSeq.getAndIncrement());
        claim.setType("generated_suggestion");
        claim.setContent(content);
        claim.setPriority("P5");
        claim.setSource("platform-memory/coding-task-update");
        claim.setConfidence("inferred");
        return claim;
    }

    // ============================ conflict update ============================

    private List<MemoryConflictFinding> updateConflicts(List<MemoryNormClaim> norms,
                                                           List<MemoryRealityClaim> realities,
                                                           List<MemoryConflictFinding> baseConflicts,
                                                           AtomicInteger idSeq) {
        // 保留历史冲突中仍未解决的（source 仍在 realities 中）
        List<MemoryConflictFinding> kept = baseConflicts == null ? List.of() : baseConflicts.stream()
                .filter(c -> stillRelevant(c, realities))
                .toList();

        List<MemoryConflictFinding> fresh = conflictDetectionService.detect(norms, realities, idSeq);

        List<MemoryConflictFinding> merged = new ArrayList<>();
        merged.addAll(kept);
        // 去重：相同 type + summary 不再新增
        Set<String> existingKeys = kept.stream()
                .map(c -> c.getType() + "|" + c.getSummary())
                .collect(Collectors.toSet());
        for (MemoryConflictFinding finding : fresh) {
            String key = finding.getType() + "|" + finding.getSummary();
            if (!existingKeys.contains(key)) {
                merged.add(finding);
                existingKeys.add(key);
            }
        }
        return merged;
    }

    private boolean stillRelevant(MemoryConflictFinding conflict, List<MemoryRealityClaim> realities) {
        if (conflict.getRealityClaimIds() == null || realities == null) {
            return false;
        }
        Set<String> realityIds = realities.stream().map(MemoryRealityClaim::getId).collect(Collectors.toSet());
        return conflict.getRealityClaimIds().stream().anyMatch(realityIds::contains);
    }

    // ============================ aggregate builders ============================

    private WorkspaceSnapshot updateWorkspaceSnapshot(WorkspaceSnapshot base,
                                                      List<MemoryRealityClaim> realities,
                                                      List<ScannedSourceFile> incrementalSourceFiles,
                                                      CodingTaskChangeResult changeResult) {
        WorkspaceSnapshot snapshot = base == null ? new WorkspaceSnapshot() : copySnapshot(base);
        Set<String> deletedPaths = changeResult == null ? Set.of() : changeResult.deletedPaths();
        Map<String, ScannedSourceFile> filesByPath = new LinkedHashMap<>();
        if (snapshot.getSourceFiles() != null) {
            for (ScannedSourceFile f : snapshot.getSourceFiles()) {
                if (deletedPaths.contains(f.getPath())) {
                    // 删除/重命名：从 snapshot source 清单移除已不存在的旧路径，避免失效事实残留。
                    continue;
                }
                filesByPath.put(f.getPath(), f);
            }
        }
        if (incrementalSourceFiles != null) {
            for (ScannedSourceFile f : incrementalSourceFiles) {
                filesByPath.put(f.getPath(), f);
            }
        }
        snapshot.setSourceFiles(new ArrayList<>(filesByPath.values()));

        Map<String, Object> limits = snapshot.getScanLimits();
        if (limits == null) {
            limits = new LinkedHashMap<>();
            snapshot.setScanLimits(limits);
        }
        limits.put("codingTaskChangedFiles", changeResult == null ? 0 : changeResult.getChangedFiles().size());
        limits.put("codingTaskHeadCommit", changeResult == null ? null : changeResult.getHeadCommit());
        limits.put("codingTaskBaseCommit", changeResult == null ? null : changeResult.getBaseCommit());
        return snapshot;
    }

    private WorkspaceSnapshot copySnapshot(WorkspaceSnapshot base) {
        WorkspaceSnapshot copy = new WorkspaceSnapshot();
        copy.setModules(base.getModules());
        copy.setEntrypoints(base.getEntrypoints());
        copy.setApiSurface(base.getApiSurface());
        copy.setDataModel(base.getDataModel());
        copy.setUiSurface(base.getUiSurface());
        copy.setStateModel(base.getStateModel());
        copy.setIntegrationPoints(base.getIntegrationPoints());
        copy.setScanLimits(base.getScanLimits() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(base.getScanLimits()));
        copy.setSourceFiles(base.getSourceFiles() == null ? new ArrayList<>() : new ArrayList<>(base.getSourceFiles()));
        return copy;
    }

    // ============================ utilities ============================

    private int nextId(List<?>... lists) {
        int max = 0;
        for (List<? extends Object> list : lists) {
            if (list == null) {
                continue;
            }
            for (Object item : list) {
                String id = null;
                if (item instanceof MemoryNormClaim c) {
                    id = c.getId();
                } else if (item instanceof MemoryRealityClaim c) {
                    id = c.getId();
                } else if (item instanceof MemoryConflictFinding c) {
                    id = c.getId();
                }
                if (id != null) {
                    try {
                        int num = Integer.parseInt(id.replaceAll("^[^0-9]*", ""));
                        max = Math.max(max, num);
                    } catch (NumberFormatException ignored) {
                        // ignore
                    }
                }
            }
        }
        return max + 1;
    }

    private List<String> extractFingerprints(List<MemoryRealityClaim> realities) {
        if (realities == null) {
            return List.of();
        }
        return realities.stream()
                .map(MemoryRealityClaim::getSourceHash)
                .filter(Objects::nonNull)
                .toList();
    }

    private <T> List<T> readJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JsonUtils.mapper().readValue(json, JsonUtils.mapper().getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException e) {
            log.warn("coding-task-memory: 反序列化 JSON 列表失败", e);
            return new ArrayList<>();
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.mapper().readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("coding-task-memory: 反序列化 JSON 失败", e);
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }
}
