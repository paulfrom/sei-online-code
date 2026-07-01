package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.entity.Iteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkspaceGcService#reclaimable} 单元测试（B30）。
 *
 * <p>验证 WHY：契约 §4 规定 GC 只回收「终态（ACCEPTED/FAILED/CANCELLED）且 finishedDate
 * 早于 TTL」的工作区。若把非终态、或虽终态但仍在 TTL 内的迭代误判为可回收，会删掉用户仍在
 * 预览/排障的活动工作区——这是数据丢失级别的错误。因此逐条钉死判定边界：终态×过期=可回收；
 * 终态×未过期、非终态、finishedDate 缺失、开关关闭=不可回收。</p>
 *
 * @author sei-online-code
 */
class WorkspaceGcServiceTest {

    private static final long TTL_HOURS = 72L;
    private static final long HOUR = 3_600_000L;

    private WorkspaceGcService service;
    private Date now;

    @BeforeEach
    void setUp() {
        service = new WorkspaceGcService();
        ReflectionTestUtils.setField(service, "ttlHours", TTL_HOURS);
        ReflectionTestUtils.setField(service, "enabled", true);
        now = new Date();
    }

    private Iteration terminal(LifecycleState state, long ageHours) {
        Iteration it = new Iteration();
        it.setState(state);
        it.setFinishedDate(new Date(now.getTime() - ageHours * HOUR));
        return it;
    }

    @Test
    void reclaimable_true_forTerminalOlderThanTtl() {
        // 终态且已过 TTL（73h > 72h）→ 可回收
        assertTrue(service.reclaimable(terminal(LifecycleState.ACCEPTED, 73), now));
        assertTrue(service.reclaimable(terminal(LifecycleState.FAILED, 100), now));
        assertTrue(service.reclaimable(terminal(LifecycleState.CANCELLED, 200), now));
    }

    @Test
    void reclaimable_false_forTerminalWithinTtl() {
        // 终态但仍在 TTL 内（1h < 72h）→ 不可回收（用户可能仍在预览）
        assertFalse(service.reclaimable(terminal(LifecycleState.ACCEPTED, 1), now));
    }

    @Test
    void reclaimable_false_forNonTerminalEvenIfOld() {
        // 非终态（仍在推进）即便超龄也不可回收
        Iteration active = new Iteration();
        active.setState(LifecycleState.PREVIEW);
        active.setFinishedDate(new Date(now.getTime() - 500 * HOUR));
        assertFalse(service.reclaimable(active, now));
    }

    @Test
    void reclaimable_false_whenFinishedDateMissing() {
        // 终态但 finishedDate 缺失（数据异常）→ 保守不回收
        Iteration it = new Iteration();
        it.setState(LifecycleState.ACCEPTED);
        assertFalse(service.reclaimable(it, now));
    }

    @Test
    void reclaimable_false_whenGcDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertFalse(service.reclaimable(terminal(LifecycleState.ACCEPTED, 1000), now),
                "GC 关闭时一律不回收");
    }
}
