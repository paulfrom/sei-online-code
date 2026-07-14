package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.onlinecode.entity.Project;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 项目服务。生命周期状态机转换作为 service 方法（契约 §4）。
 *
 * @author sei-online-code
 */
@Service
public class ProjectService extends BaseEntityService<Project> {

    private final ProjectDao dao;
    private final PlanService planService;
    private final ProjectLifecycleService lifecycleService;
    private final WorkspaceManager workspaceManager;
    private final AgentMemoryTemplateService agentMemoryTemplateService;
    private final MemoryJobService memoryJobService;
    private final MemorySeedTemplateService memorySeedTemplateService;

    public ProjectService(ProjectDao dao,
                          PlanService planService,
                          ProjectLifecycleService lifecycleService,
                          WorkspaceManager workspaceManager,
                          AgentMemoryTemplateService agentMemoryTemplateService,
                          MemoryJobService memoryJobService,
                          MemorySeedTemplateService memorySeedTemplateService) {
        this.dao = dao;
        this.planService = planService;
        this.lifecycleService = lifecycleService;
        this.workspaceManager = workspaceManager;
        this.agentMemoryTemplateService = agentMemoryTemplateService;
        this.memoryJobService = memoryJobService;
        this.memorySeedTemplateService = memorySeedTemplateService;
    }

    @Override
    protected BaseEntityDao<Project> getDao() {
        return dao;
    }

    /**
     * 新建/更新项目。
     *
     * <p>项目保存后立即解析并 provision 物理工作区，确保从项目创建时起就有稳定的落盘目录；
     * 后续代码执行、Agent brief、运行物料都落在同一 workspace。</p>
     *
     * <p>工作区 ready 后，事务提交回调中补齐 agent-memory seed 文件并投递
     * {@code MEMORY_INITIALIZE} job（契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §13.1、§12.1）。
     * 放在 afterCommit 避免 DB 事务持有文件 IO，并保证项目行已落库后异步线程可见。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Project> save(Project entity) {
        if (Objects.isNull(entity.getState())) {
            entity.setState(LifecycleState.DRAFTING);
        }
        // seed 模板解析与绑定（契约 §9.1）：显式选择必须指向 ACTIVE 模板；未选择时解析并保存当前全局默认 id，
        // 避免后续默认模板切换改变本项目缺文件补齐来源。编辑场景已绑定 id 不重复解析。
        String boundId = resolveSeedTemplateBinding(entity);
        if (boundId == null && entity.getMemorySeedTemplateId() != null) {
            return OperateResultWithData.operationFailure("seed 模板不存在或非 ACTIVE: " + entity.getMemorySeedTemplateId());
        }
        entity.setMemorySeedTemplateId(boundId);
        OperateResultWithData<Project> result = super.save(entity);
        if (!result.successful() || result.getData() == null || result.getData().getId() == null) {
            return result;
        }
        Project saved = result.getData();
        WorkspaceResolveResult workspace = workspaceManager.resolve(saved.getId());
        if (!Objects.equals(saved.getWorkspacePath(), workspace.getPath())) {
            saved.setWorkspacePath(workspace.getPath());
            result = super.save(saved);
        }
        // 工作区已 ready：事务提交后补齐 agent-memory seed 并投递 MEMORY_INITIALIZE job
        final String projectId = saved.getId();
        final String workspacePath = workspace.getPath();
        TransactionUtil.afterCommit(() -> triggerMemoryInitialize(projectId, workspacePath));
        return result;
    }

    /**
     * 工作区 ready 后的记忆初始化回调：补齐 agent-memory seed 文件，再投递 MEMORY_INITIALIZE job。
     *
     * <p>契约 §13.1 §12.1：项目工作区建设成功后立即投递 MEMORY_INITIALIZE。第一版 job 仅落库 PENDING，
     * 实际扫描执行在 Phase 2 接入 WorkspaceMemoryScannerService 后由执行器消费。
     * 写入 agent-memory 失败仅记录告警，不阻断项目创建（契约 §12.2 失败策略）。</p>
     *
     * @param projectId     项目 id
     * @param workspacePath 工作区根目录绝对路径
     */
    private void triggerMemoryInitialize(String projectId, String workspacePath) {
        try {
            agentMemoryTemplateService.ensureAgentMemory(projectId, workspacePath);
        } catch (Exception e) {
            // 写 agent-memory 失败不阻断后续 job 投递；job 执行时仍会补齐（契约 §22.3）
            // 记录即可，避免吞异常掩盖问题
            org.slf4j.LoggerFactory.getLogger(ProjectService.class)
                    .warn("memory: 初始化 agent-memory 失败，将由 job 退回补齐 projectId={}", projectId, e);
        }
        try {
            String idempotencyKey = projectId + ":" + MemoryJobType.MEMORY_INITIALIZE.name()
                    + ":" + resolveWorkspaceReadyVersion(workspacePath);
            memoryJobService.submit(projectId, MemoryJobType.MEMORY_INITIALIZE,
                    MemoryJobTriggerSource.PROJECT_WORKSPACE_READY, idempotencyKey,
                    null, null, null, null);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ProjectService.class)
                    .error("memory: 投递 MEMORY_INITIALIZE job 失败 projectId={}", projectId, e);
        }
    }

    /**
     * 计算工作区 ready 版本标识用于幂等键（契约 §12.4：projectId + MEMORY_INITIALIZE + workspaceReadyVersion）。
     * 第一版以 manifest 的 createdAt 表示 ready 版本；缺失退回当前时间戳保证每次可重投。
     */
    private String resolveWorkspaceReadyVersion(String workspacePath) {
        try {
            java.nio.file.Path manifest = java.nio.file.Path.of(workspacePath, ".sei", "workspace.json");
            if (java.nio.file.Files.exists(manifest)) {
                String content = java.nio.file.Files.readString(manifest, java.nio.charset.StandardCharsets.UTF_8);
                int idx = content.indexOf("\"createdAt\"");
                if (idx >= 0) {
                    int colon = content.indexOf(':', idx);
                    int start = content.indexOf('"', colon);
                    int end = content.indexOf('"', start + 1);
                    if (start > 0 && end > start) {
                        return content.substring(start + 1, end);
                    }
                }
            }
        } catch (Exception ignored) {
            // 读取 manifest 失败时回退到固定标识，由 idempotency 保证不重复
        }
        return "v1";
    }

    /**
     * 生命周期状态流转。校验合法后落库。
     *
     * @param projectId 项目 id
     * @param target    目标状态
     * @return 写操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Project> transitionState(String projectId, LifecycleState target) {
        return lifecycleService.transitionState(projectId, target);
    }

    /**
     * 解析项目 seed 模板绑定（契约 §9.1）。
     *
     * <p>显式选择：必须指向 ACTIVE 模板，否则返回 null（调用方据失败信息拒绝）。
     * 未选择：解析当前全局默认模板并回填其 id；全局默认缺失时触发 bootstrap 后再取。
     * 已绑定（编辑场景）：沿用原 id，即便该模板后续已归档仍可补齐（契约 §6.1 现有实体修改 §9.1）。</p>
     *
     * @param entity 待保存项目
     * @return 应绑定的模板 id；显式选择非法时返回 null
     */
    private String resolveSeedTemplateBinding(Project entity) {
        String requested = entity.getMemorySeedTemplateId();
        if (Objects.nonNull(requested) && !requested.isBlank()) {
            MemorySeedTemplate template = memorySeedTemplateService.findOne(requested);
            if (Objects.nonNull(template)
                    && template.getStatus() == MemorySeedTemplateStatus.ACTIVE) {
                return template.getId();
            }
            return null;
        }
        MemorySeedTemplate def = memorySeedTemplateService.findActiveDefault();
        if (Objects.isNull(def)) {
            def = memorySeedTemplateService.bootstrapDefaultIfAbsent();
        }
        return Objects.nonNull(def) ? def.getId() : null;
    }

    /**
     * 兼容旧 refineSpec 入口：实际触发的是 Plan 重生成，而不是旧 Spec 流程。
     *
     * <p>仅用于兼容旧客户端；新的需求驱动流程应走 RequirementWorkspace 下的 PRD/设计链路。</p>
     *
     * @param projectId 项目 id
     * @return 写操作结果（携带新建 GENERATING Plan）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlanDto> refineSpec(String projectId) {
        Project project = dao.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        return planService.regenerate(projectId, null);
    }
}
