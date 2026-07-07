package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dto.FeatureDesignDto;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FeatureDesignService 单元测试。
 *
 * <p>OperateResult 构造经 ApplicationContextHolder 解析 i18n，单测缺容器会 NPE；
 * {@code @BeforeAll} 注入回显消息码的 mock 上下文。
 */
class FeatureDesignServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private FeatureDesignDao featureDesignDao;
    private PlanAgentService planAgentService;
    private FailureInfoSupport failureInfoSupport;
    private FeatureDesignService featureDesignService;

    @BeforeEach
    void setUp() {
        featureDesignDao = mock(FeatureDesignDao.class);
        planAgentService = mock(PlanAgentService.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        featureDesignService = new FeatureDesignService(featureDesignDao, planAgentService, failureInfoSupport);
    }

    @Test
    void edit_throwsConflictWhenBuilding() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setVersion(1);
        existing.setBuildStatus(FeatureDesignBuildStatus.BUILDING);
        when(featureDesignDao.findLatestById(id)).thenReturn(existing);

        // 执行 & 验证
        assertThrows(ConflictException.class, () ->
                featureDesignService.edit(id, new FeatureDesignContent())
        );
        verify(featureDesignDao, never()).markNonLatest(any(), any());
    }

    @Test
    void regenerate_throwsConflictWhenBuilding() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setVersion(1);
        existing.setBuildStatus(FeatureDesignBuildStatus.BUILDING);
        when(featureDesignDao.findLatestById(id)).thenReturn(existing);

        // 执行 & 验证
        assertThrows(ConflictException.class, () ->
                featureDesignService.regenerate(id, "modify")
        );
        verify(featureDesignDao, never()).markNonLatest(any(), any());
        verify(planAgentService, never()).spawnFeatureDesign(any(), any(), any());
    }

    @Test
    void confirm_rejectsWhenStale() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setStatus(FeatureDesignStatus.STALE);
        when(featureDesignDao.findLatestById(id)).thenReturn(existing);

        // 执行
        OperateResultWithData<List<FeatureDesignDto>> result = featureDesignService.confirm(List.of(id));

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("仅草稿状态可确认"));
    }

    @Test
    void confirm_rejectsWhenNotDraft() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setStatus(FeatureDesignStatus.CONFIRMED);
        when(featureDesignDao.findLatestById(id)).thenReturn(existing);

        // 执行
        OperateResultWithData<List<FeatureDesignDto>> result = featureDesignService.confirm(List.of(id));

        // 验证
        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("仅草稿状态可确认"));
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
    @Test
    void edit_success() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setVersion(1);
        existing.setStatus(FeatureDesignStatus.DRAFT);
        existing.setBuildStatus(FeatureDesignBuildStatus.IDLE);
        existing.setIsLatest(true);

        FeatureDesignContent newContent = new FeatureDesignContent();
        newContent.setDesign(new com.fasterxml.jackson.databind.node.TextNode("new design"));

        when(featureDesignDao.findLatestById(id)).thenReturn(existing);
        when(featureDesignDao.save(any(FeatureDesign.class))).thenAnswer(inv -> {
            FeatureDesign fd = inv.getArgument(0);
            fd.setId("fd2");
            return fd;
        });

        // 执行
        OperateResultWithData<FeatureDesignDto> result = featureDesignService.edit(id, newContent);

        // 验证
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getVersion());
        assertTrue(result.getData().getIsLatest());
        assertEquals(FeatureDesignStatus.DRAFT, result.getData().getStatus());

        verify(featureDesignDao).markNonLatest("proj1", "feat1");

        ArgumentCaptor<FeatureDesign> fdCaptor = ArgumentCaptor.forClass(FeatureDesign.class);
        verify(featureDesignDao).save(fdCaptor.capture());
        assertEquals("new design", fdCaptor.getValue().getContent().getDesign().asText());
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
    @Test
    void edit_setsStaleWhenBuilt() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setVersion(1);
        existing.setStatus(FeatureDesignStatus.DRAFT);
        existing.setBuildStatus(FeatureDesignBuildStatus.BUILT);
        existing.setIsLatest(true);

        when(featureDesignDao.findLatestById(id)).thenReturn(existing);
        when(featureDesignDao.save(any(FeatureDesign.class))).thenAnswer(inv -> {
            FeatureDesign fd = inv.getArgument(0);
            fd.setId("fd2");
            return fd;
        });

        // 执行
        OperateResultWithData<FeatureDesignDto> result = featureDesignService.edit(id, new FeatureDesignContent());

        // 验证
        assertTrue(result.successful());
        assertEquals(FeatureDesignBuildStatus.STALE, result.getData().getBuildStatus());
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
    @Test
    void regenerate_success() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setVersion(1);
        existing.setStatus(FeatureDesignStatus.DRAFT);
        existing.setBuildStatus(FeatureDesignBuildStatus.IDLE);
        existing.setIsLatest(true);

        when(featureDesignDao.findLatestById(id)).thenReturn(existing);
        when(featureDesignDao.save(any(FeatureDesign.class))).thenAnswer(inv -> {
            FeatureDesign fd = inv.getArgument(0);
            fd.setId("fd2");
            return fd;
        });

        // 执行
        OperateResultWithData<FeatureDesignDto> result = featureDesignService.regenerate(id, "modify hint");

        // 验证
        assertTrue(result.successful());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getVersion());
        assertTrue(result.getData().getIsLatest());
        assertEquals(FeatureDesignStatus.GENERATING, result.getData().getStatus());
        assertEquals("modify hint", result.getData().getModifyHint());

        verify(featureDesignDao).markNonLatest("proj1", "feat1");
        verify(planAgentService).spawnFeatureDesign("proj1", "feat1", "modify hint");
    }

    @Disabled("super.save → BaseService.validateUniqueCode 需 @SpringBootTest；rejection 路径已验证，success 落库待集成测试")
    @Test
    void confirm_success() {
        // 准备
        String id = "fd1";
        FeatureDesign existing = new FeatureDesign();
        existing.setId(id);
        existing.setProjectId("proj1");
        existing.setFeatureId("feat1");
        existing.setStatus(FeatureDesignStatus.DRAFT);
        existing.setIsLatest(true);

        when(featureDesignDao.findLatestById(id)).thenReturn(existing);
        when(featureDesignDao.save(any(FeatureDesign.class))).thenReturn(existing);

        // 执行
        OperateResultWithData<FeatureDesignDto> result = featureDesignService.confirmOne(id);

        // 验证
        assertTrue(result.successful());
        assertEquals(FeatureDesignStatus.CONFIRMED, existing.getStatus());
    }
}
