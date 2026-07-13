package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequirementAutomationService 单元测试。
 */
class RequirementAutomationServiceTest {

    private RequirementDao requirementDao;
    private CodingTaskDao codingTaskDao;
    private CodingTaskScheduler codingTaskScheduler;
    private RequirementAutomationService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        codingTaskScheduler = mock(CodingTaskScheduler.class);
        service = new RequirementAutomationService(requirementDao, codingTaskDao, codingTaskScheduler);
    }

    @Test
    void persistSuccess_createsCodingTasksAndCallsScheduler() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        List<RequirementAutomationService.PlanTask> planTasks = List.of(
                new RequirementAutomationService.PlanTask(
                        "FE-001", "前端页面", "实现登录页", "frontend-dev-agent", "frontend",
                        List.of(), List.of("frontend/src/pages/Login.tsx")),
                new RequirementAutomationService.PlanTask(
                        "BE-001", "后端接口", "实现登录接口", "backend-dev-agent", "backend",
                        List.of(), List.of("backend/src/main/java/LoginController.java"))
        );

        service.persistSuccess("req-1", "plan-1", "loop-1", "proj-1", planTasks);

        assertEquals("loop-1", requirement.getActiveLoopId());
        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao, times(2)).save(captor.capture());
        List<CodingTask> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        CodingTask fe = saved.stream().filter(t -> "FE-001".equals(t.getPlanTaskKey())).findFirst().orElseThrow();
        assertEquals("frontend", fe.getArea());
        assertEquals("frontend-dev-agent", fe.getAssignedAgent());
        assertEquals(CodingTaskStatus.PENDING, fe.getStatus());
        assertNotNull(fe.getFileScope());
        assertEquals("loop-1", fe.getLoopId());

        verify(codingTaskScheduler).schedule(eq("req-1"));
    }
}
