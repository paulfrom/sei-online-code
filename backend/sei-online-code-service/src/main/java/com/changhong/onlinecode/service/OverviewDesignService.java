package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dto.OverviewDesignDto;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/**
 * OverviewDesign 服务。
 *
 * @author sei-online-code
 */
@Service
public class OverviewDesignService extends BaseEntityService<OverviewDesign> {

    private final OverviewDesignDao dao;
    private final OverviewDesignAgentService overviewDesignAgentService;
    private final DetailedDesignService detailedDesignService;

    public OverviewDesignService(OverviewDesignDao dao,
                                 @Lazy OverviewDesignAgentService overviewDesignAgentService,
                                 @Lazy DetailedDesignService detailedDesignService) {
        this.dao = dao;
        this.overviewDesignAgentService = overviewDesignAgentService;
        this.detailedDesignService = detailedDesignService;
    }

    @Override
    protected BaseEntityDao<OverviewDesign> getDao() {
        return dao;
    }

    /**
     * 为已确认 PRD 的需求创建处于生成中的概览设计。
     *
     * @param requirement 已确认的需求
     */
    @Transactional(rollbackFor = Exception.class)
    public void createGeneratingOverview(Requirement requirement) {
        OverviewDesign existing = dao.findByRequirementId(requirement.getId());
        if (Objects.nonNull(existing)) {
            return;
        }
        OverviewDesign overview = new OverviewDesign();
        overview.setProjectId(requirement.getProjectId());
        overview.setRequirementId(requirement.getId());
        overview.setStatus(OverviewDesignStatus.GENERATING);
        overview.setVersion(1);
        dao.save(overview);
        String overviewId = overview.getId();
        TransactionUtil.afterCommit(() -> overviewDesignAgentService.spawnOverviewDesign(overviewId, null));
    }

    /**
     * 按需求 ID 查询概览设计。
     *
     * @param requirementId 需求 ID
     * @return DTO
     */
    public OverviewDesignDto findByRequirementId(String requirementId) {
        OverviewDesign overview = dao.findByRequirementId(requirementId);
        return overview == null ? null : convertToDto(overview);
    }

    /**
     * 重生成概览设计。
     *
     * @param id     概览设计 ID
     * @param prompt 提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<OverviewDesignDto> regenerate(String id, String prompt) {
        OverviewDesign overview = dao.findOne(id);
        if (Objects.isNull(overview)) {
            return ResultData.fail("概览设计不存在: " + id);
        }
        if (overview.getStatus() != OverviewDesignStatus.DRAFT
                && overview.getStatus() != OverviewDesignStatus.FAILED) {
            return ResultData.fail("当前状态不允许重生成: " + overview.getStatus());
        }
        if (hasConfirmedDetailedDesigns(id)) {
            return ResultData.fail("已存在确认的详细设计，不能重生成");
        }
        overview.setStatus(OverviewDesignStatus.GENERATING);
        overview.setVersion(overview.getVersion() + 1);
        OperateResultWithData<OverviewDesign> result = super.save(overview);
        if (result.successful()) {
            String overviewId = overview.getId();
            TransactionUtil.afterCommit(() -> overviewDesignAgentService.spawnOverviewDesign(overviewId, prompt));
        }
        return ResultData.success(convertToDto(overview));
    }

    /**
     * 编辑概览设计。
     *
     * @param id      概览设计 ID
     * @param content 内容
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<OverviewDesignDto> edit(String id, String content) {
        OverviewDesign overview = dao.findOne(id);
        if (Objects.isNull(overview)) {
            return ResultData.fail("概览设计不存在: " + id);
        }
        if (overview.getStatus() != OverviewDesignStatus.DRAFT) {
            return ResultData.fail("仅 DRAFT 状态可编辑: " + overview.getStatus());
        }
        if (hasConfirmedDetailedDesigns(id)) {
            return ResultData.fail("已存在确认的详细设计，不能编辑");
        }
        overview.setContent(content);
        OperateResultWithData<OverviewDesign> result = super.save(overview);
        return result.successful()
                ? ResultData.success(convertToDto(result.getData()))
                : ResultData.fail(result.getMessage());
    }

    /**
     * 确认概览设计并拆分为详细设计。
     *
     * @param id 概览设计 ID
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<OverviewDesignDto> confirm(String id) {
        OverviewDesign overview = dao.findOne(id);
        if (Objects.isNull(overview)) {
            return ResultData.fail("概览设计不存在: " + id);
        }
        if (overview.getStatus() != OverviewDesignStatus.DRAFT) {
            return ResultData.fail("仅 DRAFT 状态可确认: " + overview.getStatus());
        }
        overview.setStatus(OverviewDesignStatus.CONFIRMED);
        OperateResultWithData<OverviewDesign> result = super.save(overview);
        if (result.successful()) {
            detailedDesignService.createFromOverviewDesign(overview);
        }
        return ResultData.success(convertToDto(overview));
    }

    private boolean hasConfirmedDetailedDesigns(String overviewDesignId) {
        return detailedDesignService.findByOverviewDesignId(overviewDesignId).stream()
                .anyMatch(d -> d.getStatus() == com.changhong.onlinecode.dto.enums.DetailedDesignStatus.CONFIRMED);
    }

    /**
     * 实体转 DTO。
     *
     * @param overview 概览设计实体
     * @return DTO
     */
    public OverviewDesignDto convertToDto(OverviewDesign overview) {
        OverviewDesignDto dto = new OverviewDesignDto();
        dto.setId(overview.getId());
        dto.setProjectId(overview.getProjectId());
        dto.setRequirementId(overview.getRequirementId());
        dto.setStatus(overview.getStatus());
        dto.setVersion(overview.getVersion());
        dto.setContent(overview.getContent());
        dto.setFailureSummary(overview.getFailureSummary());
        dto.setCreatedDate(overview.getCreatedDate());
        dto.setLastEditedDate(overview.getLastEditedDate());
        return dto;
    }
}
