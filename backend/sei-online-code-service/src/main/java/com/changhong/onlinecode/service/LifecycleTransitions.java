package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.LifecycleState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 生命周期状态机合法转换表。契约 §4（权威来源 CONTEXT.md）。
 *
 * <pre>
 * DRAFTING → SPEC_REFINING → SPEC_REVIEW → DISPATCHING → DEVELOPING
 *          → MERGING → DEPLOYING → PREVIEW → ACCEPTED
 * </pre>
 *
 * <p>任一非终态可进入 FAILED / CANCELLED；PREVIEW 可回退到 SPEC_REFINING（用户优化）
 * 或前进到 ACCEPTED（验收）。</p>
 *
 * @author sei-online-code
 */
public final class LifecycleTransitions {

    private static final Map<LifecycleState, Set<LifecycleState>> ALLOWED =
            new EnumMap<>(LifecycleState.class);

    /** 任一非终态均可进入的通用目标（失败 / 取消）。 */
    private static final Set<LifecycleState> UNIVERSAL =
            EnumSet.of(LifecycleState.FAILED, LifecycleState.CANCELLED);

    static {
        ALLOWED.put(LifecycleState.DRAFTING, EnumSet.of(LifecycleState.SPEC_REFINING));
        ALLOWED.put(LifecycleState.SPEC_REFINING, EnumSet.of(LifecycleState.SPEC_REVIEW));
        ALLOWED.put(LifecycleState.SPEC_REVIEW, EnumSet.of(LifecycleState.DISPATCHING));
        ALLOWED.put(LifecycleState.DISPATCHING, EnumSet.of(LifecycleState.DEVELOPING));
        ALLOWED.put(LifecycleState.DEVELOPING, EnumSet.of(LifecycleState.MERGING));
        ALLOWED.put(LifecycleState.MERGING, EnumSet.of(LifecycleState.DEPLOYING));
        ALLOWED.put(LifecycleState.DEPLOYING, EnumSet.of(LifecycleState.PREVIEW));
        // PREVIEW 可回退优化或前进验收
        ALLOWED.put(LifecycleState.PREVIEW,
                EnumSet.of(LifecycleState.SPEC_REFINING, LifecycleState.ACCEPTED));
        // 终态：ACCEPTED/CANCELLED 无出边；FAILED 可 retry 回到 DISPATCHING（契约 §3）
        ALLOWED.put(LifecycleState.ACCEPTED, EnumSet.noneOf(LifecycleState.class));
        ALLOWED.put(LifecycleState.FAILED, EnumSet.of(LifecycleState.DISPATCHING));
        ALLOWED.put(LifecycleState.CANCELLED, EnumSet.noneOf(LifecycleState.class));
    }

    private LifecycleTransitions() {
    }

    /**
     * 判断从 from 到 to 是否为合法状态流转。
     *
     * @param from 源状态
     * @param to   目标状态
     * @return 合法则 true
     */
    public static boolean canTransition(LifecycleState from, LifecycleState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        // 非终态可进入 FAILED / CANCELLED
        boolean terminal = from == LifecycleState.ACCEPTED
                || from == LifecycleState.FAILED
                || from == LifecycleState.CANCELLED;
        if (!terminal && UNIVERSAL.contains(to)) {
            return true;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(LifecycleState.class)).contains(to);
    }
}
