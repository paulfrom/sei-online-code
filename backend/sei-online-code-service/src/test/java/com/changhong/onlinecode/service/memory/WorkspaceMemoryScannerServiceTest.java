package com.changhong.onlinecode.service.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceMemoryScannerService 单元测试。
 *
 * <p>WHY：WorkspaceMemory 的核心价值是"项目自维护记忆优先、代码事实不被覆盖、冲突显性化"。
 * 扫描器必须： reviewed/P0 内容优先于 seed；seed 未 review 时只作为默认基线；代码进入 RealityClaim；
 * 当高优先级规范与代码事实矛盾时产生 ConflictFinding。否则 PRD 生成会基于错误假设。</p>
 *
 * @author sei-online-code
 */
class WorkspaceMemoryScannerServiceTest {

    private WorkspaceMemoryScannerService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceMemoryScannerService(new WorkspaceFileScanner(), new WorkspaceNormsBuilder(),
                new ConflictDetectionService());
    }

    @Test
    void scan_reviewedProjectMemoryOutranksSeed(@TempDir Path workspace) throws Exception {
        // WHY：用户把 project-memory 标记为 reviewed 后，它应成为 P0 规范，不能因 seed 版本变化被压低。
        writeAgentMemory(workspace, "project-memory.md", "---\nreviewStatus: reviewed\n---\n\n## Hard Rules\n- 前端使用 React\n");
        writeAgentMemory(workspace, "memory-rules.md", "---\n---\n\n## Scan Focus\n- 关注 service 层\n");

        WorkspaceMemoryScanResult result = service.scan("proj-1", workspace.toString());

        MemoryNormClaim hardRule = result.getNormClaims().stream()
                .filter(n -> n.getContent().contains("前端使用 React"))
                .findFirst()
                .orElseThrow();
        assertEquals("P0", hardRule.getPriority());
        assertEquals("explicit", hardRule.getConfidence());
    }

    @Test
    void scan_seedContentIsBaselineNotOverride(@TempDir Path workspace) throws Exception {
        // WHY：未 review 的 seed 内容只是默认基线（P5S/P1），不能压过 P0 项目自维护记忆。
        writeAgentMemory(workspace, "project-memory.md", "---\norigin: platform_seed\nreviewStatus: unreviewed\n---\n\n## Hard Rules\n- 前端使用 Vue\n");

        WorkspaceMemoryScanResult result = service.scan("proj-1", workspace.toString());

        MemoryNormClaim seedRule = result.getNormClaims().stream()
                .filter(n -> n.getContent().contains("前端使用 Vue"))
                .findFirst()
                .orElseThrow();
        assertEquals("P5S", seedRule.getPriority(),
                "origin=platform_seed 且未 review 的内容必须统一作为 P5S 基线");
    }

    @Test
    void scan_codeFactBecomesRealityClaim(@TempDir Path workspace) throws Exception {
        // WHY：代码现状是客观事实，不能因项目记忆不同而被覆盖，只能作为 RealityClaim 进入冲突检测。
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.vue"), "<template>\n  <div>Hello</div>\n</template>\n");

        WorkspaceMemoryScanResult result = service.scan("proj-1", workspace.toString());

        MemoryRealityClaim reality = result.getRealityClaims().stream()
                .filter(r -> r.getContent().contains("App.vue"))
                .findFirst()
                .orElseThrow();
        assertEquals("source_backed", reality.getConfidence());
        assertTrue(reality.getType().contains("frontend"));
    }

    @Test
    void scan_conflictBetweenNormAndReality(@TempDir Path workspace) throws Exception {
        // WHY：项目记忆要求 React，但代码是 Vue，这是典型的需求-现状冲突，必须显性化。
        writeAgentMemory(workspace, "project-memory.md", "---\nreviewStatus: reviewed\n---\n\n## Hard Rules\n- 前端使用 React\n");
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.vue"), "<template>\n  <div>Vue app</div>\n</template>\n");

        WorkspaceMemoryScanResult result = service.scan("proj-1", workspace.toString());

        assertFalse(result.getConflictFindings().isEmpty(), "应检测到 React vs Vue 冲突");
        MemoryConflictFinding conflict = result.getConflictFindings().get(0);
        assertEquals("HIGH", conflict.getSeverity());
        assertTrue(conflict.getSummary().toLowerCase().contains("react"));
        assertTrue(conflict.getSummary().toLowerCase().contains("vue"));
    }

    @Test
    void scan_readsProjectRuleFiles(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("CLAUDE.md"), "# CLAUDE\n\n## Hard Rules\n- 不要直接写 SQL\n");

        WorkspaceMemoryScanResult result = service.scan("proj-1", workspace.toString());

        assertTrue(result.getNormClaims().stream().anyMatch(n -> n.getSource().equals("CLAUDE.md")
                && n.getContent().contains("不要直接写 SQL")));
    }

    @Test
    void scanIncremental_onlyScansChangedFiles(@TempDir Path workspace) throws Exception {
        // WHY：CodingTask 回写应基于变更文件做增量扫描，避免遍历整个工作区；未变更文件不应出现在结果中。
        writeAgentMemory(workspace, "project-memory.md", "---\nreviewStatus: reviewed\n---\n\n## Hard Rules\n- 前端使用 React\n");
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.vue"), "<template>\n  <div>Vue app</div>\n</template>\n");
        Files.writeString(workspace.resolve("src/Old.vue"), "<template>\n  <div>Old</div>\n</template>\n");

        WorkspaceMemoryScanResult result = service.scanIncremental("proj-1", workspace.toString(), List.of("src/App.vue"));

        assertEquals(1, result.getRealityClaims().size(), "只应扫描变更文件 App.vue");
        assertTrue(result.getRealityClaims().get(0).getContent().contains("App.vue"));
        assertTrue(result.getConflictFindings().stream()
                .anyMatch(c -> c.getSummary().toLowerCase().contains("react")
                        && c.getSummary().toLowerCase().contains("vue")));
    }

    @Test
    void scanIncremental_noChangedFiles_returnsEmptyReality(@TempDir Path workspace) throws Exception {
        writeAgentMemory(workspace, "project-memory.md", "---\nreviewStatus: reviewed\n---\n\n## Hard Rules\n- 前端使用 React\n");
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.vue"), "<template>\n  <div>Vue</div>\n</template>\n");

        WorkspaceMemoryScanResult result = service.scanIncremental("proj-1", workspace.toString(), List.of());

        assertTrue(result.getRealityClaims().isEmpty(), "无变更文件时 reality claims 应为空");
        assertTrue(result.getConflictFindings().isEmpty(), "无 reality 时不应产生冲突");
    }

    private void writeAgentMemory(Path workspace, String fileName, String content) throws Exception {
        Path dir = workspace.resolve("agent-memory");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), content);
    }
}
