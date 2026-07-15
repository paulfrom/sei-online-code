package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.RunUsageDto;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Run 服务。承载运行记录的查询与状态更新，由 FeatureDesign 编码执行流程写入。
 *
 * @author sei-online-code
 */
@Service
@AllArgsConstructor
public class RunService extends BaseEntityService<Run> {

    private final RunDao dao;

    @Override
    protected BaseEntityDao<Run> getDao() {
        return dao;
    }

    /**
     * 按迭代 id 取运行记录（取消级联用）。
     *
     * @param iterationId 迭代 id
     * @return Run 列表
     */
    public List<Run> findByIteration(String iterationId) {
        return dao.findByIterationId(iterationId);
    }

    /**
     * 按编码任务 id 取运行记录。
     *
     * @param codingTaskId 编码任务 id
     * @return Run 列表
     */
    public List<Run> findByCodingTaskId(String codingTaskId) {
        return dao.findByCodingTaskId(codingTaskId);
    }

    /**
     * 按需求取全部运行记录，包括未绑定编码任务的运行。
     *
     * @param requirementId 需求 id
     * @return Run 列表
     */
    public List<Run> findByRequirementId(String requirementId) {
        return dao.findByRequirementIdOrderByCreatedDateDesc(requirementId);
    }

    /**
     * 查询单次 Run 的 token usage 详情，包含原始 usage JSON。
     *
     * @param runId 运行 id
     * @return usage 详情；Run 不存在时返回 null
     */
    public RunUsageDto findUsage(String runId) {
        Run run = dao.findOne(runId);
        return run == null ? null : toUsageDto(run);
    }

    /**
     * 将 Run 实体转换为 usage 详情 DTO。
     */
    private RunUsageDto toUsageDto(Run run) {
        RunUsageDto dto = new RunUsageDto();
        dto.setRunId(run.getId());
        dto.setAgentId(run.getAgentId());
        dto.setAgentName(run.getAgentName());
        dto.setCliTool(run.getCliTool());
        dto.setModel(run.getModel());
        dto.setInputTokens(run.getInputTokens());
        dto.setOutputTokens(run.getOutputTokens());
        dto.setCacheReadTokens(run.getCacheReadTokens());
        dto.setCacheWriteTokens(run.getCacheWriteTokens());
        dto.setTotalTokens(run.getTotalTokens());
        dto.setUsageStatus(run.getUsageStatus());
        dto.setRawUsageJson(run.getRawUsageJson());
        return dto;
    }
}
