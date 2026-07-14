package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.agent.AgentRunRecorder;
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
    private ProjectLifecycleService projectLifecycleService;
    private CliRunnerRegistry cliRunnerRegistry;
    private CliRunner runner;
    private SkillMaterializer skillMaterializer;
    private BuiltInSkillRegistry builtInSkillRegistry;
    private FailureInfoSupport failureInfoSupport;
    private AgentRunRecorder agentRunRecorder;
    private RunDao runDao;
    private PlanAgentService service;

    @BeforeEach
    void setUp() {
        planDao = mock(PlanDao.class);
        featureDesignDao = mock(FeatureDesignDao.class);
        agentService = mock(AgentService.class);
        skillService = mock(SkillService.class);
        projectLifecycleService = mock(ProjectLifecycleService.class);
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
        agentRunRecorder = mock(AgentRunRecorder.class);
        runDao = mock(RunDao.class);
        Run agentRun = new Run();
        agentRun.setId("run-1");
        when(agentRunRecorder.createAgentRun(any())).thenReturn(agentRun);
        service = new PlanAgentService(planDao, featureDesignDao, agentService,
                skillService, projectLifecycleService, cliRunnerRegistry, skillMaterializer, builtInSkillRegistry,
                failureInfoSupport, agentRunRecorder, runDao);
    }

    @Test
    void spawnPlanning_success_persistsDraftAndContent() {
        Plan plan = new Plan();
        plan.setId("plan1");
        plan.setProjectId("p1");
        plan.setStatus(PlanStatus.GENERATING);
        plan.setGenerationToken("token-1");

        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        when(agentService.findByName("planning-agent")).thenReturn(new Agent());
        when(projectLifecycleService.findById("p1")).thenReturn(new Project());
        String json = "{\"summary\":\"s\",\"techAssumptions\":[],\"features\":[],\"nonGoals\":[]}";
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult(json)));

        service.spawnPlanning("p1", "hint", "token-1");

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(captor.capture());
        assertEquals(PlanStatus.DRAFT, captor.getValue().getStatus());
        assertEquals("s", captor.getValue().getContent().getSummary());
    }

    @Test
    void spawnPlanning_parseFailure_persistsFailed() {
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.GENERATING);
        plan.setGenerationToken("token-1");

        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        when(agentService.findByName("planning-agent")).thenReturn(new Agent());
        when(projectLifecycleService.findById("p1")).thenReturn(new Project());
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult("not json")));

        service.spawnPlanning("p1", null, "token-1");

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(captor.capture());
        assertEquals(PlanStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void spawnPlanning_recoversJson_whenLlmEmitsPreamble() {
        // 回归线上报错：模型被要求"只输出 JSON"仍可能带 "前提假设：…" 前言，
        // 旧实现直接 readValue 抛 JsonParseException 落 FAILED；extractJsonObject 兜底后须落 DRAFT。
        Plan plan = new Plan();
        plan.setStatus(PlanStatus.GENERATING);
        plan.setGenerationToken("token-1");

        when(planDao.findLatestByProjectId("p1")).thenReturn(plan);
        when(agentService.findByName("planning-agent")).thenReturn(new Agent());
        when(projectLifecycleService.findById("p1")).thenReturn(new Project());
        String raw = "前提假设：单租户\n技术假设：Spring Boot\n"
                + "{\"summary\":\"s\",\"techAssumptions\":[],\"features\":[],\"nonGoals\":[]}";
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult(raw)));

        service.spawnPlanning("p1", null, "token-1");

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planDao).save(captor.capture());
        assertEquals(PlanStatus.DRAFT, captor.getValue().getStatus());
        assertEquals("s", captor.getValue().getContent().getSummary());
    }

    @Test
    void spawnPlanning_noPlan_skips() {
        when(planDao.findLatestByProjectId("p1")).thenReturn(null);
        service.spawnPlanning("p1", null, "token-1");
        verify(runner, never()).executeDetailed(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void spawnPlanning_staleToken_skipsPersistingResult() {
        Plan initial = new Plan();
        initial.setProjectId("p1");
        initial.setStatus(PlanStatus.GENERATING);
        initial.setGenerationToken("token-1");
        Plan latest = new Plan();
        latest.setProjectId("p1");
        latest.setStatus(PlanStatus.GENERATING);
        latest.setGenerationToken("token-2");

        when(planDao.findLatestByProjectId("p1")).thenReturn(initial, latest);
        when(agentService.findByName("planning-agent")).thenReturn(new Agent());
        when(projectLifecycleService.findById("p1")).thenReturn(new Project());
        String json = "{\"summary\":\"s\",\"techAssumptions\":[],\"features\":[],\"nonGoals\":[]}";
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult(json)));

        service.spawnPlanning("p1", null, "token-1");

        verify(planDao, never()).save(any(Plan.class));
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
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(cliRunResult(json)));

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
        verify(runner, never()).executeDetailed(any(), any(), any(), any(), any(), any(), any());
    }

    private static CliRunResult cliRunResult(String output) {
        CliRunResult result = new CliRunResult();
        result.setOutput(output);
        return result;
    }
}
