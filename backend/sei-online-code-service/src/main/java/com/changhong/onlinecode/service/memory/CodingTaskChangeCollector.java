package com.changhong.onlinecode.service.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CodingTask 后代码变更采集器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §16.2。
 *
 * <p>在 CodingTask 执行的工作区中执行系统 {@code git} 命令，收集变更文件列表、diff stat、diff 摘要与
 * 变更文件片段。若环境无 git 或命令失败，返回 {@code success=false}，由调用方降级到全量重建。</p>
 *
 * <p>使用 {@code git diff --name-status} 解析每个文件的变更状态（A/M/D/R），供 assembler 在删除或
 * 重命名时移除旧 RealityClaim、snapshot source 与 fingerprint，避免旧事实残留。</p>
 *
 * @author sei-online-code
 */
@Component
public class CodingTaskChangeCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodingTaskChangeCollector.class);

    /** 默认最大变更文件数；超过则不再读取片段，由调用方判断是否降级。 */
    private static final int MAX_CHANGED_FILES_FOR_SNIPPETS = 20;

    /** 单个文件片段最大字节数。 */
    private static final int MAX_SNIPPET_BYTES = 8 * 1024;

    /** Git 命令默认超时（秒）。 */
    private static final int GIT_TIMEOUT_SECONDS = 30;

    /**
     * 读取工作区当前 HEAD，供 CodingTask 在修改代码前持久化增量比较基准。
     * 无提交或 Git 命令失败时返回 null，后续回写将按原有未提交变更模式采集。
     */
    public String resolveHead(String worktreePath) {
        Path root = worktreePath == null ? null : Path.of(worktreePath);
        if (root == null || !Files.exists(root) || !Files.exists(root.resolve(".git"))) {
            return null;
        }
        String head = execGit(root, List.of("git", "rev-parse", "HEAD"));
        return head == null || head.isBlank() ? null : head.trim();
    }

    /**
     * 采集指定工作区相对于 base commit 或 HEAD 的代码变更。
     *
     * @param worktreePath 工作区根目录（必须为 git 仓库）
     * @param baseCommit   diff base commit；为空则比较工作区与 HEAD（未提交变更）
     * @return 变更结果
     */
    public CodingTaskChangeResult collect(String worktreePath, String baseCommit) {
        Path root = worktreePath == null ? null : Path.of(worktreePath);
        if (root == null || !Files.exists(root)) {
            return CodingTaskChangeResult.failure("工作区路径不存在: " + worktreePath);
        }
        if (!Files.exists(root.resolve(".git"))) {
            return CodingTaskChangeResult.failure("工作区不是 git 仓库: " + worktreePath);
        }

        String headCommit = execGit(root, List.of("git", "rev-parse", "HEAD"));
        boolean unbornHead = headCommit == null || headCommit.isBlank();
        if (unbornHead) {
            headCommit = "UNBORN";
        }

        String diffRange = (baseCommit == null || baseCommit.isBlank()) ? "HEAD" : baseCommit + "..HEAD";
        // 使用 --name-status 解析 A/M/D/R，供回写时清理删除与重命名产生的旧 source。
        List<String> diffNameStatusCommand = unbornHead
                ? List.of("git", "diff", "--name-status")
                : List.of("git", "diff", "--name-status", diffRange);
        List<StatusEntry> trackedEntries = parseNameStatus(execGit(root, diffNameStatusCommand));
        List<String> untrackedFiles = parseLines(execGit(root, List.of("git", "ls-files", "--others", "--exclude-standard")));

        List<String> changedFiles = new ArrayList<>();
        Map<String, CodingTaskChangeResult.ChangeStatus> changeStatuses = new LinkedHashMap<>();
        for (StatusEntry entry : trackedEntries) {
            applyStatus(changeStatuses, changedFiles, entry);
        }
        for (String untracked : untrackedFiles) {
            applyStatus(changeStatuses, changedFiles, new StatusEntry(untracked, "A"));
        }

        List<String> diffStatCommand = unbornHead
                ? List.of("git", "diff", "--stat")
                : List.of("git", "diff", "--stat", diffRange);
        List<String> diffCommand = unbornHead
                ? List.of("git", "diff", "--no-color")
                : List.of("git", "diff", "--no-color", diffRange);
        String diffStat = execGit(root, diffStatCommand);
        String trackedDiffSummary = truncate(execGit(root, diffCommand), 96 * 1024);
        String untrackedSummary = buildUntrackedSummary(root, untrackedFiles);
        String diffSummary = concatDiffSummary(trackedDiffSummary, untrackedSummary);

        Map<String, String> snippets = new LinkedHashMap<>();
        if (changedFiles.size() <= MAX_CHANGED_FILES_FOR_SNIPPETS) {
            for (String file : changedFiles) {
                Path filePath = root.resolve(file);
                String snippet = readSnippet(filePath);
                if (snippet != null && !snippet.isBlank()) {
                    snippets.put(file, snippet);
                }
            }
        }

        CodingTaskChangeResult result = new CodingTaskChangeResult();
        result.setSuccess(true);
        result.setHeadCommit(headCommit.trim());
        result.setBaseCommit(baseCommit);
        result.setChangedFiles(changedFiles);
        result.setChangeStatuses(changeStatuses);
        result.setDiffStat(diffStat);
        result.setDiffSummary(diffSummary);
        result.setFileSnippets(snippets);
        return result;
    }

    /**
     * 将单条 name-status 解析结果写入累积结构。
     * 旧路径（DELETED 或 REName 旧路径）记为 {@link CodingTaskChangeResult.ChangeStatus#DELETED}，
     * 供 assembler 清理旧 source；新路径（ADDED/MODIFIED/REName 新路径）加入 changedFiles。
     */
    private void applyStatus(Map<String, CodingTaskChangeResult.ChangeStatus> changeStatuses,
                            List<String> changedFiles,
                            StatusEntry entry) {
        if (entry == null || entry.newPath == null || entry.newPath.isBlank()) {
            return;
        }
        switch (entry.code) {
            case "D":
                changeStatuses.put(entry.newPath, CodingTaskChangeResult.ChangeStatus.DELETED);
                changedFiles.add(entry.oldPath != null ? entry.oldPath : entry.newPath);
                break;
            case "A", "M", "T":
                changeStatuses.put(entry.newPath, mapCode(entry.code));
                changedFiles.add(entry.newPath);
                break;
            case "R", "C":
                // 重命名/复制：旧路径记 DELETED 用于清理，新路径记 RENAMED。
                if (entry.oldPath != null && !entry.oldPath.isBlank()) {
                    changeStatuses.put(entry.oldPath, CodingTaskChangeResult.ChangeStatus.DELETED);
                    changedFiles.add(entry.oldPath);
                }
                changeStatuses.put(entry.newPath, CodingTaskChangeResult.ChangeStatus.RENAMED);
                changedFiles.add(entry.newPath);
                break;
            default:
                // X 等未知合并标记退化为 MODIFIED，保持向后兼容。
                changeStatuses.put(entry.newPath, CodingTaskChangeResult.ChangeStatus.MODIFIED);
                changedFiles.add(entry.newPath);
        }
    }

    private CodingTaskChangeResult.ChangeStatus mapCode(String code) {
        return switch (code) {
            case "A" -> CodingTaskChangeResult.ChangeStatus.ADDED;
            case "D" -> CodingTaskChangeResult.ChangeStatus.DELETED;
            case "R", "C" -> CodingTaskChangeResult.ChangeStatus.RENAMED;
            default -> CodingTaskChangeResult.ChangeStatus.MODIFIED;
        };
    }

    /**
     * 解析 {@code git diff --name-status} 输出。每行格式：
     * <ul>
     *   <li>{@code M\tpath} / {@code A\tpath} / {@code D\tpath}</li>
     *   <li>{@code R100\told\tnew} / {@code C50\told\tnew}（重命名/复制带相似度）</li>
     * </ul>
     */
    private java.util.List<StatusEntry> parseNameStatus(String output) {
        java.util.List<StatusEntry> entries = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return entries;
        }
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\t");
            if (parts.length < 2) {
                continue;
            }
            String code = parts[0].replaceAll("[0-9]+$", "");
            String oldPath = parts.length >= 3 ? parts[1] : null;
            String newPath = parts.length >= 3 ? parts[2] : parts[1];
            entries.add(new StatusEntry(newPath, oldPath, code));
        }
        return entries;
    }

    private String buildUntrackedSummary(Path root, List<String> untrackedFiles) {
        if (untrackedFiles == null || untrackedFiles.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- untracked files ---\n");
        for (String file : untrackedFiles) {
            sb.append("A\t").append(file).append("\n");
        }
        return sb.toString();
    }

    private String concatDiffSummary(String trackedDiffSummary, String untrackedSummary) {
        if (untrackedSummary == null) {
            return trackedDiffSummary;
        }
        if (trackedDiffSummary == null) {
            return untrackedSummary;
        }
        return trackedDiffSummary + untrackedSummary;
    }

    private String execGit(Path worktree, List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(worktree.toFile());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOGGER.warn("coding-task-memory: git 命令超时 command={}", command);
                return null;
            }
            if (process.exitValue() != 0) {
                String error = readStream(process);
                LOGGER.warn("coding-task-memory: git 命令失败 command={}, exit={}, output={}",
                        command, process.exitValue(), error);
                return null;
            }
            return readStream(process).trim();
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("coding-task-memory: git 命令异常 command={}", command, e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String readStream(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private List<String> parseLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    /** name-status 单行解析中间结构：newPath 为最终路径，oldPath 在重命名/复制时为旧路径。 */
    private static final class StatusEntry {
        final String newPath;
        final String oldPath;
        final String code;

        StatusEntry(String newPath, String code) {
            this(newPath, null, code);
        }

        StatusEntry(String newPath, String oldPath, String code) {
            this.newPath = newPath;
            this.oldPath = oldPath;
            this.code = code == null ? "M" : code;
        }
    }

    private String readSnippet(Path filePath) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > MAX_SNIPPET_BYTES) {
                text = text.substring(0, MAX_SNIPPET_BYTES) + "\n... (truncated)";
            }
            return text;
        } catch (IOException e) {
            LOGGER.warn("coding-task-memory: 读取文件片段失败 file={}", filePath, e);
            return null;
        }
    }

    private String truncate(String text, int maxBytes) {
        if (text == null) {
            return null;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        String truncated = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
        return truncated + "\n... (truncated " + (bytes.length - maxBytes) + " bytes)";
    }
}
