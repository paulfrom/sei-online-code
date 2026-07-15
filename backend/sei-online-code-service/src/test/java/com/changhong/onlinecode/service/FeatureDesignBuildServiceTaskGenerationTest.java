package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Task;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureDesignBuildServiceTaskGenerationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path workspace;

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    @Test
    void build_generatesExecutableTaskFromFeatureDesignAndStartsAgent() {
        FeatureDesignDao featureDesignDao = mock(FeatureDesignDao.class);
        AgentService agentService = mock(AgentService.class);
        TaskService taskService = mock(TaskService.class);
        RunService runService = mock(RunService.class);
        RunNumberService runNumberService = mock(RunNumberService.class);
        CliRunnerRegistry cliRunnerRegistry = mock(CliRunnerRegistry.class);
        FailureInfoSupport failureInfoSupport = mock(FailureInfoSupport.class);
        FeatureDesignBuildService service = new FeatureDesignBuildService(
                featureDesignDao,
                agentService,
                taskService,
                runService,
                runNumberService,
                cliRunnerRegistry,
                failureInfoSupport
        );
        when(runNumberService.assign(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FeatureDesignContent content = new FeatureDesignContent();
        content.setFeatureId("inventory-list");
        content.setGoal("实现库存列表查询与筛选");
        content.setDesign(OBJECT_MAPPER.createObjectNode().put("page", "/inventory"));
        content.setAcceptance(List.of("展示库存列表", "支持按名称筛选"));
        content.setFileScope(List.of("src/pages/inventory/index.tsx", "src/mocks/inventory.ts"));

        FeatureDesign fd = new FeatureDesign();
        fd.setId("fd1");
        fd.setProjectId("project1");
        fd.setFeatureId("inventory-list");
        fd.setStatus(FeatureDesignStatus.CONFIRMED);
        fd.setBuildStatus(FeatureDesignBuildStatus.IDLE);
        fd.setContent(content);

        Agent devAgent = new Agent();
        devAgent.setName("dev-agent");
        devAgent.setCliTool("codex");
        devAgent.setInstructions("按任务实现代码");

        Task savedTask = new Task();
        savedTask.setId("task1");
        savedTask.setIterationId("fd1");

        Run savedRun = new Run();
        savedRun.setId("run1");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(workspace);
        when(agentWorkspace.pathString()).thenReturn(workspace.toString());
        OperateResultWithData<Task> savedTaskResult = OperateResultWithData.operationSuccessWithData(savedTask);
        OperateResultWithData<Run> savedRunResult = OperateResultWithData.operationSuccessWithData(savedRun);

        when(featureDesignDao.findLatestById("fd1")).thenReturn(fd);
        when(featureDesignDao.tryAcquireBuildLock(eq("fd1"), eq(FeatureDesignBuildStatus.BUILDING))).thenReturn(1);
        when(agentService.findByName("dev-agent")).thenReturn(devAgent);
        when(taskService.save(any(Task.class))).thenReturn(savedTaskResult);
        when(cliRunnerRegistry.workspace("project1")).thenReturn(agentWorkspace);
        when(runService.save(any(Run.class))).thenReturn(savedRunResult);
        CliRunResult successResult = new CliRunResult();
        successResult.setOutput("success");
        when(cliRunnerRegistry.executeDetailed(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(successResult));

        OperateResultWithData<FeatureDesignBuildResultDto> result = service.build("fd1");

        assertTrue(result.successful());
        assertEquals("run1", result.getData().getRunId());

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).save(taskCaptor.capture());
        Task generatedTask = taskCaptor.getValue();
        assertEquals("fd1", generatedTask.getIterationId());
        assertEquals("fd1", generatedTask.getFeatureDesignId());
        assertEquals(List.of("src/pages/inventory/index.tsx", "src/mocks/inventory.ts"),
                generatedTask.getFileScope());
        assertNotNull(generatedTask.getDescription());
        assertTrue(generatedTask.getDescription().contains("实现库存列表查询与筛选"));
        assertTrue(generatedTask.getDescription().contains("支持按名称筛选"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(cliRunnerRegistry).executeDetailed(eq(agentWorkspace), any(), promptCaptor.capture(), any());
        assertTrue(promptCaptor.getValue().contains("实现库存列表查询与筛选"));
        assertTrue(promptCaptor.getValue().contains("src/pages/inventory/index.tsx"));
    }
}
