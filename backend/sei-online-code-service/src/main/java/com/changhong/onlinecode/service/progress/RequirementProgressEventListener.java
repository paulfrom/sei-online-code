package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dto.progress.RequirementProgressEvent;
import com.changhong.onlinecode.ws.RequirementProgressWebSocketHub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Requirement 进度事件监听器（EXE-003 / ADR-001 §10.6）。
 *
 * <p>仅在事务提交后触发（{@link TransactionPhase#AFTER_COMMIT}），保证进度事件不先于数据落地；
 * 乱序/重复事件不会回退 snapshotVersion——前端以权威聚合查询为准。</p>
 *
 */
@Slf4j
@Component
public class RequirementProgressEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProgress(RequirementProgressEvent event) {
        log.info("requirement progress event: requirementId={}, loopId={}, revisionSeq={}, "
                        + "revisionState={}, snapshotVersion={}, type={}",
                event.getRequirementId(), event.getLoopId(), event.getRevisionSeq(),
                event.getRevisionState(), event.getSnapshotVersion(), event.getEventType());
        RequirementProgressWebSocketHub.broadcast(event);
    }
}
