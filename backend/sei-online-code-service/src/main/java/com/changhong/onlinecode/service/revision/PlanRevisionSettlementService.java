package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.CodingTaskExecutionService;
import com.changhong.onlinecode.service.CodingTaskScheduler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** Selectively settles runs displaced by an applied patch, then resumes the same loop. */
@Service
@Slf4j
@AllArgsConstructor
public class PlanRevisionSettlementService {

    private final RequirementDao requirementDao;
    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final TaskHandoffSnapshotService snapshotService;
    private final CodingTaskExecutionService executionService;
    private final CodingTaskScheduler scheduler;

    public void settle(PlanRevisionAppliedEvent event) {
        Requirement requirement = requirementDao.findOne(event.requirementId());
        if (requirement == null || !Objects.equals(requirement.getActiveLoopId(), event.loopId())) {
            log.info("Ignore stale revision settlement requirementId={}, loopId={}",
                    event.requirementId(), event.loopId());
            return;
        }

        for (String taskId : event.applyResult().taskIdsToSettle()) {
            settleTask(requirement, event, taskId);
        }
        // Scheduling resolves the latest effective graph; it does not create or replace a loop.
        scheduler.schedule(event.requirementId());
    }

    private void settleTask(Requirement requirement, PlanRevisionAppliedEvent event, String taskId) {
        CodingTask task = codingTaskDao.findOne(taskId);
        if (task == null || !event.requirementId().equals(task.getRequirementId())
                || !event.loopId().equals(task.getLoopId())) {
            return;
        }
        if (task.getStatus() != CodingTaskStatus.SUPERSEDED) {
            snapshotService.refresh(event.requirementId(), taskId, event.applyResult().revisionSeq(),
                    requirement.getRevisionTriggerCommentId());
            // Persist the terminal disposition before terminating the process so even an immediate
            // completion callback observes SUPERSEDED and cannot downgrade it to CANCELLED.
            task.setStatus(CodingTaskStatus.SUPERSEDED);
            codingTaskDao.save(task);
        }
        List<Run> running = runDao.findByCodingTaskId(taskId).stream()
                .filter(run -> run.getState() == RunState.RUNNING)
                .toList();
        for (Run run : running) {
            if (!Boolean.TRUE.equals(run.getCancelRequested())) {
                executionService.cancelRun(run.getId(), requirement.getRevisionTriggerCommentId());
            }
        }
    }
}
