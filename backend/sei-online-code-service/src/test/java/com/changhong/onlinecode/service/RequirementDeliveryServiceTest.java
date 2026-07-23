package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.service.progress.EffectService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementDeliveryServiceTest {

    @Test
    void changeRequest_reusesPreviouslyDeliveredBranch() throws Exception {
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class),
                mock(ConfigService.class), mock(WorkspaceManager.class),
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class),
                mock(ProjectDao.class));
        Requirement requirement = new Requirement();
        requirement.setId("requirement-12345678");
        requirement.setActiveLoopId("new-loop-12345678");
        requirement.setDeliveryBranch("feature/req-old-loop");
        Method branchName = RequirementDeliveryService.class.getDeclaredMethod("branchName", Requirement.class);
        branchName.setAccessible(true);

        assertEquals("feature/req-old-loop", branchName.invoke(service, requirement));
    }

    @Test
    void successCommentMetadataContainsAllDeliveryAndValidationFacts() throws Exception {
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class),
                mock(ConfigService.class), mock(WorkspaceManager.class),
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class),
                mock(ProjectDao.class));
        Method method = RequirementDeliveryService.class.getDeclaredMethod("buildSuccessMetadata",
                String.class, String.class, String.class, String.class, String.class, String.class);
        method.setAccessible(true);

        String json = (String) method.invoke(service, "feature/req-1", "abc123", "main",
                "https://gitlab/mr/1", "run-1", "全量验证通过\"含引号\"");
        JsonNode root = new ObjectMapper().readTree(json);

        assertEquals("feature/req-1", root.path("branch").asText());
        assertEquals("abc123", root.path("commitHash").asText());
        assertEquals("main", root.path("targetBranch").asText());
        assertEquals("https://gitlab/mr/1", root.path("mrUrl").asText());
        assertEquals("run-1", root.path("runId").asText());
        assertEquals("全量验证通过\"含引号\"", root.path("validationSummary").asText());
    }

    @Test
    void deliveryEffectKeys_includeCandidateCommitSoMrUpdatesDoNotConflict() throws Exception {
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class),
                mock(ConfigService.class), mock(WorkspaceManager.class),
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class),
                mock(ProjectDao.class));
        Method pushKey = RequirementDeliveryService.class.getDeclaredMethod("pushEffectKey",
                String.class, String.class, String.class);
        Method mrKey = RequirementDeliveryService.class.getDeclaredMethod("mrEffectKey",
                String.class, String.class, String.class);
        pushKey.setAccessible(true);
        mrKey.setAccessible(true);

        assertEquals("push:42:feature/req-1:commit-a",
                pushKey.invoke(service, "42", "feature/req-1", "commit-a"));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                mrKey.invoke(service, "42", "feature/req-1", "commit-a"),
                mrKey.invoke(service, "42", "feature/req-1", "commit-b"));
    }

    @Test
    void projectDeliveryTargetBranch_overridesPlatformFallback() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class), configService, mock(WorkspaceManager.class),
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class), projectDao);
        Requirement requirement = new Requirement();
        requirement.setProjectId("project-1");
        Project project = new Project();
        project.setDeliveryTargetBranch("release/1.0");
        when(projectDao.findOne("project-1")).thenReturn(project);

        Method method = RequirementDeliveryService.class.getDeclaredMethod(
                "resolveDeliveryTargetBranch", Requirement.class);
        method.setAccessible(true);

        assertEquals("release/1.0", method.invoke(service, requirement));
    }
}
