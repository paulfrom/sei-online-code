package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.entity.Run;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunNumberServiceTest {

    @Test
    void assign_usesRequirementAndLoopSequence() {
        RunDao runDao = mock(RunDao.class);
        Run existing = new Run();
        existing.setRunNo(3);
        when(runDao.findByRequirementIdAndLoopId("req-1", "loop-1")).thenReturn(List.of(existing));
        RunNumberService service = new RunNumberService(runDao);

        Run run = new Run();
        run.setRequirementId("req-1");
        run.setLoopId("loop-1");

        service.assign(run);

        assertEquals(4, run.getRunNo());
    }

    @Test
    void assign_preservesExistingRunNo() {
        RunDao runDao = mock(RunDao.class);
        RunNumberService service = new RunNumberService(runDao);
        Run run = new Run();
        run.setRunNo(9);

        service.assign(run);

        assertEquals(9, run.getRunNo());
        verify(runDao, never()).findByRequirementIdOrderByCreatedDateDesc(org.mockito.ArgumentMatchers.any());
    }
}
