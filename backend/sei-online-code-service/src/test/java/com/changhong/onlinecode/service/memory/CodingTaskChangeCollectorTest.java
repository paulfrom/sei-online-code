package com.changhong.onlinecode.service.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodingTaskChangeCollector 单元测试。
 *
 * <p>WHY：变更采集依赖系统 git 命令与文件系统，必须在真实临时仓库中验证命令执行、
 * 输出解析与失败降级行为，确保主流程不因 git 异常而中断。</p>
 *
 * @author sei-online-code
 */
class CodingTaskChangeCollectorTest {

    private CodingTaskChangeCollector collector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        collector = new CodingTaskChangeCollector();
    }

    @Test
    void collect_nonGitDirectory_returnsFailure() {
        CodingTaskChangeResult result = collector.collect(tempDir.toString(), null);
        assertFalse(result.isSuccess());
        assertNotNull(result.getFailureReason());
    }

    @Test
    void collect_noChanges_returnsEmptyList() throws Exception {
        initRepo();
        CodingTaskChangeResult result = collector.collect(tempDir.toString(), null);
        assertTrue(result.isSuccess());
        assertNotNull(result.getHeadCommit());
        assertTrue(result.getChangedFiles().isEmpty());
    }

    @Test
    void collect_uncommittedUntrackedChange_detectsFile() throws Exception {
        initRepo();
        Path file = tempDir.resolve("src/main/java/Example.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "public class Example {}", StandardCharsets.UTF_8);

        CodingTaskChangeResult result = collector.collect(tempDir.toString(), null);
        assertTrue(result.isSuccess());
        assertEquals(List.of("src/main/java/Example.java"), result.getChangedFiles());
        assertNotNull(result.getDiffStat());
        assertNotNull(result.getDiffSummary());
        assertTrue(result.getDiffSummary().contains("untracked files"));
        assertTrue(result.getFileSnippets().containsKey("src/main/java/Example.java"));
    }

    @Test
    void collect_trackedModifiedChange_detectsFile() throws Exception {
        initRepo();
        Path file = tempDir.resolve("tracked.java");
        Files.writeString(file, "v1", StandardCharsets.UTF_8);
        exec("git", "add", ".");
        exec("git", "commit", "-m", "base");

        Files.writeString(file, "v2", StandardCharsets.UTF_8);

        CodingTaskChangeResult result = collector.collect(tempDir.toString(), null);
        assertTrue(result.isSuccess());
        assertEquals(List.of("tracked.java"), result.getChangedFiles());
        assertNotNull(result.getDiffStat());
        assertNotNull(result.getDiffSummary());
    }

    @Test
    void collect_committedChange_withBaseCommit() throws Exception {
        initRepo();
        Path file = tempDir.resolve("Example.java");
        Files.writeString(file, "v1", StandardCharsets.UTF_8);
        exec("git", "add", ".");
        exec("git", "commit", "-m", "base");
        String baseCommit = exec("git", "rev-parse", "HEAD").trim();

        Files.writeString(file, "v2", StandardCharsets.UTF_8);
        exec("git", "add", ".");
        exec("git", "commit", "-m", "change");

        CodingTaskChangeResult result = collector.collect(tempDir.toString(), baseCommit);
        assertTrue(result.isSuccess());
        assertEquals(List.of("Example.java"), result.getChangedFiles());
        assertEquals(baseCommit, result.getBaseCommit());
    }

    @Test
    void resolveHead_returnsCommitCapturedBeforeCodingStarts() throws Exception {
        initRepo();
        Files.writeString(tempDir.resolve("base.txt"), "base", StandardCharsets.UTF_8);
        exec("git", "add", ".");
        exec("git", "commit", "-m", "base");

        String head = collector.resolveHead(tempDir.toString());

        assertEquals(exec("git", "rev-parse", "HEAD").trim(), head);
    }

    @Test
    void collect_deletedFile_recordsDeletedStatus() throws Exception {
        // WHY：删除文件必须解析出 D 状态，否则回写器无法识别需要清理的旧 source。
        initRepo();
        Path file = tempDir.resolve("ToDelete.java");
        Files.writeString(file, "v1", StandardCharsets.UTF_8);
        exec("git", "add", ".");
        exec("git", "commit", "-m", "base");
        String baseCommit = exec("git", "rev-parse", "HEAD").trim();

        Files.delete(file);
        exec("git", "add", "-A");
        exec("git", "commit", "-m", "remove");

        CodingTaskChangeResult result = collector.collect(tempDir.toString(), baseCommit);
        assertTrue(result.isSuccess());
        assertEquals(CodingTaskChangeResult.ChangeStatus.DELETED,
                result.getChangeStatuses().get("ToDelete.java"));
        assertTrue(result.deletedPaths().contains("ToDelete.java"));
    }

    @Test
    void collect_renamedFile_recordsDeletedOldAndRenamedNew() throws Exception {
        // WHY：重命名需同时记录旧路径 DELETED 与新路径 RENAMED，让回写器清理旧 source 并新增新 source。
        initRepo();
        Path oldFile = tempDir.resolve("Old.java");
        Files.writeString(oldFile, "class Old {}", StandardCharsets.UTF_8);
        exec("git", "add", ".");
        exec("git", "commit", "-m", "base");
        String baseCommit = exec("git", "rev-parse", "HEAD").trim();

        Files.move(oldFile, oldFile.resolveSibling("New.java"));
        exec("git", "add", "-A");
        exec("git", "commit", "-m", "rename");

        CodingTaskChangeResult result = collector.collect(tempDir.toString(), baseCommit);
        assertTrue(result.isSuccess());
        assertEquals(CodingTaskChangeResult.ChangeStatus.DELETED,
                result.getChangeStatuses().get("Old.java"));
        assertEquals(CodingTaskChangeResult.ChangeStatus.RENAMED,
                result.getChangeStatuses().get("New.java"));
        assertTrue(result.deletedPaths().contains("Old.java"));
    }

    private void initRepo() throws Exception {
        exec("git", "init");
        exec("git", "config", "user.email", "test@test.com");
        exec("git", "config", "user.name", "Test");
    }

    private String exec(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git command timeout: " + String.join(" ", command));
        }
        byte[] output = process.getInputStream().readAllBytes();
        String text = new String(output, StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git command failed: " + String.join(" ", command) + "\n" + text);
        }
        return text;
    }
}
