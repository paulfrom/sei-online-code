package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.entity.Project;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * ProjectService 单元测试。
 *
 * <p>验证新流程下项目仅保存元数据，不再触发旧规划代理，且 workspacePath 可自动生成。</p>
 */
class ProjectServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private ProjectDao projectDao;
    private PlanService planService;
    private ProjectLifecycleService lifecycleService;
    private WorkspaceManager workspaceManager;
    private AgentMemoryTemplateService agentMemoryTemplateService;
    private MemoryJobService memoryJobService;
    private MemorySeedTemplateService memorySeedTemplateService;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectDao = mock(ProjectDao.class);
        planService = mock(PlanService.class);
        lifecycleService = mock(ProjectLifecycleService.class);
        workspaceManager = mock(WorkspaceManager.class);
        agentMemoryTemplateService = mock(AgentMemoryTemplateService.class);
        memoryJobService = mock(MemoryJobService.class);
        memorySeedTemplateService = mock(MemorySeedTemplateService.class);
        projectService = new ProjectService(projectDao, planService, lifecycleService, workspaceManager,
                agentMemoryTemplateService, memoryJobService, memorySeedTemplateService);
    }

    @Disabled("新建项目需在 super.save 后获取 id，依赖 Spring 容器与数据库；本地无 Docker 时由集成测试覆盖")
    @Test
    void save_newProject_generatesWorkspacePathAndDoesNotSpawnPlan() {
        // 准备
        Project entity = new Project();
        entity.setName("测试项目");
        entity.setDesign("测试设计");
        when(projectDao.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("proj-1");
            }
            return p;
        });
        when(workspaceManager.resolve("proj-1"))
                .thenReturn(new com.changhong.onlinecode.dto.WorkspaceResolveResult("/tmp/sei-online-code/proj-1",
                        false, null));

        // 执行
        OperateResultWithData<Project> result = projectService.save(entity);

        // 验证
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals("proj-1", result.getData().getId());
        assertNotNull(result.getData().getWorkspacePath());
        assertTrue(result.getData().getWorkspacePath().contains("proj-1"));
        verify(planService, never()).regenerate(anyString(), any());
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 Spring 容器与数据库；本地无 Docker 时由集成测试覆盖")
    @Test
    void save_preservesProvidedWorkspacePath() {
        // 准备
        Project entity = new Project();
        entity.setName("测试项目");
        entity.setWorkspacePath("/custom/workspace/proj-2");
        when(projectDao.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // 执行
        OperateResultWithData<Project> result = projectService.save(entity);

        // 验证
        assertTrue(result.successful());
        assertEquals("/custom/workspace/proj-2", result.getData().getWorkspacePath());
        verify(workspaceManager, never()).resolve(anyString());
    }

    @Test
    void refineSpec_delegatesToPlanService() {
        // 准备
        String projectId = "proj-1";
        Project project = new Project();
        project.setId(projectId);
        when(projectDao.findOne(projectId)).thenReturn(project);
        PlanDto planDto = new PlanDto();
        OperateResultWithData<PlanDto> success = OperateResultWithData.operationSuccessWithData(planDto);
        when(planService.regenerate(eq(projectId), isNull())).thenReturn(success);

        // 执行
        OperateResultWithData<PlanDto> result = projectService.refineSpec(projectId);

        // 验证
        assertTrue(result.successful());
        verify(planService).regenerate(eq(projectId), isNull());
    }
}
