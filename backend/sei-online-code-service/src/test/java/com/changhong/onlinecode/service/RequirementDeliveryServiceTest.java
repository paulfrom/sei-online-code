package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.entity.Requirement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RequirementDeliveryServiceTest {

    @Test
    void changeRequest_reusesPreviouslyDeliveredBranch() throws Exception {
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(ConfigService.class), mock(WorkspaceManager.class),
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class));
        Requirement requirement = new Requirement();
        requirement.setId("requirement-12345678");
        requirement.setActiveLoopId("new-loop-12345678");
        requirement.setDeliveryBranch("feature/req-old-loop");
        Method branchName = RequirementDeliveryService.class.getDeclaredMethod("branchName", Requirement.class);
        branchName.setAccessible(true);

        assertEquals("feature/req-old-loop", branchName.invoke(service, requirement));
    }
}
