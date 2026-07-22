package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.RequirementDesignContextService;
import com.changhong.onlinecode.service.agent.PmAgentClient;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraph;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraphResolver;
import com.changhong.onlinecode.service.revision.apply.PlanPatchApplicationService;
import com.changhong.onlinecode.service.revision.apply.PlanPatchApplyResult;
import com.changhong.onlinecode.service.revision.contract.PlanRevisionInput;
import com.changhong.onlinecode.dto.progress.RequirementProgressEvent;
import com.changhong.sei.core.utils.TransactionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Coordinates snapshotting, PM planning and atomic patch application for one revision token. */
@Service
public class PlanRevisionOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanRevisionOrchestrationService.class);

    private final RequirementDao requirementDao;
    private final ExecutionPlanDao executionPlanDao;
    private final RequirementCommentService commentService;
    private final RequirementDesignContextService designContextService;
    private final EffectiveTaskGraphResolver graphResolver;
    private final TaskHandoffSnapshotService snapshotService;
    private final PmAgentClient pmAgentClient;
    private final PlanPatchApplicationService patchApplicationService;
    private final PlanRevisionStateService stateService;
    private final ApplicationEventPublisher eventPublisher;

    public PlanRevisionOrchestrationService(RequirementDao requirementDao,
                                            ExecutionPlanDao executionPlanDao,
                                            RequirementCommentService commentService,
                                            RequirementDesignContextService designContextService,
                                            EffectiveTaskGraphResolver graphResolver,
                                            TaskHandoffSnapshotService snapshotService,
                                            PmAgentClient pmAgentClient,
                                            PlanPatchApplicationService patchApplicationService,
                                            PlanRevisionStateService stateService,
                                            ApplicationEventPublisher eventPublisher) {
        this.requirementDao = requirementDao;
        this.executionPlanDao = executionPlanDao;
        this.commentService = commentService;
        this.designContextService = designContextService;
        this.graphResolver = graphResolver;
        this.snapshotService = snapshotService;
        this.pmAgentClient = pmAgentClient;
        this.patchApplicationService = patchApplicationService;
        this.stateService = stateService;
        this.eventPublisher = eventPublisher;
    }

    /** Requeues the current failed revision without allocating a new token or loop. */
    @Transactional(rollbackFor = Exception.class)
    public Requirement retryFailed(String requirementId) {
        PlanRevisionRequestedEvent retry = stateService.retryFailed(requirementId);
        Requirement current = requirementDao.findOne(requirementId);
        LOGGER.info("Retrying plan revision requirementId={}, loopId={}, revisionSeq={}",
                retry.requirementId(), retry.loopId(), retry.revisionSeq());
        TransactionUtil.afterCommit(() -> {
            publishProgress(retry, RequirementRevisionState.PENDING, null);
            eventPublisher.publishEvent(retry);
        });
        return current;
    }

    /** Stale tokens deliberately return without changing state or task rows. */
    public void process(PlanRevisionRequestedEvent event) {
        if (!stateService.transition(event.requirementId(), event.loopId(), event.revisionSeq(),
                RequirementRevisionState.PENDING, RequirementRevisionState.SNAPSHOTTING)) {
            LOGGER.info("Ignoring stale plan revision request requirementId={}, loopId={}, revisionSeq={}",
                    event.requirementId(), event.loopId(), event.revisionSeq());
            return;
        }
        publishProgress(event, RequirementRevisionState.SNAPSHOTTING, null);

        try {
            RevisionWork work = snapshot(event);
            if (!stateService.transition(event.requirementId(), event.loopId(), event.revisionSeq(),
                    RequirementRevisionState.SNAPSHOTTING, RequirementRevisionState.PLANNING)) {
                return;
            }
            publishProgress(event, RequirementRevisionState.PLANNING, null);

            // Intentionally outside a database transaction: PM execution may take minutes.
            RequirementDesignContext context = prepareContext(event.requirementId());
            PlanPatch patch = pmAgentClient.generatePlanPatch(work.requirement(), work.input(), context);
            if (!stateService.isCurrent(event.requirementId(), event.loopId(), event.revisionSeq(),
                    RequirementRevisionState.PLANNING)) {
                LOGGER.info("Ignoring stale PM plan patch requirementId={}, revisionSeq={}",
                        event.requirementId(), event.revisionSeq());
                return;
            }
            if (patch == null) {
                fail(event, "PM agent 未返回有效的增量计划补丁", null);
                return;
            }
            if (!stateService.transition(event.requirementId(), event.loopId(), event.revisionSeq(),
                    RequirementRevisionState.PLANNING, RequirementRevisionState.APPLYING)) {
                return;
            }
            publishProgress(event, RequirementRevisionState.APPLYING, null);

            PlanPatchApplyResult result = patchApplicationService.apply(event.requirementId(),
                    event.loopId(), event.revisionSeq(), patch);
            try {
                publishProgress(event, RequirementRevisionState.NONE, null);
            } catch (RuntimeException progressFailure) {
                LOGGER.error("Plan revision completion progress failed requirementId={}, loopId={}, revisionSeq={}",
                        event.requirementId(), event.loopId(), event.revisionSeq(), progressFailure);
            }
            try {
                eventPublisher.publishEvent(new PlanRevisionAppliedEvent(
                        event.requirementId(), event.loopId(), result));
            } catch (RuntimeException eventFailure) {
                // appliedRevisionSeq is already committed. Notification failure must not make the
                // same revision retryable or roll its state back to FAILED.
                LOGGER.error("Plan revision applied notification failed requirementId={}, loopId={}, revisionSeq={}",
                        event.requirementId(), event.loopId(), event.revisionSeq(), eventFailure);
            }
        } catch (RuntimeException exception) {
            fail(event, "计划修订失败: " + exception.getMessage(), exception);
        }
    }

    private RevisionWork snapshot(PlanRevisionRequestedEvent event) {
        Requirement requirement = requirementDao.findOne(event.requirementId());
        if (requirement == null) {
            throw new IllegalStateException("需求不存在: " + event.requirementId());
        }
        ExecutionPlan basePlan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                event.requirementId(), event.loopId());
        if (basePlan == null) {
            throw new IllegalStateException("当前 loop 没有可修订的执行计划");
        }
        EffectiveTaskGraph graph = graphResolver.resolve(basePlan);
        List<PlanRevisionInput.TaskSnapshot> tasks = graph.tasks().stream()
                .map(this::taskSnapshot)
                .toList();
        List<PlanRevisionInput.HandoffSnapshot> handoffs = new ArrayList<>();
        for (CodingTask task : graph.tasks()) {
            handoffs.add(snapshotService.capture(event.requirementId(), task.getId(),
                    event.revisionSeq(), requirement.getRevisionTriggerCommentId()).summary());
        }
        List<PlanRevisionInput.CommentSnapshot> comments = commentService
                .findByRequirementId(event.requirementId()).stream()
                .map(this::commentSnapshot)
                .toList();
        PlanRevisionInput input = new PlanRevisionInput(requirement.getId(), event.loopId(),
                event.revisionSeq(), basePlan.getId(), basePlan.getVersion(), requirement.getTitle(),
                requirement.getDescription(), requirement.getPrdContent(), basePlan.getPlanJson(),
                comments, tasks, handoffs);
        return new RevisionWork(requirement, input);
    }

    private RequirementDesignContext prepareContext(String requirementId) {
        RequirementDesignContext current = designContextService.findCurrentByRequirement(requirementId);
        if (current != null
                && current.getContextStatus() == com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus.READY) {
            return current;
        }
        return designContextService.prepare(requirementId);
    }

    private PlanRevisionInput.TaskSnapshot taskSnapshot(CodingTask task) {
        return new PlanRevisionInput.TaskSnapshot(task.getId(), task.getPlanTaskKey(), task.getTitle(),
                task.getDescription(), task.getAssignedAgent(), task.getArea(), task.getDependsOn(),
                task.getFileScope(), List.of(), task.getStatus().name(), task.getFailureSummary());
    }

    private PlanRevisionInput.CommentSnapshot commentSnapshot(RequirementComment comment) {
        return new PlanRevisionInput.CommentSnapshot(
                comment.getAuthorType() == null ? null : comment.getAuthorType().name(),
                comment.getCommentType() == null ? null : comment.getCommentType().name(),
                comment.getAuthorName(), comment.getContent());
    }

    private void fail(PlanRevisionRequestedEvent event, String reason, RuntimeException exception) {
        boolean recorded = stateService.failIfCurrent(event.requirementId(), event.loopId(),
                event.revisionSeq(), reason);
        if (recorded) {
            LOGGER.error("Plan revision failed requirementId={}, loopId={}, revisionSeq={}: {}",
                    event.requirementId(), event.loopId(), event.revisionSeq(), reason, exception);
            publishProgress(event, RequirementRevisionState.FAILED, reason);
        } else {
            LOGGER.info("Plan revision failure belongs to stale token requirementId={}, revisionSeq={}",
                    event.requirementId(), event.revisionSeq());
        }
    }

    private void publishProgress(PlanRevisionRequestedEvent revision,
                                 RequirementRevisionState state,
                                 String failureReason) {
        RequirementProgressEvent event = new RequirementProgressEvent();
        event.setEventType("plan.revision.updated");
        event.setRequirementId(revision.requirementId());
        event.setLoopId(revision.loopId());
        event.setRevisionSeq(revision.revisionSeq());
        event.setRevisionState(state);
        event.setRevisionFailureReason(failureReason);
        event.setOccurredAt(new Date());
        eventPublisher.publishEvent(event);
    }

    private record RevisionWork(Requirement requirement, PlanRevisionInput input) {
    }
}
