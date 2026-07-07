package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanModule;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 规划服务（Task 9）
 *
 * <p>职责：规划书的 CRUD、编辑、重生、确认、历史版本查询。</p>
 */
@Service
public class PlanService extends BaseEntityService<Plan> {

    private final PlanDao planDao;
    private final FeatureDesignDao featureDesignDao;
    private final SpecDao specDao;
    private final SpecAgentService specAgentService;
    private final PlanAgentService planAgentService;

    public PlanService(PlanDao planDao,
                       FeatureDesignDao featureDesignDao,
                       SpecDao specDao,
                       SpecAgentService specAgentService,
                       PlanAgentService planAgentService) {
        this.planDao = planDao;
        this.featureDesignDao = featureDesignDao;
        this.specDao = specDao;
        this.specAgentService = specAgentService;
        this.planAgentService = planAgentService;
    }

    @Override
    protected BaseEntityDao<Plan> getDao() {
        return planDao;
    }

    /**
     * 查找项目最新规划
     *
     * @param projectId 项目 id
     * @return 最新规划 DTO，不存在时返回 null
     */
    public PlanDto findLatest(String projectId) {
        Plan plan = planDao.findLatestByProjectId(projectId);
        return plan == null ? null : toDto(plan);
    }

    /**
     * 查找项目最新已确认概要设计。
     *
     * @param projectId 项目 id
     * @return 最新已确认概要设计 DTO，不存在时返回 null
     */
    public PlanDto findLatestConfirmed(String projectId) {
        List<Plan> plans = planDao.findByProjectIdOrderByVersionDesc(projectId);
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream()
                .filter(plan -> plan.getStatus() == PlanStatus.CONFIRMED)
                .findFirst()
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * 编辑规划
     *
     * @param projectId 项目 id
     * @param content 规划内容
     * @return 操作结果，包含新规划 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlanDto> edit(String projectId, PlanContent content) {
        Plan latest = planDao.findLatestByProjectId(projectId);
        if (latest != null && latest.getStatus() == PlanStatus.GENERATING) {
            return OperateResultWithData.operationFailure("规划正在生成中，不可编辑");
        }

        // 标记旧版本为非最新
        planDao.markNonLatest(projectId);

        // 创建新版本
        Plan newPlan = new Plan();
        newPlan.setProjectId(projectId);
        newPlan.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        newPlan.setStatus(PlanStatus.DRAFT);
        newPlan.setContent(content);
        newPlan.setModifyHint(null);
        newPlan.setIsLatest(true);

        // 级联标记功能设计为 STALE
        featureDesignDao.cascadeStale(projectId);

        OperateResultWithData<Plan> saved = super.save(newPlan);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }
        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 重新生成规划
     *
     * @param projectId 项目 id
     * @param modifyHint 修改提示
     * @return 操作结果，包含新规划 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlanDto> regenerate(String projectId, String modifyHint) {
        Plan latest = planDao.findLatestByProjectId(projectId);
        if (latest != null && latest.getStatus() == PlanStatus.GENERATING) {
            return OperateResultWithData.operationFailure("规划正在生成中，不可重复发起");
        }

        // 标记旧版本为非最新
        planDao.markNonLatest(projectId);

        // 创建新版本
        Plan newPlan = new Plan();
        newPlan.setProjectId(projectId);
        newPlan.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        newPlan.setStatus(PlanStatus.GENERATING);
        newPlan.setContent(null);
        newPlan.setModifyHint(modifyHint);
        newPlan.setIsLatest(true);

        OperateResultWithData<Plan> saved = super.save(newPlan);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }

        // spawn 规划智能体
        planAgentService.spawnPlanning(projectId, modifyHint);

        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 确认规划
     *
     * @param projectId 项目 id
     * @return 操作结果，包含确认后的规划 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlanDto> confirm(String projectId) {
        Plan latest = planDao.findLatestByProjectId(projectId);
        if (latest == null) {
            return OperateResultWithData.operationFailure("规划不存在");
        }
        if (latest.getStatus() != PlanStatus.DRAFT) {
            return OperateResultWithData.operationFailure("仅草稿状态可确认");
        }

        // 置为 CONFIRMED
        latest.setStatus(PlanStatus.CONFIRMED);
        OperateResultWithData<Plan> saved = savePlan(latest);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }

        PlanContent content = latest.getContent();
        List<PlanModule> modules = modulesOrFallback(content);
        int version = nextSpecVersion(projectId);
        for (PlanModule module : modules) {
            Spec spec = new Spec();
            spec.setProjectId(projectId);
            spec.setVersion(version++);
            spec.setState(SpecState.GENERATING);
            spec.setModuleId(module.getModuleId());
            spec.setModuleTitle(module.getTitle());
            spec.setModuleSummary(module.getSummary());
            Spec savedSpec = specDao.save(spec);
            specAgentService.spawnRequirement(projectId, null, savedSpec.getId());
        }

        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    private List<PlanModule> modulesOrFallback(PlanContent content) {
        if (content == null) {
            return List.of();
        }
        if (content.getModules() != null && !content.getModules().isEmpty()) {
            return content.getModules();
        }
        if (content.getFeatures() == null || content.getFeatures().isEmpty()) {
            return List.of();
        }
        PlanModule fallback = new PlanModule();
        fallback.setModuleId(null);
        fallback.setTitle("默认模块");
        fallback.setSummary(content.getSummary());
        fallback.setFeatures(content.getFeatures());
        return List.of(fallback);
    }

    private Integer nextSpecVersion(String projectId) {
        List<Spec> specs = specDao.findByProjectIdOrderByVersionDesc(projectId);
        if (specs == null || specs.isEmpty() || specs.get(0).getVersion() == null) {
            return 1;
        }
        return specs.get(0).getVersion() + 1;
    }

    protected OperateResultWithData<Plan> savePlan(Plan plan) {
        return super.save(plan);
    }

    /**
     * 查询历史版本
     *
     * @param projectId 项目 id
     * @return 历史版本 DTO 列表，按版本倒序
     */
    public List<PlanDto> history(String projectId) {
        List<Plan> plans = planDao.findByProjectIdOrderByVersionDesc(projectId);
        return plans.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 实体转 DTO
     *
     * @param plan 实体
     * @return DTO
     */
    private PlanDto toDto(Plan plan) {
        if (plan == null) {
            return null;
        }
        PlanDto dto = new PlanDto();
        dto.setId(plan.getId());
        dto.setProjectId(plan.getProjectId());
        dto.setVersion(plan.getVersion());
        dto.setStatus(plan.getStatus());
        dto.setContent(plan.getContent());
        dto.setModifyHint(plan.getModifyHint());
        dto.setIsLatest(plan.getIsLatest());
        dto.setCreatedDate(plan.getCreatedDate());
        dto.setLastEditedDate(plan.getLastEditedDate());
        return dto;
    }
}
