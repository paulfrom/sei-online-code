package com.changhong.onlinecode.service.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceFileScanner 单元测试。
 *
 * <p>WHY：扫描预算（200 文件 / 128 KB / 5 MB / depth 8）是平台第一版安全上限，必须验证
 * 超限时正确截断并给出原因，否则大仓库会撑爆内存和 DB 列。</p>
 *
 * @author sei-online-code
 */
class WorkspaceFileScannerTest {

    private final WorkspaceFileScanner scanner = new WorkspaceFileScanner();

    @Test
    void scan_emptyWorkspace_returnsEmptyResult(@TempDir Path workspace) {
        ScanResult result = scanner.scan(workspace);

        assertTrue(result.getFiles().isEmpty());
        assertFalse(result.isTruncated());
    }

    @Test
    void scan_excludesNodeModules(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("node_modules/lodash"));
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("node_modules/lodash/index.js"), "module.exports = {};");
        Files.writeString(workspace.resolve("src/app.js"), "console.log('ok');");

        ScanResult result = scanner.scan(workspace);

        assertEquals(1, result.getTotalFiles());
        assertTrue(result.getFiles().stream().anyMatch(f -> f.getPath().equals("src/app.js")));
        assertTrue(result.getFiles().stream().noneMatch(f -> f.getPath().contains("node_modules")));
    }

    @Test
    void scan_excludesMemoryAndDocumentationInputsFromCodeReality(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("agent-memory"));
        Files.createDirectories(workspace.resolve("platform-memory"));
        Files.createDirectories(workspace.resolve("docs"));
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("agent-memory/project-memory.md"), "React");
        Files.writeString(workspace.resolve("platform-memory/workspace-norms.md"), "React");
        Files.writeString(workspace.resolve("docs/design.md"), "React");
        Files.writeString(workspace.resolve("src/App.vue"), "Vue");

        ScanResult result = scanner.scan(workspace);

        assertEquals(List.of("src/App.vue"), result.getFiles().stream().map(ScannedSourceFile::getPath).toList());
    }

    @Test
    void scan_skipsLargeFiles(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("small.js"), "x");
        Files.write(workspace.resolve("big.js"), new byte[(int) WorkspaceFileScanner.MAX_FILE_BYTES + 1]);

        ScanResult result = scanner.scan(workspace);

        assertEquals(1, result.getTotalFiles());
        assertEquals("small.js", result.getFiles().get(0).getPath());
    }

    @Test
    void scan_budgetStopsAtMaxFiles(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        for (int i = 0; i < WorkspaceFileScanner.MAX_FILES + 10; i++) {
            Files.writeString(workspace.resolve("src/f" + i + ".js"), "console.log(" + i + ");");
        }

        ScanResult result = scanner.scan(workspace);

        assertEquals(WorkspaceFileScanner.MAX_FILES, result.getTotalFiles());
        assertTrue(result.isTruncated());
        assertEquals("maxFiles exceeded", result.getReason());
    }

    @Test
    void scan_computesFingerprintAndSummary(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("main.java"), "package com.example;\npublic class Main {}");

        ScanResult result = scanner.scan(workspace);

        assertEquals(1, result.getTotalFiles());
        ScannedSourceFile file = result.getFiles().get(0);
        assertNotNull(file.getFingerprint());
        assertEquals(64, file.getFingerprint().length());
        assertTrue(file.getSummary().contains("package com.example"));
    }

    @Test
    void scanPaths_onlyScansGivenFiles(@TempDir Path workspace) throws Exception {
        // WHY：CodingTask 后回写只需扫描变更文件，不能重新遍历整个工作区。
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/a.js"), "console.log('a');");
        Files.writeString(workspace.resolve("src/b.js"), "console.log('b');");
        Files.writeString(workspace.resolve("src/c.js"), "console.log('c');");

        ScanResult result = scanner.scanPaths(workspace, List.of("src/a.js", "src/c.js"));

        assertEquals(2, result.getTotalFiles());
        assertTrue(result.getFiles().stream().anyMatch(f -> f.getPath().equals("src/a.js")));
        assertTrue(result.getFiles().stream().anyMatch(f -> f.getPath().equals("src/c.js")));
        assertTrue(result.getFiles().stream().noneMatch(f -> f.getPath().equals("src/b.js")));
    }

    @Test
    void scanPaths_excludesMemoryAndDocumentationInputs(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("agent-memory"));
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("agent-memory/project-memory.md"), "React");
        Files.writeString(workspace.resolve("src/app.js"), "console.log('ok');");

        ScanResult result = scanner.scanPaths(workspace, List.of("agent-memory/project-memory.md", "src/app.js"));

        assertEquals(1, result.getTotalFiles());
        assertEquals("src/app.js", result.getFiles().get(0).getPath());
    }

    @Test
    void scanPaths_emptyPaths_returnsEmptyResult(@TempDir Path workspace) {
        ScanResult result = scanner.scanPaths(workspace, List.of());

        assertTrue(result.getFiles().isEmpty());
        assertFalse(result.isTruncated());
    }
}
