package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.onlinecode.entity.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * agent-memory 模板初始化服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §3.1、§6.1、§10.2、§13。
 *
 * <p>从项目绑定 seed 模板读取四个 agent-memory 文件内容，写入目标工作区 {@code agent-memory/} 目录：
 * 不覆盖已存在文件；缺文件补齐；写入时保留 seed front matter（{@code origin=platform_seed}、
 * {@code memorySeedTemplateId}、{@code memorySeedTemplateCode}、{@code agentMemorySeedVersion}、
 * {@code reviewStatus=unreviewed}）。项目未绑定时解析全局默认 seed 并写回 {@link Project}。</p>
 *
 * <p>平台不自动修改已存在的 agent-memory 文件（契约 §3.1、§6.1.2）。</p>
 *
 * @author sei-online-code
 */
@Service
public class AgentMemoryTemplateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentMemoryTemplateService.class);

    /** 工作区内 agent-memory 目录相对路径。 */
    public static final String AGENT_MEMORY_DIR = "agent-memory";

    private static final List<String> SEED_FILES = List.of(
            MemorySeedTemplateService.FILE_PROJECT_MEMORY,
            MemorySeedTemplateService.FILE_MEMORY_RULES,
            MemorySeedTemplateService.FILE_DECISIONS,
            MemorySeedTemplateService.FILE_MODULES);

    /** 写入 seed 文件时由平台权威值覆盖/补齐的 front matter 字段（契约 §6.1.1）。 */
    private static final Set<String> SEED_FM_OVERRIDE_KEYS = Set.of(
            "owner", "origin", "memorySeedTemplateId", "memorySeedTemplateCode",
            "agentMemorySeedVersion", "reviewStatus");

    private final ProjectDao projectDao;
    private final MemorySeedTemplateService seedTemplateService;

    public AgentMemoryTemplateService(ProjectDao projectDao,
                                       MemorySeedTemplateService seedTemplateService) {
        this.projectDao = projectDao;
        this.seedTemplateService = seedTemplateService;
    }

    /**
     * 给指定工作区初始化 agent-memory 四文件（缺文件补齐，不覆盖已有）。
     *
     * <p>项目已绑定模板：使用其 id 解析（即使已归档仍可沿用，契约 §6 现有实体修改 §9.1）。
     * 未绑定：解析当前全局默认 seed 并写回 Project.memorySeedTemplateId（契约 §13.1、§13.2）。</p>
     *
     * @param projectId 项目 id（用于解析绑定模板与写回默认绑定）
     * @param workspacePath 工作区根目录绝对路径
     * @return 已补齐的文件相对路径列表（仅含本次写入的文件；已存在文件不在此列）
     */
    public List<String> ensureAgentMemory(String projectId, String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalStateException("工作区路径为空，无法初始化 agent-memory: " + projectId);
        }
        Project project = projectId == null ? null : projectDao.findOne(projectId);
        MemorySeedTemplate seed = resolveAndBindSeed(project);
        if (Objects.isNull(seed)) {
            LOGGER.warn("agent-memory: 无可用 seed 模板，跳过初始化 projectId={}", projectId);
            return List.of();
        }

        Path agentMemoryDir = Path.of(workspacePath, AGENT_MEMORY_DIR);
        try {
            Files.createDirectories(agentMemoryDir);
        } catch (IOException e) {
            throw new IllegalStateException("创建 agent-memory 目录失败: " + agentMemoryDir, e);
        }

        Map<String, String> templates = collectTemplates(seed);
        List<String> written = new java.util.ArrayList<>();
        for (String fileName : SEED_FILES) {
            Path target = agentMemoryDir.resolve(fileName);
            if (Files.exists(target)) {
                // 已有文件绝不覆盖（契约 §3.1、§13.2）
                continue;
            }
            String body = templates.get(fileName);
            if (body == null || body.isBlank()) {
                continue;
            }
            writeFile(target, body);
            written.add(AGENT_MEMORY_DIR + "/" + fileName);
        }
        LOGGER.info("agent-memory: 初始化完成 projectId={}, seedId={}, seedVersion={}, written={}",
                projectId, seed.getId(), seed.getVersion(), written);
        return written;
    }

    /**
     * 返回项目当前绑定 seed 的版本。未绑定项目会按初始化规则绑定全局默认模板。
     */
    public Integer resolveSeedVersion(String projectId) {
        Project project = projectId == null ? null : projectDao.findOne(projectId);
        MemorySeedTemplate seed = resolveAndBindSeed(project);
        return seed == null ? null : seed.getVersion();
    }

    /**
     * 解析项目绑定 seed：项目已有 {@code memorySeedTemplateId} 则用之；否则解析全局默认并写回 Project。
     */
    private MemorySeedTemplate resolveAndBindSeed(Project project) {
        if (Objects.isNull(project)) {
            return seedTemplateService.findActiveDefault();
        }
        MemorySeedTemplate seed = seedTemplateService.resolveForProject(project.getMemorySeedTemplateId());
        if (Objects.nonNull(seed)) {
            // 未绑定模板回填默认绑定（契约 §9.1、§13.1）；已绑定不覆盖
            if (project.getMemorySeedTemplateId() == null || project.getMemorySeedTemplateId().isBlank()) {
                project.setMemorySeedTemplateId(seed.getId());
                projectDao.save(project);
            }
            return seed;
        }
        // 兜底：DB 暂无默认模板，尝试 bootstrap
        return seedTemplateService.bootstrapDefaultIfAbsent();
    }

    /**
     * 从 seed 模板实体收集四个文件正文，并按 §6.1.1 规范注入/校正 seed front matter。
     *
     * <p>不依赖用户配置模板正文自觉携带规范 front matter：解析已有 front matter 保留项目侧字段，
     * 强制写入/覆盖四个平台权威字段：{@code origin: platform_seed}、{@code memorySeedTemplateId}、
     * {@code memorySeedTemplateCode}、{@code agentMemorySeedVersion}、{@code reviewStatus: unreviewed}。
     * 文件无 front matter 时前置一段。这样即使用户配置模板正文缺这些字段，写入工作区的 seed 文件也满足契约。</p>
     */
    private Map<String, String> collectTemplates(MemorySeedTemplate seed) {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put(MemorySeedTemplateService.FILE_PROJECT_MEMORY, seed.getProjectMemoryTemplate());
        templates.put(MemorySeedTemplateService.FILE_MEMORY_RULES, seed.getMemoryRulesTemplate());
        templates.put(MemorySeedTemplateService.FILE_DECISIONS, seed.getDecisionsTemplate());
        templates.put(MemorySeedTemplateService.FILE_MODULES, seed.getModulesTemplate());
        String templateId = seed.getId();
        String templateCode = seed.getCode();
        String seedVersion = String.valueOf(seed.getVersion());
        templates.replaceAll((fileName, body) -> injectSeedFrontMatter(body, templateId, templateCode, seedVersion));
        return templates;
    }

    /**
     * 注入/校正 seed front matter。保留 front matter 内其它已存在字段（如 owner、lastReviewedAt），
     * 仅以平台权威值覆盖/补齐五个 seed 字段。
     */
    private String injectSeedFrontMatter(String body, String templateId, String templateCode, String seedVersion) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String trimmed = body;
        boolean hasFm = trimmed.startsWith("---\n") || trimmed.startsWith("---\r\n");
        if (!hasFm) {
            // 无 front matter 时前置一段并接原正文
            return seedFrontMatterBlock(templateId, templateCode, seedVersion) + "\n" + trimmed;
        }
        int firstEnd = trimmed.indexOf('\n');
        int secondMarker = trimmed.indexOf("\n---", firstEnd);
        if (secondMarker < 0) {
            // 形似 front matter 但无闭合，保守前置标准段
            return seedFrontMatterBlock(templateId, templateCode, seedVersion) + "\n" + trimmed;
        }
        int fmBodyStart = secondMarker + "\n---".length();
        String fmContent = trimmed.substring(firstEnd + 1, secondMarker);
        String afterFm = trimmed.substring(fmBodyStart);
        StringBuilder rebuilt = new StringBuilder();
        for (String line : fmContent.split("\\r?\\n")) {
            String key = frontMatterKey(line);
            if (key != null && SEED_FM_OVERRIDE_KEYS.contains(key)) {
                continue; // 丢弃已有平台权威字段，由标准段重写
            }
            rebuilt.append(line).append('\n');
        }
        return seedFrontMatterBlock(templateId, templateCode, seedVersion)
                + rebuilt + afterFm;
    }

    private String frontMatterKey(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return line.substring(0, colon).trim();
    }

    private String seedFrontMatterBlock(String templateId, String templateCode, String seedVersion) {
        return "---\n"
                + "owner: project\n"
                + "origin: platform_seed\n"
                + "memorySeedTemplateId: " + templateId + "\n"
                + "memorySeedTemplateCode: " + templateCode + "\n"
                + "agentMemorySeedVersion: " + seedVersion + "\n"
                + "reviewStatus: unreviewed\n"
                + "lastReviewedAt:\n"
                + "---\n";
    }

    private void writeFile(Path target, String content) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("写入 agent-memory 文件失败: " + target, e);
        }
    }
}
