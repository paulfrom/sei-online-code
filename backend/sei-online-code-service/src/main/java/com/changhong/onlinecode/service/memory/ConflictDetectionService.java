package com.changhong.onlinecode.service.memory;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一冲突检测服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §7.3、§11.3、§16.3。
 *
 * <p>WHY：原 {@link WorkspaceMemoryScannerService} 与 {@link CodingTaskMemoryUpdateAssembler}
 * 各维护一份近乎同构的 {@code CONTRADICTION_PAIRS} + {@code detectConflicts} + {@code findContradiction}，
 * 规则只在两侧分别演化，存在漂移风险；且所有命中统一标记为 {@code HIGH} / {@code clarify}，
 * 没有 MEDIUM/LOW 分级。本组件收口为唯一规则源，并按对立性质区分严重度与建议处理方式。</p>
 *
 * <p>第一版仍是确定性关键词规则，后续可替换为 LLM 语义判定而不改变本契约。</p>
 *
 * @author sei-online-code
 */
@Component
public class ConflictDetectionService {

    /**
     * 单条对立规则。
     */
    @AllArgsConstructor
    private static final class OppositionPair {
        final String normKey;
        final String realityKey;
        final String severity;
        final String recommendedHandling;
    }

    /**
     * 对立规则表。WHY：技术栈根本对立（选其一即排除另一）为 HIGH，需澄清；
     * 版本或同生态下的产品差异为 MEDIUM，仅需确认。
     */
    private static final List<OppositionPair> OPPOSITION_PAIRS = List.of(
            new OppositionPair("react", "vue", "HIGH", "clarify"),
            new OppositionPair("vue", "react", "HIGH", "clarify"),
            new OppositionPair("java", "kotlin", "HIGH", "clarify"),
            new OppositionPair("kotlin", "java", "HIGH", "clarify"),
            new OppositionPair("gradle", "maven", "HIGH", "clarify"),
            new OppositionPair("maven", "gradle", "HIGH", "clarify"),
            new OppositionPair("antd", "suid", "HIGH", "clarify"),
            new OppositionPair("umijs", "next.js", "HIGH", "clarify"),
            // 版本/同生态产品差异：不直接互斥，但需显式确认是否并存
            new OppositionPair("java 17", "java 21", "MEDIUM", "confirm"),
            new OppositionPair("java 21", "java 17", "MEDIUM", "confirm"),
            new OppositionPair("postgresql", "mysql", "MEDIUM", "confirm"),
            new OppositionPair("mysql", "postgresql", "MEDIUM", "confirm"));

    /** 暴露规则条目数，便于测试断言规则表未被无意删减。 */
    static int oppositionPairCount() {
        return OPPOSITION_PAIRS.size();
    }

    /**
     * 检测 norm 与 reality 之间的冲突。
     *
     * <p>语义与原两侧实现保持一致：仅对优先级 ≤ P2 的 norm 检测（{@code priority > P2} 跳过），
     * 命中后生成 {@link MemoryConflictFinding}，severity 与 recommendedHandling 来自规则表。
     * id 由 {@code idSeq} 分配，保持与调用方其余 claim/finding id 递增序列一致。</p>
     *
     * @param norms    全量 norm claim
     * @param realities 全量 reality claim
     * @param idSeq    finding id 分配序列
     * @return 新检测到的冲突列表；无冲突返回空表
     */
    public List<MemoryConflictFinding> detect(List<MemoryNormClaim> norms,
                                                 List<MemoryRealityClaim> realities,
                                                 AtomicInteger idSeq) {
        List<MemoryConflictFinding> findings = new ArrayList<>();
        if (norms == null || realities == null) {
            return findings;
        }
        for (MemoryNormClaim norm : norms) {
            if (norm == null) {
                continue;
            }
            String priority = norm.getPriority();
            if (priority != null && priority.compareTo("P2") > 0) {
                continue; // 仅高优先级项目规范与代码现状做冲突检测
            }
            String normText = Objects.requireNonNullElse(norm.getContent(), "").toLowerCase(Locale.ROOT);
            if (normText.isBlank()) {
                continue;
            }
            for (MemoryRealityClaim reality : realities) {
                if (reality == null) {
                    continue;
                }
                String realityText = Objects.requireNonNullElse(reality.getContent(), "").toLowerCase(Locale.ROOT);
                OppositionPair hit = findOpposition(normText, realityText);
                if (hit != null) {
                    MemoryConflictFinding finding = new MemoryConflictFinding();
                    finding.setId("conflict-" + idSeq.getAndIncrement());
                    finding.setType(norm.getType());
                    finding.setSeverity(hit.severity);
                    finding.setSummary("项目记忆要求: " + hit.normKey + " vs " + hit.realityKey
                            + "，但代码现状为: " + reality.getContent());
                    finding.setNormClaimIds(List.of(norm.getId()));
                    finding.setRealityClaimIds(List.of(reality.getId()));
                    finding.setRecommendedHandling(hit.recommendedHandling);
                    findings.add(finding);
                }
            }
        }
        return findings;
    }

    private OppositionPair findOpposition(String normText, String realityText) {
        for (OppositionPair pair : OPPOSITION_PAIRS) {
            if (normText.contains(pair.normKey) && realityText.contains(pair.realityKey)) {
                return pair;
            }
        }
        return null;
    }
}