package com.changhong.onlinecode.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent 工作区租约（方案 §6.2）。
 *
 * <p>单实例内存租约：同一 {@code (projectId, workspaceKey)} 同时最多一个活动 agent。
 * 从 {@code AgentExecutionService.activeAgentRuns} 重构而来，调度器与执行器共用同一组件，
 * 避免双锁死锁。多实例部署前须替换为带过期时间/心跳的持久化或分布式租约（方案 §11 风险）。</p>
 *
 * <p>注意：与 {@code WorkspaceLeaseService} 的 DB lease（面向进度账本/Git commit，含 fencing token）
 * 语义不同，本租约只负责"同一 requirement 工作区同时最多一个 agent 进程"。</p>
 *
 * @author sei-online-code
 */
@Component
@Slf4j
public class AgentWorkspaceLease {

    private final ConcurrentMap<String, String> activeRunsBySlot = new ConcurrentHashMap<>();

    /**
     * 尝试取得租约。
     *
     * @param slotKey  工作区槽位 key（{@code projectId::workspaceKey}）
     * @param runId    申请持有租约的 Run id
     * @return ACQUIRED（成功）或 BUSY（被其他 Run 持有，携带持有者 runId）
     */
    public AcquireResult tryAcquire(String slotKey, String runId) {
        Objects.requireNonNull(slotKey, "slotKey");
        Objects.requireNonNull(runId, "runId");
        String existing = activeRunsBySlot.putIfAbsent(slotKey, runId);
        if (existing == null) {
            return AcquireResult.ok();
        }
        if (Objects.equals(existing, runId)) {
            return AcquireResult.ok();
        }
        return AcquireResult.busy(existing);
    }

    /**
     * 释放租约（仅当当前持有者匹配时）。
     */
    public void release(String slotKey, String runId) {
        if (slotKey == null || runId == null) {
            return;
        }
        activeRunsBySlot.remove(slotKey, runId);
    }

    /** 当前持有者（用于日志/诊断）；无人持有时返回 null。 */
    public String currentHolder(String slotKey) {
        return slotKey == null ? null : activeRunsBySlot.get(slotKey);
    }
}
