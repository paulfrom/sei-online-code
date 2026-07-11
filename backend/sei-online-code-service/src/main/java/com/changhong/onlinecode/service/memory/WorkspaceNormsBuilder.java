package com.changhong.onlinecode.service.memory;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作区规范聚合构建器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §7.1、§7.2。
 *
 * <p>WHY：原 scanner 与 assemble 各维护一份按 type 分桶的 {@code buildWorkspaceNorms}，且都只是简单过滤，
 * 既未按 priority 排序，也未尊重 {@link MemoryNormClaim#getOverrides()} 显式覆盖关系，更未区分 seed 基线
 * （priority {@code P5S}）与项目自维护高优先级 claim（{@code P0}）。结果 P0 项目记忆可能和 P5S seed
 * 同时出现在同一聚合桶里，{@code overrides} 字段建模后从未参与覆盖消解，规则在两侧也容易漂移。</p>
 *
 * <p>本组件统一两侧实现，形成可解释的覆盖闭环：先按 priority 升序排序（P0 最高、P5S seed 最低），
 * 每个桶内只保留该 type 下未被更高优先级 claim 显式 overrides 的项；被覆盖项不进入最终聚合。</p>
 *
 * @author sei-online-code
 */
@Component
public class WorkspaceNormsBuilder {

    /** priority 排序值：P0 最高，P5S seed 最低；未知 priority 退化为中间值 P3。 */
    private static int priorityRank(String priority) {
        if (priority == null) {
            return 30;
        }
        return switch (priority) {
            case "P0" -> 0;
            case "P1" -> 10;
            case "P2" -> 20;
            case "P4" -> 40;
            case "P5" -> 50;
            case "P5S" -> 60;
            default -> 30;
        };
    }

    /**
     * 构建工作区规范聚合。
     *
     * <p>覆盖算法：按 priority 升序排序后遍历，被任何已生效（更高优先级）claim 的 {@code overrides}
     * 列表命中的 claim 视为被覆盖，不进入聚合。同一 type 下高优先级 claim 优先于低优先级 claim 生效，
     * {@code P5S} seed 仅作为默认基线，被更高优先级项目自维护 claim 覆盖时让位。</p>
     *
     * @param norms 全量 norm claim 列表，可为 null
     * @return 不可变的 {@link WorkspaceNorms}
     */
    public WorkspaceNorms build(List<MemoryNormClaim> norms) {
        List<MemoryNormClaim> effective = effectiveClaims(norms);
        WorkspaceNorms n = new WorkspaceNorms();
        n.setProjectMemoryOverrides(filterByType(effective, Set.of("tech_stack", "domain_terms")));
        n.setHardRules(filterByType(effective, Set.of("hard_rule")));
        n.setPreferredDirection(filterByType(effective, Set.of("preferred_direction")));
        n.setForbiddenChoices(filterByType(effective, Set.of("forbidden_choice")));
        n.setDocumentationRules(filterByType(effective, Set.of("documentation_rule", "scan_focus", "required_sections")));
        n.setTestingAndDeliveryRules(filterByType(effective, Set.of("testing_and_delivery")));
        n.setSourceFiles(effective.stream().map(MemoryNormClaim::getSource)
                .filter(s -> s != null && !s.isBlank())
                .distinct().toList());
        return n;
    }

    /**
     * 解析覆盖关系，返回按 priority 升序、且未被更高优先级 claim overrides 命中的 claim 列表。
     */
    private List<MemoryNormClaim> effectiveClaims(List<MemoryNormClaim> norms) {
        if (norms == null || norms.isEmpty()) {
            return List.of();
        }
        List<MemoryNormClaim> sorted = new ArrayList<>(norms);
        sorted.sort(Comparator.comparingInt(c -> priorityRank(c.getPriority())));

        // 先收集所有已生效 claim 的 overrides 目标 id（按出现顺序），被命中的 claim 被剔除。
        Set<String> overriddenIds = new HashSet<>();
        for (MemoryNormClaim claim : sorted) {
            List<String> overrides = claim.getOverrides();
            if (overrides != null) {
                overriddenIds.addAll(overrides);
            }
        }
        List<MemoryNormClaim> effective = new ArrayList<>(sorted.size());
        for (MemoryNormClaim claim : sorted) {
            if (claim.getId() != null && overriddenIds.contains(claim.getId())) {
                // 被更高优先级 claim 显式 overrides，不再生效。
                continue;
            }
            effective.add(claim);
        }
        return effective;
    }

    private List<MemoryNormClaim> filterByType(List<MemoryNormClaim> norms, Set<String> types) {
        return norms.stream()
                .filter(n -> n.getType() != null && types.contains(n.getType()))
                .toList();
    }
}