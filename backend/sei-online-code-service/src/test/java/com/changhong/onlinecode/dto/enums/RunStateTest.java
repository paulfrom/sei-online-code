package com.changhong.onlinecode.dto.enums;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunState 兼容性测试（ADR-001 at-least-once 续作）。
 *
 * <p>WHY：历史 oc_run 行的 state 取值为 RUNNING/SUCCEEDED/FAILED/CANCELLED；扩展 QUEUED/UNKNOWN
 * 用于调度排队与超时/回调丢失对账。必须保证旧值仍是合法枚举成员（历史行可读），且新值存在（新 Run 可写入），
 * 否则会破坏历史数据读取或 at-least-once 续作语义。</p>
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

    /** ADR-001 新增 QUEUED（排队未运行）与 UNKNOWN（超时/回调丢失，需对账后再续作，不得直接判失败）。 */
    @Test
    void atLeastOnceResumeStatesExist() {
        EnumSet<RunState> all = EnumSet.allOf(RunState.class);
        assertTrue(all.contains(RunState.QUEUED), "QUEUED required for at-least-once scheduling");
        assertTrue(all.contains(RunState.UNKNOWN), "UNKNOWN required for reconcile-before-resume");
    }

    /** 枚举值数量 = 4 历史 + 2 新增，防止误删或重复。 */
    @Test
    void hasExactlySixStates() {
        assertEquals(6, RunState.values().length);
    }
}
