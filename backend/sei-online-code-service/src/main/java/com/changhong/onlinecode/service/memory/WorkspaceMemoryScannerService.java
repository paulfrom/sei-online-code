package com.changhong.onlinecode.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * WorkspaceMemory 扫描服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §10.4、§11。
 *
 * <p>读取 agent-memory、项目规范文件，并在固定预算内扫描代码现状；按优先级合并为
 * NormClaim / RealityClaim / ConflictFinding，最终产出 WorkspaceNorms 与 WorkspaceSnapshot。</p>
 *
 * @author sei-online-code
 */
@Service
public class WorkspaceMemoryScannerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceMemoryScannerService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> AGENT_MEMORY_FILES = Set.of(
            "project-memory.md", "memory-rules.md", "decisions.md", "modules.md");

    private static final Set<String> RULE_FILES = Set.of(
            "AGENTS.md", "CLAUDE.md", "README.md");

    private final WorkspaceFileScanner fileScanner;
    private final WorkspaceNormsBuilder normsBuilder;
    private final ConflictDetectionService conflictDetectionService;

    public WorkspaceMemoryScannerService(WorkspaceFileScanner fileScanner,
                                         WorkspaceNormsBuilder normsBuilder,
                                         ConflictDetectionService conflictDetectionService) {
        this.fileScanner = fileScanner;
        this.normsBuilder = normsBuilder;
        this.conflictDetectionService = conflictDetectionService;
    }

    /**
     * 全量扫描指定工作区并产出记忆结构。
     *
     * @param projectId     项目 id
     * @param workspacePath 工作区根目录绝对路径
     * @return 扫描结果
     */
    public WorkspaceMemoryScanResult scan(String projectId, String workspacePath) {
        Path root = Path.of(workspacePath == null ? "" : workspacePath);

        Map<String, MemoryFile> agentMemory = readAgentMemory(root);
        Map<String, MemoryFile> ruleMemory = readProjectRules(root);
        ScanResult codeScan = fileScanner.scan(root);

        return buildScanResult(projectId, root, agentMemory, ruleMemory, codeScan);
    }

    /**
     * 增量扫描：只扫描变更文件，用于 CodingTask 后记忆回写。
     *
     * <p>WHY：原实现仍调用 {@link #scan(String, String)} 全量扫描整个工作区，
     * 删除/重命名问题虽已解决，但性能上未获得增量收益。本方法只扫描 {@code changedFiles}
     * 中的代码文件；agent-memory 与项目规范文件仍全量读取（文件小且需当前完整规范）。</p>
     *
     * @param projectId     项目 id
     * @param workspacePath 工作区根目录绝对路径
     * @param changedFiles  本次变更的相对路径列表
     * @return 增量扫描结果
     */
    public WorkspaceMemoryScanResult scanIncremental(String projectId, String workspacePath, List<String> changedFiles) {
        Path root = Path.of(workspacePath == null ? "" : workspacePath);

        Map<String, MemoryFile> agentMemory = readAgentMemory(root);
        Map<String, MemoryFile> ruleMemory = readProjectRules(root);
        ScanResult codeScan = fileScanner.scanPaths(root, changedFiles);

        return buildScanResult(projectId, root, agentMemory, ruleMemory, codeScan);
    }

    private WorkspaceMemoryScanResult buildScanResult(String projectId,
                                                      Path root,
                                                      Map<String, MemoryFile> agentMemory,
                                                      Map<String, MemoryFile> ruleMemory,
                                                      ScanResult codeScan) {
        List<MemoryNormClaim> normClaims = new ArrayList<>();
        List<MemoryRealityClaim> realityClaims = new ArrayList<>();

        AtomicInteger idSeq = new AtomicInteger(1);

        // P0/P1/P2/P5S agent-memory
        agentMemory.forEach((fileName, file) -> {
            String basePriority = basePriorityForAgentFile(fileName, file.frontMatter);
            List<MemoryNormClaim> sectionClaims = parseSectionClaims(file, "agent-memory/" + fileName, basePriority, idSeq);
            normClaims.addAll(sectionClaims);
        });

        // P3 project rules
        ruleMemory.forEach((fileName, file) -> {
            normClaims.addAll(parseSectionClaims(file, fileName, "P3", idSeq));
        });

        // P4 code reality
        codeScan.getFiles().forEach(scanned -> {
            MemoryRealityClaim claim = new MemoryRealityClaim();
            claim.setId("reality-" + idSeq.getAndIncrement());
            claim.setType(realityTypeForPath(scanned.getPath()));
            claim.setContent(scanned.getPath() + " | " + scanned.getSummary());
            claim.setSource(scanned.getPath());
            claim.setSourceHash(scanned.getFingerprint());
            claim.setConfidence("source_backed");
            realityClaims.add(claim);
        });

        List<MemoryConflictFinding> conflicts = conflictDetectionService.detect(normClaims, realityClaims, idSeq);

        WorkspaceNorms norms = normsBuilder.build(normClaims);
        WorkspaceSnapshot snapshot = buildWorkspaceSnapshot(codeScan, agentMemory.get("modules.md"));

        String agentMemoryMarkdown = agentMemory.values().stream()
                .map(f -> "# " + f.fileName + "\n\n" + f.body)
                .collect(Collectors.joining("\n\n---\n\n"));
        String projectRuleMarkdown = ruleMemory.values().stream()
                .map(f -> "# " + f.fileName + "\n\n" + f.body)
                .collect(Collectors.joining("\n\n---\n\n"));

        WorkspaceMemoryScanResult result = new WorkspaceMemoryScanResult();
        result.setAgentMemoryFingerprint(sha256(agentMemoryMarkdown));
        result.setAgentMemoryMarkdown(agentMemoryMarkdown);
        result.setProjectRuleFingerprint(sha256(projectRuleMarkdown));
        result.setProjectRuleMarkdown(projectRuleMarkdown);
        result.setSourceFingerprintsJson(toJson(codeScan.getFiles().stream()
                .map(ScannedSourceFile::getFingerprint)
                .toList()));
        result.setNormClaims(List.copyOf(normClaims));
        result.setRealityClaims(List.copyOf(realityClaims));
        result.setConflictFindings(List.copyOf(conflicts));
        result.setWorkspaceNorms(norms);
        result.setWorkspaceSnapshot(snapshot);
        result.setScanTruncated(codeScan.isTruncated());
        result.setTruncatedReason(codeScan.getReason());
        return result;
    }

    // ============================ readers ============================

    private Map<String, MemoryFile> readAgentMemory(Path root) {
        Map<String, MemoryFile> result = new LinkedHashMap<>();
        Path dir = root.resolve("agent-memory");
        for (String fileName : AGENT_MEMORY_FILES) {
            MemoryFile file = readMemoryFile(dir.resolve(fileName), fileName);
            if (file != null) {
                result.put(fileName, file);
            }
        }
        return result;
    }

    private Map<String, MemoryFile> readProjectRules(Path root) {
        Map<String, MemoryFile> result = new LinkedHashMap<>();
        for (String fileName : RULE_FILES) {
            MemoryFile file = readMemoryFile(root.resolve(fileName), fileName);
            if (file != null) {
                result.put(fileName, file);
            }
        }
        Path docs = root.resolve("docs");
        if (Files.isDirectory(docs)) {
            try (Stream<Path> stream = Files.walk(docs, 4)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .forEach(p -> {
                            String relative = root.relativize(p).toString().replace('\\', '/');
                            MemoryFile file = readMemoryFile(p, relative);
                            if (file != null) {
                                result.put(relative, file);
                            }
                        });
            } catch (IOException e) {
                LOGGER.warn("memory-scan: 读取 docs 失败 projectId=?", e);
            }
        }
        return result;
    }

    private MemoryFile readMemoryFile(Path path, String fileName) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            FrontMatterParse parse = parseFrontMatter(raw);
            MemoryFile file = new MemoryFile();
            file.fileName = fileName;
            file.frontMatter = parse.frontMatter;
            file.body = parse.body;
            return file;
        } catch (IOException e) {
            LOGGER.warn("memory-scan: 读取文件失败 file={}", path, e);
            return null;
        }
    }

    private FrontMatterParse parseFrontMatter(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("---")) {
            return new FrontMatterParse(Collections.emptyMap(), raw);
        }
        int firstEnd = trimmed.indexOf('\n');
        int secondMarker = trimmed.indexOf("\n---", firstEnd + 1);
        if (secondMarker < 0) {
            return new FrontMatterParse(Collections.emptyMap(), raw);
        }
        Map<String, String> fm = new LinkedHashMap<>();
        String content = trimmed.substring(firstEnd + 1, secondMarker);
        for (String line : content.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                fm.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        String body = trimmed.substring(secondMarker + "\n---".length());
        if (body.startsWith("\n")) {
            body = body.substring(1);
        }
        return new FrontMatterParse(fm, body);
    }

    // ============================ claim builders ============================

    private String basePriorityForAgentFile(String fileName, Map<String, String> fm) {
        String reviewStatus = fm.getOrDefault("reviewStatus", "").toLowerCase(Locale.ROOT);
        String origin = fm.getOrDefault("origin", "").toLowerCase(Locale.ROOT);
        boolean reviewed = "reviewed".equals(reviewStatus) || "project".equals(origin);
        if (reviewed) {
            return "P0";
        }
        if ("platform_seed".equals(origin)) {
            return "P5S";
        }
        if ("memory-rules.md".equals(fileName)) {
            return "P2";
        }
        if ("project-memory.md".equals(fileName)) {
            return "P1";
        }
        return "P1";
    }

    private List<MemoryNormClaim> parseSectionClaims(MemoryFile file, String source, String basePriority, AtomicInteger idSeq) {
        List<MemoryNormClaim> claims = new ArrayList<>();
        String[] lines = file.body.split("\\r?\\n");
        String currentHeading = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("## ")) {
                flushSectionClaim(file, source, basePriority, idSeq, currentHeading, currentBody, claims);
                currentHeading = line.substring(3).trim();
                currentBody.setLength(0);
            } else if (currentHeading != null) {
                currentBody.append(line).append('\n');
            }
        }
        flushSectionClaim(file, source, basePriority, idSeq, currentHeading, currentBody, claims);
        return claims;
    }

    private void flushSectionClaim(MemoryFile file, String source, String basePriority, AtomicInteger idSeq,
                                   String heading, StringBuilder body, List<MemoryNormClaim> claims) {
        if (heading == null || body.toString().trim().isEmpty()) {
            return;
        }
        MemoryNormClaim claim = new MemoryNormClaim();
        claim.setId("norm-" + idSeq.getAndIncrement());
        claim.setType(sectionType(heading));
        claim.setContent(heading + "\n" + body.toString().trim());
        claim.setPriority(basePriority);
        claim.setSource(source);
        claim.setSourceHash(sha256(file.body));
        claim.setConfidence("P0".equals(basePriority) || "P1".equals(basePriority) ? "explicit" : "inferred");
        claims.add(claim);
    }

    private String sectionType(String heading) {
        String lower = heading.toLowerCase(Locale.ROOT);
        if (lower.contains("tech") || lower.contains("stack") || lower.contains("技术栈")) {
            return "tech_stack";
        }
        if (lower.contains("hard") || lower.contains("rule") || lower.contains("规则")) {
            return "hard_rule";
        }
        if (lower.contains("forbidden") || lower.contains("禁止")) {
            return "forbidden_choice";
        }
        if (lower.contains("module") || lower.contains("模块")) {
            return "module";
        }
        if (lower.contains("decision") || lower.contains("决策")) {
            return "decision";
        }
        return "norm";
    }

    private String realityTypeForPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return lower.contains("/test/") ? "backend_test" : "backend_code";
        }
        if (lower.endsWith(".tsx") || lower.endsWith(".jsx") || lower.endsWith(".ts")
                || lower.endsWith(".js") || lower.endsWith(".vue")) {
            if (lower.contains("/pages/") || lower.contains("/routes/")) {
                return "frontend_page";
            }
            return "frontend_component";
        }
        if (lower.endsWith(".sql")) {
            return "migration";
        }
        if (lower.endsWith(".gradle") || lower.endsWith("pom.xml") || lower.contains("package.json")) {
            return "build_config";
        }
        return "source_file";
    }

    // ============================ aggregates ============================

    private WorkspaceSnapshot buildWorkspaceSnapshot(ScanResult scan, MemoryFile modulesFile) {
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setModules(modulesFile == null ? Collections.emptyList() : parseModules(modulesFile));
        snapshot.setEntrypoints(scan.getFiles().stream()
                .filter(f -> f.getPath().contains("/main/") || f.getPath().contains("/app.") || f.getPath().contains("/bootstrap"))
                .map(ScannedSourceFile::getPath)
                .toList());
        snapshot.setApiSurface(scan.getFiles().stream()
                .filter(f -> f.getPath().contains("/controller/") || f.getPath().contains("/api/"))
                .map(ScannedSourceFile::getPath)
                .toList());
        snapshot.setDataModel(scan.getFiles().stream()
                .filter(f -> f.getPath().contains("/entity/") || f.getPath().contains("/dto/") || f.getPath().endsWith(".sql"))
                .map(ScannedSourceFile::getPath)
                .toList());
        snapshot.setUiSurface(scan.getFiles().stream()
                .filter(f -> f.getPath().contains("/pages/") || f.getPath().contains("/components/"))
                .map(ScannedSourceFile::getPath)
                .toList());
        snapshot.setStateModel(scan.getFiles().stream()
                .filter(f -> f.getPath().contains("/store/") || f.getPath().contains("/models/"))
                .map(ScannedSourceFile::getPath)
                .toList());
        snapshot.setIntegrationPoints(scan.getFiles().stream()
                .filter(f -> f.getPath().contains("/client/") || f.getPath().contains("/integration/"))
                .map(ScannedSourceFile::getPath)
                .toList());
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxFiles", WorkspaceFileScanner.MAX_FILES);
        limits.put("maxFileBytes", WorkspaceFileScanner.MAX_FILE_BYTES);
        limits.put("maxTotalBytes", WorkspaceFileScanner.MAX_TOTAL_BYTES);
        limits.put("maxDepth", WorkspaceFileScanner.MAX_DEPTH);
        limits.put("scannedFiles", scan.getTotalFiles());
        limits.put("totalBytes", scan.getTotalBytes());
        limits.put("truncated", scan.isTruncated());
        limits.put("truncatedReason", scan.getReason());
        snapshot.setScanLimits(limits);
        snapshot.setSourceFiles(List.copyOf(scan.getFiles()));
        return snapshot;
    }

    private List<String> parseModules(MemoryFile modulesFile) {
        List<String> modules = new ArrayList<>();
        for (String line : modulesFile.body.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ") || trimmed.startsWith("- ")) {
                modules.add(trimmed.replaceFirst("^(## |- )", "").trim());
            }
        }
        return modules;
    }

    // ============================ utilities ============================

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    private static final class MemoryFile {
        String fileName;
        Map<String, String> frontMatter;
        String body;
    }

    private record FrontMatterParse(Map<String, String> frontMatter, String body) {
    }
}
