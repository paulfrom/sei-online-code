package com.changhong.onlinecode.service.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConflictDetectionService 单元测试。
 *
 * <p>WHY：冲突检测是初始化扫描与 CodingTask 回写共享的规则源，必须保证两侧使用同一份规则、
 * 且严重度分级正确——HIGH 是技术栈根本对立需澄清，MEDIUM 是版本/产品差异只需确认，
 * 低优先级 norm 不参与检测以免 seed 噪声淹没真实冲突。规则表条目数断言防止规则被无意删减。</p>
 *
 * @author sei-online-code
 */
class ConflictDetectionServiceTest {

    private final ConflictDetectionService service = new ConflictDetectionService();
    private final AtomicInteger idSeq = new AtomicInteger(1);

    @Test
    void detect_techStackOpposition_returnsHighClarify() {
        // WHY：react vs vue 是技术栈根本对立，必须 HIGH + clarify，引导用户显式澄清而非默认放行。
        MemoryNormClaim norm = norm("norm-1", "hard_rule", "P0", "前端必须使用 React");
        MemoryRealityClaim reality = reality("reality-1", "src/App.vue", "Vue component");

        List<MemoryConflictFinding> findings = service.detect(List.of(norm), List.of(reality), idSeq);

        assertEquals(1, findings.size());
        MemoryConflictFinding f = findings.get(0);
        assertEquals("HIGH", f.getSeverity());
        assertEquals("clarify", f.getRecommendedHandling());
        assertEquals(List.of("norm-1"), f.getNormClaimIds());
        assertEquals(List.of("reality-1"), f.getRealityClaimIds());
        assertTrue(f.getSummary().contains("react") && f.getSummary().contains("vue"));
        assertEquals("conflict-1", f.getId());
    }

    @Test
    void detect_versionDifference_returnsMediumConfirm() {
        // WHY：Java 17 vs 21 不互斥，仅是版本差异，定为 MEDIUM + confirm，避免与根本对立混为一谈。
        MemoryNormClaim norm = norm("norm-1", "tech_stack", "P0", "目标版本 Java 17");
        MemoryRealityClaim reality = reality("reality-1", "build.gradle", "使用 Java 21 编译");

        List<MemoryConflictFinding> findings = service.detect(List.of(norm), List.of(reality), idSeq);

        assertEquals(1, findings.size());
        assertEquals("MEDIUM", findings.get(0).getSeverity());
        assertEquals("confirm", findings.get(0).getRecommendedHandling());
    }

    @Test
    void detect_lowPriorityNormSkipped() {
        // WHY：P3+ 的 norm 多为 seed/规则文件噪声，若参与检测会用泛化文本制造海量假冲突。
        MemoryNormClaim norm = norm("norm-1", "hard_rule", "P3", "前端必须使用 React");
        MemoryRealityClaim reality = reality("reality-1", "src/App.vue", "Vue component");

        List<MemoryConflictFinding> findings = service.detect(List.of(norm), List.of(reality), idSeq);

        assertTrue(findings.isEmpty(), "P2 以下优先级 norm 不参与冲突检测");
    }

    @Test
    void detect_noOpposition_returnsEmpty() {
        MemoryNormClaim norm = norm("norm-1", "hard_rule", "P0", "前端必须使用 React");
        MemoryRealityClaim reality = reality("reality-1", "src/App.tsx", "React component");

        List<MemoryConflictFinding> findings = service.detect(List.of(norm), List.of(reality), idSeq);

        assertTrue(findings.isEmpty());
    }

    @Test
    void detect_nullArgs_returnEmpty() {
        assertTrue(service.detect(null, List.of(), idSeq).isEmpty());
        assertTrue(service.detect(List.of(), null, idSeq).isEmpty());
    }

    @Test
    void detect_multipleNormsAssignDistinctFindingIds() {
        MemoryNormClaim reactRule = norm("norm-1", "hard_rule", "P0", "前端必须使用 React");
        MemoryNormClaim gradleRule = norm("norm-2", "tech_stack", "P0", "构建使用 Gradle");
        MemoryRealityClaim vue = reality("reality-1", "src/App.vue", "Vue");
        MemoryRealityClaim maven = reality("reality-2", "pom.xml", "Maven build");

        List<MemoryConflictFinding> findings = service.detect(List.of(reactRule, gradleRule), List.of(vue, maven), idSeq);

        assertEquals(2, findings.size());
        assertNotEquals(findings.get(0).getId(), findings.get(1).getId());
    }

    @Test
    void oppositionPairCount_coversTechAndVersionRules() {
        // WHY：规则表是两侧共享的唯一规则源，条目数断言防止重构时被无意删减而静默退化。
        // 8 条技术栈对立（5 组双向）+ 4 条版本/产品差异（2 组双向）= 12。
        assertEquals(12, ConflictDetectionService.oppositionPairCount());
    }

    // ============================ fixtures ============================

    private MemoryNormClaim norm(String id, String type, String priority, String content) {
        MemoryNormClaim c = new MemoryNormClaim();
        c.setId(id);
        c.setType(type);
        c.setPriority(priority);
        c.setContent(content);
        c.setSource("agent-memory/project-memory.md");
        c.setConfidence("explicit");
        return c;
    }

    private MemoryRealityClaim reality(String id, String source, String content) {
        MemoryRealityClaim c = new MemoryRealityClaim();
        c.setId(id);
        c.setType("source_file");
        c.setContent(content);
        c.setSource(source);
        c.setSourceHash("h-" + id);
        c.setConfidence("source_backed");
        return c;
    }
}