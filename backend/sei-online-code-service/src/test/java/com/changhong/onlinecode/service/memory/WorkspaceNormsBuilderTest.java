package com.changhong.onlinecode.service.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceNormsBuilder 单元测试。
 *
 * <p>WHY：NormClaim 覆盖闭环直接决定 prompt 注入的项目记忆是否“高优先级覆盖低优先级”、seed 是否
 * 仅作默认基线。若只按 type 分桶而不解 priority/overrides，P0 项目自维护记忆会与 P5S seed 同时生效，
 * overrides 字段建模后形同虚设，下游 DesignBasis 与设计校验都会基于错误规范。</p>
 *
 * @author sei-online-code
 */
class WorkspaceNormsBuilderTest {

    private final WorkspaceNormsBuilder builder = new WorkspaceNormsBuilder();

    @Test
    void build_p0OverridesP5SSeed_seedRemovedFromAggregate() {
        // WHY：P0 项目自维护记忆通过 overrides 显式声明覆盖 P5S seed 时，seed 必须让位，
        // 否则同一聚合桶里两套规范并存，下游 prd/设计无法确定以谁为准。覆盖闭环要求 overrides 字段
        // 在运行时真正生效，而不只是建模。
        MemoryNormClaim p5s = normClaim("seed-1", "tech_stack", "P5S", "前端使用 Vue", "seed.md");
        MemoryNormClaim p0 = normClaim("proj-1", "tech_stack", "P0", "前端使用 React", "project-memory.md");
        p0.setOverrides(List.of("seed-1"));

        WorkspaceNorms norms = builder.build(List.of(p5s, p0));

        assertTrue(norms.getProjectMemoryOverrides().stream().anyMatch(n -> "proj-1".equals(n.getId())));
        assertTrue(norms.getProjectMemoryOverrides().stream().noneMatch(n -> "seed-1".equals(n.getId())),
                "被 P0 显式 overrides 的 P5S seed 必须从有效聚合中剔除");
    }

    @Test
    void build_explicitOverridesField_removesOverriddenClaim() {
        // WHY：overrides 显式声明 P0 覆盖某 P1 claim 时，被覆盖项必须不进入最终聚合，
        // 否则 overrides 字段建模后无任何运行时效果，覆盖关系断裂。
        MemoryNormClaim p1 = normClaim("rule-1", "hard_rule", "P1", "禁止使用 antd", "memory-rules.md");
        MemoryNormClaim p0 = normClaim("rule-2", "hard_rule", "P0", "允许使用 antd", "project-memory.md");
        p0.setOverrides(List.of("rule-1"));

        WorkspaceNorms norms = builder.build(List.of(p1, p0));

        assertTrue(norms.getHardRules().stream().anyMatch(n -> "rule-2".equals(n.getId())));
        assertTrue(norms.getHardRules().stream().noneMatch(n -> "rule-1".equals(n.getId())),
                "被 overrides 命中的 claim 必须被剔除");
    }

    @Test
    void build_seedOnlyProject_keepsSeedAsBaseline() {
        // WHY：空项目仅存在 seed 时 seed 仍应作为默认基线生效，覆盖算法不应误删唯一来源。
        MemoryNormClaim seed = normClaim("seed-1", "tech_stack", "P5S", "默认使用 Vue", "seed.md");

        WorkspaceNorms norms = builder.build(List.of(seed));

        assertTrue(norms.getProjectMemoryOverrides().stream().anyMatch(n -> "seed-1".equals(n.getId())));
    }

    @Test
    void build_priorityOrderWithinBucket_preserved() {
        // WHY：同 type 同无 overrides 时，按 priority 升序排列，便于下游优先呈现高优先级 claim。
        MemoryNormClaim p5 = normClaim("a", "hard_rule", "P5", "低优先级规则", "rules.md");
        MemoryNormClaim p0 = normClaim("b", "hard_rule", "P0", "高优先级规则", "project-memory.md");

        WorkspaceNorms norms = builder.build(List.of(p5, p0));

        assertEquals("b", norms.getHardRules().get(0).getId(), "P0 应在 P5 之前");
    }

    private MemoryNormClaim normClaim(String id, String type, String priority, String content, String source) {
        MemoryNormClaim claim = new MemoryNormClaim();
        claim.setId(id);
        claim.setType(type);
        claim.setPriority(priority);
        claim.setContent(content);
        claim.setSource(source);
        claim.setConfidence("explicit");
        return claim;
    }
}