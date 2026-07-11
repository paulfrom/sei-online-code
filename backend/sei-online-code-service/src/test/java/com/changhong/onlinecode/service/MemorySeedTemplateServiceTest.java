package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.MemorySeedTemplateDao;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateSourceType;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MemorySeedTemplateService 单元测试。
 *
 * <p>WHY：审核发现 publish 会归档当前默认模板导致系统失去全局默认（P0-3），saveDraft 新建草稿不校验
 * code/name 落 NOT NULL 列（P0-2）。本测试针对这两条不变式：发布当前默认模板时新版本必须延续 isDefault，
 * 发布非默认模板时不抢默认；save 新建缺 code/name 或 code+version 冲突时给出可读业务错误而非触达 DB。</p>
 *
 * <p>publish/setDefault/archive 走 dao.save 直接入库，不触达 super.save 的容器校验，可在纯 Mockito 下测。
 * save 校验失败路径在 super.save 之前返回，亦可测；save 成功路径依赖 super.save 容器逻辑，留集成测试。</p>
 */
class MemorySeedTemplateServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        // OperateResultWithData.operationFailure 经 ApplicationContextHolder 解析 i18n，单测缺容器会 NPE；
        // 注入回显消息码的 mock 上下文。
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private MemorySeedTemplateDao dao;
    private EntityManager entityManager;
    private MemorySeedTemplateService service;

    @BeforeEach
    void setUp() {
        dao = mock(MemorySeedTemplateDao.class);
        entityManager = mock(EntityManager.class);
        service = new MemorySeedTemplateService(dao);
        // @PersistenceContext 字段以反射注入 mock，避免空指针
        reflectSetEntityManager(service, entityManager);
    }

    private static void reflectSetEntityManager(MemorySeedTemplateService target, EntityManager em) {
        try {
            java.lang.reflect.Field f = MemorySeedTemplateService.class.getDeclaredField("entityManager");
            f.setAccessible(true);
            f.set(target, em);
        } catch (Exception e) {
            fail("注入 EntityManager 失败: " + e.getMessage());
        }
    }

    private MemorySeedTemplate activeTemplate(String id, String code, int version, boolean isDefault) {
        MemorySeedTemplate t = new MemorySeedTemplate();
        t.setId(id);
        t.setCode(code);
        t.setName(code + " 模板");
        t.setVersion(version);
        t.setStatus(MemorySeedTemplateStatus.ACTIVE);
        t.setIsDefault(isDefault);
        t.setSourceType(MemorySeedTemplateSourceType.BUILTIN);
        return t;
    }

    @Test
    void publish_currentDefault_newVersionKeepsDefaultAndArchivesOld() {
        // WHY：发布当前默认模板时若新版本 isDefault=false，归档旧默认后系统将没有 ACTIVE+is_default=true 模板。
        MemorySeedTemplate current = activeTemplate("default:1", "default", 1, true);
        when(dao.findOne("default:1")).thenReturn(current);
        when(dao.findByCode("default")).thenReturn(List.of(current));
        when(dao.findActiveDefault(MemorySeedTemplateStatus.ACTIVE)).thenReturn(current);
        when(dao.save(any(MemorySeedTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        OperateResultWithData<MemorySeedTemplate> result = service.publish("default:1");

        assertTrue(result.successful());
        MemorySeedTemplate published = result.getData();
        assertEquals(MemorySeedTemplateStatus.ACTIVE, published.getStatus());
        assertEquals(2, published.getVersion());
        // 关键不变式：新版本延续全局默认
        assertTrue(published.getIsDefault(), "发布当前默认模板时新版本必须延续 isDefault=true");
        // 旧默认被归档并复位 isDefault
        assertEquals(MemorySeedTemplateStatus.ARCHIVED, current.getStatus());
        assertFalse(current.getIsDefault(), "归档行应复位 isDefault，避免 ARCHIVED 残留 true 误导语义");
    }

    @Test
    void publish_nonDefaultTemplate_newVersionNotDefaultAndKeepsGlobalDefault() {
        // WHY：发布非默认模板不应抢夺全局默认；当前默认模板保持不变。
        MemorySeedTemplate currentDefault = activeTemplate("default:1", "default", 1, true);
        MemorySeedTemplate other = activeTemplate("java:1", "java-service", 1, false);
        when(dao.findOne("java:1")).thenReturn(other);
        when(dao.findByCode("java-service")).thenReturn(List.of(other));
        when(dao.findActiveDefault(MemorySeedTemplateStatus.ACTIVE)).thenReturn(currentDefault);
        when(dao.save(any(MemorySeedTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        OperateResultWithData<MemorySeedTemplate> result = service.publish("java:1");

        assertTrue(result.successful());
        assertFalse(result.getData().getIsDefault(), "发布非默认模板不应设为默认");
        // 全局默认不受影响
        assertTrue(currentDefault.getIsDefault());
        assertEquals(MemorySeedTemplateStatus.ACTIVE, currentDefault.getStatus());
    }

    @Test
    void archive_currentDefault_rejected() {
        // WHY：当前默认模板必须先 setDefault 切换后才能归档，否则系统失去默认。
        MemorySeedTemplate current = activeTemplate("default:1", "default", 1, true);
        when(dao.findOne("default:1")).thenReturn(current);

        OperateResultWithData<MemorySeedTemplate> result = service.archive("default:1");

        assertFalse(result.successful());
        assertEquals(MemorySeedTemplateStatus.ACTIVE, current.getStatus(), "拒绝归档不应改状态");
    }

    @Test
    void setDefault_switchesOffOldDefaultInSameCall() {
        // WHY：setDefault 必须在同一事务内取消原默认，否则会出现两个 ACTIVE+is_default=true 违反唯一约束。
        MemorySeedTemplate oldDefault = activeTemplate("default:1", "default", 1, true);
        MemorySeedTemplate target = activeTemplate("java:1", "java-service", 1, false);
        when(dao.findOne("java:1")).thenReturn(target);
        when(dao.findActiveDefault(MemorySeedTemplateStatus.ACTIVE)).thenReturn(oldDefault);
        when(dao.save(any(MemorySeedTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        OperateResultWithData<MemorySeedTemplate> result = service.setDefault("java:1");

        assertTrue(result.successful());
        assertTrue(target.getIsDefault());
        assertFalse(oldDefault.getIsDefault(), "原默认必须在同一调用内被撤销");
    }

    @Test
    void save_newDraftMissingCode_rejectedBeforeDb() {
        // WHY：迁移脚本 code NOT NULL，缺 code 直接落库会抛 DB 异常；service 应给出可读业务错误。
        MemorySeedTemplate draft = new MemorySeedTemplate();
        draft.setName("无 code 草稿");
        // 不设 code

        OperateResultWithData<MemorySeedTemplate> result = service.save(draft);

        assertFalse(result.successful());
        verify(dao, never()).save(any(MemorySeedTemplate.class));
    }

    @Test
    void save_newDraftMissingName_rejectedBeforeDb() {
        // WHY：name 同为 NOT NULL，缺 name 同样应拒绝。
        MemorySeedTemplate draft = new MemorySeedTemplate();
        draft.setCode("java-service");
        // 不设 name

        OperateResultWithData<MemorySeedTemplate> result = service.save(draft);

        assertFalse(result.successful());
        verify(dao, never()).save(any(MemorySeedTemplate.class));
    }

    @Test
    void save_newDraftCodeVersionConflict_rejectedBeforeDb() {
        // WHY：uk_memory_seed_template_code_version 唯一索引兜底，但 DB 异常不可读；service 应预检。
        MemorySeedTemplate existing = activeTemplate("java:1", "java-service", 1, false);
        when(dao.findByCode("java-service")).thenReturn(List.of(existing));

        MemorySeedTemplate draft = new MemorySeedTemplate();
        draft.setCode("java-service");
        draft.setName("重复版本草稿");
        draft.setVersion(1); // 与已存在版本冲突

        OperateResultWithData<MemorySeedTemplate> result = service.save(draft);

        assertFalse(result.successful());
        verify(dao, never()).save(any(MemorySeedTemplate.class));
    }

    @Test
    void resolveForProject_boundIdResolvedEvenIfArchived() {
        // WHY：项目已绑定模板即便后续归档，仍可沿用补齐，不因归档失效（契约 §9.1）。
        MemorySeedTemplate archived = activeTemplate("java:1", "java-service", 1, false);
        archived.setStatus(MemorySeedTemplateStatus.ARCHIVED);
        when(dao.findOne("java:1")).thenReturn(archived);

        MemorySeedTemplate resolved = service.resolveForProject("java:1");

        assertNotNull(resolved);
        assertEquals("java:1", resolved.getId());
    }

    @Test
    void resolveForProject_unbound_fallsBackToActiveDefault() {
        // WHY：项目未绑定时回退全局默认，保证空项目也能拿到 seed。
        when(dao.findActiveDefault(MemorySeedTemplateStatus.ACTIVE)).thenReturn(null);

        assertNull(service.resolveForProject(null));
        assertNull(service.resolveForProject(""));
        assertNull(service.resolveForProject("   "));
    }
}
