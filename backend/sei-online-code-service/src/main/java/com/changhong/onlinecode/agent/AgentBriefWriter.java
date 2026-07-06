package com.changhong.onlinecode.agent;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent runtime brief 写入器（PR3 #7）。参考 multica
 * {@code server/internal/daemon/execenv/runtime_config.go}。
 *
 * <p>spawn 前在 workdir 写托管 brief：codex → {@code AGENTS.md}，claude → {@code CLAUDE.md}
 * （codex 原生读 AGENTS.md，非 CLAUDE.md）。marker 块幂等注入——保留用户既有内容，重复运行
 * 替换块体而非追加。brief 内容为 agent identity（name + instructions），对齐 multica
 * {@code ## Agent Identity}。由 service 层在 spawn 前调用（multica 亦由 daemon 写、非 backend）。</p>
 *
 * <p><b>不做 cleanup</b>：SEI workdir 为临时区（PlanAgentService）或 worktree（DispatchService，
 * WorktreeManager 管理生命周期），非用户本地仓库，无需 byte-exact 回滚（multica 仅 local_directory
 * 流才 cleanup）。对齐「仅用最少代码」。marker 块仍提供幂等性（同 workdir 重跑不追加重复块）。</p>
 *
 * @author sei-online-code
 */
public final class AgentBriefWriter {

    static final String MARKER_BEGIN = "<!-- BEGIN SEI-RUNTIME (auto-managed; do not edit) -->";
    static final String MARKER_END = "<!-- END SEI-RUNTIME -->";

    private AgentBriefWriter() {
    }

    /**
     * 在 workdir 写托管 brief。workDir null/blank、cliTool 未知、或 name+instructions 皆空 → no-op。
     *
     * <p>cliTool null/blank → 默认 claude（对齐 {@link CliRunnerRegistry#DEFAULT_TOOL}），
     * 故默认 claude agent 仍写 CLAUDE.md。</p>
     *
     * @param workDir      codex/claude 工作目录路径（AGENTS.md/CLAUDE.md 写入此）
     * @param cliTool      "codex" → AGENTS.md，"claude"/null/blank → CLAUDE.md，其他 → skip
     * @param agentName    agent 名（可为 null/blank）
     * @param instructions agent 指令（可为 null/blank）
     * @param logger       日志器（可为 null）
     */
    public static void writeBrief(String workDir, String cliTool, String agentName,
                                  String instructions, String model, boolean hasMcpConfig, Logger logger) {
        writeBriefInternal(workDir, cliTool, agentName, instructions, model, hasMcpConfig, logger);
    }

    public static void writeBrief(String workDir, String cliTool, String agentName,
                                  String instructions, Logger logger) {
        writeBriefInternal(workDir, cliTool, agentName, instructions, null, false, logger);
    }

    private static void writeBriefInternal(String workDir, String cliTool, String agentName,
                                           String instructions, String model, boolean hasMcpConfig, Logger logger) {
        if (workDir == null || workDir.isBlank()) {
            return;
        }
        Path target = runtimeConfigPath(Path.of(workDir), cliTool);
        if (target == null) {
            return; // 未知 cliTool → prompt-only 模式
        }
        String brief = buildBrief(agentName, instructions, cliTool, model, hasMcpConfig);
        if (brief == null) {
            return; // name + instructions 皆空 → 不写
        }
        try {
            writeRuntimeFile(target, brief);
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("agent brief 写入失败 path={}", target, e);
            }
        }
    }

    /**
     * codex → AGENTS.md，claude（含 null/blank 默认）→ CLAUDE.md，其他 → null（skip）。
     * 对齐 multica {@code runtimeConfigPath}。
     */
    static Path runtimeConfigPath(Path workDir, String cliTool) {
        String tool = (cliTool == null || cliTool.isBlank()) ? CliRunnerRegistry.DEFAULT_TOOL : cliTool;
        switch (tool) {
            case "claude":
                return workDir.resolve("CLAUDE.md");
            case "codex":
                return workDir.resolve("AGENTS.md");
            default:
                return null;
        }
    }

    /**
     * 构建 agent identity brief。name + instructions 皆空 → 返回 null（不写文件）。
     */
    static String buildBrief(String agentName, String instructions) {
        return buildBrief(agentName, instructions, null, null, false);
    }

    /**
     * 构建 agent identity brief + runtime context。name + instructions 皆空 → 返回 null（不写文件）。
     * runtime context 暴露 cliTool/model/mcpConfig，让 agent 在 workdir 自感知运行环境。
     */
    static String buildBrief(String agentName, String instructions, String cliTool,
                             String model, boolean hasMcpConfig) {
        String name = agentName == null ? "" : agentName.trim();
        String instr = instructions == null ? "" : instructions.trim();
        if (name.isEmpty() && instr.isEmpty()) {
            return null;
        }
        String tool = (cliTool == null || cliTool.isBlank()) ? CliRunnerRegistry.DEFAULT_TOOL : cliTool;
        StringBuilder sb = new StringBuilder();
        sb.append("# SEI Agent Runtime\n\n");
        sb.append("## Agent Identity\n\n");
        if (!name.isEmpty()) {
            sb.append("**You are: ").append(name).append("**\n\n");
        }
        if (!instr.isEmpty()) {
            sb.append(instr).append('\n');
        }
        sb.append("## Runtime Context\n\n");
        sb.append("- CLI tool: ").append(tool).append('\n');
        if (model != null && !model.isBlank()) {
            sb.append("- Model: ").append(model.trim()).append('\n');
        }
        sb.append("- MCP config: ").append(hasMcpConfig ? "configured" : "not configured").append('\n');
        return sb.toString();
    }

    /**
     * marker 块幂等注入。参考 multica {@code writeRuntimeConfigFile}：
     * 文件缺失 → 仅写 marker 块；已有 marker → 替换块体；已有内容无 marker → append 分隔符 + 块。
     */
    private static void writeRuntimeFile(Path path, String brief) throws IOException {
        String block = MARKER_BEGIN + "\n" + stripTrailing(brief) + "\n" + MARKER_END + "\n";

        String existing = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        if (existing == null) {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, block, StandardCharsets.UTF_8);
            return;
        }
        int[] range = locateMarkerBlock(existing);
        if (range != null) {
            String newContent = existing.substring(0, range[0]) + block + existing.substring(range[1]);
            Files.writeString(path, newContent, StandardCharsets.UTF_8);
            return;
        }
        Files.writeString(path, existing + "\n\n" + block, StandardCharsets.UTF_8);
    }

    /**
     * 定位 marker 块 [start, end)。end 为 END marker 行尾换行后一位。
     * begin 缺失 → null；begin 有但无 END → 视块到串尾（下次写替换，避免半块下堆叠）。
     */
    private static int[] locateMarkerBlock(String content) {
        int start = content.indexOf(MARKER_BEGIN);
        if (start < 0) {
            return null;
        }
        int afterBegin = start + MARKER_BEGIN.length();
        int endRel = content.indexOf(MARKER_END, afterBegin);
        int end;
        if (endRel < 0) {
            end = content.length();
        } else {
            end = endRel + MARKER_END.length();
            if (end < content.length() && content.charAt(end) == '\n') {
                end++;
            }
        }
        return new int[]{start, end};
    }

    private static String stripTrailing(String s) {
        return s.replaceAll("\\s+$", "");
    }
}
