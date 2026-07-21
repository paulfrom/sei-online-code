package com.changhong.onlinecode.dto.enums;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunState 兼容性测试。
 *
 * <p>WHY：历史 oc_run 行的 state 取值为 RUNNING/SUCCEEDED/FAILED/CANCELLED；扩展 QUEUED/UNKNOWN
 * 用于调度排队及无法确认结果的兼容场景。超时 Run 当前统一写 FAILED/TIMEOUT，UNKNOWN 不表示执行中。</p>
 */
class RunStateTest {

    /** 历史 Run 行的 state 仍必须是合法枚举值——历史数据可读，不因枚举扩展而失效。 */
    @Test
    void historicalStatesRemainValid() {
        EnumSet<RunState> historical = EnumSet.of(
                RunState.RUNNING, RunState.SUCCEEDED, RunState.FAILED, RunState.CANCELLED);
        assertTrue(EnumSet.allOf(RunState.class).containsAll(historical),
                "historical Run states must remain valid enum members");
    }

    /** QUEUED 用于排队；UNKNOWN 仅为兼容值，不承担超时恢复调度。 */
    @Test
    void atLeastOnceResumeStatesExist() {
        EnumSet<RunState> all = EnumSet.allOf(RunState.class);
        assertTrue(all.contains(RunState.QUEUED), "QUEUED required for at-least-once scheduling");
        assertTrue(all.contains(RunState.UNKNOWN), "UNKNOWN remains available for indeterminate legacy results");
    }

    /** 枚举值数量 = 4 历史 + 2 新增，防止误删或重复。 */
    @Test
    void hasExactlySixStates() {
        assertEquals(6, RunState.values().length);
    }
}
