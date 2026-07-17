package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionEffectDao;
import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.enums.ExecutionEffectType;
import com.changhong.onlinecode.entity.ExecutionEffect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EffectService 单元测试（EXE-006）。
 *
 * <p>WHY：覆盖 ADR-001 §5 关键不变量——相同 key+hash 幂等复用、不同 hash 稳定冲突、
 * PREPARED→APPLIED→CONFIRMED/UNKNOWN 状态迁移、并发冲突回读。</p>
 */
@ExtendWith(MockitoExtension.class)
class EffectServiceTest {

    @Mock
    private ExecutionEffectDao effectDao;

    @InjectMocks
    private EffectService effectService;

    private static final String EFFECT_KEY = "push:p1:feature/test";
    private static final String HASH_A = "abc123";
    private static final String HASH_B = "def456";
    private static final String EXECUTION_ID = "exec1";
    private static final String STEP_ID = "step1";
    private static final String EFFECT_ID = "effect-1";

    // ======================== findOrPrepare ========================

    @Test
    void findOrPrepare_newKey_insertsAndReturnsPrepared() {
        when(effectDao.findByEffectKey(EFFECT_KEY)).thenReturn(Optional.empty());
        ExecutionEffect saved = effect(HASH_A, ExecutionEffectStatus.PREPARED);
        when(effectDao.saveAndFlush(any())).thenReturn(saved);

        ExecutionEffect result = effectService.findOrPrepare(
                EFFECT_KEY, ExecutionEffectType.PUSH, HASH_A, EXECUTION_ID, STEP_ID, 0L);

        assertSame(saved, result);
        assertEquals(ExecutionEffectStatus.PREPARED, result.getStatus());
        verify(effectDao).saveAndFlush(any());
    }

    @Test
    void findOrPrepare_sameKeySameHash_returnsExisting() {
        ExecutionEffect existing = effect(HASH_A, ExecutionEffectStatus.APPLIED);
        when(effectDao.findByEffectKey(EFFECT_KEY)).thenReturn(Optional.of(existing));

        ExecutionEffect result = effectService.findOrPrepare(
                EFFECT_KEY, ExecutionEffectType.PUSH, HASH_A, EXECUTION_ID, STEP_ID, 0L);

        assertSame(existing, result);
        assertEquals(ExecutionEffectStatus.APPLIED, result.getStatus());
        verify(effectDao, never()).saveAndFlush(any());
    }

    @Test
    void findOrPrepare_sameKeyDifferentHash_throwsConflict() {
        ExecutionEffect existing = effect(HASH_A, ExecutionEffectStatus.CONFIRMED);
        when(effectDao.findByEffectKey(EFFECT_KEY)).thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                effectService.findOrPrepare(EFFECT_KEY, ExecutionEffectType.PUSH, HASH_B, EXECUTION_ID, STEP_ID, 0L));

        assertTrue(ex.getMessage().contains("different requestHash"));
        verify(effectDao, never()).saveAndFlush(any());
    }

    @Test
    void findOrPrepare_concurrentConflict_fallsBackToFindByKey() {
        when(effectDao.findByEffectKey(EFFECT_KEY))
                .thenReturn(Optional.empty())  // first call: not found
                .thenReturn(Optional.of(effect(HASH_A, ExecutionEffectStatus.PREPARED))); // second call: fallback
        when(effectDao.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));

        ExecutionEffect result = effectService.findOrPrepare(
                EFFECT_KEY, ExecutionEffectType.MR, HASH_A, EXECUTION_ID, STEP_ID, 1L);

        assertEquals(ExecutionEffectStatus.PREPARED, result.getStatus());
    }

    // ======================== markApplied ========================

    @Test
    void markApplied_preparedToApplied_callsDao() {
        ExecutionEffect prepared = effect(HASH_A, ExecutionEffectStatus.PREPARED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(prepared);
        when(effectDao.applyEffect(eq(EFFECT_ID), eq(ExecutionEffectStatus.APPLIED),
                eq(ExecutionEffectStatus.PREPARED), any(), any())).thenReturn(1);

        effectService.markApplied(EFFECT_ID, "{\"ok\":true}", "branch@commit");

        verify(effectDao).applyEffect(eq(EFFECT_ID), eq(ExecutionEffectStatus.APPLIED),
                eq(ExecutionEffectStatus.PREPARED), eq("{\"ok\":true}"), eq("branch@commit"));
    }

    @Test
    void markApplied_alreadyApplied_skips() {
        ExecutionEffect applied = effect(HASH_A, ExecutionEffectStatus.APPLIED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(applied);

        effectService.markApplied(EFFECT_ID, "result", "ref");

        verify(effectDao, never()).applyEffect(any(), any(), any(), any(), any());
    }

    @Test
    void markApplied_alreadyConfirmed_skips() {
        ExecutionEffect confirmed = effect(HASH_A, ExecutionEffectStatus.CONFIRMED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(confirmed);

        effectService.markApplied(EFFECT_ID, "result", "ref");

        verify(effectDao, never()).applyEffect(any(), any(), any(), any(), any());
    }

    // ======================== markConfirmed ========================

    @Test
    void markConfirmed_appliedToConfirmed_callsDao() {
        ExecutionEffect applied = effect(HASH_A, ExecutionEffectStatus.APPLIED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(applied);
        when(effectDao.confirmEffect(eq(EFFECT_ID), eq(ExecutionEffectStatus.CONFIRMED),
                eq(ExecutionEffectStatus.APPLIED))).thenReturn(1);

        effectService.markConfirmed(EFFECT_ID);

        verify(effectDao).confirmEffect(EFFECT_ID, ExecutionEffectStatus.CONFIRMED, ExecutionEffectStatus.APPLIED);
    }

    @Test
    void markConfirmed_alreadyConfirmed_skips() {
        ExecutionEffect confirmed = effect(HASH_A, ExecutionEffectStatus.CONFIRMED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(confirmed);

        effectService.markConfirmed(EFFECT_ID);

        verify(effectDao, never()).confirmEffect(any(), any(), any());
    }

    // ======================== markUnknown ========================

    @Test
    void markUnknown_appliedToUnknown_callsDao() {
        ExecutionEffect applied = effect(HASH_A, ExecutionEffectStatus.APPLIED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(applied);
        when(effectDao.markEffectUnknown(eq(EFFECT_ID), eq(ExecutionEffectStatus.UNKNOWN),
                eq(ExecutionEffectStatus.APPLIED))).thenReturn(1);

        effectService.markUnknown(EFFECT_ID);

        verify(effectDao).markEffectUnknown(EFFECT_ID, ExecutionEffectStatus.UNKNOWN, ExecutionEffectStatus.APPLIED);
    }

    @Test
    void markUnknown_alreadyUnknown_skips() {
        ExecutionEffect unknown = effect(HASH_A, ExecutionEffectStatus.UNKNOWN);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(unknown);

        effectService.markUnknown(EFFECT_ID);

        verify(effectDao, never()).markEffectUnknown(any(), any(), any());
    }

    // ======================== findByKey ========================

    @Test
    void findByKey_existing_returnsEffect() {
        ExecutionEffect existing = effect(HASH_A, ExecutionEffectStatus.CONFIRMED);
        when(effectDao.findByEffectKey(EFFECT_KEY)).thenReturn(Optional.of(existing));

        Optional<ExecutionEffect> result = effectService.findByKey(EFFECT_KEY);

        assertTrue(result.isPresent());
        assertSame(existing, result.get());
    }

    @Test
    void findByKey_notFound_returnsEmpty() {
        when(effectDao.findByEffectKey(EFFECT_KEY)).thenReturn(Optional.empty());

        Optional<ExecutionEffect> result = effectService.findByKey(EFFECT_KEY);

        assertTrue(result.isEmpty());
    }

    // ======================== reconcile ========================

    @Test
    void reconcile_unknownEffect_reconcilerReturnsResult_confirmsEffect() {
        ExecutionEffect unknown = effect(HASH_A, ExecutionEffectStatus.UNKNOWN);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(unknown);
        when(effectDao.confirmEffect(eq(EFFECT_ID), eq(ExecutionEffectStatus.CONFIRMED),
                eq(ExecutionEffectStatus.APPLIED))).thenReturn(1);
        ExecutionEffect saved = effect(HASH_A, ExecutionEffectStatus.CONFIRMED);
        when(effectDao.save(any(ExecutionEffect.class))).thenReturn(saved);

        ExecutionEffect result = effectService.reconcile(EFFECT_ID,
                e -> "external result confirmed");

        assertNotNull(result);
        verify(effectDao).confirmEffect(EFFECT_ID, ExecutionEffectStatus.CONFIRMED, ExecutionEffectStatus.APPLIED);
    }

    @Test
    void reconcile_notUnknown_skipsReconcile() {
        ExecutionEffect confirmed = effect(HASH_A, ExecutionEffectStatus.CONFIRMED);
        when(effectDao.findOne(EFFECT_ID)).thenReturn(confirmed);

        ExecutionEffect result = effectService.reconcile(EFFECT_ID,
                e -> "should not be called");

        assertNotNull(result);
        verify(effectDao, never()).confirmEffect(any(), any(), any());
    }

    // ======================== helpers ========================

    private static ExecutionEffect effect(String requestHash, ExecutionEffectStatus status) {
        ExecutionEffect e = new ExecutionEffect();
        e.setId(EFFECT_ID);
        e.setEffectKey(EFFECT_KEY);
        e.setExecutionId(EXECUTION_ID);
        e.setStepId(STEP_ID);
        e.setEffectType(ExecutionEffectType.PUSH);
        e.setRequestHash(requestHash);
        e.setStatus(status);
        e.setFencingToken(0L);
        e.setPreparedAt(new java.util.Date());
        return e;
    }
}
