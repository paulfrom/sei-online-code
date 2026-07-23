package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidationTaskExecutionServiceTest {

    @Test
    void executeRunsAgentOutsideSettlementAndDelegatesToTransactionalFinisher() {
        CodingTaskDao taskDao = mock(CodingTaskDao.class);
        RequirementDao requirementDao = mock(RequirementDao.class);
        ValidationLoopService validationLoopService = mock(ValidationLoopService.class);
        ValidationTaskSettlementService settlement = mock(ValidationTaskSettlementService.class);
        Executor directExecutor = Runnable::run;
        ValidationTaskExecutionService service = new ValidationTaskExecutionService(
                taskDao, requirementDao, validationLoopService, settlement, directExecutor);
        CodingTask task = validatingTask();
        Requirement requirement = requirement();
        when(taskDao.findOne("task-1")).thenReturn(task);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(validationLoopService.validateTask(task))
                .thenReturn(new ValidationLoopService.ValidationOutcome(true, List.of()));

        service.executeAsync("task-1");

        verify(validationLoopService).validateTask(task);
        verify(settlement).finish("task-1", true);
    }

    @Test
    void rejectedSubmissionRestoresPendingThroughSettlementBoundary() {
        ValidationTaskSettlementService settlement = mock(ValidationTaskSettlementService.class);
        Executor rejectingExecutor = command -> { throw new RejectedExecutionException("full"); };
        ValidationTaskExecutionService service = new ValidationTaskExecutionService(
                mock(CodingTaskDao.class), mock(RequirementDao.class),
                mock(ValidationLoopService.class), settlement, rejectingExecutor);

        service.executeAsync("task-1");

        verify(settlement).defer("task-1", "validation agent executor queue is full");
    }

    @Test
    void claimRejectsTaskThatIsNoLongerPending() {
        CodingTaskDao taskDao = mock(CodingTaskDao.class);
        CodingTask running = validatingTask();
        ValidationTaskExecutionService service = new ValidationTaskExecutionService(
                taskDao, mock(RequirementDao.class), mock(ValidationLoopService.class),
                mock(ValidationTaskSettlementService.class), Runnable::run);
        when(taskDao.findOne("task-1")).thenReturn(running);

        assertFalse(service.claim(running));
    }

    @Test
    void settlementMethodsRequireNewTransaction() throws Exception {
        for (String methodName : List.of("finish", "finishOnFailure", "markStale", "defer")) {
            Method method = switch (methodName) {
                case "finish" -> ValidationTaskSettlementService.class.getMethod(methodName, String.class, boolean.class);
                case "finishOnFailure", "defer" -> ValidationTaskSettlementService.class.getMethod(
                        methodName, String.class, String.class);
                default -> ValidationTaskSettlementService.class.getMethod(methodName, String.class);
            };
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertEquals(Propagation.REQUIRES_NEW, transactional.propagation(), methodName);
        }
    }

    private CodingTask validatingTask() {
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("req-1");
        task.setLoopId("loop-1");
        task.setStatus(CodingTaskStatus.VALIDATING);
        return task;
    }

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-1");
        return requirement;
    }
}
