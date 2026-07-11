package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceMemoryFreshnessChecker 单元测试。
 *
 * <p>WHY：PRD 生成前必须知道 WorkspaceMemory 是否仍反映当前工作区。若 agent-memory、
 * 项目规范或代码现状已变化但未刷新，继续使用旧记忆会导致设计依据过时。检查器必须正确识别
 * 三类变化并保留 PLATFORM_MEMORY_DRIFT 状态。</p>
 *
 * @author sei-online-code
 */
class WorkspaceMemoryFreshnessCheckerTest {

    private WorkspaceMemoryFreshnessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new WorkspaceMemoryFreshnessChecker(
                new WorkspaceMemoryScannerService(new WorkspaceFileScanner(), new WorkspaceNormsBuilder(),
                        new ConflictDetectionService()));
    }

    @Test
    void check_noChanges_returnsFresh(@TempDir Path workspace) throws Exception {
        seedAgentMemory(workspace);
        seedProjectRules(workspace);
        WorkspaceMemory memory = currentMemory(workspace);

        WorkspaceMemoryFreshness freshness = checker.check(memory, workspace.toString());

        assertEquals(WorkspaceMemoryFreshness.FRESH, freshness);
    }

    @Test
    void check_agentMemoryChanged_returnsStaleByProjectMemoryChange(@TempDir Path workspace) throws Exception {
        seedAgentMemory(workspace);
        WorkspaceMemory memory = currentMemory(workspace);

        Files.writeString(workspace.resolve("agent-memory/project-memory.md"),
                "---\nreviewStatus: reviewed\n---\n\n## Hard Rules\n- 新的规则\n");

        WorkspaceMemoryFreshness freshness = checker.check(memory, workspace.toString());

        assertEquals(WorkspaceMemoryFreshness.STALE_BY_PROJECT_MEMORY_CHANGE, freshness);
    }

    @Test
    void check_projectRuleChanged_returnsStaleByRuleChange(@TempDir Path workspace) throws Exception {
        seedAgentMemory(workspace);
        seedProjectRules(workspace);
        WorkspaceMemory memory = currentMemory(workspace);

        Files.writeString(workspace.resolve("CLAUDE.md"), "# CLAUDE\n\n## Hard Rules\n- 更新后的规则\n");

        WorkspaceMemoryFreshness freshness = checker.check(memory, workspace.toString());

        assertEquals(WorkspaceMemoryFreshness.STALE_BY_RULE_CHANGE, freshness);
    }

    @Test
    void check_codeChanged_returnsStaleBySourceChange(@TempDir Path workspace) throws Exception {
        seedAgentMemory(workspace);
        seedProjectRules(workspace);
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.vue"), "original");
        WorkspaceMemory memory = currentMemory(workspace);

        Files.writeString(workspace.resolve("src/App.vue"), "changed");

        WorkspaceMemoryFreshness freshness = checker.check(memory, workspace.toString());

        assertEquals(WorkspaceMemoryFreshness.STALE_BY_SOURCE_CHANGE, freshness);
    }

    @Test
    void check_specVersionMismatch_returnsStaleBySpecChange(@TempDir Path workspace) throws Exception {
        WorkspaceMemory memory = currentMemory(workspace);
        memory.setMemorySpecVersion(999);

        WorkspaceMemoryFreshness freshness = checker.check(memory, workspace.toString());

        assertEquals(WorkspaceMemoryFreshness.STALE_BY_SPEC_CHANGE, freshness);
    }

    @Test
    void check_existingDrift_preservesDrift(@TempDir Path workspace) throws Exception {
        WorkspaceMemory memory = currentMemory(workspace);
        memory.setFreshness(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT);

        WorkspaceMemoryFreshness freshness = checker.check(memory, workspace.toString());

        assertEquals(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT, freshness);
    }

    private void seedAgentMemory(Path workspace) throws Exception {
        Path dir = workspace.resolve("agent-memory");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("project-memory.md"), "---\nreviewStatus: reviewed\n---\n\n## Hard Rules\n- 前端使用 React\n");
        Files.writeString(dir.resolve("memory-rules.md"), "---\n---\n\n## Scan Focus\n- service\n");
        Files.writeString(dir.resolve("decisions.md"), "# Decisions\n");
        Files.writeString(dir.resolve("modules.md"), "# Modules\n");
    }

    private void seedProjectRules(Path workspace) throws Exception {
        Files.writeString(workspace.resolve("CLAUDE.md"), "# CLAUDE\n\n## Hard Rules\n- 不要直接写 SQL\n");
    }

    private WorkspaceMemory currentMemory(Path workspace) {
        WorkspaceMemoryScanResult scan = new WorkspaceMemoryScannerService(new WorkspaceFileScanner(), new WorkspaceNormsBuilder(),
                new ConflictDetectionService())
                .scan("proj-1", workspace.toString());
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setProjectId("proj-1");
        memory.setMemorySpecVersion(WorkspaceMemoryFreshnessChecker.CURRENT_MEMORY_SPEC_VERSION);
        memory.setFreshness(WorkspaceMemoryFreshness.FRESH);
        memory.setAgentMemoryFingerprint(scan.getAgentMemoryFingerprint());
        memory.setProjectRuleFingerprint(scan.getProjectRuleFingerprint());
        memory.setSourceFingerprintsJson(scan.getSourceFingerprintsJson());
        return memory;
    }
}
