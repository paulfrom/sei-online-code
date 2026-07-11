package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dto.DetailedDesignDto;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.RequirementDesignContext;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DetailedDesign 服务。
 *
 * @author sei-online-code
 */
@Service
public class DetailedDesignService extends BaseEntityService<DetailedDesign> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetailedDesignService.class);
    private static final Pattern MODULE_TABLE_ROW =
            Pattern.compile("^\\|\\s*([^|\\s][^|]*)\\s*\\|\\s*([^|][^|]*)\\s*\\|\\s*([^|]*)\\|\\s*$");

    private final DetailedDesignDao dao;
    private final DetailedDesignAgentService detailedDesignAgentService;
    private final CodingTaskService codingTaskService;
    private final RequirementDesignContextDao requirementDesignContextDao;
    private final DesignMemoryValidationService designMemoryValidationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public DetailedDesignService(DetailedDesignDao dao,
                                 @Lazy DetailedDesignAgentService detailedDesignAgentService,
                                 @Lazy CodingTaskService codingTaskService,
                                 RequirementDesignContextDao requirementDesignContextDao,
                                 DesignMemoryValidationService designMemoryValidationService,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.dao = dao;
        this.detailedDesignAgentService = detailedDesignAgentService;
        this.codingTaskService = codingTaskService;
        this.requirementDesignContextDao = requirementDesignContextDao;
        this.designMemoryValidationService = designMemoryValidationService;
        this.objectMapper = objectMapper;
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
        design.setGenerationToken(GenerationTokenSupport.newToken());
        OperateResultWithData<DetailedDesign> result = super.save(design);
        if (result.successful()) {
            String designId = design.getId();
            String generationToken = design.getGenerationToken();
            TransactionUtil.afterCommit(() -> detailedDesignAgentService.spawnDetailedDesign(designId, prompt,
                    generationToken));
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
        revalidateAfterEdit(design, DesignMemoryValidationService.DocumentType.DETAILED, content);
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
        if (design.getMemoryValidationStatus() == MemoryValidationStatus.FAILED) {
            return ResultData.fail("记忆校验未通过（FAILED），请修改后重新校验");
        }
        ResultData<Void> validation = validateDesignContextForConfirm(design.getDesignContextId());
        if (!validation.successful()) {
            return ResultData.fail(validation.getMessage());
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
        List<String> failures = new ArrayList<>();
        for (String id : ids) {
            ResultData<DetailedDesignDto> result = confirm(id);
            if (result.successful() && result.getData() != null) {
                list.add(result.getData());
            } else {
                failures.add(id + ": " + result.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            return ResultData.fail("部分详细设计确认失败: " + String.join("; ", failures));
        }
        return ResultData.success(list);
    }

    /**
     * 按概览设计中的模块清单创建模块级详细设计。
     *
     * @param overviewDesign 概览设计
     */
    @Transactional(rollbackFor = Exception.class)
    public void createFromOverviewDesign(com.changhong.onlinecode.entity.OverviewDesign overviewDesign) {
        List<ModuleRef> modules = parseModules(overviewDesign.getContent());
        if (modules.isEmpty()) {
            modules = List.of(new ModuleRef("default-module", "默认模块"));
        }
        for (ModuleRef module : modules) {
            DetailedDesign design = new DetailedDesign();
            design.setProjectId(overviewDesign.getProjectId());
            design.setRequirementId(overviewDesign.getRequirementId());
            design.setOverviewDesignId(overviewDesign.getId());
            design.setModuleId(module.moduleId);
            design.setModuleTitle(module.moduleTitle);
            design.setStatus(DetailedDesignStatus.GENERATING);
            design.setVersion(1);
            design.setContent("# 详细设计: " + module.moduleTitle);
            design.setLastFailedAt(new Date());
            design.setGenerationToken(GenerationTokenSupport.newToken());
            dao.save(design);
            String designId = design.getId();
            String generationToken = design.getGenerationToken();
            TransactionUtil.afterCommit(() -> detailedDesignAgentService.spawnDetailedDesign(designId, null,
                    generationToken));
        }
    }

    /**
     * 仅补全概览设计下缺失的详细设计。用于补偿器，不覆盖/不修改已有设计。
     *
     * @param overviewDesign 已确认的概览设计
     */
    @Transactional(rollbackFor = Exception.class)
    public void createMissingFromOverviewDesign(com.changhong.onlinecode.entity.OverviewDesign overviewDesign) {
        List<ModuleRef> expected = parseModules(overviewDesign.getContent());
        if (expected.isEmpty()) {
            expected = List.of(new ModuleRef("default-module", "默认模块"));
        }
        List<DetailedDesign> existing = dao.findByOverviewDesignId(overviewDesign.getId());
        Set<String> existingModuleIds = existing.stream()
                .map(DetailedDesign::getModuleId)
                .collect(Collectors.toSet());
        for (ModuleRef module : expected) {
            if (existingModuleIds.contains(module.moduleId)) {
                continue;
            }
            DetailedDesign design = new DetailedDesign();
            design.setProjectId(overviewDesign.getProjectId());
            design.setRequirementId(overviewDesign.getRequirementId());
            design.setOverviewDesignId(overviewDesign.getId());
            design.setModuleId(module.moduleId);
            design.setModuleTitle(module.moduleTitle);
            design.setStatus(DetailedDesignStatus.GENERATING);
            design.setVersion(1);
            design.setContent("# 详细设计: " + module.moduleTitle);
            design.setLastFailedAt(new Date());
            design.setGenerationToken(GenerationTokenSupport.newToken());
            dao.save(design);
            String designId = design.getId();
            String generationToken = design.getGenerationToken();
            TransactionUtil.afterCommit(() -> detailedDesignAgentService.spawnDetailedDesign(designId, null,
                    generationToken));
        }
    }

    List<ModuleRef> parseModules(String content) {
        List<ModuleRef> list = new ArrayList<>();
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
                    list.add(new ModuleRef(moduleId, moduleTitle));
                }
                return deduplicateModules(list);
            }
        } catch (Exception e) {
            LOGGER.debug("概览设计 content 非 legacy JSON，继续按 Markdown 解析: {}", e.getMessage());
        }
        boolean inModuleTable = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("| moduleId | moduleTitle | summary |")) {
                inModuleTable = true;
                continue;
            }
            if (!inModuleTable) {
                continue;
            }
            if (trimmed.isBlank()) {
                break;
            }
            if (trimmed.matches("^\\|\\s*-+\\s*\\|\\s*-+\\s*\\|\\s*-+\\s*\\|\\s*$")) {
                continue;
            }
            Matcher matcher = MODULE_TABLE_ROW.matcher(trimmed);
            if (matcher.matches()) {
                String moduleId = matcher.group(1).trim();
                String moduleTitle = matcher.group(2).trim();
                if ("moduleId".equalsIgnoreCase(moduleId)) {
                    continue;
                }
                list.add(new ModuleRef(moduleId, moduleTitle.isBlank() ? moduleId : moduleTitle));
                continue;
            }
            if (trimmed.startsWith("|")) {
                LOGGER.warn("模块清单行格式无法解析，已跳过: {}", trimmed);
                continue;
            }
            break;
        }
        return deduplicateModules(list);
    }

    private List<ModuleRef> deduplicateModules(List<ModuleRef> modules) {
        List<ModuleRef> deduplicated = new ArrayList<>();
        Set<String> seen = modules.stream()
                .map(ModuleRef::moduleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (ModuleRef module : modules) {
            if (module.moduleId == null || module.moduleId.isBlank()) {
                continue;
            }
            if (!seen.remove(module.moduleId)) {
                continue;
            }
            deduplicated.add(module);
        }
        return deduplicated;
    }

    record ModuleRef(String moduleId, String moduleTitle) {
    }

    private boolean hasConfirmedDetailedDesigns(String overviewDesignId) {
        return findByOverviewDesignId(overviewDesignId).stream()
                .anyMatch(d -> d.getStatus() == DetailedDesignStatus.CONFIRMED);
    }

    /**
     * 手动编辑后重新校验详细设计。
     */
    private void revalidateAfterEdit(DetailedDesign design,
                                     DesignMemoryValidationService.DocumentType type,
                                     String content) {
        RequirementDesignContext context = findCurrentContext(design.getDesignContextId());
        if (context == null) {
            design.setMemoryValidationStatus(MemoryValidationStatus.NOT_RUN);
            design.setMemoryValidationResultJson(null);
            return;
        }
        DesignMemoryValidationService.ValidationResult result = designMemoryValidationService.validate(type, content, context);
        design.setMemoryValidationStatus(result.getStatus());
        design.setMemoryValidationResultJson(toJson(result));
    }

    private RequirementDesignContext findCurrentContext(String designContextId) {
        if (designContextId == null || designContextId.isBlank()) {
            return null;
        }
        return requirementDesignContextDao.findOne(designContextId);
    }

    private String toJson(DesignMemoryValidationService.ValidationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"status\":\"FAILED\",\"findings\":[{\"severity\":\"HIGH\",\"message\":\"校验结果序列化失败\"}]}";
        }
    }

    /**
     * 确认前校验：design_context_id 存在、context 未 STALE/FAILED。
     */
    private ResultData<Void> validateDesignContextForConfirm(String designContextId) {
        if (designContextId == null || designContextId.isBlank()) {
            return ResultData.fail("未关联设计上下文，请重新生成详细设计");
        }
        RequirementDesignContext context = requirementDesignContextDao.findOne(designContextId);
        if (context == null) {
            return ResultData.fail("未找到有效设计上下文，请重新生成详细设计");
        }
        if (context.getContextStatus() == RequirementDesignContextStatus.FAILED) {
            return ResultData.fail("设计上下文状态为 FAILED，请重新生成详细设计");
        }
        if (context.getContextStatus() == RequirementDesignContextStatus.STALE) {
            return ResultData.fail("设计上下文已过期（STALE），请重新生成详细设计");
        }
        return ResultData.success(null);
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
        dto.setStatus(design.getStatus());
        dto.setVersion(design.getVersion());
        dto.setContent(design.getContent());
        dto.setDesignContextId(design.getDesignContextId());
        dto.setMemoryValidationStatus(design.getMemoryValidationStatus());
        dto.setMemoryValidationResultJson(design.getMemoryValidationResultJson());
        dto.setFailureSummary(design.getFailureSummary());
        dto.setCreatedDate(design.getCreatedDate());
        dto.setLastEditedDate(design.getLastEditedDate());
        return dto;
    }
}
