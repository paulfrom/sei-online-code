package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.progress.RequirementProgressEvent;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.RequirementDesignContextService;
import com.changhong.onlinecode.service.agent.PmAgentClient;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraph;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraphResolver;
import com.changhong.onlinecode.service.revision.apply.PlanPatchApplicationService;
import com.changhong.onlinecode.service.revision.apply.PlanPatchApplyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlanRevisionOrchestrationServiceTest {

    private RequirementDao requirementDao;
    private ExecutionPlanDao executionPlanDao;
    private RequirementCommentService commentService;
    private RequirementDesignContextService contextService;
    private EffectiveTaskGraphResolver graphResolver;
    private TaskHandoffSnapshotService snapshotService;
    private PmAgentClient pmAgentClient;
    private PlanPatchApplicationService applicationService;
    private PlanRevisionStateService stateService;
    private ApplicationEventPublisher eventPublisher;
    private PlanRevisionOrchestrationService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        commentService = mock(RequirementCommentService.class);
        contextService = mock(RequirementDesignContextService.class);
        graphResolver = mock(EffectiveTaskGraphResolver.class);
        snapshotService = mock(TaskHandoffSnapshotService.class);
        pmAgentClient = mock(PmAgentClient.class);
        applicationService = mock(PlanPatchApplicationService.class);
        stateService = mock(PlanRevisionStateService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new PlanRevisionOrchestrationService(requirementDao, executionPlanDao,
                commentService, contextService, graphResolver, snapshotService, pmAgentClient,
                applicationService, stateService, eventPublisher);
    }

    @Test
    void pmResultBecomesStale_isIgnoredWithoutApplyingOrFailingLatestRevision() {
        PlanRevisionRequestedEvent event = prepareWork();
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.PENDING, RequirementRevisionState.SNAPSHOTTING)).thenReturn(true);
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.SNAPSHOTTING, RequirementRevisionState.PLANNING)).thenReturn(true);
        when(stateService.isCurrent("req-1", "loop-1", 4L,
                RequirementRevisionState.PLANNING)).thenReturn(false);

        service.process(event);

        verify(pmAgentClient).generatePlanPatch(any(), any(), any());
        verify(applicationService, never()).apply(any(), any(), any(Long.class), any());
        verify(stateService, never()).failIfCurrent(any(), any(), any(Long.class), any());
    }

    @Test
    void pmFailure_marksCurrentRevisionFailedAndPreservesTaskState() {
        PlanRevisionRequestedEvent event = prepareWork();
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.PENDING, RequirementRevisionState.SNAPSHOTTING)).thenReturn(true);
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.SNAPSHOTTING, RequirementRevisionState.PLANNING)).thenReturn(true);
        when(stateService.isCurrent("req-1", "loop-1", 4L,
                RequirementRevisionState.PLANNING)).thenReturn(true);
        when(pmAgentClient.generatePlanPatch(any(), any(), any())).thenReturn(null);

        service.process(event);

        verify(stateService).failIfCurrent(eq("req-1"), eq("loop-1"), eq(4L), any());
        verify(applicationService, never()).apply(any(), any(), any(Long.class), any());
        verify(eventPublisher, never()).publishEvent(any(PlanRevisionAppliedEvent.class));
    }

    @Test
    void staleRequest_isNoOpBeforeReadingWorkspace() {
        PlanRevisionRequestedEvent event = new PlanRevisionRequestedEvent("req-1", "loop-1", 3L);
        when(stateService.transition("req-1", "loop-1", 3L,
                RequirementRevisionState.PENDING, RequirementRevisionState.SNAPSHOTTING)).thenReturn(false);

        service.process(event);

        verify(requirementDao, never()).findOne(anyString());
        verify(pmAgentClient, never()).generatePlanPatch(any(), any(), any());
    }

    @Test
    void retryFailed_reusesCurrentTokenAndPublishesRequest() {
        Requirement requirement = new Requirement();
        requirement.setId("req-retry");
        requirement.setActiveLoopId("loop-2");
        requirement.setRevisionSeq(7L);
        when(stateService.retryFailed("req-retry"))
                .thenReturn(new PlanRevisionRequestedEvent("req-retry", "loop-2", 7L));
        when(requirementDao.findOne("req-retry")).thenReturn(requirement);

        Requirement result = service.retryFailed("req-retry");

        assertSame(requirement, result);
        verify(eventPublisher).publishEvent(new PlanRevisionRequestedEvent("req-retry", "loop-2", 7L));
    }

    @Test
    void appliedEventFailure_doesNotMakeCommittedRevisionFailed() {
        PlanRevisionRequestedEvent event = prepareSuccessfulApplication();
        doThrow(new IllegalStateException("listener unavailable"))
                .when(eventPublisher).publishEvent(any(PlanRevisionAppliedEvent.class));

        service.process(event);

        verify(stateService, never()).failIfCurrent(any(), any(), any(Long.class), any());
    }

    @Test
    void completionProgressFailure_doesNotSuppressAppliedEvent() {
        PlanRevisionRequestedEvent event = prepareSuccessfulApplication();
        doThrow(new IllegalStateException("progress unavailable"))
                .when(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(candidate ->
                        candidate instanceof RequirementProgressEvent progress
                                && progress.getRevisionState() == RequirementRevisionState.NONE));

        service.process(event);

        verify(eventPublisher).publishEvent(any(PlanRevisionAppliedEvent.class));
        verify(stateService, never()).failIfCurrent(any(), any(), any(Long.class), any());
    }

    private PlanRevisionRequestedEvent prepareSuccessfulApplication() {
        PlanRevisionRequestedEvent event = prepareWork();
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.PENDING, RequirementRevisionState.SNAPSHOTTING)).thenReturn(true);
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.SNAPSHOTTING, RequirementRevisionState.PLANNING)).thenReturn(true);
        when(stateService.isCurrent("req-1", "loop-1", 4L,
                RequirementRevisionState.PLANNING)).thenReturn(true);
        when(stateService.transition("req-1", "loop-1", 4L,
                RequirementRevisionState.PLANNING, RequirementRevisionState.APPLYING)).thenReturn(true);
        PlanPatch patch = new PlanPatch();
        when(pmAgentClient.generatePlanPatch(any(), any(), any())).thenReturn(patch);
        when(applicationService.apply("req-1", "loop-1", 4L, patch)).thenReturn(
                new PlanPatchApplyResult(
                        "plan-4", 4, 4L, List.of(), List.of(), List.of(), List.of()));
        return event;
    }

    private PlanRevisionRequestedEvent prepareWork() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("project-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setRevisionSeq(4L);
        requirement.setRevisionTriggerCommentId("comment-4");
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-3");
        plan.setRequirementId("req-1");
        plan.setLoopId("loop-1");
        plan.setVersion(3);
        plan.setPlanJson("{\"tasks\":[]}");
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(plan);
        when(graphResolver.resolve(plan)).thenReturn(new EffectiveTaskGraph(3L, List.of(), Map.of()));
        when(commentService.findByRequirementId("req-1")).thenReturn(List.of());

        RequirementDesignContext context = new RequirementDesignContext();
        context.setContextStatus(RequirementDesignContextStatus.READY);
        when(contextService.findCurrentByRequirement("req-1")).thenReturn(context);
        return new PlanRevisionRequestedEvent("req-1", "loop-1", 4L);
    }
}
