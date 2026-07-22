package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionCheckpointDao;
import com.changhong.onlinecode.dao.ExecutionEffectDao;
import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.RunObservationDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.progress.RequirementExecutionOverviewDto;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionProgressQueryRevisionTest {

    @Test
    void overviewExposesAuthoritativeRevisionStateFromRequirement() {
        RequirementDao requirementDao = mock(RequirementDao.class);
        RequirementWorkspaceDao workspaceDao = mock(RequirementWorkspaceDao.class);
        TaskExecutionDao taskExecutionDao = mock(TaskExecutionDao.class);
        RunDao runDao = mock(RunDao.class);
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-4");
        requirement.setRevisionSeq(6L);
        requirement.setAppliedRevisionSeq(5L);
        requirement.setRevisionState(RequirementRevisionState.FAILED);
        requirement.setRevisionFailureReason("PM output invalid");
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(workspaceDao.findByRequirementId("req-1")).thenReturn(Optional.empty());
        when(taskExecutionDao.findFirstByRequirementIdOrderByCreatedDateDesc("req-1"))
                .thenReturn(Optional.empty());
        when(runDao.findByRequirementIdOrderByCreatedDateDesc("req-1")).thenReturn(List.of());

        ExecutionProgressQueryService service = new ExecutionProgressQueryService(
                workspaceDao,
                taskExecutionDao,
                mock(ExecutionStepDao.class),
                mock(ExecutionCheckpointDao.class),
                mock(ExecutionEffectDao.class),
                mock(RunObservationDao.class),
                runDao,
                mock(ProgressService.class),
                requirementDao);

        RequirementExecutionOverviewDto overview = service.findOverview("req-1");

        assertEquals("loop-4", overview.getActiveLoopId());
        assertEquals(6L, overview.getRevisionSeq());
        assertEquals(5L, overview.getAppliedRevisionSeq());
        assertEquals(RequirementRevisionState.FAILED, overview.getRevisionState());
        assertEquals("PM output invalid", overview.getRevisionFailureReason());
    }
}
