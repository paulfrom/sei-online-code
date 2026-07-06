package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.IterationDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IterationServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    @Test
    void confirmSpec_startsPlanGenerationInsteadOfCreatingIteration() {
        IterationDao iterationDao = mock(IterationDao.class);
        ProjectService projectService = mock(ProjectService.class);
        SpecService specService = mock(SpecService.class);
        PlanService planService = mock(PlanService.class);
        IterationService service = new IterationService(iterationDao, projectService, specService, planService);

        Spec spec = new Spec();
        spec.setId("spec1");
        spec.setProjectId("project1");
        spec.setVersion(1);
        spec.setState(SpecState.SPEC_REVIEW);
        when(specService.findOne("spec1")).thenReturn(spec);
        OperateResultWithData<Spec> savedSpec = OperateResultWithData.operationSuccessWithData(spec);
        when(specService.save(spec)).thenReturn(savedSpec);

        PlanDto generatingPlan = new PlanDto();
        generatingPlan.setId("plan1");
        generatingPlan.setProjectId("project1");
        generatingPlan.setVersion(1);
        generatingPlan.setStatus(PlanStatus.GENERATING);
        OperateResultWithData<PlanDto> savedPlan = OperateResultWithData.operationSuccessWithData(generatingPlan);
        when(planService.regenerate("project1", null))
                .thenReturn(savedPlan);

        OperateResultWithData<PlanDto> result = service.confirmSpec("spec1");

        assertTrue(result.successful());
        assertEquals(SpecState.CONFIRMED, spec.getState());
        assertEquals("plan1", result.getData().getId());
        assertEquals(PlanStatus.GENERATING, result.getData().getStatus());
        verify(planService).regenerate("project1", null);
        verify(iterationDao, never()).save(any(Iteration.class));
    }
}
