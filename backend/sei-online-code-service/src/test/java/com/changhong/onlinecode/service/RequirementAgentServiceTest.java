package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
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
    private RequirementAgentService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        agentService = mock(AgentService.class);
        skillService = mock(SkillService.class);
        projectService = mock(ProjectService.class);
        cliRunnerRegistry = mock(CliRunnerRegistry.class);
        runner = mock(CliRunner.class);
        when(cliRunnerRegistry.resolve(any())).thenReturn(runner);
        skillMaterializer = mock(SkillMaterializer.class);
        builtInSkillRegistry = mock(BuiltInSkillRegistry.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        service = new RequirementAgentService(requirementDao, agentService, skillService, projectService,
                cliRunnerRegistry, skillMaterializer, builtInSkillRegistry, failureInfoSupport);
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
        when(runner.execute(eq("req1"), anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("   "));

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
        when(runner.execute(eq("req1"), anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(content));

        service.spawnPrd("req1", null, "token-1");

        verify(requirementDao, never()).save(any(Requirement.class));
    }
}
