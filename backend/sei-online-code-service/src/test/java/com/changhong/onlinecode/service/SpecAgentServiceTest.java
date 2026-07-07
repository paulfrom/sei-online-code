package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Spec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecAgentServiceTest {

    private SpecDao specDao;
    private AgentService agentService;
    private SkillService skillService;
    private ProjectService projectService;
    private CliRunnerRegistry cliRunnerRegistry;
    private CliRunner runner;
    private SkillMaterializer skillMaterializer;
    private BuiltInSkillRegistry builtInSkillRegistry;
    private FailureInfoSupport failureInfoSupport;
    private SpecAgentService service;

    @BeforeEach
    void setUp() {
        specDao = mock(SpecDao.class);
        agentService = mock(AgentService.class);
        skillService = mock(SkillService.class);
        projectService = mock(ProjectService.class);
        cliRunnerRegistry = mock(CliRunnerRegistry.class);
        runner = mock(CliRunner.class);
        when(cliRunnerRegistry.resolve(any())).thenReturn(runner);
        skillMaterializer = mock(SkillMaterializer.class);
        builtInSkillRegistry = mock(BuiltInSkillRegistry.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        service = new SpecAgentService(specDao, agentService, skillService, projectService,
                cliRunnerRegistry, skillMaterializer, builtInSkillRegistry, failureInfoSupport);
    }

    @Test
    void spawnRequirement_recoversWhenJsonOnlyMissesTrailingClosers() {
        Spec spec = new Spec();
        spec.setId("spec1");
        spec.setProjectId("p1");
        spec.setState(SpecState.GENERATING);
        when(specDao.findById("spec1")).thenReturn(Optional.of(spec));
        when(agentService.findByName("requirement-agent")).thenReturn(new Agent());
        when(projectService.findOne("p1")).thenReturn(new Project());
        String truncated = """
                {
                  "pages":[{"key":"inventory-list","title":"资产盘点","route":"/asset-operations/inventory","description":"盘点任务列表"}],
                  "components":[{"key":"inventory-filter","type":"FilterForm","page":"inventory-list","description":"筛选条件"}],
                  "entities":[{"key":"InventoryTask","fields":[{"name":"id","type":"string","description":"主键"}]}],
                  "apiContract":[{"method":"GET","path":"/api/inventory/tasks","requestShape":"InventoryQuery","responseShape":"ResultData<Page<InventoryTaskDto>>","description":"查询盘点任务"}]
                """;
        when(runner.execute(eq("p1"), anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(truncated));

        service.spawnRequirement("p1", null, "spec1");

        ArgumentCaptor<Spec> captor = ArgumentCaptor.forClass(Spec.class);
        verify(specDao).save(captor.capture());
        Spec saved = captor.getValue();
        assertEquals(SpecState.SPEC_REVIEW, saved.getState());
        assertNotNull(saved.getPages());
        assertEquals(1, saved.getPages().size());
        assertNotNull(saved.getEntities());
        assertEquals(1, saved.getEntities().size());
        verify(projectService).transitionState("p1", LifecycleState.SPEC_REVIEW);
    }
}
