package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.DeliveryMrStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.progress.EffectService;
import org.junit.jupiter.api.Test;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.MergeRequest;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

class RequirementDeliveryServiceTest {

    @Test
    void changeRequest_reusesPreviouslyDeliveredBranch() throws Exception {
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class),
                mock(ConfigService.class), mock(WorkspaceManager.class),
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class),
                mock(ProjectDao.class), mock(RequirementWorkspaceDao.class));
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
                mock(ProjectDao.class), mock(RequirementWorkspaceDao.class));
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
                mock(ProjectDao.class), mock(RequirementWorkspaceDao.class));
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
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class), projectDao,
                mock(RequirementWorkspaceDao.class));
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

    @Test
    void manualDelivery_usesCurrentWorkspaceAndBranchWithoutResolvingRequirementBranch() throws Exception {
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        RequirementWorkspaceDao workspaceDao = mock(RequirementWorkspaceDao.class);
        RequirementDeliveryService service = new RequirementDeliveryService(
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class), mock(ConfigService.class), workspaceManager,
                mock(RequirementCommentService.class), mock(MemoryJobService.class),
                mock(WorkspaceMemoryService.class), mock(EffectService.class), mock(GitApi.class),
                mock(ProjectDao.class), workspaceDao);
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setProjectId("project-1");
        RequirementWorkspace workspace = new RequirementWorkspace();
        workspace.setWorkspacePath("/tmp");
        when(workspaceDao.findByProjectIdAndRequirementId("project-1", "requirement-1"))
                .thenReturn(Optional.of(workspace));
        when(workspaceManager.getCurrentBranch(Path.of("/tmp"))).thenReturn("hotfix/arbitrary-branch");

        Method resolveWorkspace = RequirementDeliveryService.class.getDeclaredMethod(
                "resolveDeliveryWorkspace", Requirement.class, boolean.class);
        resolveWorkspace.setAccessible(true);
        Path resolved = (Path) resolveWorkspace.invoke(service, requirement, true);
        Method resolveSourceBranch = RequirementDeliveryService.class.getDeclaredMethod(
                "resolveSourceBranch", Requirement.class, Path.class, boolean.class);
        resolveSourceBranch.setAccessible(true);

        assertEquals(Path.of("/tmp"), resolved);
        assertEquals("hotfix/arbitrary-branch", resolveSourceBranch.invoke(service, requirement, resolved, true));
        verify(workspaceManager, never()).resolveRequirementWorkspace("project-1", "requirement-1");
        verify(workspaceManager, never()).ensureOnBranch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void manualDelivery_allowsLatestPlanRegardlessOfPlanStatusWhenWorkspaceIsDirty() {
        RequirementDao requirementDao = mock(RequirementDao.class);
        ExecutionPlanDao executionPlanDao = mock(ExecutionPlanDao.class);
        RunDao runDao = mock(RunDao.class);
        ConfigService configService = mock(ConfigService.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        RequirementWorkspaceDao workspaceDao = mock(RequirementWorkspaceDao.class);
        RequirementDeliveryService service = new RequirementDeliveryService(
                requirementDao, executionPlanDao, runDao, mock(RunNumberService.class),
                configService, workspaceManager, mock(RequirementCommentService.class),
                mock(MemoryJobService.class), mock(WorkspaceMemoryService.class),
                mock(EffectService.class), mock(GitApi.class), mock(ProjectDao.class), workspaceDao);
        Requirement requirement = manualRequirement();
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        RequirementWorkspace workspace = new RequirementWorkspace();
        workspace.setWorkspacePath("/tmp");
        when(requirementDao.findOne("requirement-1")).thenReturn(requirement);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                "requirement-1", "loop-1")).thenReturn(plan);
        when(executionPlanDao.findOne("plan-1")).thenReturn(plan);
        when(workspaceDao.findByProjectIdAndRequirementId("project-1", "requirement-1"))
                .thenReturn(Optional.of(workspace));
        when(workspaceManager.getChangedFiles(Path.of("/tmp"))).thenReturn(List.of("src/App.tsx"));
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-1");
            }
            return run;
        });

        Requirement result = service.submit("requirement-1");

        assertEquals(requirement, result);
        assertEquals(RequirementAutomationStatus.WAITING_HUMAN, result.getAutomationStatus());
        verify(workspaceManager).getChangedFiles(Path.of("/tmp"));
    }

    @Test
    void manualDelivery_rejectsCleanWorkspace() {
        RequirementDao requirementDao = mock(RequirementDao.class);
        ExecutionPlanDao executionPlanDao = mock(ExecutionPlanDao.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        RequirementWorkspaceDao workspaceDao = mock(RequirementWorkspaceDao.class);
        RequirementDeliveryService service = new RequirementDeliveryService(
                requirementDao, executionPlanDao, mock(RunDao.class), mock(RunNumberService.class),
                mock(ConfigService.class), workspaceManager, mock(RequirementCommentService.class),
                mock(MemoryJobService.class), mock(WorkspaceMemoryService.class),
                mock(EffectService.class), mock(GitApi.class), mock(ProjectDao.class), workspaceDao);
        Requirement requirement = manualRequirement();
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setStatus(ExecutionPlanStatus.READY);
        RequirementWorkspace workspace = new RequirementWorkspace();
        workspace.setWorkspacePath("/tmp");
        when(requirementDao.findOne("requirement-1")).thenReturn(requirement);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                "requirement-1", "loop-1")).thenReturn(plan);
        when(workspaceDao.findByProjectIdAndRequirementId("project-1", "requirement-1"))
                .thenReturn(Optional.of(workspace));
        when(workspaceManager.getChangedFiles(Path.of("/tmp"))).thenReturn(List.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.submit("requirement-1"));

        assertEquals("当前工作区没有未提交修改", exception.getMessage());
    }

    private Requirement manualRequirement() {
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setProjectId("project-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        requirement.setAutomationStatus(RequirementAutomationStatus.WAITING_HUMAN);
        return requirement;
    }

    @Test
    void refreshMrStatus_recordsAuthoritativeMergedState() throws Exception {
        RequirementDao requirementDao = mock(RequirementDao.class);
        ConfigService configService = mock(ConfigService.class);
        GitApi gitApi = mock(GitApi.class);
        RequirementCommentService commentService = mock(RequirementCommentService.class);
        RequirementDeliveryService service = new RequirementDeliveryService(
                requirementDao, mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RunNumberService.class), configService, mock(WorkspaceManager.class),
                commentService, mock(MemoryJobService.class), mock(WorkspaceMemoryService.class),
                mock(EffectService.class), gitApi, mock(ProjectDao.class), mock(RequirementWorkspaceDao.class));
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        requirement.setDeliveryMrUrl("https://gitlab.example/group/project/-/merge_requests/12");
        requirement.setDeliveryMrStatus(DeliveryMrStatus.OPEN);
        when(requirementDao.findOne("requirement-1")).thenReturn(requirement);
        when(configService.resolveGitlabProjectId(null)).thenReturn("group/project");
        GitLabApi client = mock(GitLabApi.class);
        MergeRequestApi mergeRequestApi = mock(MergeRequestApi.class);
        when(gitApi.client(null)).thenReturn(client);
        when(client.getMergeRequestApi()).thenReturn(mergeRequestApi);
        MergeRequest mr = new MergeRequest();
        mr.setIid(12L);
        mr.setState("merged");
        mr.setMergeCommitSha("merge-sha");
        when(mergeRequestApi.getMergeRequest("group/project", 12L)).thenReturn(mr);

        DeliveryMrStatus result = service.refreshMrStatus("requirement-1");

        assertEquals(DeliveryMrStatus.MERGED, result);
        assertEquals(12L, requirement.getDeliveryMrIid());
        assertEquals("merge-sha", requirement.getDeliveryMergeCommitHash());
        assertEquals(RequirementStatus.WAITING_FEEDBACK, requirement.getStatus());
        verify(requirementDao).save(requirement);
        verify(commentService).append(eq("requirement-1"), eq("loop-1"),
                org.mockito.ArgumentMatchers.any(), eq("gitlab"),
                eq(com.changhong.onlinecode.dto.enums.RequirementCommentType.MR_MERGED),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
