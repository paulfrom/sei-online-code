package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryNormClaim;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.onlinecode.service.memory.WorkspaceMemoryScanResult;
import com.changhong.onlinecode.service.memory.WorkspaceMemoryScannerService;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 需求级设计上下文服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §10.5、§14。
 *
 * <p>职责：PRD/概览/详细设计生成前按需准备 {@link RequirementDesignContext}；做需求相关补扫；
 * 生成 RequirementConflictReport 与 DesignBasis；在同一事务中归档旧 CURRENT 后写入新 CURRENT。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
@AllArgsConstructor
public class RequirementDesignContextService {

    private final RequirementDao requirementDao;
    private final ProjectDao projectDao;
    private final WorkspaceMemoryService workspaceMemoryService;
    private final WorkspaceMemoryScannerService scannerService;
    private final RequirementDesignContextDao requirementDesignContextDao;

    /**
     * 查询需求当前 CURRENT RequirementDesignContext。
     *
     * @param requirementId 需求 id
     * @return 当前上下文；不存在返回 null
     */
    public RequirementDesignContext findCurrentByRequirement(String requirementId) {
        return requirementDesignContextDao.findByRequirementIdAndStatus(requirementId, MemoryRecordStatus.CURRENT);
    }

    /**
     * 准备（或复用）需求设计上下文。
     *
     * <p>流程：<ol>
     *   <li>读取 Requirement；</li>
     *   <li>确保项目存在 CURRENT WorkspaceMemory；</li>
     *   <li>基于需求关键词做补扫；</li>
     *   <li>生成 RequirementConflictReport 与 DesignBasis；</li>
     *   <li>归档旧 CURRENT，写入新 CURRENT。</li>
     * </ol></p>
     *
     * @param requirementId 需求 id
     * @return 新 CURRENT RequirementDesignContext
     */
    @Transactional(rollbackFor = Exception.class)
    public RequirementDesignContext prepare(String requirementId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (Objects.isNull(requirement)) {
            throw new IllegalStateException("需求不存在: " + requirementId);
        }
        String projectId = requirement.getProjectId();
        Project project = projectDao.findOne(projectId);
        if (Objects.isNull(project)) {
            throw new IllegalStateException("项目不存在: " + projectId);
        }

        WorkspaceMemory workspaceMemory = workspaceMemoryService.ensureCurrentWorkspaceMemory(projectId);
        if (Objects.isNull(workspaceMemory)) {
            throw new IllegalStateException("无法确保 WorkspaceMemory CURRENT: " + projectId);
        }

        String workspacePath = project.getWorkspacePath();
        WorkspaceMemoryScanResult scan = scannerService.scan(projectId,
                workspacePath == null || workspacePath.isBlank() ? null : workspacePath);

        List<MemoryNormClaim> relevantNorms = filterRelevantClaims(scan.getNormClaims(), requirement);
        List<MemoryRealityClaim> relevantRealities = filterRelevantRealities(scan.getRealityClaims(), requirement);
        List<MemoryConflictFinding> relevantConflicts = filterRelevantFindings(scan.getConflictFindings(), requirement);

        RequirementConflictReport conflictReport = buildConflictReport(relevantConflicts);
        String designBasis = buildDesignBasis(workspaceMemory, requirement, relevantNorms, relevantRealities,
                relevantConflicts);

        requirementDesignContextDao.archiveCurrent(requirementId, MemoryRecordStatus.CURRENT, MemoryRecordStatus.ARCHIVED);
        int nextVersion = nextVersion(requirementId);
        RequirementDesignContext context = new RequirementDesignContext();
        context.setProjectId(projectId);
        context.setRequirementId(requirementId);
        context.setWorkspaceMemoryId(workspaceMemory.getId());
        context.setVersion(nextVersion);
        context.setStatus(MemoryRecordStatus.CURRENT);
        context.setContextStatus(RequirementDesignContextStatus.READY);
        context.setRequirementFingerprint(fingerprintRequirement(requirement));
        context.setRequirementRelatedSnapshotJson(toJson(relevantRealities));
        context.setRequirementConflictReportJson(toJson(conflictReport));
        context.setDesignBasis(designBasis);
        context.setSourceFingerprintsJson(scan.getSourceFingerprintsJson());
        context.setGeneratedAt(new Date());
        RequirementDesignContext saved = requirementDesignContextDao.save(context);
        log.info("requirement-context: 已准备 requirementId={}, contextId={}, workspaceMemoryId={}",
                requirementId, saved.getId(), workspaceMemory.getId());
        return saved;
    }

    /**
     * 使当前上下文失效（改为 STALE）。
     *
     * @param requirementId 需求 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void invalidate(String requirementId) {
        RequirementDesignContext current = findCurrentByRequirement(requirementId);
        if (Objects.nonNull(current)
                && current.getContextStatus() != RequirementDesignContextStatus.FAILED) {
            current.setContextStatus(RequirementDesignContextStatus.STALE);
            requirementDesignContextDao.save(current);
        }
    }

    // ============================ private helpers ============================

    private int nextVersion(String requirementId) {
        List<RequirementDesignContext> history = requirementDesignContextDao
                .findByRequirementIdOrderByVersionDesc(requirementId);
        if (history.isEmpty()) {
            return 1;
        }
        return Objects.requireNonNullElse(history.get(0).getVersion(), 0) + 1;
    }

    private String fingerprintRequirement(Requirement requirement) {
        String text = Objects.requireNonNullElse(requirement.getTitle(), "")
                + "\n" + Objects.requireNonNullElse(requirement.getDescription(), "");
        return sha256(text);
    }

    private <T extends MemoryNormClaim> List<T> filterRelevantClaims(List<T> claims, Requirement requirement) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        String keywords = (requirement.getTitle() + " " + requirement.getDescription()).toLowerCase();
        return claims.stream()
                .filter(c -> matchesKeywords(c.getContent() + " " + c.getSource(), keywords))
                .toList();
    }

    private List<MemoryRealityClaim> filterRelevantRealities(List<MemoryRealityClaim> claims, Requirement requirement) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        String keywords = (requirement.getTitle() + " " + requirement.getDescription()).toLowerCase();
        return claims.stream()
                .filter(c -> matchesKeywords(c.getContent() + " " + c.getSource(), keywords))
                .toList();
    }

    private List<MemoryConflictFinding> filterRelevantFindings(List<MemoryConflictFinding> findings,
                                                                    Requirement requirement) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        String keywords = (requirement.getTitle() + " " + requirement.getDescription()).toLowerCase();
        return findings.stream()
                .filter(f -> matchesKeywords(f.getSummary() + " " + f.getType(), keywords))
                .toList();
    }

    /**
     * 判断 claim 文本是否匹配需求关键词。
     *
     * <p>WHY：原实现在未命中任何关键词时无条件 {@code return true}，使需求级 RealityClaim/NormClaim
     * 过滤退化为全量注入，注入大量与当前需求无关的代码事实与规范噪声，削弱 source-backed 设计质量。
     * 正确语义是“需求关键词命中才相关”；但当需求文本无法提取出可用关键词（标题/描述全为空或仅单字符）
     * 时，没有任何关键词可比对，此时退化到全取以避免漏注入全部上下文。</p>
     *
     * @param text      claim/findings 文本
     * @param keywords  需求关键词原始字符串（标题 + 空格 + 描述，小写）
     * @return true 表示相关
     */
    private boolean matchesKeywords(String text, String keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        List<String> usable = usableKeywords(keywords);
        if (usable.isEmpty()) {
            // 无可用关键词时没有可比对项，退化到全取避免漏注入。
            return true;
        }
        String lower = text.toLowerCase();
        for (String keyword : usable) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从原始需求关键词字符串提取可用于匹配的关键词：长度 &gt; 1 的非空白 token。
     */
    private List<String> usableKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        List<String> usable = new ArrayList<>();
        for (String token : keywords.split("\\s+")) {
            if (token.length() > 1) {
                usable.add(token);
            }
        }
        return usable;
    }

    private RequirementConflictReport buildConflictReport(List<MemoryConflictFinding> conflicts) {
        RequirementConflictReport report = new RequirementConflictReport();
        report.setHigh(filterBySeverity(conflicts, "HIGH"));
        report.setMedium(filterBySeverity(conflicts, "MEDIUM"));
        report.setLow(filterBySeverity(conflicts, "LOW"));
        return report;
    }

    private List<MemoryConflictFinding> filterBySeverity(List<MemoryConflictFinding> conflicts, String severity) {
        return conflicts.stream()
                .filter(c -> severity.equalsIgnoreCase(c.getSeverity()))
                .toList();
    }

    private String buildDesignBasis(WorkspaceMemory workspaceMemory,
                                    Requirement requirement,
                                    List<MemoryNormClaim> norms,
                                    List<MemoryRealityClaim> realities,
                                    List<MemoryConflictFinding> conflicts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Design Basis\n\n");
        sb.append("## Requirement\n");
        sb.append("**标题**: ").append(Objects.requireNonNullElse(requirement.getTitle(), "")).append("\n\n");
        sb.append("**描述**: ").append(Objects.requireNonNullElse(requirement.getDescription(), "")).append("\n\n");
        sb.append("## Active Workspace Memory\n");
        sb.append("- WorkspaceMemory v").append(workspaceMemory.getVersion())
                .append(" (id=").append(workspaceMemory.getId()).append(")\n");
        sb.append("- freshness: ").append(workspaceMemory.getFreshness()).append("\n");
        sb.append("\n## Applicable Norms\n");
        for (MemoryNormClaim norm : norms) {
            sb.append("- **").append(norm.getPriority()).append("** ")
                    .append(norm.getContent().replace("\n", " ")).append("\n");
        }
        sb.append("\n## Relevant Current Reality\n");
        for (MemoryRealityClaim reality : realities) {
            sb.append("- ").append(reality.getContent()).append("\n");
        }
        sb.append("\n## Conflicts To Address\n");
        for (MemoryConflictFinding conflict : conflicts) {
            sb.append("- **[").append(conflict.getSeverity()).append("]** ")
                    .append(conflict.getSummary()).append("\n");
        }
        sb.append("\n## Decisions\n");
        norms.stream()
                .filter(n -> "decision".equals(n.getType()) || "confirmed".equals(n.getType()))
                .forEach(n -> sb.append("- ").append(n.getContent().replace("\n", " ")).append("\n"));
        sb.append("\n## Constraints For PRD\n");
        sb.append("- 必须包含与现有系统关系、复用/扩展/重构范围、冲突与待确认项、非目标、验收标准、规范符合性说明\n");
        sb.append("\n## Constraints For Overview Design\n");
        sb.append("- 必须包含模块清单表、模块与现有代码映射、新增/复用/调整模块说明、架构影响、接口/页面/数据影响范围、风险与待确认\n");
        sb.append("\n## Constraints For Detailed Design\n");
        sb.append("- 必须包含当前模块目标、职责边界、现有代码影响点、新增/修改文件建议、接口设计、数据模型影响、页面/组件设计、状态流转、异常处理、测试要点、兼容和回归风险\n");
        sb.append("\n## Constraints For Coding\n");
        sb.append("- 必须遵守 Hard Rules 与 Forbidden Choices；高严重度冲突需显式处理或确认\n");
        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 RequirementDesignContext JSON 字段失败", e);
        }
    }

    private static String sha256(String content) {
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

    /**
     * 需求冲突报告内部结构（不暴露为 DTO）。
     */
    private static final class RequirementConflictReport {
        private List<MemoryConflictFinding> high;
        private List<MemoryConflictFinding> medium;
        private List<MemoryConflictFinding> low;

        public List<MemoryConflictFinding> getHigh() { return high; }
        public void setHigh(List<MemoryConflictFinding> high) { this.high = high; }
        public List<MemoryConflictFinding> getMedium() { return medium; }
        public void setMedium(List<MemoryConflictFinding> medium) { this.medium = medium; }
        public List<MemoryConflictFinding> getLow() { return low; }
        public void setLow(List<MemoryConflictFinding> low) { this.low = low; }
    }
}
