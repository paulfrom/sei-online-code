package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.agent.AgentRunRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequirementAgentServiceTest {

    private RequirementDao requirementDao;
    private AgentService agentService;
    private SkillService skillService;
    private ProjectService projectService;
    private CliRunnerRegistry cliRunnerRegistry;
    private CliRunner runner;
    private SkillMaterializer skillMaterializer;
    private BuiltInSkillRegistry builtInSkillRegistry;
    private FailureInfoSupport failureInfoSupport;
    private RequirementCommentService requirementCommentService;
    private AgentRunRecorder agentRunRecorder;
    private RunDao runDao;
    private RequirementAgentService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        agentService = mock(AgentService.class);
        skillService = mock(SkillService.class);
        projectService = mock(ProjectService.class);
        cliRunnerRegistry = mock(CliRunnerRegistry.class);
        runner = mock(CliRunner.class);
        com.changhong.onlinecode.agent.AgentWorkspace workspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(workspace.path()).thenReturn(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")));
        when(workspace.pathString()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(cliRunnerRegistry.workspace(anyString())).thenReturn(workspace);
        skillMaterializer = mock(SkillMaterializer.class);
        builtInSkillRegistry = mock(BuiltInSkillRegistry.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        requirementCommentService = mock(RequirementCommentService.class);
        agentRunRecorder = mock(AgentRunRecorder.class);
        runDao = mock(RunDao.class);
        Run agentRun = new Run();
        agentRun.setId("run-1");
        when(agentRunRecorder.createAgentRun(any())).thenReturn(agentRun);
        service = new RequirementAgentService(requirementDao, agentService, skillService, projectService,
                cliRunnerRegistry, skillMaterializer, builtInSkillRegistry, failureInfoSupport,
                mock(RequirementDesignContextService.class), mock(DesignContextPromptAssembler.class),
                requirementCommentService, agentRunRecorder, runDao,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void spawnPrd_blankOutput_marksFailed() {
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        requirement.setProjectId("p1");
        requirement.setStatus(RequirementStatus.PRD_GENERATING);
        requirement.setGenerationToken("token-1");
        when(requirementDao.findOne("req1")).thenReturn(requirement, requirement);
        when(agentService.findByName("prd-agent")).thenReturn(new Agent());
        when(projectService.findOne("p1")).thenReturn(new Project());
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult("   ")));

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
        when(agentService.findByName("prd-agent")).thenReturn(new Agent());
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
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult(content)));

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
        when(agentService.findByName("memory-review-agent")).thenReturn(new Agent());
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult("""
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
        CompletableFuture<CliRunResult> response = new CompletableFuture<>();
        when(requirementDao.findOne("req1")).thenReturn(reviewed, latest);
        when(agentService.findByName("memory-review-agent")).thenReturn(new Agent());
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any())).thenReturn(response);

        service.reviewMemory("req1", "old", context);
        CliRunResult lateResult = new CliRunResult();
        lateResult.setOutput("{\"findings\":[{\"message\":\"迟到差异\"}]}");
        response.complete(lateResult);

        verify(requirementDao, never()).save(any(Requirement.class));
        verify(requirementCommentService, never()).append(anyString(), any(), any(), anyString(), any(),
                anyString(), any());
    }

    private static CliRunResult cliRunResult(String output) {
        CliRunResult result = new CliRunResult();
        result.setOutput(output);
        return result;
    }
}
