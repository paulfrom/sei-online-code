package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionEffectDao;
import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.enums.ExecutionEffectType;
import com.changhong.onlinecode.entity.ExecutionEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * EffectService —— ADR-001 §5 / 数据模型 §8 effect ledger 核心（EXE-006）。
 *
 * <p>提供 effect 记录、幂等查找（effect_key + request_hash）、状态推进与对账。
 * 所有写操作通过 CAS 查询实现；并发冲突通过 effect_key 唯一约束解析。</p>
 *
 * <p>副作用类型注册与具体执行逻辑由调用方（例如 RequirementDeliveryService）自行实现；
 * 本服务仅负责 effect 记录的持久化与状态机。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class EffectService {

    private final ExecutionEffectDao effectDao;

    public EffectService(ExecutionEffectDao effectDao) {
        this.effectDao = effectDao;
    }

    // ======================== findOrPrepare ========================

    /**
     * 查找或准备 effect（ADR-001 §5 幂等：相同 key+hash 返回首次结果）。
     *
     * <p>已存在：hash 相同 → 直接返回已有记录（幂等复用）；hash 不同 → 抛出冲突异常。
     * 不存在：INSERT 新 PREPARED 记录（并发冲突回读已有）。</p>
     *
     * @param effectKey       稳定幂等键
     * @param effectType      副作用类型
     * @param requestHash     请求内容 SHA-256
     * @param executionId     所属 Execution
     * @param stepId          所属步骤
     * @param fencingToken    工作区 fencing token
     * @return effect 记录（PREPARED 或已有状态）
     * @throws IllegalStateException hash 冲突（相同 key 不同 hash）
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExecutionEffect findOrPrepare(String effectKey, ExecutionEffectType effectType,
                                          String requestHash, String executionId,
                                          String stepId, Long fencingToken) {
        Optional<ExecutionEffect> existing = effectDao.findByEffectKey(effectKey);
        if (existing.isPresent()) {
            ExecutionEffect effect = existing.get();
            // 相同 key + 相同 hash → 幂等复用（ADR-001 §5）
            if (requestHash.equals(effect.getRequestHash())) {
                log.debug("findOrPrepare: effect {} key={} 已存在，hash 一致 → 幂等复用", effect.getId(), effectKey);
                return effect;
            }
            // 相同 key + 不同 hash → 冲突（ADR-001 §5：不同 hash 稳定冲突，不覆盖原请求）
            throw new IllegalStateException(
                    "effect key conflict: " + effectKey + " has different requestHash. "
                            + "existing=" + effect.getRequestHash() + " new=" + requestHash);
        }
        ExecutionEffect candidate = new ExecutionEffect();
        candidate.setEffectKey(effectKey);
        candidate.setExecutionId(executionId);
        candidate.setStepId(stepId);
        candidate.setEffectType(effectType);
        candidate.setRequestHash(requestHash);
        candidate.setStatus(ExecutionEffectStatus.PREPARED);
        candidate.setFencingToken(fencingToken != null ? fencingToken : 0L);
        candidate.setPreparedAt(new Date());
        try {
            return effectDao.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException e) {
            log.debug("findOrPrepare: effect_key={} 并发冲突，回读首次记录", effectKey);
            return effectDao.findByEffectKey(effectKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "effect vanished after unique-key conflict: " + effectKey, e));
        }
    }

    // ======================== state transitions ========================

    /**
     * 标记 effect 为 APPLIED（执行已完成，结果已记录）。
     *
     * <p>幂等：已是 APPLIED/CONFIRMED → 直接返回，不报错。</p>
     *
     * @param effectId           effect ID
     * @param resultSnapshot     脱敏后的结果 JSON（可空）
     * @param externalReference  外部引用（branch/MR IID/job key 等，可空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void markApplied(String effectId, String resultSnapshot, String externalReference) {
        ExecutionEffect effect = effectDao.findOne(effectId);
        if (effect == null) {
            log.warn("markApplied: effect not found id={}", effectId);
            return;
        }
        if (effect.getStatus() == ExecutionEffectStatus.APPLIED
                || effect.getStatus() == ExecutionEffectStatus.CONFIRMED) {
            return; // 幂等
        }
        int updated = effectDao.applyEffect(effectId, ExecutionEffectStatus.APPLIED,
                ExecutionEffectStatus.PREPARED, resultSnapshot, externalReference);
        if (updated == 0) {
            log.warn("markApplied: CAS 失败 effectId={}, currentStatus={}", effectId, effect.getStatus());
        }
    }

    /**
     * 标记 effect 为 CONFIRMED（结果已核实）。
     *
     * <p>幂等：已 CONFIRMED → 直接返回。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void markConfirmed(String effectId) {
        ExecutionEffect effect = effectDao.findOne(effectId);
        if (effect == null) {
            log.warn("markConfirmed: effect not found id={}", effectId);
            return;
        }
        if (effect.getStatus() == ExecutionEffectStatus.CONFIRMED) {
            return; // 幂等
        }
        int updated = effectDao.confirmEffect(effectId, ExecutionEffectStatus.CONFIRMED,
                ExecutionEffectStatus.APPLIED);
        if (updated == 0) {
            log.warn("markConfirmed: CAS 失败 effectId={}, currentStatus={}", effectId, effect.getStatus());
        }
    }

    /**
     * 标记 effect 为 UNKNOWN（结果不确定，需对账）。
     *
     * <p>幂等：已 UNKNOWN → 直接返回。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void markUnknown(String effectId) {
        ExecutionEffect effect = effectDao.findOne(effectId);
        if (effect == null) {
            log.warn("markUnknown: effect not found id={}", effectId);
            return;
        }
        if (effect.getStatus() == ExecutionEffectStatus.UNKNOWN) {
            return; // 幂等
        }
        int updated = effectDao.markEffectUnknown(effectId, ExecutionEffectStatus.UNKNOWN,
                ExecutionEffectStatus.APPLIED);
        if (updated == 0) {
            log.warn("markUnknown: CAS 失败 effectId={}, currentStatus={}", effectId, effect.getStatus());
        }
    }

    // ======================== query ========================

    /**
     * 按 effect_key 查找已有 effect。
     *
     * @param effectKey effect 键
     * @return effect 记录（存在时）
     */
    public Optional<ExecutionEffect> findByKey(String effectKey) {
        return effectDao.findByEffectKey(effectKey);
    }

    /**
     * 对账 UNKNOWN effect：调用外部查询函数验证副作用结果。
     *
     * <p>reconciler 返回非空结果 → markConfirmed；返回 null（仍无法确认）→ 保持 UNKNOWN。</p>
     *
     * @param effectId   effect ID
     * @param reconciler 外部查询函数（e.g., GitLab MR 查询、git ls-remote），返回确认结果或 null
     * @return 对账后的 effect
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionEffect reconcile(String effectId, java.util.function.Function<ExecutionEffect, String> reconciler) {
        ExecutionEffect effect = effectDao.findOne(effectId);
        if (effect == null) {
            return null;
        }
        if (effect.getStatus() != ExecutionEffectStatus.UNKNOWN) {
            return effect;
        }
        try {
            String externalResult = reconciler.apply(effect);
            if (externalResult != null) {
                // 外部查询确认结果
                effect.setResultSnapshot(externalResult);
                effectDao.save(effect);
                int updated = effectDao.confirmUnknownEffect(effectId, ExecutionEffectStatus.CONFIRMED,
                        ExecutionEffectStatus.UNKNOWN);
                if (updated == 0) {
                    log.warn("reconcile: UNKNOWN→CONFIRMED CAS 失败 effectId={}, currentStatus={}",
                            effectId, effect.getStatus());
                } else {
                    effect.setStatus(ExecutionEffectStatus.CONFIRMED);
                    effect.setConfirmedAt(new Date());
                }
                log.info("reconcile: effectId={} UNKNOWN→CONFIRMED via external query", effectId);
            }
        } catch (Exception e) {
            log.warn("reconcile: effectId={} external query failed, stays UNKNOWN", effectId, e);
        }
        effect.setLastReconciledAt(new Date());
        return effectDao.save(effect);
    }
}
