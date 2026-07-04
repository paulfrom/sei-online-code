package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlanAgentService 单元测试（Task 13）。
 *
 * <p>验证 WHY：D11 链式落库是 agent 编排的核心契约——成功解析须落 DRAFT+content，解析失败须落 FAILED
 * （E3：单条失败不影响聚合 FAILED 判定）。用 {@code completedFuture} 使链式同步完成，便于断言。
 * 纯 DAO save（非 super.save），无需 bootstrapContext。</p>
 */
class PlanAgentServiceTest {

    private PlanDao planDao;
    private FeatureDesignDao featureDesignDao;
    private AgentService agentService;
    private SkillService skillService;
    private ProjectService projectService;
    private CliRunnerRegistry cliRunnerRegistry;
    private CliRunner runner;
    private SkillMaterializer skillMaterializer;
    private BuiltInSkillRegistry builtInSkillRegistry;
    private PlanAgentService service;

    @BeforeEach
    void setUp() {
        planDao = mock(PlanDao.class);
        featureDesignDao = mock(FeatureDesignDao.class);
        agentService = mock(AgentService.class);
        skillService = mock(SkillService.class);
        projectService = mock(ProjectService.class);
        cliRunnerRegistry = mock(CliRunnerRegistry.class);
        runner = mock(CliRunner.class);
        when(cliRunnerRegistry.resolve(any())).thenReturn(runner);
        skillMaterializer = mock(SkillMaterializer.class);
        builtInSkillRegistry = mock(BuiltInSkillRegistry.class);
        service = new PlanAgentService(planDao, featureDesignDao, agentService,
                skillService, projectService, cliRunnerRegistry, skillMaterializer, builtInSkillRegistry);
    }

    @Test
    void spawnPlanning_success_persistsDraftAndContent() {
        Plan plan = new Plan();
        plan.setId("plan1");
        plan.setProjectId("p1");
        plan.setStatus(PlanStatus.GENERATING);

        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        when(agentService.findByName("planning-agent")).thenReturn(new Agent());
        when(projectService.findOne("p1")).thenReturn(new Project());
        String json = "{\"summary\":\"s\",\"techAssumptions\":[],\"features\":[],\"nonGoals\":[]}";
        when(runner.execute(eq("p1"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(json));

        service.spawnPlanning("p1", "hint");

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(captor.capture());
        assertEquals(PlanStatus.DRAFT, captor.getValue().getStatus());
        assertEquals("s", captor.getValue().getContent().getSummary());
    }

    @Test
    void spawnPlanning_parseFailure_persistsFailed() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.GENERATING);

        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        when(agentService.findByName("planning-agent")).thenReturn(new Agent());
        when(projectService.findOne("p1")).thenReturn(new Project());
        when(runner.execute(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("not json"));

        service.spawnPlanning("p1", null);

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(captor.capture());
        assertEquals(PlanStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void spawnPlanning_noPlan_skips() {
        when(planDao.findLatestByProjectId("p1")).thenReturn(null);
        service.spawnPlanning("p1", null);
        verify(runner, never()).execute(anyString(), anyString(), anyString());
    }

    @Test
    void spawnFeatureDesign_success_persistsDraftAndContent() {
        FeatureDesign fd = new FeatureDesign();
        fd.setId("fd1");
        fd.setProjectId("p1");
        fd.setFeatureId("feat1");
        fd.setStatus(FeatureDesignStatus.PENDING);

        when(featureDesignDao.findLatestByProjectId("p1")).thenReturn(List.of(fd));
        when(planDao.findLatestByProjectId("p1")).thenReturn(new Plan());
        when(agentService.findByName("feature-design-agent")).thenReturn(new Agent());
        String json = "{\"featureId\":\"feat1\",\"goal\":\"g\",\"design\":null,\"acceptance\":[],\"fileScope\":[]}";
        when(runner.execute(eq("p1:feat1"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(json));

        service.spawnFeatureDesign("p1", "feat1", null);

        ArgumentCaptor<FeatureDesign> captor = ArgumentCaptor.forClass(FeatureDesign.class);
        verify(featureDesignDao, org.mockito.Mockito.times(2)).save(captor.capture());
        // 至少一次 GENERATING 置位 + 一次 DRAFT 落库（captor 取最后一次）
        FeatureDesign last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(FeatureDesignStatus.DRAFT, last.getStatus());
        assertEquals("g", last.getContent().getGoal());
    }

    @Test
    void spawnFeatureDesigns_empty_skips() {
        service.spawnFeatureDesigns("p1", List.of());
        verify(runner, never()).execute(anyString(), anyString(), anyString());
    }
}
