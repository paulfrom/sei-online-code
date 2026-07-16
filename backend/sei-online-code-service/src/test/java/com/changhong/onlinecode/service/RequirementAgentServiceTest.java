package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequirementAgentServiceTest {

    private RequirementDao requirementDao;
    private ProjectService projectService;
    private AgentExecutionService agentExecutionService;
    private FailureInfoSupport failureInfoSupport;
    private RequirementCommentService requirementCommentService;
    private RunDao runDao;
    private RequirementAgentService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        projectService = mock(ProjectService.class);
        agentExecutionService = mock(AgentExecutionService.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        requirementCommentService = mock(RequirementCommentService.class);
        runDao = mock(RunDao.class);
        service = new RequirementAgentService(requirementDao, projectService, agentExecutionService, failureInfoSupport,
                mock(RequirementDesignContextService.class), mock(DesignContextPromptAssembler.class),
                requirementCommentService, runDao);
    }

    @Test
    void spawnPrd_blankOutput_marksFailed() {
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setProjectId("p1");
        requirement.setStatus(RequirementStatus.PRD_GENERATING);
        requirement.setGenerationToken("token-1");
        when(requirementDao.findOne("req1")).thenReturn(requirement, requirement);
        when(projectService.findOne("p1")).thenReturn(new Project());
        when(agentExecutionService.executeAsync(eq("prd-agent"), any()))
                .thenReturn(CompletableFuture.completedFuture(agentResult("   ")));

        service.spawnPrd("req1", null, "token-1");

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementDao).save(captor.capture());
        assertEquals(RequirementStatus.FAILED, captor.getValue().getStatus());
        verify(failureInfoSupport).markRequirementFailure(eq(requirement), any(), any(), anyString(), anyString(),
                any(), any());
    }

    @Test
    void spawnPrd_staleToken_skipsPersistingResult() {
        Requirement initial = new Requirement();
        initial.setId("req1");
        initial.setProjectId("p1");
        initial.setStatus(RequirementStatus.PRD_GENERATING);
        initial.setGenerationToken("token-1");
        Requirement latest = new Requirement();
        latest.setId("req1");
        latest.setProjectId("p1");
        latest.setStatus(RequirementStatus.PRD_GENERATING);
        latest.setGenerationToken("token-2");
        when(requirementDao.findOne("req1")).thenReturn(initial, latest);
        when(projectService.findOne("p1")).thenReturn(new Project());
        String content = """
                # PRD

                ## 需求概述
                内容

                ## 业务目标
                内容

                ## 功能需求
                内容
                """;
        when(agentExecutionService.executeAsync(eq("prd-agent"), any()))
                .thenReturn(CompletableFuture.completedFuture(agentResult(content)));

        service.spawnPrd("req1", null, "token-1");

        verify(requirementDao, never()).save(any(Requirement.class));
    }

    @Test
    void reviewMemory_agentDifferencesPersistAsNonBlockingWarning() {
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setProjectId("p1");
        requirement.setStatus(RequirementStatus.PRD_REVIEW);
        requirement.setPrdContent("# PRD");
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx1");
        when(requirementDao.findOne("req1")).thenReturn(requirement, requirement);
        when(agentExecutionService.executeAsync(eq("memory-review-agent"), any()))
                .thenReturn(CompletableFuture.completedFuture(agentResult("""
                        {"findings":[{"severity":"HIGH","message":"新增缓存策略尚未沉淀",
                        "suggestedAction":"交付后更新项目记忆"}]}
                        """)));

        service.reviewMemory("req1", "# PRD", context);

        assertEquals(MemoryValidationStatus.WARNING, requirement.getMemoryValidationStatus());
        verify(requirementDao).save(requirement);
        verify(requirementCommentService).append(eq("req1"), any(), any(), eq("记忆审阅 agent"), any(),
                org.mockito.ArgumentMatchers.contains("不是必须校验项"),
                org.mockito.ArgumentMatchers.contains("\"status\":\"WARNING\""));
    }

    @Test
    void reviewMemory_newerPrdDiscardsLateAgentResult() {
        Requirement reviewed = new Requirement();
        reviewed.setId("req1");
        reviewed.setProjectId("p1");
        reviewed.setPrdContent("old");
        Requirement latest = new Requirement();
        latest.setId("req1");
        latest.setPrdContent("new");
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx1");
        CompletableFuture<AgentExecutionResult> response = new CompletableFuture<>();
        when(requirementDao.findOne("req1")).thenReturn(reviewed, latest);
        when(agentExecutionService.executeAsync(eq("memory-review-agent"), any())).thenReturn(response);

        service.reviewMemory("req1", "old", context);
        response.complete(agentResult("{\"findings\":[{\"message\":\"迟到差异\"}]}"));

        verify(requirementDao, never()).save(any(Requirement.class));
        verify(requirementCommentService, never()).append(anyString(), any(), any(), anyString(), any(),
                anyString(), any());
    }

    @Test
    void reviewMemory_logStreamKeyStaysWithinVarchar36Bound() {
        // WHY: oc_run.log_stream_key is varchar(36). A composite "reqId-memory-review-{uuid}" (~70 chars)
        // overflowed the column and aborted the run insert. log_stream_key is the WS log-stream key
        // (兼作 runNo 分组兜底); for a requirement-scoped review it must be the requirement id (<=36),
        // matching the other requirement-scoped agent runs.
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setProjectId("p1");
        requirement.setStatus(RequirementStatus.PRD_REVIEW);
        requirement.setPrdContent("# PRD");
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx1");
        when(requirementDao.findOne("req1")).thenReturn(requirement, requirement);
        when(agentExecutionService.executeAsync(eq("memory-review-agent"), any())).thenReturn(new CompletableFuture<>());

        service.reviewMemory("req1", "# PRD", context);

        ArgumentCaptor<AgentExecutionRequest> captor = ArgumentCaptor.forClass(AgentExecutionRequest.class);
        verify(agentExecutionService).executeAsync(eq("memory-review-agent"), captor.capture());
        String logStreamKey = captor.getValue().getLogStreamKey();
        assertTrue(logStreamKey.length() <= 36, "log_stream_key must fit varchar(36): " + logStreamKey);
        assertEquals("req1", logStreamKey);
    }

    private static AgentExecutionResult agentResult(String output) {
        return new AgentExecutionResult("run-1", output, true, null);
    }
}
