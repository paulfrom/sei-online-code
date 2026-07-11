package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryNormClaim;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.onlinecode.service.memory.WorkspaceNorms;
import com.changhong.onlinecode.service.memory.WorkspaceSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DesignMemoryValidationService 单元测试。
 *
 * <p>WHY：校验服务决定设计文档能否进入 REVIEW，必须验证三类核心语义：
 * 1) 结构化 forbidden choice 命中即 FAILED；2) 声称复用不存在的模块给出 WARNING；
 * 3) 影响点必须命中真实 RealityClaim.source；以及 PASSED/WARNING/FAILED 三态流转。
 * 旧实现用占位启发（只识 Vue/antd、只判 JSON 空否、只判斜杠、summary 前 40 字符匹配），
 * 这些用例正是为锁死结构化比对不被回退。</p>
 *
 * @author sei-online-code
 */
class DesignMemoryValidationServiceTest {

    private final WorkspaceMemoryDao workspaceMemoryDao = mock(WorkspaceMemoryDao.class);
    private final DesignMemoryValidationService service = new DesignMemoryValidationService(workspaceMemoryDao);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validate_returnsPassedWhenAllSectionsPresentAndNoConflicts() {
        // WHY：PASSED 是三态基线，必须保证结构齐全、无冲突时不误报。
        RequirementDesignContext context = contextWithMemory(null);
        String document = fullPrdWith("设计依据：ctx-1\n");

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.PRD, document, context);

        assertEquals(MemoryValidationStatus.PASSED, result.getStatus());
    }

    @Test
    void validate_failsWhenRequiredSectionMissing() {
        RequirementDesignContext context = contextWithMemory(null);
        String document = """
                # PRD
                ## 复用/扩展/重构范围
                ## 冲突与待确认项
                ## 非目标
                ## 验收标准
                ## 规范符合性
                设计依据：ctx-1
                """;

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.PRD, document, context);

        assertEquals(MemoryValidationStatus.FAILED, result.getStatus());
        assertTrue(result.getFindings().stream().anyMatch(f -> f.getMessage().contains("与现有系统关系")));
    }

    @Test
    void validate_highConflictReferencedByIdsDoesNotFlag() {
        // WHY：高严重度冲突若已用 conflict id / norm id 在文档显式标注，应视为已处理，不报 FAILED。
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx-1");
        context.setRequirementConflictReportJson("""
                {"high":[{"id":"conflict-7","severity":"HIGH","summary":"必须使用统一鉴权组件",
                          "normClaimIds":["norm-3"],"recommendedHandling":"补充鉴权设计"}],
                 "medium":[],"low":[]}
                """);
        String document = fullPrdWith("已处理 conflict-7 / norm-3：统一接入鉴权组件。\n设计依据：ctx-1\n");

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.PRD, document, context);

        assertFalse(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("必须使用统一鉴权组件")),
                "已用 conflict id 引用的高严重度冲突不应再判为遗漏");
        assertNotFailed(result);
    }

    @Test
    void validate_highConflictOmittedFailsWithSummaryInMessage() {
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx-1");
        context.setRequirementConflictReportJson("""
                {"high":[{"severity":"HIGH","summary":"必须使用统一鉴权组件","recommendedHandling":"补充鉴权设计"}],
                 "medium":[],"low":[]}
                """);
        String document = fullPrdWith("设计依据：ctx-1\n");

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.PRD, document, context);

        assertEquals(MemoryValidationStatus.FAILED, result.getStatus());
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("必须使用统一鉴权组件")));
    }

    @Test
    void validate_structuredForbiddenChoiceHitsAnyToken() {
        // WHY：不再硬编码 Vue/antd；结构化 forbidden_claim 中任意技术 token 命中即 FAILED。
        WorkspaceMemory memory = memoryWithNorms(forbiddenChoice("禁止使用 Vue 框架"));
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(memory);
        RequirementDesignContext context = contextWithMemory("wm-1");
        String document = fullPrdWith("前端选用 Vue 3。\n设计依据：ctx-1\n");

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.PRD, document, context);

        assertEquals(MemoryValidationStatus.FAILED, result.getStatus());
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("Forbidden Choice") && f.getMessage().contains("vue")));
    }

    @Test
    void validate_reuseOfNonExistentModuleWarns() {
        // WHY：声称复用的是真实存在的模块/路径才放行，否则 WARNING——防止凭空复用幻觉；
        // 明确写“不复用”的不应误判。
        WorkspaceMemory memory = memoryWithSnapshotAndRealities(
                List.of("UserRepo"), // modules 真实存在
                List.of(reality("src/main/java/OrderService.java")));
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(memory);
        RequirementDesignContext context = contextWithMemory("wm-1");
        String document = fullOverviewWith("复用 OrderService，不复用 PhantomRepo，正向复用 GhostRepo。\n设计依据：ctx-1\n");

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.OVERVIEW, document, context);

        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("ghostrepo")
                        && "WARNING".equals(f.getSeverity())),
                "不存在模块应 WARNING");
        // 命中真实模块/路径的不应报；“不复用”的也不应报
        assertFalse(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("orderservice")));
        assertFalse(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("phantomrepo")));
    }

    @Test
    void validate_sourceBackedImpactMustHitRealSource() {
        // WHY：影响点段必须命中真实 RealityClaim.source；只写斜杠、写不存在的路径应 WARNING。
        WorkspaceMemory memory = memoryWithSnapshotAndRealities(List.of(),
                List.of(reality("src/main/java/PaymentService.java")));
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(memory);
        RequirementDesignContext context = contextWithMemory("wm-1");
        String detailed = """
                # 详细设计
                ## 当前模块目标
                ## 职责边界
                ## 现有代码影响点
                影响点：some/random/path/not-in-reality
                ## 新增/修改文件建议
                ## 接口设计
                ## 数据模型影响
                ## 测试要点
                设计依据：ctx-1
                """;

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.DETAILED, detailed, context);

        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "WARNING".equals(f.getSeverity())
                        && f.getMessage().contains("影响点未命中任何真实代码 source")));
    }

    @Test
    void validate_sourceBackedImpactHitsRealSourceDoesNotWarn() {
        WorkspaceMemory memory = memoryWithSnapshotAndRealities(List.of(),
                List.of(reality("src/main/java/PaymentService.java")));
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(memory);
        RequirementDesignContext context = contextWithMemory("wm-1");
        String detailed = """
                # 详细设计
                ## 当前模块目标
                ## 职责边界
                ## 现有代码影响点
                影响点：src/main/java/PaymentService.java
                ## 新增/修改文件建议
                ## 接口设计
                ## 数据模型影响
                ## 测试要点
                设计依据：ctx-1
                """;

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.DETAILED, detailed, context);

        assertFalse(result.getFindings().stream()
                .anyMatch(f -> f.getMessage().contains("影响点未命中任何真实代码 source")));
        assertNotFailed(result);
    }

    @Test
    void validate_malformedConflictJsonDoesNotCrash() {
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx-1");
        context.setRequirementConflictReportJson("not-json");
        String document = fullPrdWith("设计依据：ctx-1\n");

        DesignMemoryValidationService.ValidationResult result =
                service.validate(DesignMemoryValidationService.DocumentType.PRD, document, context);

        // malformed 不应抛异常，也不应误报 HIGH 冲突遗漏
        assertNotFailed(result);
    }

    // ============================ assertions helpers ============================

    private static void assertNotFailed(DesignMemoryValidationService.ValidationResult result) {
        assertFalse(result.getFindings().stream().anyMatch(f -> "HIGH".equals(f.getSeverity())),
                "不应有 HIGH finding: " + result.getFindings());
    }

    // ============================ fixtures ============================

    private RequirementDesignContext contextWithMemory(String workspaceMemoryId) {
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx-1");
        context.setWorkspaceMemoryId(workspaceMemoryId);
        return context;
    }

    private String fullPrdWith(String extra) {
        return """
                # PRD
                ## 与现有系统关系
                ## 复用/扩展/重构范围
                ## 冲突与待确认项
                ## 非目标
                ## 验收标准
                ## 规范符合性
                """ + extra;
    }

    private String fullOverviewWith(String extra) {
        return """
                # 概览设计
                ## 模块清单
                ## 模块与现有代码映射
                ## 新增/复用/调整模块
                ## 架构影响
                ## 接口/页面/数据影响范围
                ## 风险与待确认
                """ + extra;
    }

    private WorkspaceMemory memoryWithNorms(MemoryNormClaim forbidden) {
        WorkspaceMemory memory = new WorkspaceMemory();
        WorkspaceNorms norms = new WorkspaceNorms();
        norms.setForbiddenChoices(List.of(forbidden));
        memory.setWorkspaceNormsJson(toJsonSafe(norms));
        memory.setRealityClaimsJson("[]");
        memory.setWorkspaceSnapshotJson(toJsonSafe(new WorkspaceSnapshot()));
        return memory;
    }

    private WorkspaceMemory memoryWithSnapshotAndRealities(List<String> modules, List<MemoryRealityClaim> realities) {
        WorkspaceMemory memory = new WorkspaceMemory();
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
        snapshot.setModules(modules);
        memory.setWorkspaceNormsJson(toJsonSafe(new WorkspaceNorms()));
        memory.setRealityClaimsJson(toJsonSafe(realities));
        memory.setWorkspaceSnapshotJson(toJsonSafe(snapshot));
        return memory;
    }

    private MemoryNormClaim forbiddenChoice(String content) {
        MemoryNormClaim c = new MemoryNormClaim();
        c.setId("norm-f");
        c.setType("forbidden_choice");
        c.setPriority("P0");
        c.setContent(content);
        c.setSource("agent-memory/memory-rules.md");
        c.setConfidence("explicit");
        return c;
    }

    private MemoryRealityClaim reality(String source) {
        MemoryRealityClaim c = new MemoryRealityClaim();
        c.setId("reality-1");
        c.setType("backend_code");
        c.setContent(source + " | summary");
        c.setSource(source);
        c.setSourceHash("h");
        c.setConfidence("source_backed");
        return c;
    }

    private String toJsonSafe(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}