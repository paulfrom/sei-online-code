package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.MemoryJobDao;
import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MemoryJobService 单元测试。
 *
 * <p>WHY：审核发现 tryClaim 是 read-check-write（先 findOne 再 save），两个执行器并发读到 PENDING
 * 会都切 RUNNING（P1-4）。改为条件 UPDATE 后，tryClaim 必须按受影响行数判定：行数=1 才算抢占成功，
 * 行数=0（状态已被他人改）返回 null。本测试验证 CAS 语义不依赖读到的旧状态。</p>
 *
 * <p>submit 幂等：相同 idempotencyKey 命中既有 job 直接返回，不重复落库。</p>
 */
class MemoryJobServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private MemoryJobDao dao;
    private EntityManager entityManager;
    private MemoryJobService service;

    @BeforeEach
    void setUp() {
        dao = mock(MemoryJobDao.class);
        entityManager = mock(EntityManager.class);
        service = new MemoryJobService(dao);
        try {
            java.lang.reflect.Field f = MemoryJobService.class.getDeclaredField("entityManager");
            f.setAccessible(true);
            f.set(service, entityManager);
        } catch (Exception e) {
            fail("注入 EntityManager 失败: " + e.getMessage());
        }
    }

    private MemoryJob pendingJob(String id) {
        MemoryJob job = new MemoryJob();
        job.setId(id);
        job.setProjectId("proj-1");
        job.setJobType(MemoryJobType.MEMORY_INITIALIZE);
        job.setStatus(MemoryJobStatus.PENDING);
        job.setTriggerSource(MemoryJobTriggerSource.PROJECT_WORKSPACE_READY);
        job.setIdempotencyKey("key-1");
        return job;
    }

    @Test
    void tryClaim_affectedOne_returnsJobWithTargetStatus() {
        // WHY：CAS 成功（条件 UPDATE 命中 1 行）应回读并返回目标状态 job。
        MemoryJob running = pendingJob("job-1");
        running.setStatus(MemoryJobStatus.RUNNING);
        when(dao.claimIfStatus(eq("job-1"), eq(MemoryJobStatus.PENDING),
                eq(MemoryJobStatus.RUNNING), any())).thenReturn(1);
        when(dao.findOne("job-1")).thenReturn(running);

        MemoryJob claimed = service.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING);

        assertNotNull(claimed);
        assertEquals(MemoryJobStatus.RUNNING, claimed.getStatus());
        // 必须显式 flush，保证条件更新在回读前已落库（避免一级缓存读到旧值）
        verify(entityManager).flush();
    }

    @Test
    void tryClaim_affectedZero_returnsNullAndSkipsReadback() {
        // WHY：CAS 失败（状态已被他人抢占，条件 UPDATE 0 行）应立即返回 null，不回读、不误判成功。
        when(dao.claimIfStatus(eq("job-1"), eq(MemoryJobStatus.PENDING),
                eq(MemoryJobStatus.RUNNING), any())).thenReturn(0);

        MemoryJob claimed = service.tryClaim("job-1", MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING);

        assertNull(claimed, "条件 UPDATE 0 行应判定抢占失败");
        verify(dao, never()).findOne(anyString());
    }

    @Test
    void submit_idempotencyHit_returnsExistingWithoutSave() {
        // WHY：相同 idempotencyKey 重复投递应直接返回既有 job，不重复落库（契约 §12.4）。
        MemoryJob existing = pendingJob("job-1");
        when(dao.findByIdempotencyKey("key-1")).thenReturn(existing);

        OperateResultWithData<MemoryJob> result = service.submit("proj-1",
                MemoryJobType.MEMORY_INITIALIZE,
                MemoryJobTriggerSource.PROJECT_WORKSPACE_READY,
                "key-1", null, null, null, null);

        assertTrue(result.successful());
        assertEquals("job-1", result.getData().getId());
        // 既有 job 命中时不进入 super.save
        verify(dao, never()).save(any(MemoryJob.class));
    }
}
