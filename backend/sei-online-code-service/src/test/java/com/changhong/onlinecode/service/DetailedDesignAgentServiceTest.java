package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.DetailedDesign;
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

class DetailedDesignAgentServiceTest {

    private DetailedDesignDao detailedDesignDao;
    private OverviewDesignDao overviewDesignDao;
    private RequirementDao requirementDao;
    private AgentService agentService;
    private SkillService skillService;
    private CliRunnerRegistry cliRunnerRegistry;
    private CliRunner runner;
    private SkillMaterializer skillMaterializer;
    private BuiltInSkillRegistry builtInSkillRegistry;
    private FailureInfoSupport failureInfoSupport;
    private DetailedDesignAgentService service;

    @BeforeEach
    void setUp() {
        detailedDesignDao = mock(DetailedDesignDao.class);
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
        service = new DetailedDesignAgentService(detailedDesignDao, overviewDesignDao, requirementDao, agentService,
                skillService, cliRunnerRegistry, skillMaterializer, builtInSkillRegistry, failureInfoSupport);
    }

    @Test
    void spawnDetailedDesign_missingRequiredSections_marksFailed() {
        DetailedDesign design = new DetailedDesign();
        design.setId("dd1");
        design.setRequirementId("req1");
        design.setStatus(DetailedDesignStatus.GENERATING);
        design.setGenerationToken("token-1");
        Requirement requirement = new Requirement();
        requirement.setId("req1");
        when(detailedDesignDao.findOne("dd1")).thenReturn(design, design);
        when(requirementDao.findOne("req1")).thenReturn(requirement);
        when(agentService.findByName("detailed-design-agent")).thenReturn(new Agent());
        when(runner.execute(eq("dd1"), anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("# 详细设计\n\n## 模块目标\n有目标，但没接口设计"));

        service.spawnDetailedDesign("dd1", null, "token-1");

        ArgumentCaptor<DetailedDesign> captor = ArgumentCaptor.forClass(DetailedDesign.class);
        verify(detailedDesignDao).save(captor.capture());
        assertEquals(DetailedDesignStatus.FAILED, captor.getValue().getStatus());
        verify(failureInfoSupport).markDetailedDesignFailure(eq(design), anyString(), anyString(), any(), any());
    }
}
