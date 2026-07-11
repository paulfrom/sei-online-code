package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryConflictFinding;
import com.changhong.onlinecode.service.memory.MemoryNormClaim;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.changhong.onlinecode.service.memory.WorkspaceMemoryScanResult;
import com.changhong.onlinecode.service.memory.WorkspaceMemoryScannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequirementDesignContextService 单元测试。
 *
 * <p>WHY：需求级上下文过滤直接决定注入 PRD/概览/详细设计的 RealityClaim/NormClaim 范围。
 * 原实现 matchesKeywords 在未命中时无条件返回 true，使过滤退化为全量注入，引入大量与需求无关的
 * 代码事实与规范噪声，削弱 source-backed 设计质量。这里通过 prepare 端到端断言：只有命中需求关键词的
 * claim 才进入 RequirementRelatedSnapshot，不相关的被剔除；需求关键词全过短时退化全取避免漏注入。</p>
 *
 * @author sei-online-code
 */
class RequirementDesignContextServiceTest {

    private RequirementDao requirementDao;
    private ProjectDao projectDao;
    private WorkspaceMemoryService workspaceMemoryService;
    private WorkspaceMemoryScannerService scannerService;
    private RequirementDesignContextDao requirementDesignContextDao;
    private RequirementDesignContextService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        projectDao = mock(ProjectDao.class);
        workspaceMemoryService = mock(WorkspaceMemoryService.class);
        scannerService = mock(WorkspaceMemoryScannerService.class);
        requirementDesignContextDao = mock(RequirementDesignContextDao.class);
        service = new RequirementDesignContextService(requirementDao, projectDao,
                workspaceMemoryService, scannerService, requirementDesignContextDao);
    }

    @Test
    void prepare_filtersOutRealitiesNotMatchingRequirementKeywords() throws Exception {
        // WHY：需求关键词命中是“相关”的唯一判定，未命中不应注入，否则上下文被无关代码事实淹没。
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        requirement.setTitle("订单导入");
        requirement.setDescription("支持批量导入订单");

        Project project = new Project();
        project.setId("proj-1");
        project.setWorkspacePath("/workspace/proj-1");

        WorkspaceMemory wm = new WorkspaceMemory();
        wm.setId("wm-1");
        wm.setVersion(1);

        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(projectDao.findOne("proj-1")).thenReturn(project);
        when(workspaceMemoryService.ensureCurrentWorkspaceMemory("proj-1")).thenReturn(wm);

        // 命中：含“订单”；未命中：仅含“用户”
        MemoryRealityClaim hit = realityClaim("reality-1", "src/main/java/OrderService.java",
                "OrderService 订单导入服务");
        MemoryRealityClaim miss = realityClaim("reality-2", "src/main/java/UserService.java",
                "UserService 用户登录服务");
        WorkspaceMemoryScanResult scan = new WorkspaceMemoryScanResult();
        scan.setNormClaims(List.of());
        scan.setRealityClaims(new ArrayList<>(List.of(hit, miss)));
        scan.setConflictFindings(List.of());
        scan.setSourceFingerprintsJson("[]");
        when(scannerService.scan(eq("proj-1"), eq("/workspace/proj-1"))).thenReturn(scan);
        when(requirementDesignContextDao.findByRequirementIdOrderByVersionDesc("req-1"))
                .thenReturn(List.of());
        when(requirementDesignContextDao.save(any(RequirementDesignContext.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.prepare("req-1");

        ArgumentCaptor<RequirementDesignContext> captor = ArgumentCaptor.forClass(RequirementDesignContext.class);
        verify(requirementDesignContextDao).save(captor.capture());
        String snapshotJson = captor.getValue().getRequirementRelatedSnapshotJson();
        assertTrue(snapshotJson.contains("OrderService"), "命中关键词的 claim 必须保留");
        assertTrue(!snapshotJson.contains("UserService"), "未命中关键词的 claim 必须被过滤掉");
    }

    @Test
    void prepare_emptyKeywordsKeepsAllToAvoidMissingContext() throws Exception {
        // WHY：需求标题/描述全空时无法提取可用关键词，没有可比对项，此时退化到全取以避免漏注入。
        Requirement requirement = new Requirement();
        requirement.setId("req-2");
        requirement.setProjectId("proj-1");
        requirement.setTitle(" ");
        requirement.setDescription("");

        Project project = new Project();
        project.setId("proj-1");
        project.setWorkspacePath("/workspace/proj-1");
        WorkspaceMemory wm = new WorkspaceMemory();
        wm.setId("wm-1");
        wm.setVersion(1);

        when(requirementDao.findOne("req-2")).thenReturn(requirement);
        when(projectDao.findOne("proj-1")).thenReturn(project);
        when(workspaceMemoryService.ensureCurrentWorkspaceMemory("proj-1")).thenReturn(wm);

        MemoryRealityClaim anyClaim = realityClaim("reality-1", "src/main/java/Anything.java",
                "Anything 任意服务");
        WorkspaceMemoryScanResult scan = new WorkspaceMemoryScanResult();
        scan.setNormClaims(List.of());
        scan.setRealityClaims(new ArrayList<>(List.of(anyClaim)));
        scan.setConflictFindings(List.of());
        scan.setSourceFingerprintsJson("[]");
        when(scannerService.scan(eq("proj-1"), eq("/workspace/proj-1"))).thenReturn(scan);
        when(requirementDesignContextDao.findByRequirementIdOrderByVersionDesc("req-2"))
                .thenReturn(List.of());
        when(requirementDesignContextDao.save(any(RequirementDesignContext.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.prepare("req-2");

        ArgumentCaptor<RequirementDesignContext> captor = ArgumentCaptor.forClass(RequirementDesignContext.class);
        verify(requirementDesignContextDao).save(captor.capture());
        String snapshotJson = captor.getValue().getRequirementRelatedSnapshotJson();
        assertTrue(snapshotJson.contains("Anything"), "无可用关键词时应退化全取，避免漏注入");
    }

    private MemoryRealityClaim realityClaim(String id, String source, String content) {
        MemoryRealityClaim claim = new MemoryRealityClaim();
        claim.setId(id);
        claim.setType("backend_code");
        claim.setContent(content);
        claim.setSource(source);
        claim.setSourceHash("hash-" + id);
        claim.setConfidence("source_backed");
        return claim;
    }
}