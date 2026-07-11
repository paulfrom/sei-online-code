package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryStatus;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.WorkspaceMemoryFreshnessChecker;
import com.changhong.onlinecode.service.memory.WorkspaceMemoryScanResult;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * WorkspaceMemory 服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2、§10.3、§22。
 *
 * <p>职责：查询当前版本；归档旧版本；创建新版本（事务内归档旧 CURRENT 后写入新 CURRENT，
 * 由 partial unique index 兜底唯一 CURRENT）；标记 freshness；提供 PRD 生成前的
 * {@code ensureCurrentWorkspaceMemory(projectId)} 骨架（第一版仅保证 DB 有 CURRENT 记录）。</p>
 *
 * <p>第一版仅提供状态与版本原语；实际 NormClaim/RealityClaim/ConflictFinding 生成在 Phase 2
 * 由 {@code WorkspaceMemoryScannerService} 产出并回填。本服务的 {@code ensureCurrentWorkspaceMemory}
 * 第一版在缺失时创建空骨架 CURRENT 记录，待 Phase 2 扫描器接入后替换为真实内容。</p>
 *
 * @author sei-online-code
 */
@Service
public class WorkspaceMemoryService extends BaseEntityService<WorkspaceMemory> {

    private final WorkspaceMemoryDao dao;
    private final ProjectDao projectDao;
    private final RequirementDao requirementDao;
    private final RequirementDesignContextDao requirementDesignContextDao;
    private final WorkspaceMemoryFreshnessChecker freshnessChecker;

    public WorkspaceMemoryService(WorkspaceMemoryDao dao,
                                  ProjectDao projectDao,
                                  RequirementDao requirementDao,
                                  RequirementDesignContextDao requirementDesignContextDao,
                                  WorkspaceMemoryFreshnessChecker freshnessChecker) {
        this.dao = dao;
        this.projectDao = projectDao;
        this.requirementDao = requirementDao;
        this.requirementDesignContextDao = requirementDesignContextDao;
        this.freshnessChecker = freshnessChecker;
    }

    @Override
    protected BaseEntityDao<WorkspaceMemory> getDao() {
        return dao;
    }

    /**
     * 查询项目当前 CURRENT WorkspaceMemory。
     *
     * @param projectId 项目 id
     * @return 当前版本；不存在返回 null
     */
    public WorkspaceMemory findCurrent(String projectId) {
        return dao.findByProjectIdAndStatus(projectId, WorkspaceMemoryStatus.CURRENT);
    }

    /**
     * 查询项目全部历史版本（版本倒序）。
     *
     * @param projectId 项目 id
     * @return 全部版本
     */
    public List<WorkspaceMemory> findHistory(String projectId) {
        return dao.findByProjectIdOrderByVersionDesc(projectId);
    }

    /**
     * 创建新一版 WorkspaceMemory：事务内归档旧 CURRENT，再写入新 CURRENT。
     *
     * @param projectId           项目 id
     * @param memorySpecVersion   记忆规范版本
     * @param memorySeedTemplateId 绑定 seed 模板 id（可空）
     * @param agentMemorySeedVersion 写入 agent-memory 时使用的 seed 版本（可空）
     * @return 新 CURRENT WorkspaceMemory
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<WorkspaceMemory> createNewVersion(String projectId,
                                                                   Integer memorySpecVersion,
                                                                   String memorySeedTemplateId,
                                                                   Integer agentMemorySeedVersion) {
        // 归档旧 CURRENT；partial unique index uk_workspace_memory_current 保证写入新 CURRENT 不冲突
        dao.archiveCurrent(projectId, WorkspaceMemoryStatus.CURRENT, WorkspaceMemoryStatus.ARCHIVED);
        WorkspaceMemory previous = null;
        int nextVersion = 1;
        List<WorkspaceMemory> history = dao.findByProjectIdOrderByVersionDesc(projectId);
        if (!history.isEmpty()) {
            previous = history.get(0);
            nextVersion = Objects.requireNonNullElse(previous.getVersion(), 0) + 1;
        }
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setProjectId(projectId);
        memory.setVersion(nextVersion);
        memory.setStatus(WorkspaceMemoryStatus.CURRENT);
        memory.setFreshness(WorkspaceMemoryFreshness.FRESH);
        memory.setMemorySpecVersion(Objects.requireNonNullElse(memorySpecVersion, 1));
        memory.setMemorySeedTemplateId(memorySeedTemplateId);
        memory.setAgentMemorySeedVersion(agentMemorySeedVersion);
        memory.setGeneratedAt(new Date());
        return super.save(memory);
    }

    /**
     * 从扫描结果创建新一版 CURRENT WorkspaceMemory。
     *
     * @param projectId              项目 id
     * @param scan                   扫描结果
     * @param memorySeedTemplateId   绑定 seed 模板 id（可空）
     * @param agentMemorySeedVersion 写入 agent-memory 时使用的 seed 版本号（可空）
     * @return 新 CURRENT WorkspaceMemory
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<WorkspaceMemory> createNewVersionFromScan(String projectId,
                                                                           WorkspaceMemoryScanResult scan,
                                                                           String memorySeedTemplateId,
                                                                           Integer agentMemorySeedVersion) {
        dao.archiveCurrent(projectId, WorkspaceMemoryStatus.CURRENT, WorkspaceMemoryStatus.ARCHIVED);
        int nextVersion = nextVersion(projectId);
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setProjectId(projectId);
        memory.setVersion(nextVersion);
        memory.setStatus(WorkspaceMemoryStatus.CURRENT);
        memory.setFreshness(WorkspaceMemoryFreshness.FRESH);
        memory.setMemorySpecVersion(1);
        memory.setMemorySeedTemplateId(memorySeedTemplateId);
        memory.setAgentMemorySeedVersion(agentMemorySeedVersion);
        memory.setAgentMemoryFingerprint(scan.getAgentMemoryFingerprint());
        memory.setAgentMemoryMarkdown(scan.getAgentMemoryMarkdown());
        memory.setProjectRuleFingerprint(scan.getProjectRuleFingerprint());
        memory.setProjectRuleMarkdown(scan.getProjectRuleMarkdown());
        memory.setSourceFingerprintsJson(scan.getSourceFingerprintsJson());
        memory.setNormClaimsJson(toJson(scan.getNormClaims()));
        memory.setRealityClaimsJson(toJson(scan.getRealityClaims()));
        memory.setConflictFindingsJson(toJson(scan.getConflictFindings()));
        memory.setWorkspaceNormsJson(toJson(scan.getWorkspaceNorms()));
        memory.setWorkspaceSnapshotJson(toJson(scan.getWorkspaceSnapshot()));
        memory.setGeneratedAt(new Date());
        OperateResultWithData<WorkspaceMemory> result = super.save(memory);
        if (result.successful() && result.getData() != null) {
            invalidateRequirementContexts(projectId);
        }
        return result;
    }

    /**
     * 标记 freshness（用于 PRD 前检测过期）。
     *
     * @param memory     WorkspaceMemory（必须已持久化）
     * @param freshness  目标新鲜度
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFreshness(WorkspaceMemory memory, WorkspaceMemoryFreshness freshness) {
        if (Objects.isNull(memory) || Objects.isNull(freshness)) {
            return;
        }
        memory.setFreshness(freshness);
        dao.save(memory);
    }

    /**
     * 重新计算并更新当前 WorkspaceMemory 的 freshness。
     *
     * <p>比较 agent-memory、项目规范、代码现状指纹与当前记录，发现变化时写入对应 STALE 状态。
     * 若已是 PLATFORM_MEMORY_DRIFT 则保持不变。调用方可选择在 PRD 前或页面加载时执行。</p>
     *
     * @param projectId 项目 id
     * @return 更新后的 CURRENT WorkspaceMemory；不存在返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemory recheckFreshness(String projectId) {
        WorkspaceMemory current = findCurrent(projectId);
        if (Objects.isNull(current)) {
            return null;
        }
        Project project = projectDao.findOne(projectId);
        String workspacePath = project == null ? null : project.getWorkspacePath();
        WorkspaceMemoryFreshness freshness = freshnessChecker.check(current, workspacePath);
        if (freshness != current.getFreshness()) {
            markFreshness(current, freshness);
        }
        return current;
    }

    /**
     * 记录失败：新增 FAILED 记录，旧 CURRENT 不变（契约 §8.2、§12.2）。
     *
     * @param projectId       项目 id
     * @param failureSummary  失败摘要
     * @param failureDetail   失败详情
     * @return FAILED 记录
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<WorkspaceMemory> recordFailure(String projectId,
                                                                String failureSummary,
                                                                String failureDetail) {
        List<WorkspaceMemory> history = dao.findByProjectIdOrderByVersionDesc(projectId);
        int nextVersion = history.isEmpty() ? 1 : history.get(0).getVersion() + 1;
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setProjectId(projectId);
        memory.setVersion(nextVersion);
        memory.setStatus(WorkspaceMemoryStatus.FAILED);
        memory.setFreshness(WorkspaceMemoryFreshness.FRESH);
        memory.setFailureSummary(failureSummary);
        memory.setFailureDetail(failureDetail);
        memory.setGeneratedAt(new Date());
        return super.save(memory);
    }

    /**
     * 保证项目存在 CURRENT WorkspaceMemory；缺失则创建空骨架（第一版骨架）。
     *
     * <p>第一版仅保证 DB 存在 CURRENT 记录，不触发真实扫描（留给 Phase 2）。</p>
     *
     * @param projectId 项目 id
     * @return 当前 CURRENT WorkspaceMemory（缺失时新建骨架）
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemory ensureCurrentWorkspaceMemory(String projectId) {
        WorkspaceMemory current = findCurrent(projectId);
        if (Objects.nonNull(current)) {
            return current;
        }
        OperateResultWithData<WorkspaceMemory> result = createNewVersion(projectId, 1, null, null);
        if (result.notSuccessful() || result.getData() == null) {
            throw new IllegalStateException("初始化 WorkspaceMemory 失败: " + result.getMessage());
        }
        return result.getData();
    }

    private int nextVersion(String projectId) {
        List<WorkspaceMemory> history = dao.findByProjectIdOrderByVersionDesc(projectId);
        if (history.isEmpty()) {
            return 1;
        }
        return Objects.requireNonNullElse(history.get(0).getVersion(), 0) + 1;
    }

    /**
     * WorkspaceMemory 更新后，将项目下所有 CURRENT RequirementDesignContext 置为 STALE。
     */
    private void invalidateRequirementContexts(String projectId) {
        List<Requirement> requirements = requirementDao.findByProjectId(projectId);
        for (Requirement requirement : requirements) {
            RequirementDesignContext current = requirementDesignContextDao
                    .findByRequirementIdAndStatus(requirement.getId(), MemoryRecordStatus.CURRENT);
            if (current != null && current.getContextStatus() != com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus.FAILED) {
                current.setContextStatus(com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus.STALE);
                requirementDesignContextDao.save(current);
            }
        }
    }

    private String toJson(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 WorkspaceMemory JSON 字段失败", e);
        }
    }
}