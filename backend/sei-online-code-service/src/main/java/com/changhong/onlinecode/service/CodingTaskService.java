package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.context.annotation.Lazy;
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
public class CodingTaskService extends BaseEntityService<CodingTask> {

    private final CodingTaskDao dao;
    private final RunDao runDao;
    private final ProjectService projectService;
    private final CodingTaskExecutionService executionService;

    public CodingTaskService(CodingTaskDao dao,
                             RunDao runDao,
                             ProjectService projectService,
                             @Lazy CodingTaskExecutionService executionService) {
        this.dao = dao;
        this.runDao = runDao;
        this.projectService = projectService;
        this.executionService = executionService;
    }

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
     * 从详细设计创建任务。
     *
     * @param design 详细设计
     * @return 任务
     */
    @Transactional(rollbackFor = Exception.class)
    public CodingTask createFromDetailedDesign(DetailedDesign design) {
        CodingTask task = new CodingTask();
        task.setProjectId(design.getProjectId());
        task.setRequirementId(design.getRequirementId());
        task.setDetailedDesignId(design.getId());
        task.setDetailedDesignVersion(design.getVersion());
        task.setStatus(CodingTaskStatus.PENDING);
        task.setTitle(firstNonBlank(design.getModuleTitle(), "未命名模块"));
        task.setDescription(design.getContent());
        dao.save(task);

        Project project = projectService.findOne(design.getProjectId());
        if (project != null && Boolean.TRUE.equals(project.getAutoRunCodingTask())) {
            executionService.execute(task.getId(), null);
        }
        return task;
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
        if (task.getStatus() == CodingTaskStatus.RUNNING) {
            return ResultData.fail("任务正在执行中");
        }
        if (task.getStatus() == CodingTaskStatus.PENDING) {
            return ResultData.fail("请先运行任务");
        }
        if (task.getStatus() == CodingTaskStatus.STALE) {
            return ResultData.fail("任务已过期，无法重跑");
        }
        return executionService.execute(id, rerunPrompt);
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
            active.setState(RunState.CANCELLED);
            active.setFinishedDate(new Date());
            runDao.save(active);
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
        dto.setDetailedDesignId(task.getDetailedDesignId());
        dto.setDetailedDesignVersion(task.getDetailedDesignVersion());
        dto.setStatus(task.getStatus());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setFileScope(task.getFileScope());
        dto.setFailureSummary(task.getFailureSummary());
        dto.setCreatedDate(task.getCreatedDate());
        dto.setLastEditedDate(task.getLastEditedDate());
        return dto;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
