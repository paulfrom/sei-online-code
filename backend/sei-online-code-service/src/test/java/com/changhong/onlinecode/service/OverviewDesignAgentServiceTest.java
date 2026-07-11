package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.OverviewDesign;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverviewDesignAgentServiceTest {

    private OverviewDesignDao overviewDesignDao;
    private RequirementDao requirementDao;
    private AgentService agentService;
    private SkillService skillService;
    private CliRunnerRegistry cliRunnerRegistry;
    private CliRunner runner;
    private SkillMaterializer skillMaterializer;
    private BuiltInSkillRegistry builtInSkillRegistry;
    private FailureInfoSupport failureInfoSupport;
    private OverviewDesignAgentService service;

    @BeforeEach
    void setUp() {
        overviewDesignDao = mock(OverviewDesignDao.class);
        requirementDao = mock(RequirementDao.class);
        agentService = mock(AgentService.class);
        skillService = mock(SkillService.class);
        cliRunnerRegistry = mock(CliRunnerRegistry.class);
        runner = mock(CliRunner.class);
        when(cliRunnerRegistry.resolve(any())).thenReturn(runner);
        skillMaterializer = mock(SkillMaterializer.class);
        builtInSkillRegistry = mock(BuiltInSkillRegistry.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        service = new OverviewDesignAgentService(overviewDesignDao, requirementDao, agentService, skillService,
                cliRunnerRegistry, skillMaterializer, builtInSkillRegistry, failureInfoSupport,
                mock(RequirementDesignContextService.class), mock(DesignContextPromptAssembler.class),
                mock(DesignMemoryValidationService.class), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void spawnOverviewDesign_missingModuleTable_marksFailed() {
        OverviewDesign overview = new OverviewDesign();
        overview.setId("ov1");
        overview.setRequirementId("req1");
        overview.setStatus(OverviewDesignStatus.GENERATING);
        overview.setGenerationToken("token-1");
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        when(overviewDesignDao.findOne("ov1")).thenReturn(overview, overview);
        when(requirementDao.findOne("req1")).thenReturn(requirement);
        when(agentService.findByName("overview-design-agent")).thenReturn(new Agent());
        when(runner.execute(eq("ov1"), anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("# 概览设计\n\n只有标题，没有模块表"));

        service.spawnOverviewDesign("ov1", null, "token-1");

        ArgumentCaptor<OverviewDesign> captor = ArgumentCaptor.forClass(OverviewDesign.class);
        verify(overviewDesignDao).save(captor.capture());
        assertEquals(OverviewDesignStatus.FAILED, captor.getValue().getStatus());
        verify(failureInfoSupport).markOverviewDesignFailure(eq(overview), anyString(), anyString(), any(), any());
    }
}
