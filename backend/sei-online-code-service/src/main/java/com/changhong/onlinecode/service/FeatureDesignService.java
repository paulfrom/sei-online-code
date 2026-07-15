package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dto.FeatureDesignDto;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 功能设计服务（Task 10）。
 *
 * <p>职责：功能设计的查询、编辑、重生、确认、历史版本查询。与编码执行互斥（BUILDING 态抛 409 ConflictException）。
 */
@Service
@AllArgsConstructor
public class FeatureDesignService extends BaseEntityService<FeatureDesign> {

    private final FeatureDesignDao featureDesignDao;
    private final PlanAgentService planAgentService;
    private final FailureInfoSupport failureInfoSupport;

    @Override
    protected BaseEntityDao<FeatureDesign> getDao() {
        return featureDesignDao;
    }

    /**
     * 查找项目下所有最新版功能设计
     *
     * @param projectId 项目 id
     * @return 最新版功能设计 DTO 列表
     */
    public List<FeatureDesignDto> findLatestByProject(String projectId) {
        List<FeatureDesign> designs = featureDesignDao.findLatestByProjectId(projectId);
        return designs.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 查找单条最新版功能设计
     *
     * @param id 功能设计 id
     * @return 最新版功能设计 DTO，不存在时返回 null
     */
    public FeatureDesignDto findOneLatest(String id) {
        FeatureDesign design = featureDesignDao.findLatestById(id);
        return design == null ? null : toDto(design);
    }

    /**
     * 编辑功能设计
     *
     * @param id 功能设计 id
     * @param content 设计内容
     * @return 操作结果，包含新功能设计 DTO
     * @throws ConflictException 当 build_status=BUILDING 时
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<FeatureDesignDto> edit(String id, FeatureDesignContent content) {
        FeatureDesign latest = featureDesignDao.findLatestById(id);
        if (latest == null) {
            return OperateResultWithData.operationFailure("功能设计不存在");
        }
        if (latest.getBuildStatus() == FeatureDesignBuildStatus.BUILDING) {
            throw new ConflictException("功能设计正在编码执行中，不可编辑");
        }

        // 标记旧版本为非最新
        featureDesignDao.markNonLatest(latest.getProjectId(), latest.getFeatureId());

        // 创建新版本
        FeatureDesign newDesign = new FeatureDesign();
        newDesign.setProjectId(latest.getProjectId());
        newDesign.setFeatureId(latest.getFeatureId());
        newDesign.setVersion(latest.getVersion() + 1);
        newDesign.setStatus(FeatureDesignStatus.DRAFT);
        newDesign.setContent(content);
        newDesign.setModifyHint(null);
        newDesign.setIsLatest(true);
        failureInfoSupport.clearFeatureDesignFailure(newDesign);
        // 若旧 build_status 为 BUILT/BUILD_FAILED，新 build_status 为 STALE；否则继承旧值
        if (latest.getBuildStatus() == FeatureDesignBuildStatus.BUILT
                || latest.getBuildStatus() == FeatureDesignBuildStatus.BUILD_FAILED) {
            newDesign.setBuildStatus(FeatureDesignBuildStatus.STALE);
        } else {
            newDesign.setBuildStatus(latest.getBuildStatus());
        }

        OperateResultWithData<FeatureDesign> saved = super.save(newDesign);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }
        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 重新生成功能设计
     *
     * @param id 功能设计 id
     * @param modifyHint 修改提示
     * @return 操作结果，包含新功能设计 DTO
     * @throws ConflictException 当 build_status=BUILDING 时
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<FeatureDesignDto> regenerate(String id, String modifyHint) {
        FeatureDesign latest = featureDesignDao.findLatestById(id);
        if (latest == null) {
            return OperateResultWithData.operationFailure("功能设计不存在");
        }
        if (latest.getBuildStatus() == FeatureDesignBuildStatus.BUILDING) {
            throw new ConflictException("功能设计正在编码执行中，不可重复发起");
        }

        // 标记旧版本为非最新
        featureDesignDao.markNonLatest(latest.getProjectId(), latest.getFeatureId());

        // 创建新版本
        FeatureDesign newDesign = new FeatureDesign();
        newDesign.setProjectId(latest.getProjectId());
        newDesign.setFeatureId(latest.getFeatureId());
        newDesign.setVersion(latest.getVersion() + 1);
        newDesign.setStatus(FeatureDesignStatus.GENERATING);
        newDesign.setContent(null);
        newDesign.setModifyHint(modifyHint);
        newDesign.setIsLatest(true);
        newDesign.setLastTriggerSource(com.changhong.onlinecode.dto.enums.TriggerSource.USER_ACTION);
        // 若旧 build_status 为 BUILT/BUILD_FAILED，新 build_status 为 STALE；否则继承旧值
        if (latest.getBuildStatus() == FeatureDesignBuildStatus.BUILT
                || latest.getBuildStatus() == FeatureDesignBuildStatus.BUILD_FAILED) {
            newDesign.setBuildStatus(FeatureDesignBuildStatus.STALE);
        } else {
            newDesign.setBuildStatus(latest.getBuildStatus());
        }

        OperateResultWithData<FeatureDesign> saved = super.save(newDesign);
        if (!saved.successful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }

        // spawn 功能设计智能体
        planAgentService.spawnFeatureDesign(latest.getProjectId(), latest.getFeatureId(), modifyHint);

        return OperateResultWithData.operationSuccessWithData(toDto(saved.getData()));
    }

    /**
     * 批量确认功能设计
     *
     * @param ids 功能设计 id 列表
     * @return 操作结果，包含确认后的功能设计 DTO 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<List<FeatureDesignDto>> confirm(List<String> ids) {
        List<FeatureDesign> designs = ids.stream()
                .map(featureDesignDao::findLatestById)
                .collect(Collectors.toList());

        // 校验全部存在且状态为 DRAFT
        for (int i = 0; i < ids.size(); i++) {
            FeatureDesign design = designs.get(i);
            if (design == null) {
                return OperateResultWithData.operationFailure("功能设计不存在: " + ids.get(i));
            }
            if (design.getStatus() != FeatureDesignStatus.DRAFT) {
                return OperateResultWithData.operationFailure("仅草稿状态可确认: " + ids.get(i));
            }
        }

        // 置为 CONFIRMED
        List<FeatureDesign> savedDesigns = designs.stream()
                .map(design -> {
                    design.setStatus(FeatureDesignStatus.CONFIRMED);
                    OperateResultWithData<FeatureDesign> saved = super.save(design);
                    if (!saved.successful()) {
                        throw new RuntimeException(saved.getMessage());
                    }
                    return saved.getData();
                })
                .collect(Collectors.toList());

        return OperateResultWithData.operationSuccessWithData(
                savedDesigns.stream().map(this::toDto).collect(Collectors.toList())
        );
    }

    /**
     * 确认单个功能设计（便捷方法）
     *
     * @param id 功能设计 id
     * @return 操作结果，包含确认后的功能设计 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<FeatureDesignDto> confirmOne(String id) {
        OperateResultWithData<List<FeatureDesignDto>> result = confirm(List.of(id));
        if (!result.successful()) {
            return OperateResultWithData.operationFailure(result.getMessage());
        }
        return OperateResultWithData.operationSuccessWithData(result.getData().get(0));
    }

    /**
     * 查询历史版本
     *
     * @param id 功能设计 id（从该 id 对应记录取 featureId）
     * @return 历史版本 DTO 列表，按版本倒序
     */
    public List<FeatureDesignDto> history(String id) {
        FeatureDesign design = featureDesignDao.findLatestById(id);
        if (design == null) {
            return List.of();
        }
        List<FeatureDesign> history = featureDesignDao.findByFeatureIdOrderByVersionDesc(design.getFeatureId());
        return history.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 实体转 DTO
     *
     * @param design 实体
     * @return DTO
     */
    private FeatureDesignDto toDto(FeatureDesign design) {
        if (design == null) {
            return null;
        }
        FeatureDesignDto dto = new FeatureDesignDto();
        dto.setId(design.getId());
        dto.setProjectId(design.getProjectId());
        dto.setFeatureId(design.getFeatureId());
        dto.setVersion(design.getVersion());
        dto.setStatus(design.getStatus());
        dto.setBuildStatus(design.getBuildStatus());
        dto.setContent(design.getContent());
        dto.setModifyHint(design.getModifyHint());
        dto.setIsLatest(design.getIsLatest());
        dto.setCreatedDate(design.getCreatedDate());
        dto.setLastEditedDate(design.getLastEditedDate());
        dto.setFailureCode(design.getFailureCode());
        dto.setFailureStage(design.getFailureStage());
        dto.setFailureSummary(design.getFailureSummary());
        dto.setFailureDetail(design.getFailureDetail());
        dto.setLastFailedAt(design.getLastFailedAt());
        dto.setLastRetryAt(design.getLastRetryAt());
        dto.setRetryCount(design.getRetryCount());
        dto.setNextRetryAt(design.getNextRetryAt());
        dto.setLastTriggerSource(design.getLastTriggerSource());
        return dto;
    }
}
