package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementDao;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RequirementService spawn 时序回归测试。
 *
 * <p>WHY：{@code spawnPrd} 为 {@code @Async}，若在状态写入事务提交前触发，异步线程以
 * READ_COMMITTED 读不到未提交的需求行 → {@code findOne} 返回 null → agent 静默跳过、状态卡在
 * PRD_GENERATING。故 spawn 必须延后到事务提交后。本测试不经过 {@code super.save}（其
 * {@code validateUniqueCode} 需 Spring 容器，见 {@code PlanServiceTest#edit_success} 的 @Disabled），
 * 直接验证 {@link RequirementService#triggerPrdSpawnAfterCommit} 的延后语义——它是
 * {@code save}/{@code regeneratePrd} 的唯一 spawn 触发点。</p>
 */
class RequirementServiceAfterCommitTest {

    @Test
    void spawnPrd_deferredUntilAfterCommit() {
        RequirementDao dao = mock(RequirementDao.class);
        RequirementAgentService agent = mock(RequirementAgentService.class);
        OverviewDesignService overviewDesignService = mock(OverviewDesignService.class);
        RequirementService service = new RequirementService(dao, agent, overviewDesignService);

        TransactionSynchronizationManager.initSynchronization();
        try {
            // 提交前：不得触发 spawn（否则异步线程读不到未提交行 → agent 静默跳过）
            service.triggerPrdSpawnAfterCommit("req1", "hint");
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            verify(agent, never()).spawnPrd(anyString(), any());

            // 模拟事务提交：afterCommit 回调触发 spawn
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(agent).spawnPrd("req1", "hint");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void spawnPrd_runsImmediatelyWhenNoTransaction() {
        RequirementDao dao = mock(RequirementDao.class);
        RequirementAgentService agent = mock(RequirementAgentService.class);
        OverviewDesignService overviewDesignService = mock(OverviewDesignService.class);
        RequirementService service = new RequirementService(dao, agent, overviewDesignService);

        // 无活动事务时 TransactionUtil.afterCommit 立即执行（非事务上下文调用方的安全回退）
        service.triggerPrdSpawnAfterCommit("req2", null);
        verify(agent).spawnPrd("req2", null);
    }
}
