package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RunNumberService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunRecorderTest {

    @Test
    void createAgentRun_compensationBindsPreviousFailedAttempt() {
        RunDao runDao = mock(RunDao.class);
        RunNumberService runNumberService = mock(RunNumberService.class);
        AgentRunRecorder recorder = new AgentRunRecorder(runDao, runNumberService);

        Run previous = new Run();
        previous.setId("run-1");
        previous.setAttemptNo(2);
        when(runDao.findTopByCodingTaskIdAndStateInOrderByCreatedDateDesc(
                eq("task-1"), any())).thenReturn(Optional.of(previous));
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentRunCreateCommand command = new AgentRunCreateCommand();
        command.setCodingTaskId("task-1");
        command.setTriggerSource(TriggerSource.SCHEDULED_COMPENSATION);

        Run saved = recorder.createAgentRun(command);

        assertEquals(RunType.AGENT, saved.getRunType());
        assertEquals("run-1", saved.getParentRunId());
        assertEquals("run-1", saved.getCompensatesRunId());
        assertEquals(3, saved.getAttemptNo());
        assertEquals(RunState.RUNNING, saved.getState());
        verify(runDao).findTopByCodingTaskIdAndStateInOrderByCreatedDateDesc(
                eq("task-1"), eq(List.of(RunState.FAILED, RunState.CANCELLED)));
    }
}
