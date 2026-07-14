package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dto.RequirementDto;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RequirementDesignContextDao requirementDesignContextDao;
    private final RequirementDesignContextService requirementDesignContextService;
    private final DesignMemoryValidationService designMemoryValidationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private RequirementAutomationService requirementAutomationService;

    public RequirementService(RequirementDao dao,
                              RequirementAgentService requirementAgentService,
                              RequirementDesignContextDao requirementDesignContextDao,
                              RequirementDesignContextService requirementDesignContextService,
                              DesignMemoryValidationService designMemoryValidationService,
                              com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.dao = dao;
        this.requirementAgentService = requirementAgentService;
        this.requirementDesignContextDao = requirementDesignContextDao;
        this.requirementDesignContextService = requirementDesignContextService;
        this.designMemoryValidationService = designMemoryValidationService;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setRequirementAutomationService(RequirementAutomationService requirementAutomationService) {
        this.requirementAutomationService = requirementAutomationService;
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
        if (!isNew) {
            Requirement existing = dao.findOne(entity.getId());
            if (existing != null && requirementChanged(existing, entity)) {
                requirementDesignContextService.invalidate(entity.getId());
            }
        }
        if (Objects.isNull(entity.getAutomationStatus())) {
            entity.setAutomationStatus(RequirementAutomationStatus.IDLE);
        }

        OperateResultWithData<Requirement> result = super.save(entity);
        Requirement saved = result.getData();
        if (result.successful() && isNew && saved != null) {
            triggerPrdSpawnAfterCommit(saved.getId(), null, saved.getGenerationToken());
        }
        return result;
    }

    private boolean requirementChanged(Requirement existing, Requirement updated) {
        return !Objects.equals(existing.getTitle(), updated.getTitle())
                || !Objects.equals(existing.getDescription(), updated.getDescription());
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
        revalidateAfterEdit(requirement, DesignMemoryValidationService.DocumentType.PRD, prdContent);
        return super.save(requirement);
    }

    /**
     * 确认 PRD，冻结并启动 PM 自动化执行循环。
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
        if (requirement.getMemoryValidationStatus() == MemoryValidationStatus.FAILED) {
            return OperateResultWithData.operationFailure("记忆校验未通过（FAILED），请修改后重新校验");
        }
        OperateResultWithData<Void> validation = validateDesignContextForConfirm(requirement.getDesignContextId(), id);
        if (validation.notSuccessful()) {
            return OperateResultWithData.operationFailure(validation.getMessage());
        }
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        requirement.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        OperateResultWithData<Requirement> result = super.save(requirement);
        if (result.successful() && requirementAutomationService != null) {
            TransactionUtil.afterCommit(() -> requirementAutomationService.startInitialLoop(id));
        }
        return result;
    }

    /**
     * 手动编辑后重新校验文档，并写入 Requirement 的 memory_validation_status/result。
     */
    private void revalidateAfterEdit(Requirement requirement,
                                     DesignMemoryValidationService.DocumentType type,
                                     String content) {
        RequirementDesignContext context = null;
        if (requirement.getDesignContextId() != null && !requirement.getDesignContextId().isBlank()) {
            context = requirementDesignContextDao.findOne(requirement.getDesignContextId());
        }
        if (context == null) {
            context = requirementDesignContextDao.findByRequirementIdAndStatus(requirement.getId(),
                    com.changhong.onlinecode.dto.enums.MemoryRecordStatus.CURRENT);
        }
        if (context == null) {
            requirement.setMemoryValidationStatus(MemoryValidationStatus.NOT_RUN);
            requirement.setMemoryValidationResultJson(null);
            return;
        }
        DesignMemoryValidationService.ValidationResult result = designMemoryValidationService.validate(type, content, context);
        requirement.setMemoryValidationStatus(result.getStatus());
        requirement.setMemoryValidationResultJson(toJson(result));
    }

    private String toJson(DesignMemoryValidationService.ValidationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"status\":\"FAILED\",\"findings\":[{\"severity\":\"HIGH\",\"message\":\"校验结果序列化失败\"}]}";
        }
    }

    /**
     * 确认前校验：design_context_id 存在、validation 未 FAILED、context 未 STALE。
     *
     * @param designContextId 设计上下文 id
     * @param requirementId   需求 id（用于回退查询）
     * @return 校验结果
     */
    private OperateResultWithData<Void> validateDesignContextForConfirm(String designContextId, String requirementId) {
        if (designContextId == null || designContextId.isBlank()) {
            return OperateResultWithData.operationFailure("未关联设计上下文，请重新生成 PRD");
        }
        RequirementDesignContext context = requirementDesignContextDao.findOne(designContextId);
        if (context == null) {
            context = requirementDesignContextDao.findByRequirementIdAndStatus(requirementId,
                    com.changhong.onlinecode.dto.enums.MemoryRecordStatus.CURRENT);
        }
        if (context == null) {
            return OperateResultWithData.operationFailure("未找到有效设计上下文，请重新生成 PRD");
        }
        if (context.getContextStatus() == RequirementDesignContextStatus.FAILED) {
            return OperateResultWithData.operationFailure("设计上下文状态为 FAILED，请重新生成 PRD");
        }
        if (context.getContextStatus() == RequirementDesignContextStatus.STALE) {
            return OperateResultWithData.operationFailure("设计上下文已过期（STALE），请重新生成 PRD");
        }
        return OperateResultWithData.operationSuccess();
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
        dto.setAutomationStatus(requirement.getAutomationStatus());
        dto.setPrdVersion(requirement.getPrdVersion());
        dto.setPrdContent(requirement.getPrdContent());
        dto.setDesignContextId(requirement.getDesignContextId());
        dto.setMemoryValidationStatus(requirement.getMemoryValidationStatus());
        dto.setMemoryValidationResultJson(requirement.getMemoryValidationResultJson());
        dto.setActiveLoopId(requirement.getActiveLoopId());
        dto.setAcceptedAt(requirement.getAcceptedAt());
        dto.setAcceptedByAgent(requirement.getAcceptedByAgent());
        dto.setDeliveryBranch(requirement.getDeliveryBranch());
        dto.setDeliveryCommitHash(requirement.getDeliveryCommitHash());
        dto.setDeliveryMrUrl(requirement.getDeliveryMrUrl());
        dto.setDeliveryTargetBranch(requirement.getDeliveryTargetBranch());
        dto.setFailureSummary(requirement.getFailureSummary());
        dto.setCreatedDate(requirement.getCreatedDate());
        dto.setLastEditedDate(requirement.getLastEditedDate());
        return dto;
    }
}
