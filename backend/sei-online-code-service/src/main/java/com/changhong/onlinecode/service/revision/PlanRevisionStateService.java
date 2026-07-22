package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.entity.Requirement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/** Small transactional boundary for the asynchronous revision state machine. */
@Service
public class PlanRevisionStateService {

    private static final int MAX_FAILURE_REASON_LENGTH = 8_000;
    private static final Set<RequirementRevisionState> FAILABLE_STATES = Set.of(
            RequirementRevisionState.SNAPSHOTTING,
            RequirementRevisionState.PLANNING,
            RequirementRevisionState.APPLYING);

    private final RequirementDao requirementDao;

    public PlanRevisionStateService(RequirementDao requirementDao) {
        this.requirementDao = requirementDao;
    }

    @Transactional(rollbackFor = Exception.class)
    public long request(String requirementId, String loopId, String triggerCommentId) {
        int updated = requirementDao.requestRevision(requirementId, loopId, triggerCommentId,
                RequirementRevisionState.PENDING);
        if (updated != 1) {
            throw new IllegalStateException("需求 loop 已变化，无法创建计划修订");
        }
        Requirement current = requirementDao.findOne(requirementId);
        if (current == null || !Objects.equals(loopId, current.getActiveLoopId())) {
            throw new IllegalStateException("需求 loop 已变化，无法读取计划修订序号");
        }
        return value(current.getRevisionSeq());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean transition(String requirementId, String loopId, long revisionSeq,
                              RequirementRevisionState expected, RequirementRevisionState target) {
        return requirementDao.transitionRevisionState(requirementId, loopId, revisionSeq,
                expected, target) == 1;
    }

    @Transactional(readOnly = true)
    public boolean isCurrent(String requirementId, String loopId, long revisionSeq,
                             RequirementRevisionState state) {
        Requirement current = requirementDao.findOne(requirementId);
        return current != null
                && Objects.equals(loopId, current.getActiveLoopId())
                && value(current.getRevisionSeq()) == revisionSeq
                && current.getRevisionState() == state;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean failIfCurrent(String requirementId, String loopId, long revisionSeq, String reason) {
        return requirementDao.failRevisionIfCurrent(requirementId, loopId, revisionSeq,
                FAILABLE_STATES, truncate(reason), RequirementRevisionState.FAILED) == 1;
    }

    /**
     * Requeues exactly the latest failed token. A newer comment changes either the sequence or
     * state, so the compare-and-set prevents replaying obsolete work.
     */
    @Transactional(rollbackFor = Exception.class)
    public PlanRevisionRequestedEvent retryFailed(String requirementId) {
        Requirement current = requirementDao.findOne(requirementId);
        if (current == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        if (current.getRevisionState() != RequirementRevisionState.FAILED) {
            throw new IllegalStateException("仅 FAILED 状态的计划修订可重试");
        }
        String loopId = current.getActiveLoopId();
        long revisionSeq = value(current.getRevisionSeq());
        if (loopId == null || loopId.isBlank() || revisionSeq <= 0) {
            throw new IllegalStateException("当前需求没有可重试的计划修订");
        }
        int updated = requirementDao.transitionRevisionState(requirementId, loopId, revisionSeq,
                RequirementRevisionState.FAILED, RequirementRevisionState.PENDING);
        if (updated != 1) {
            throw new IllegalStateException("计划修订已变化，请刷新后重试");
        }
        return new PlanRevisionRequestedEvent(requirementId, loopId, revisionSeq);
    }

    private String truncate(String reason) {
        String value = reason == null || reason.isBlank() ? "计划修订失败" : reason;
        if (value.length() <= MAX_FAILURE_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
