package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryStatus;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryNormClaim;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.onlinecode.service.memory.WorkspaceNorms;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlatformMemoryWriterService 单元测试。
 *
 * <p>WHY：审核发现漂移检测只查 metadata.json 的 doNotEdit 字符串，修改 Markdown 正文无法识别（补3）。
 * 改为记录各 md 文件指纹后，必须验证：首次写入不报漂移；doNotEdit 被撤报漂移；md 正文被人工改动报漂移；
 * 未触碰的 md 文件不误报。否则 PLATFORM_MEMORY_DRIFT 失效，无法提示用户把长期修改迁移到 agent-memory。</p>
 */
class PlatformMemoryWriterServiceTest {

    private PlatformMemoryWriterService service;
    private Path workspace;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        service = new PlatformMemoryWriterService();
        workspace = tmp;
    }

    private WorkspaceMemory memory(String id) {
        WorkspaceMemory m = new WorkspaceMemory();
        m.setId(id);
        m.setProjectId("proj-1");
        m.setVersion(1);
        m.setStatus(WorkspaceMemoryStatus.CURRENT);
        m.setFreshness(WorkspaceMemoryFreshness.FRESH);
        m.setMemorySpecVersion(1);
        return m;
    }

    @Test
    void firstWrite_noDrift_keepsFresh() {
        // WHY：首次写入没有旧 metadata，不应误报漂移。
        WorkspaceMemory m = memory("wm-1");
        boolean ok = service.writePlatformMemory(workspace.toString(), m);

        assertTrue(ok);
        assertEquals(WorkspaceMemoryFreshness.FRESH, m.getFreshness());
        assertTrue(Files.exists(workspace.resolve("platform-memory/metadata.json")));
    }

    @Test
    void secondUnchangedWrite_noDrift() {
        // WHY：内容未改的重复写入不应误报漂移，否则正常重建流程被误判。
        WorkspaceMemory m = memory("wm-1");
        service.writePlatformMemory(workspace.toString(), m);

        WorkspaceMemory m2 = memory("wm-2");
        service.writePlatformMemory(workspace.toString(), m2);

        assertNotEquals(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT, m2.getFreshness(),
                "未改正文不应触发漂移");
    }

    @Test
    void manualEditToMarkdownBody_triggersDrift() throws Exception {
        // WHY：用户手改 md 正文（doNotEdit 仍为 true）必须被识别为漂移，提示迁移到 agent-memory。
        WorkspaceMemory m = memory("wm-1");
        service.writePlatformMemory(workspace.toString(), m);

        // 人工编辑正文，不动 metadata
        Path md = workspace.resolve("platform-memory/workspace-norms.md");
        Files.writeString(md, Files.readString(md) + "\n## 人工补充的规范\n- 手改内容\n");

        WorkspaceMemory m2 = memory("wm-2");
        service.writePlatformMemory(workspace.toString(), m2);

        assertEquals(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT, m2.getFreshness(),
                "md 正文被人工改动应识别为漂移");
    }

    @Test
    void doNotEditRemoved_triggersDrift() throws Exception {
        // WHY：用户撤掉 doNotEdit 标记也应识别为漂移。
        WorkspaceMemory m = memory("wm-1");
        service.writePlatformMemory(workspace.toString(), m);

        Path meta = workspace.resolve("platform-memory/metadata.json");
        String content = Files.readString(meta).replace("\"doNotEdit\" : true", "\"doNotEdit\" : false");
        // Jackson 默认格式为 "doNotEdit" : true；兼容两种写法
        if (content.equals(Files.readString(meta))) {
            content = Files.readString(meta).replace("\"doNotEdit\": true", "\"doNotEdit\": false");
        }
        Files.writeString(meta, content);

        WorkspaceMemory m2 = memory("wm-2");
        service.writePlatformMemory(workspace.toString(), m2);

        assertEquals(WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT, m2.getFreshness());
    }

    @Test
    void blankWorkspacePath_returnsFalseNoThrow() {
        // WHY：工作区路径为空时不应抛异常，仅记录跳过（契约 §22.2 兼容）。
        WorkspaceMemory m = memory("wm-1");
        assertFalse(service.writePlatformMemory(null, m));
        assertFalse(service.writePlatformMemory("  ", m));
    }

    @Test
    void renderedConflictReportContainsRealFindingsAndSourceFiles() throws Exception {
        // WHY：原 requirement-conflict-report.md 只是空骨架，必须渲染真实 HIGH/MEDIUM/LOW 冲突、
        // Open Questions、Assumptions 与 Source Files。
        WorkspaceMemory m = memory("wm-1");

        MemoryRealityClaim reality = new MemoryRealityClaim();
        reality.setId("reality-1");
        reality.setSource("src/main/java/App.vue");
        reality.setContent("src/main/java/App.vue | Vue app");

        MemoryConflictFinding high = new MemoryConflictFinding();
        high.setId("conflict-1");
        high.setType("tech_stack");
        high.setSeverity("HIGH");
        high.setSummary("项目记忆要求 React，代码现状为 Vue");
        high.setRecommendedHandling("clarify");
        high.setRealityClaimIds(List.of("reality-1"));

        MemoryConflictFinding medium = new MemoryConflictFinding();
        medium.setId("conflict-2");
        medium.setType("version");
        medium.setSeverity("MEDIUM");
        medium.setSummary("Java 17 vs Java 21");
        medium.setRecommendedHandling("confirm");

        ObjectMapper mapper = new ObjectMapper();
        m.setConflictFindingsJson(mapper.writeValueAsString(List.of(high, medium)));
        m.setRealityClaimsJson(mapper.writeValueAsString(List.of(reality)));

        boolean ok = service.writePlatformMemory(workspace.toString(), m);

        assertTrue(ok);
        String written = Files.readString(workspace.resolve("platform-memory/requirement-conflict-report.md"));
        assertTrue(written.contains("项目记忆要求 React，代码现状为 Vue"));
        assertTrue(written.contains("Java 17 vs Java 21"));
        assertTrue(written.contains("conflict-1"));
        assertTrue(written.contains("src/main/java/App.vue"));
        assertTrue(written.contains("Open Questions"));
        assertTrue(written.contains("Assumptions"));
    }
}
