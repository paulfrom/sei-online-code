package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodingTaskMemoryUpdateAssembler 单元测试。
 *
 * <p>WHY：增量回写的核心是保证 base WorkspaceMemory 中的项目规范与代码事实不被破坏：
 * 变更文件的现实 claim 应被覆盖更新，新增文件应追加，生成的 suggestions 应进入 norms，
 * 冲突应基于更新后的现实重新检测。</p>
 *
 * @author sei-online-code
 */
class CodingTaskMemoryUpdateAssemblerTest {

    private final CodingTaskMemoryUpdateAssembler assembler = new CodingTaskMemoryUpdateAssembler(
            new WorkspaceNormsBuilder(), new ConflictDetectionService());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void assemble_mergesRealityClaimsBySource() throws Exception {
        WorkspaceMemory base = baseMemory();
        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/main/java/Example.java"));

        MemoryRealityClaim updated = realityClaim("reality-100", "src/main/java/Example.java", "updated content");
        WorkspaceMemoryScanResult incremental = new WorkspaceMemoryScanResult();
        incremental.setRealityClaims(List.of(updated));
        incremental.setWorkspaceSnapshot(new WorkspaceSnapshot());

        WorkspaceMemoryScanResult result = assembler.assemble(base, changeResult, incremental, task(), run());

        List<MemoryRealityClaim> realities = result.getRealityClaims();
        assertEquals(2, realities.size());
        assertTrue(realities.stream().anyMatch(r -> "src/main/java/Example.java".equals(r.getSource())
                && "updated content".equals(r.getContent())));
        assertTrue(realities.stream().anyMatch(r -> "src/main/java/Other.java".equals(r.getSource())));
    }

    @Test
    void appendGeneratedSuggestionsForNewBackendModule() throws Exception {
        WorkspaceMemory base = baseMemory();
        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/main/java/com/example/controller/NewController.java"));

        MemoryRealityClaim newController = realityClaim("reality-100",
                "src/main/java/com/example/controller/NewController.java", "class NewController");
        WorkspaceMemoryScanResult incremental = new WorkspaceMemoryScanResult();
        incremental.setRealityClaims(List.of(newController));
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setSourceFiles(List.of(scannedFile(newController.getSource(), "fp1")));
        incremental.setWorkspaceSnapshot(snapshot);

        WorkspaceMemoryScanResult result = assembler.assemble(base, changeResult, incremental, task(), run());

        List<MemoryNormClaim> norms = result.getNormClaims();
        assertTrue(norms.stream().anyMatch(n -> "generated_suggestion".equals(n.getType())
                && n.getContent().contains("新增后端模块")));
    }

    @Test
    void assemble_detectsConflictAfterRealityUpdate() throws Exception {
        WorkspaceMemory base = baseMemory();
        MemoryNormClaim reactRule = normClaim("norm-1", "hard_rule", "P0", "前端必须使用 React", "project-memory.md");
        base.setNormClaimsJson(mapper.writeValueAsString(List.of(reactRule)));

        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/pages/Home.vue"));

        MemoryRealityClaim vueReality = realityClaim("reality-100", "src/pages/Home.vue", "Vue component");
        WorkspaceMemoryScanResult incremental = new WorkspaceMemoryScanResult();
        incremental.setRealityClaims(List.of(vueReality));
        incremental.setWorkspaceSnapshot(new WorkspaceSnapshot());

        WorkspaceMemoryScanResult result = assembler.assemble(base, changeResult, incremental, task(), run());

        assertFalse(result.getConflictFindings().isEmpty());
        assertTrue(result.getConflictFindings().stream()
                .anyMatch(f -> f.getSummary().contains("react") && f.getSummary().contains("vue")));
    }

    @Test
    void assemble_updatesSnapshotScanLimits() throws Exception {
        WorkspaceMemory base = baseMemory();
        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("a.java"));
        changeResult.setHeadCommit("abc123");

        WorkspaceMemoryScanResult incremental = new WorkspaceMemoryScanResult();
        incremental.setRealityClaims(List.of());
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setSourceFiles(List.of());
        incremental.setWorkspaceSnapshot(snapshot);

        WorkspaceMemoryScanResult result = assembler.assemble(base, changeResult, incremental, task(), run());

        Map<String, Object> limits = result.getWorkspaceSnapshot().getScanLimits();
        assertEquals(1, limits.get("codingTaskChangedFiles"));
        assertEquals("abc123", limits.get("codingTaskHeadCommit"));
    }

    @Test
    void assemble_deletedFile_removesStaleRealityAndSnapshotSource() throws Exception {
        // WHY：删除文件后旧 RealityClaim 与 snapshot source 若不清理，会随多轮回写逐渐失真，
        // 让“代码现状”记录已不存在的代码，污染后续设计与冲突判断。
        WorkspaceMemory base = baseMemory();
        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/main/java/Other.java"));
        // collector 解析 D 状态后会把 deletedPaths() 报告为旧路径
        changeResult.setChangeStatuses(Map.of(
                "src/main/java/Other.java", CodingTaskChangeResult.ChangeStatus.DELETED));

        WorkspaceMemoryScanResult incremental = new WorkspaceMemoryScanResult();
        incremental.setRealityClaims(List.of());
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setSourceFiles(List.of());
        incremental.setWorkspaceSnapshot(snapshot);

        WorkspaceMemoryScanResult result = assembler.assemble(base, changeResult, incremental, task(), run());

        // Other.java 的旧 RealityClaim 必须被移除，只剩 Example.java
        List<MemoryRealityClaim> realities = result.getRealityClaims();
        assertEquals(1, realities.size());
        assertEquals("src/main/java/Example.java", realities.get(0).getSource());
        // snapshot source 文件清单里 Other.java 也要被移除
        assertTrue(result.getWorkspaceSnapshot().getSourceFiles().stream()
                .noneMatch(f -> "src/main/java/Other.java".equals(f.getPath())));
    }

    @Test
    void assemble_renamedFile_replacesOldSourceWithNew() throws Exception {
        // WHY：重命名文件时旧路径记 DELETED、新路径记 RENAMED，旧 RealityClaim 必须清理，
        // 否则会同时保留旧路径与新路径两份事实。
        WorkspaceMemory base = baseMemory();
        CodingTaskChangeResult changeResult = new CodingTaskChangeResult();
        changeResult.setSuccess(true);
        changeResult.setChangedFiles(List.of("src/main/java/Other.java", "src/main/java/Renamed.java"));
        changeResult.setChangeStatuses(Map.of(
                "src/main/java/Other.java", CodingTaskChangeResult.ChangeStatus.DELETED,
                "src/main/java/Renamed.java", CodingTaskChangeResult.ChangeStatus.RENAMED));

        MemoryRealityClaim renamed = realityClaim("reality-100", "src/main/java/Renamed.java", "class Renamed");
        WorkspaceMemoryScanResult incremental = new WorkspaceMemoryScanResult();
        incremental.setRealityClaims(List.of(renamed));
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setSourceFiles(List.of(scannedFile("src/main/java/Renamed.java", "fp-renamed")));
        incremental.setWorkspaceSnapshot(snapshot);

        WorkspaceMemoryScanResult result = assembler.assemble(base, changeResult, incremental, task(), run());

        List<MemoryRealityClaim> realities = result.getRealityClaims();
        assertTrue(realities.stream().noneMatch(r -> "src/main/java/Other.java".equals(r.getSource())));
        assertTrue(realities.stream().anyMatch(r -> "src/main/java/Renamed.java".equals(r.getSource())));
    }

    // ============================ fixtures ============================

    private WorkspaceMemory baseMemory() throws Exception {
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setId("wm-base");
        memory.setAgentMemoryFingerprint("afp");
        memory.setAgentMemoryMarkdown("agent");
        memory.setProjectRuleFingerprint("pfp");
        memory.setProjectRuleMarkdown("rules");

        MemoryNormClaim norm = normClaim("norm-1", "hard_rule", "P1", "遵守规则", "project-memory.md");
        memory.setNormClaimsJson(mapper.writeValueAsString(List.of(norm)));

        MemoryRealityClaim reality1 = realityClaim("reality-1", "src/main/java/Example.java", "class Example");
        MemoryRealityClaim reality2 = realityClaim("reality-2", "src/main/java/Other.java", "class Other");
        memory.setRealityClaimsJson(mapper.writeValueAsString(List.of(reality1, reality2)));

        memory.setConflictFindingsJson(mapper.writeValueAsString(List.of()));

        WorkspaceNorms norms = new WorkspaceNorms();
        norms.setHardRules(List.of(norm));
        memory.setWorkspaceNormsJson(mapper.writeValueAsString(norms));

        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setSourceFiles(List.of(scannedFile("src/main/java/Example.java", "fp1"),
                scannedFile("src/main/java/Other.java", "fp2")));
        memory.setWorkspaceSnapshotJson(mapper.writeValueAsString(snapshot));
        return memory;
    }

    private MemoryNormClaim normClaim(String id, String type, String priority, String content, String source) {
        MemoryNormClaim claim = new MemoryNormClaim();
        claim.setId(id);
        claim.setType(type);
        claim.setPriority(priority);
        claim.setContent(content);
        claim.setSource(source);
        claim.setConfidence("explicit");
        return claim;
    }

    private MemoryRealityClaim realityClaim(String id, String source, String content) {
        MemoryRealityClaim claim = new MemoryRealityClaim();
        claim.setId(id);
        claim.setType("backend_code");
        claim.setContent(content);
        claim.setSource(source);
        claim.setSourceHash("hash-" + id);
        claim.setConfidence("source_backed");
        return claim;
    }

    private ScannedSourceFile scannedFile(String path, String fingerprint) {
        ScannedSourceFile file = new ScannedSourceFile();
        file.setPath(path);
        file.setFingerprint(fingerprint);
        file.setSize(100L);
        file.setSummary("summary");
        return file;
    }

    private CodingTask task() {
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setTitle("Implement feature");
        return task;
    }

    private Run run() {
        Run run = new Run();
        run.setId("run-1");
        return run;
    }
}
