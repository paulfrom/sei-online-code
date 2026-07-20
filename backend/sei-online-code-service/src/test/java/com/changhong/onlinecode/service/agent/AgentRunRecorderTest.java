package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RunNumberService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        previous.setFailureReason("PRD 输出缺少 Markdown 标题");
        when(runDao.findTopByCodingTaskIdAndAgentNameAndStateOrderByCreatedDateDesc(
                "task-1", "prd-agent", RunState.FAILED)).thenReturn(Optional.of(previous));
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentRunCreateCommand command = new AgentRunCreateCommand();
        command.setCodingTaskId("task-1");
        command.setAgentName("prd-agent");
        command.setTriggerSource(TriggerSource.SCHEDULED_COMPENSATION);
        command.setUserPrompt("请生成 PRD。");

        Run saved = recorder.createAgentRun(command);

        assertEquals(RunType.AGENT, saved.getRunType());
        assertEquals("run-1", saved.getParentRunId());
        assertEquals("run-1", saved.getCompensatesRunId());
        assertEquals(3, saved.getAttemptNo());
        assertEquals(RunState.RUNNING, saved.getState());
        assertTrue(saved.getUserPrompt().startsWith("请生成 PRD。"));
        assertTrue(saved.getUserPrompt().contains("## 上一次 Agent 执行失败"));
        assertTrue(saved.getUserPrompt().contains("PRD 输出缺少 Markdown 标题"));
        verify(runDao).findTopByCodingTaskIdAndAgentNameAndStateOrderByCreatedDateDesc(
                "task-1", "prd-agent", RunState.FAILED);
    }

    @Test
    void createAgentRun_nextExecutionAfterFailureCarriesReasonEvenWhenCallerLostCompensationSource() {
        RunDao runDao = mock(RunDao.class);
        RunNumberService runNumberService = mock(RunNumberService.class);
        AgentRunRecorder recorder = new AgentRunRecorder(runDao, runNumberService);

        Run previous = new Run();
        previous.setId("run-1");
        previous.setFailureReason("test-agent 返回的 JSON 无法解析");
        when(runDao.findTopByRequirementIdAndLoopIdAndAgentNameAndStateOrderByCreatedDateDesc(
                "req-1", "loop-1", "test-agent", RunState.FAILED)).thenReturn(Optional.of(previous));
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentRunCreateCommand command = new AgentRunCreateCommand();
        command.setRequirementId("req-1");
        command.setLoopId("loop-1");
        command.setAgentName("test-agent");
        command.setTriggerSource(TriggerSource.AUTO);
        command.setUserPrompt("执行验收。");

        Run saved = recorder.createAgentRun(command);

        assertEquals("run-1", saved.getCompensatesRunId());
        assertEquals(2, saved.getAttemptNo());
        assertTrue(saved.getUserPrompt().contains("test-agent 返回的 JSON 无法解析"));
    }
}
