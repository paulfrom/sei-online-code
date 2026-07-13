package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 需求自动化编排服务（PM Orchestrator 集成入口）。
 *
 * <p>负责在 PM 执行计划生成成功后持久化任务并启动调度器。</p>
 */
@Service
public class RequirementAutomationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementAutomationService.class);

    private final RequirementDao requirementDao;
    private final CodingTaskDao codingTaskDao;
    private final CodingTaskScheduler codingTaskScheduler;

    public RequirementAutomationService(RequirementDao requirementDao,
                                        CodingTaskDao codingTaskDao,
                                        CodingTaskScheduler codingTaskScheduler) {
        this.requirementDao = requirementDao;
        this.codingTaskDao = codingTaskDao;
        this.codingTaskScheduler = codingTaskScheduler;
    }

    /**
     * PM 执行计划生成成功后的持久化入口。
     *
     * @param requirementId   需求 ID
     * @param executionPlanId 执行计划 ID
     * @param loopId          当前循环 ID
     * @param projectId       项目 ID
     * @param planTasks       计划任务列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistSuccess(String requirementId, String executionPlanId, String loopId,
                               String projectId, List<PlanTask> planTasks) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            LOGGER.warn("persistSuccess: requirement not found {}", requirementId);
            return;
        }
        requirement.setActiveLoopId(loopId);
        requirementDao.save(requirement);

        createCodingTasks(requirementId, executionPlanId, loopId, projectId, planTasks);
        codingTaskScheduler.schedule(requirementId);
    }

    private void createCodingTasks(String requirementId, String executionPlanId, String loopId,
                                   String projectId, List<PlanTask> planTasks) {
        for (PlanTask planTask : planTasks) {
            CodingTask task = new CodingTask();
            task.setProjectId(projectId);
            task.setRequirementId(requirementId);
            task.setExecutionPlanId(executionPlanId);
            task.setPlanTaskKey(planTask.taskKey());
            task.setTitle(planTask.title());
            task.setDescription(planTask.description());
            task.setAssignedAgent(planTask.agent());
            task.setArea(planTask.area());
            task.setDependsOn(planTask.dependsOn());
            task.setFileScope(planTask.fileScope());
            task.setLoopId(loopId);
            task.setStatus(CodingTaskStatus.PENDING);
            task.setDetailedDesignVersion(1);
            codingTaskDao.save(task);
        }
    }

    /**
     * 执行计划任务描述。
     */
    public record PlanTask(String taskKey,
                           String title,
                           String description,
                           String agent,
                           String area,
                           List<String> dependsOn,
                           List<String> fileScope) {
        public PlanTask {
            Objects.requireNonNull(taskKey, "taskKey");
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            fileScope = fileScope == null ? List.of() : List.copyOf(fileScope);
        }
    }
}
