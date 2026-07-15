package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CodingTask 服务。
 *
 * @author sei-online-code
 */
@Service
@AllArgsConstructor
public class CodingTaskService extends BaseEntityService<CodingTask> {

    private final CodingTaskDao dao;
    private final RunDao runDao;
    private final CodingTaskExecutionService executionService;

    @Override
    protected BaseEntityDao<CodingTask> getDao() {
        return dao;
    }

    /**
     * 按需求 ID 查询。
     *
     * @param requirementId 需求 ID
     * @return DTO 列表
     */
    public List<CodingTaskDto> findByRequirementId(String requirementId) {
        return dao.findByRequirementId(requirementId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 运行任务。
     *
     * @param id         任务 ID
     * @param userPrompt 用户提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> run(String id, String userPrompt) {
        CodingTask task = dao.findOne(id);
        if (Objects.isNull(task)) {
            return ResultData.fail("编码任务不存在: " + id);
        }
        if (task.getStatus() != CodingTaskStatus.PENDING) {
            return ResultData.fail("仅待执行任务可运行");
        }
        return executionService.execute(id, userPrompt);
    }

    /**
     * 重跑任务。
     *
     * @param id          任务 ID
     * @param rerunPrompt 重跑提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> rerun(String id, String rerunPrompt) {
        if (rerunPrompt == null || rerunPrompt.isBlank()) {
            return ResultData.fail("rerunPrompt 不能为空");
        }
        CodingTask task = dao.findOne(id);
        if (Objects.isNull(task)) {
            return ResultData.fail("编码任务不存在: " + id);
        }
        if (task.getStatus() != CodingTaskStatus.FAILED
                && task.getStatus() != CodingTaskStatus.VALIDATION_FAILED) {
            return ResultData.fail("仅开发失败或验证失败任务可重跑");
        }
        CodingTaskStatus failedStatus = task.getStatus();
        if (dao.updateStatusIfMatch(id, failedStatus, CodingTaskStatus.RUNNING) == 0) {
            return ResultData.fail("任务已被补偿任务或其他重跑请求抢占，请刷新后查看最新状态");
        }
        ResultData<CodingTaskDto> result;
        if (task.getExecutionPlanId() != null) {
            result = executionService.executePlanTask(id, task.getAssignedAgent(), rerunPrompt,
                    TriggerSource.USER_ACTION);
        } else {
            result = executionService.execute(id, rerunPrompt);
        }
        if (result == null || !result.successful()) {
            dao.updateStatusIfMatch(id, CodingTaskStatus.RUNNING, failedStatus);
        }
        return result;
    }

    /**
     * 取消任务。
     *
     * @param id 任务 ID
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> cancel(String id) {
        CodingTask task = dao.findOne(id);
        if (Objects.isNull(task)) {
            return ResultData.fail("编码任务不存在: " + id);
        }
        if (task.getStatus() != CodingTaskStatus.RUNNING) {
            return ResultData.fail("仅执行中任务可取消");
        }
        Run active = runDao.findByCodingTaskId(id).stream()
                .filter(r -> r.getState() == RunState.RUNNING)
                .findFirst()
                .orElse(null);
        if (active != null) {
            active.setCancelRequested(Boolean.TRUE);
            active.setState(RunState.CANCELLED);
            active.setTerminalReason(RunTerminalReason.CANCELLED);
            active.setFinishedDate(new Date());
            runDao.save(active);
            executionService.cancelRun(active.getId());
        }
        task.setStatus(CodingTaskStatus.CANCELLED);
        OperateResultWithData<CodingTask> result = super.save(task);
        return result.successful()
                ? ResultData.success(convertToDto(result.getData()))
                : ResultData.fail(result.getMessage());
    }

    /**
     * 实体转 DTO。
     *
     * @param task 任务实体
     * @return DTO
     */
    public CodingTaskDto convertToDto(CodingTask task) {
        CodingTaskDto dto = new CodingTaskDto();
        dto.setId(task.getId());
        dto.setProjectId(task.getProjectId());
        dto.setRequirementId(task.getRequirementId());
        dto.setStatus(task.getStatus());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setFileScope(task.getFileScope());
        dto.setArea(task.getArea());
        dto.setDependsOn(task.getDependsOn());
        dto.setExecutionPlanId(task.getExecutionPlanId());
        dto.setPlanTaskKey(task.getPlanTaskKey());
        dto.setAssignedAgent(task.getAssignedAgent());
        dto.setLoopId(task.getLoopId());
        dto.setFailureSummary(task.getFailureSummary());
        dto.setCreatedDate(task.getCreatedDate());
        dto.setLastEditedDate(task.getLastEditedDate());
        return dto;
    }

}
