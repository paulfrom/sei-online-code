package com.changhong.onlinecode.service.agent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentWorkspaceLease 单元测试（方案 §6.2）。
 *
 * <p>覆盖：单租约取得、并发互斥（仅一个 ACQUIRED）、释放后可重新取得、跨工作区独立。</p>
 */
class AgentWorkspaceLeaseTest {

    @Test
    void tryAcquire_grantsFirstAcquirer() {
        AgentWorkspaceLease lease = new AgentWorkspaceLease();
        AcquireResult result = lease.tryAcquire("p1::requirement-r1", "run-1");
        assertTrue(result.acquired());
        assertNull(result.busyRunId());
    }

    @Test
    void tryAcquire_rejectsSecondAcquirerForSameSlotAndExposesHolder() {
        AgentWorkspaceLease lease = new AgentWorkspaceLease();
        lease.tryAcquire("p1::requirement-r1", "run-1");

        AcquireResult second = lease.tryAcquire("p1::requirement-r1", "run-2");

        assertFalse(second.acquired());
        assertEquals("run-1", second.busyRunId());
    }

    @Test
    void tryAcquire_sameRunReacquiresIdempotently() {
        AgentWorkspaceLease lease = new AgentWorkspaceLease();
        lease.tryAcquire("p1::requirement-r1", "run-1");

        AcquireResult again = lease.tryAcquire("p1::requirement-r1", "run-1");
        assertTrue(again.acquired());
    }

    @Test
    void release_allowsNextAcquirer() {
        AgentWorkspaceLease lease = new AgentWorkspaceLease();
        lease.tryAcquire("p1::requirement-r1", "run-1");
        lease.release("p1::requirement-r1", "run-1");

        AcquireResult next = lease.tryAcquire("p1::requirement-r1", "run-2");
        assertTrue(next.acquired());
    }

    @Test
    void differentWorkspacesAreIndependent() {
        AgentWorkspaceLease lease = new AgentWorkspaceLease();
        AcquireResult r1 = lease.tryAcquire("p1::requirement-r1", "run-1");
        AcquireResult r2 = lease.tryAcquire("p1::requirement-r2", "run-2");
        assertTrue(r1.acquired());
        assertTrue(r2.acquired());
    }

    @Test
    void concurrentAcquirers_onlyOneWins() throws Exception {
        // 验证多线程并发下同一 slot 仅一个 ACQUIRED（方案 §6.2 互斥）。
        AgentWorkspaceLease lease = new AgentWorkspaceLease();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger busy = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            final String runId = "run-" + i;
            pool.execute(() -> {
                try {
                    start.await();
                    AcquireResult result = lease.tryAcquire("p1::requirement-r1", runId);
                    if (result.acquired()) {
                        acquired.incrementAndGet();
                    } else {
                        busy.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, acquired.get(), "exactly one acquirer should win the lease");
        assertEquals(threads - 1, busy.get(), "all others should observe BUSY");
    }
}
