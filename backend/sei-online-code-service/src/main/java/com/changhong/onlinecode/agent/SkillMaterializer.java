package com.changhong.onlinecode.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SkillMaterializer（B21）。契约 Phase 3 §3，参考 multica {@code daemon/local_skills.go}。
 *
 * <p>在每次 per-task {@link ClaudeRunner} spawn 前，把已解析 agent 绑定的技能写入
 * worktree 的 {@code .claude/skills/<name>/}：</p>
 * <pre>
 * dir/SKILL.md          = skill.content
 * dir/.lock             = skill.computedHash   // 复现标记
 * dir/&lt;file.path&gt;      = file.content         // Phase 5：辅助文件（含子目录）
 * </pre>
 *
 * <p>幂等：磁盘上 {@code .lock} 与 {@code computedHash} 一致则跳过重写（SKILL.md 与辅助文件一并跳过）。
 * 辅助文件<b>不进</b> §6 hash recipe——{@code .lock} 仍只覆盖 SKILL.md 五元组；import 以 name 去重且无
 * update 端点 → 辅助文件导入后不可变 → 不影响幂等。本阶段 compile-only，但 materializer 是真实可单测
 * 的方法；真实 worktree 路径解析与 spawn 仍是 Phase 2 的 {@code TODO(oma-deferred)} 运行期接缝。
 * {@code .claude/skills/} 布局与 CLI 对齐。</p>
 *
 * <p>路径安全：辅助文件 path 经 service 层 @Valid 校验（禁绝对/{@code ..} 段）；materializer 再加
 * {@code normalize() + startsWith(dir)} 越界 guard 作 defense-in-depth，越界路径 warn + 跳过，绝不写出
 * 技能目录之外。</p>
 *
 * @author sei-online-code
 */
@Component
@Slf4j
public class SkillMaterializer {

    private static final String SKILL_FILE = "SKILL.md";
    private static final String LOCK_FILE = ".lock";

    /**
     * 待 materialize 的单个技能载体（name / content / computedHash / files）。
     *
     * <p>刻意与 {@code Skill} 实体解耦，便于单测直接构造、也避免 materializer 依赖持久层。
     * {@code files} 为相对 {@code .claude/skills/<name>/} 的辅助文件列表。</p>
     *
     * @param name         技能名（映射目录名）
     * @param content      SKILL.md 正文
     * @param computedHash 内容锁（写入 .lock，用于幂等比对）
     * @param files        辅助文件列表（可空，规范化为空列表）
     */
    public record SkillPayload(String name, String content, String computedHash,
                               List<SkillFileRef> files) {
        public SkillPayload(String name, String content, String computedHash) {
            this(name, content, computedHash, List.of());
        }

        public SkillPayload {
            files = files == null ? List.of() : files;
        }
    }

    /**
     * 辅助文件载体（相对路径 + 正文）。与 {@code SkillFile} 实体解耦。
     *
     * @param path    相对技能目录的路径（允许子目录）
     * @param content 文件正文
     */
    public record SkillFileRef(String path, String content) {
    }

    /**
     * 将一组技能写入 worktree 的 {@code .claude/skills/<name>/}，幂等。
     *
     * @param worktreePath worktree 根目录
     * @param skills       待写入技能
     * @return 实际发生写入（新建或更新）的技能数（幂等跳过的不计入）
     */
    public int materialize(String worktreePath, List<SkillPayload> skills) {
        if (worktreePath == null || worktreePath.isBlank() || skills == null || skills.isEmpty()) {
            return 0;
        }
        Path skillsRoot = Path.of(worktreePath, ".claude", "skills");
        int written = 0;
        for (SkillPayload skill : skills) {
            try {
                if (writeSkill(skillsRoot, skill)) {
                    written++;
                }
            } catch (IOException e) {
                log.warn("materialize: 写入技能失败 name={} path={}", skill.name(), worktreePath, e);
            }
        }
        return written;
    }

    /**
     * 写入单个技能目录，幂等：磁盘 {@code .lock} 与 {@code computedHash} 一致则跳过。
     *
     * @param skillsRoot {@code <worktree>/.claude/skills}
     * @param skill      技能载体
     * @return true 表示实际写入；false 表示幂等跳过
     * @throws IOException 目录创建或文件写入失败
     */
    private boolean writeSkill(Path skillsRoot, SkillPayload skill) throws IOException {
        Path dir = skillsRoot.resolve(skill.name());
        Path lock = dir.resolve(LOCK_FILE);

        // 幂等：已有 .lock 且与 computedHash 一致 → 跳过重写（SKILL.md + 辅助文件一并跳过）
        if (Files.exists(lock)) {
            String onDisk = Files.readString(lock, StandardCharsets.UTF_8);
            if (skill.computedHash() != null && skill.computedHash().equals(onDisk)) {
                return false;
            }
        }

        Files.createDirectories(dir);
        Files.writeString(dir.resolve(SKILL_FILE),
                skill.content() == null ? "" : skill.content(), StandardCharsets.UTF_8);
        Files.writeString(lock,
                skill.computedHash() == null ? "" : skill.computedHash(), StandardCharsets.UTF_8);
        writeAuxFiles(dir, skill);
        return true;
    }

    /**
     * 写入辅助文件到 {@code <dir>/<path>}，含子目录。越界路径（normalize 后不在 dir 下）warn + 跳过。
     *
     * @param dir   技能目录 {@code <worktree>/.claude/skills/<name>}
     * @param skill 技能载体
     * @throws IOException 父目录创建或文件写入失败
     */
    private void writeAuxFiles(Path dir, SkillPayload skill) throws IOException {
        for (SkillFileRef file : skill.files()) {
            if (file == null || file.path() == null || file.path().isBlank()) {
                continue;
            }
            Path resolved = dir.resolve(file.path()).normalize();
            if (!resolved.startsWith(dir)) {
                log.warn("materialize: 辅助文件路径越界，跳过 name={} path={}", skill.name(), file.path());
                continue;
            }
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved,
                    file.content() == null ? "" : file.content(), StandardCharsets.UTF_8);
        }
    }
}
