package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;

class PlanRevisionStateServiceTest {

    private RequirementDao requirementDao;
    private PlanRevisionStateService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        service = new PlanRevisionStateService(requirementDao);
    }

    @Test
    void retryFailed_requeuesSameRevisionToken() {
        Requirement requirement = requirement(RequirementRevisionState.FAILED, 9L);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(requirementDao.transitionRevisionState("req-1", "loop-1", 9L,
                RequirementRevisionState.FAILED, RequirementRevisionState.PENDING)).thenReturn(1);

        PlanRevisionRequestedEvent event = service.retryFailed("req-1");

        assertEquals("loop-1", event.loopId());
        assertEquals(9L, event.revisionSeq());
    }

    @Test
    void retryFailed_rejectsNonFailedLatestRevision() {
        when(requirementDao.findOne("req-1"))
                .thenReturn(requirement(RequirementRevisionState.PLANNING, 10L));

        assertThrows(IllegalStateException.class, () -> service.retryFailed("req-1"));

        verify(requirementDao, never()).transitionRevisionState("req-1", "loop-1", 10L,
                RequirementRevisionState.FAILED, RequirementRevisionState.PENDING);
    }

    @Test
    void retryFailed_rejectsConcurrentTokenChange() {
        when(requirementDao.findOne("req-1"))
                .thenReturn(requirement(RequirementRevisionState.FAILED, 9L));
        when(requirementDao.transitionRevisionState("req-1", "loop-1", 9L,
                RequirementRevisionState.FAILED, RequirementRevisionState.PENDING)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.retryFailed("req-1"));
    }

    @Test
    void failIfCurrent_usesOnlyInFlightStatesAsCasGuard() {
        when(requirementDao.failRevisionIfCurrent(eq("req-1"), eq("loop-1"), eq(9L),
                anySet(), eq("boom"), eq(RequirementRevisionState.FAILED))).thenReturn(1);

        assertEquals(true, service.failIfCurrent("req-1", "loop-1", 9L, "boom"));

        verify(requirementDao).failRevisionIfCurrent(eq("req-1"), eq("loop-1"), eq(9L),
                org.mockito.ArgumentMatchers.argThat(states ->
                        states.size() == 3
                                && states.contains(RequirementRevisionState.SNAPSHOTTING)
                                && states.contains(RequirementRevisionState.PLANNING)
                                && states.contains(RequirementRevisionState.APPLYING)),
                eq("boom"), eq(RequirementRevisionState.FAILED));
    }

    private Requirement requirement(RequirementRevisionState state, long revisionSeq) {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setRevisionSeq(revisionSeq);
        requirement.setRevisionState(state);
        return requirement;
    }
}
