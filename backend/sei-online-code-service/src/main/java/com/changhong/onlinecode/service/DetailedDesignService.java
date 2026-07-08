package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dto.DetailedDesignDto;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * DetailedDesign 服务。
 *
 * @author sei-online-code
 */
@Service
public class DetailedDesignService extends BaseEntityService<DetailedDesign> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetailedDesignService.class);

    private final DetailedDesignDao dao;
    private final DetailedDesignAgentService detailedDesignAgentService;
    private final CodingTaskService codingTaskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DetailedDesignService(DetailedDesignDao dao,
                                 @Lazy DetailedDesignAgentService detailedDesignAgentService,
                                 @Lazy CodingTaskService codingTaskService) {
        this.dao = dao;
        this.detailedDesignAgentService = detailedDesignAgentService;
        this.codingTaskService = codingTaskService;
    }

    @Override
    protected BaseEntityDao<DetailedDesign> getDao() {
        return dao;
    }

    /**
     * 按 ID 查询并转 DTO。
     *
     * @param id 详细设计 ID
     * @return DTO
     */
    public DetailedDesignDto findOneDto(String id) {
        DetailedDesign design = dao.findOne(id);
        return design == null ? null : convertToDto(design);
    }

    /**
     * 按概览设计 ID 查询。
     *
     * @param overviewDesignId 概览设计 ID
     * @return DTO 列表
     */
    public List<DetailedDesignDto> findByOverviewDesignId(String overviewDesignId) {
        return dao.findByOverviewDesignId(overviewDesignId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 重生成详细设计。
     *
     * @param id     详细设计 ID
     * @param prompt 提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<DetailedDesignDto> regenerate(String id, String prompt) {
        DetailedDesign design = dao.findOne(id);
        if (Objects.isNull(design)) {
            return ResultData.fail("详细设计不存在: " + id);
        }
        if (design.getStatus() != DetailedDesignStatus.REVIEW
                && design.getStatus() != DetailedDesignStatus.FAILED) {
            return ResultData.fail("当前状态不允许重生成: " + design.getStatus());
        }
        design.setStatus(DetailedDesignStatus.GENERATING);
        design.setVersion(design.getVersion() + 1);
        design.setLastFailedAt(null);
        OperateResultWithData<DetailedDesign> result = super.save(design);
        if (result.successful()) {
            String designId = design.getId();
            TransactionUtil.afterCommit(() -> detailedDesignAgentService.spawnDetailedDesign(designId, prompt));
        }
        return ResultData.success(convertToDto(design));
    }

    /**
     * 编辑详细设计内容。
     *
     * @param id      详细设计 ID
     * @param content 内容
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<DetailedDesignDto> edit(String id, String content) {
        DetailedDesign design = dao.findOne(id);
        if (Objects.isNull(design)) {
            return ResultData.fail("详细设计不存在: " + id);
        }
        if (design.getStatus() != DetailedDesignStatus.REVIEW) {
            return ResultData.fail("仅 REVIEW 状态可编辑: " + design.getStatus());
        }
        design.setContent(content);
        OperateResultWithData<DetailedDesign> result = super.save(design);
        return result.successful()
                ? ResultData.success(convertToDto(result.getData()))
                : ResultData.fail(result.getMessage());
    }

    /**
     * 确认详细设计并创建 CodingTask。
     *
     * @param id 详细设计 ID
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<DetailedDesignDto> confirm(String id) {
        DetailedDesign design = dao.findOne(id);
        if (Objects.isNull(design)) {
            return ResultData.fail("详细设计不存在: " + id);
        }
        if (design.getStatus() != DetailedDesignStatus.REVIEW) {
            return ResultData.fail("仅 REVIEW 状态可确认: " + design.getStatus());
        }
        design.setStatus(DetailedDesignStatus.CONFIRMED);
        OperateResultWithData<DetailedDesign> result = super.save(design);
        if (result.successful()) {
            codingTaskService.createFromDetailedDesign(design);
        }
        return ResultData.success(convertToDto(design));
    }

    /**
     * 批量确认。
     *
     * @param ids ID 列表
     * @return 结果列表
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<List<DetailedDesignDto>> batchConfirm(List<String> ids) {
        List<DetailedDesignDto> list = new ArrayList<>();
        for (String id : ids) {
            ResultData<DetailedDesignDto> result = confirm(id);
            if (result.successful() && result.getData() != null) {
                list.add(result.getData());
            }
        }
        return ResultData.success(list);
    }

    /**
     * 按概览设计内容扁平化创建详细设计（占位实现）。
     *
     * @param overviewDesign 概览设计
     */
    @Transactional(rollbackFor = Exception.class)
    public void createFromOverviewDesign(com.changhong.onlinecode.entity.OverviewDesign overviewDesign) {
        List<FeatureRef> features = parseFeatures(overviewDesign.getContent());
        if (features.isEmpty()) {
            features = List.of(new FeatureRef("default", "默认模块", "default", "默认功能"));
        }
        for (FeatureRef f : features) {
            DetailedDesign design = new DetailedDesign();
            design.setProjectId(overviewDesign.getProjectId());
            design.setRequirementId(overviewDesign.getRequirementId());
            design.setOverviewDesignId(overviewDesign.getId());
            design.setModuleId(f.moduleId);
            design.setModuleTitle(f.moduleTitle);
            design.setFeatureId(f.featureId);
            design.setFeatureTitle(f.featureTitle);
            design.setStatus(DetailedDesignStatus.GENERATING);
            design.setVersion(1);
            design.setContent("{\"placeholder\":true}");
            design.setLastFailedAt(new Date());
            dao.save(design);
            String designId = design.getId();
            TransactionUtil.afterCommit(() -> detailedDesignAgentService.spawnDetailedDesign(designId, null));
        }
    }

    private List<FeatureRef> parseFeatures(String content) {
        List<FeatureRef> list = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return list;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode modules = root.path("modules");
            if (modules.isArray()) {
                for (JsonNode module : modules) {
                    String moduleId = module.path("moduleId").asText("default");
                    String moduleTitle = module.path("moduleTitle").asText(moduleId);
                    JsonNode features = module.path("features");
                    if (features.isArray()) {
                        for (JsonNode feature : features) {
                            String featureId = feature.path("featureId").asText("default");
                            String featureTitle = feature.path("featureTitle").asText(featureId);
                            list.add(new FeatureRef(moduleId, moduleTitle, featureId, featureTitle));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析概览设计 content 失败，回退到默认 feature: {}", e.getMessage());
        }
        return list;
    }

    private record FeatureRef(String moduleId, String moduleTitle, String featureId, String featureTitle) {
    }

    private boolean hasConfirmedDetailedDesigns(String overviewDesignId) {
        return findByOverviewDesignId(overviewDesignId).stream()
                .anyMatch(d -> d.getStatus() == DetailedDesignStatus.CONFIRMED);
    }

    /**
     * 实体转 DTO。
     *
     * @param design 详细设计实体
     * @return DTO
     */
    public DetailedDesignDto convertToDto(DetailedDesign design) {
        DetailedDesignDto dto = new DetailedDesignDto();
        dto.setId(design.getId());
        dto.setProjectId(design.getProjectId());
        dto.setRequirementId(design.getRequirementId());
        dto.setOverviewDesignId(design.getOverviewDesignId());
        dto.setModuleId(design.getModuleId());
        dto.setModuleTitle(design.getModuleTitle());
        dto.setFeatureId(design.getFeatureId());
        dto.setFeatureTitle(design.getFeatureTitle());
        dto.setStatus(design.getStatus());
        dto.setVersion(design.getVersion());
        dto.setContent(design.getContent());
        dto.setFailureSummary(design.getFailureSummary());
        dto.setCreatedDate(design.getCreatedDate());
        dto.setLastEditedDate(design.getLastEditedDate());
        return dto;
    }
}
