package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.entity.Run;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunServiceTest {

    @Test
    void findByRequirement_returnsAllRequirementRuns() {
        RunDao dao = mock(RunDao.class);
        RunService service = new RunService(dao);
        List<Run> expected = List.of(new Run(), new Run());
        when(dao.findByRequirementIdOrderByCreatedDateDesc("req-1")).thenReturn(expected);

        List<Run> actual = service.findByRequirementId("req-1");

        assertSame(expected, actual);
        verify(dao).findByRequirementIdOrderByCreatedDateDesc("req-1");
    }
}
