package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.RequirementDto;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/**
 * Requirement 服务。
 *
 * @author sei-online-code
 */
@Service
public class RequirementService extends BaseEntityService<Requirement> {

    private final RequirementDao dao;
    private final RequirementAgentService requirementAgentService;
    private final OverviewDesignService overviewDesignService;

    public RequirementService(RequirementDao dao,
                              @Lazy RequirementAgentService requirementAgentService,
                              @Lazy OverviewDesignService overviewDesignService) {
        this.dao = dao;
        this.requirementAgentService = requirementAgentService;
        this.overviewDesignService = overviewDesignService;
    }

    @Override
    protected BaseEntityDao<Requirement> getDao() {
        return dao;
    }

    /**
     * 保存需求：新建时自动启动 PRD 生成。
     *
     * @param entity 需求实体
     * @return 写操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Requirement> save(Requirement entity) {
        if (Objects.isNull(entity.getStatus())) {
            entity.setStatus(RequirementStatus.PRD_GENERATING);
        }
        if (Objects.isNull(entity.getPrdVersion())) {
            entity.setPrdVersion(1);
        }
        if (Objects.isNull(entity.getGenerationToken()) || entity.getGenerationToken().isBlank()) {
            entity.setGenerationToken(GenerationTokenSupport.newToken());
        }
        boolean isNew = entity.getId() == null;
        OperateResultWithData<Requirement> result = super.save(entity);
        Requirement saved = result.getData();
        if (result.successful() && isNew && saved != null) {
            triggerPrdSpawnAfterCommit(saved.getId(), null, saved.getGenerationToken());
        }
        return result;
    }

    /**
     * 重生成 PRD。
     *
     * @param id     需求 ID
     * @param prompt 提示词
     * @return 写操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Requirement> regeneratePrd(String id, String prompt) {
        Requirement requirement = dao.findOne(id);
        if (Objects.isNull(requirement)) {
            return OperateResultWithData.operationFailure("需求不存在: " + id);
        }
        if (requirement.getStatus() != RequirementStatus.PRD_REVIEW
                && requirement.getStatus() != RequirementStatus.FAILED) {
            return OperateResultWithData.operationFailure("当前状态不允许重生成 PRD: " + requirement.getStatus());
        }
        requirement.setStatus(RequirementStatus.PRD_GENERATING);
        requirement.setPrdVersion(requirement.getPrdVersion() + 1);
        requirement.setLastRetryAt(new Date());
        requirement.setGenerationToken(GenerationTokenSupport.newToken());
        OperateResultWithData<Requirement> result = super.save(requirement);
        if (result.successful()) {
            triggerPrdSpawnAfterCommit(id, prompt, requirement.getGenerationToken());
        }
        return result;
    }

    /**
     * 在当前事务提交后触发 PRD 生成。{@code spawnPrd} 为 {@code @Async}，若在事务提交前执行，
     * 异步线程读不到未提交的需求行（{@code findOne} 返回 null）→ agent 静默跳过、状态卡在
     * PRD_GENERATING。故用 {@link TransactionUtil#afterCommit(Runnable)} 延后到提交后再触发
     * （sei-core 惯例，见 eadp-backend skill references/service.md）。
     *
     * @param requirementId 需求 ID
     * @param prompt        可选提示词
     */
    void triggerPrdSpawnAfterCommit(String requirementId, String prompt, String generationToken) {
        TransactionUtil.afterCommit(() -> requirementAgentService.spawnPrd(requirementId, prompt, generationToken));
    }

    /**
     * 编辑 PRD 内容。
     *
     * @param id        需求 ID
     * @param prdContent PRD 内容
     * @return 写操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Requirement> editPrd(String id, String prdContent) {
        Requirement requirement = dao.findOne(id);
        if (Objects.isNull(requirement)) {
            return OperateResultWithData.operationFailure("需求不存在: " + id);
        }
        if (requirement.getStatus() != RequirementStatus.PRD_REVIEW) {
            return OperateResultWithData.operationFailure("仅 PRD_REVIEW 状态可编辑: " + requirement.getStatus());
        }
        requirement.setPrdContent(prdContent);
        return super.save(requirement);
    }

    /**
     * 确认 PRD，冻结并创建概览设计。
     *
     * @param id 需求 ID
     * @return 写操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Requirement> confirmPrd(String id) {
        Requirement requirement = dao.findOne(id);
        if (Objects.isNull(requirement)) {
            return OperateResultWithData.operationFailure("需求不存在: " + id);
        }
        if (requirement.getStatus() != RequirementStatus.PRD_REVIEW) {
            return OperateResultWithData.operationFailure("仅 PRD_REVIEW 状态可确认: " + requirement.getStatus());
        }
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        OperateResultWithData<Requirement> result = super.save(requirement);
        if (result.successful()) {
            overviewDesignService.createGeneratingOverview(requirement);
        }
        return result;
    }

    /**
     * 实体转 DTO。
     *
     * @param requirement 需求实体
     * @return DTO
     */
    public RequirementDto convertToDto(Requirement requirement) {
        RequirementDto dto = new RequirementDto();
        dto.setId(requirement.getId());
        dto.setProjectId(requirement.getProjectId());
        dto.setTitle(requirement.getTitle());
        dto.setDescription(requirement.getDescription());
        dto.setStatus(requirement.getStatus());
        dto.setPrdVersion(requirement.getPrdVersion());
        dto.setPrdContent(requirement.getPrdContent());
        dto.setFailureSummary(requirement.getFailureSummary());
        dto.setCreatedDate(requirement.getCreatedDate());
        dto.setLastEditedDate(requirement.getLastEditedDate());
        return dto;
    }
}
