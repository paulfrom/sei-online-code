package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.entity.Iteration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 工作区 GC 服务（B30）。契约 Phase 4 §4，蓝图参照 daemon/gc.go + diskusage.go。
 *
 * <p>周期性回收「终态迭代」（ACCEPTED/FAILED/CANCELLED）且 finishedDate 早于 TTL 的工作区目录。
 * TTL 与开关来自环境变量并带兜底（backend 规则 #11）：{@code oc.gc.ttl-hours}（默认 72）、
 * {@code oc.gc.enabled}（默认 true）。</p>
 *
 * <p>本轮仅落地「TTL 选择 + 可单测的 reclaimable 判定」；真实 fs 删除与磁盘用量核算
 * 挂在 Phase 2 运行期 seam 之后（{@code TODO(oma-deferred)}）。</p>
 *
 * @author sei-online-code
 */
@Service
public class WorkspaceGcService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceGcService.class);

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    /** 保留时长（小时）；环境未配置时兜底 72。 */
    @Value("${oc.gc.ttl-hours:72}")
    private long ttlHours;

    /** GC 总开关；环境未配置时兜底 true。 */
    @Value("${oc.gc.enabled:true}")
    private boolean enabled;

    /**
     * 判定某迭代在给定时刻是否可回收：处于终态（ACCEPTED/FAILED/CANCELLED）
     * 且 finishedDate 早于 now - TTL。
     *
     * <p>GC 关闭或 finishedDate 缺失（尚未终结）时一律不可回收。</p>
     *
     * @param iteration 迭代
     * @param now       当前时刻
     * @return 可回收则 true
     */
    public boolean reclaimable(Iteration iteration, Date now) {
        if (!enabled || iteration == null || now == null) {
            return false;
        }
        if (!isTerminal(iteration.getState())) {
            return false;
        }
        Date finishedDate = iteration.getFinishedDate();
        if (finishedDate == null) {
            return false;
        }
        long ageMillis = now.getTime() - finishedDate.getTime();
        return ageMillis > ttlHours * MILLIS_PER_HOUR;
    }

    /**
     * 回收单个迭代的工作区目录。
     *
     * @param iteration 已判定可回收的迭代
     */
    public void reclaim(Iteration iteration) {
        // TODO(oma-deferred): 接入 Phase 2 运行期 WorkspaceManager 解析工作区路径后，
        //   执行真实 fs 删除与磁盘用量核算（参照 daemon/gc.go + diskusage.go）。
        LOGGER.info("gc: 标记可回收工作区 iterationId={}（真实删除待运行期接入）", iteration.getId());
    }

    /**
     * 是否为终态（ACCEPTED/FAILED/CANCELLED）。
     *
     * @param state 生命周期状态
     * @return 终态则 true
     */
    private boolean isTerminal(LifecycleState state) {
        return state == LifecycleState.ACCEPTED
                || state == LifecycleState.FAILED
                || state == LifecycleState.CANCELLED;
    }
}
