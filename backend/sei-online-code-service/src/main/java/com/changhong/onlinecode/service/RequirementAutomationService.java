package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.utils.TransactionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    private ExecutionPlanDao executionPlanDao;
    private RequirementCommentService requirementCommentService;
    private RequirementDesignContextService requirementDesignContextService;
    private RunDao runDao;
    private RequirementDeliveryService requirementDeliveryService;
    private ObjectMapper objectMapper = new ObjectMapper();

    public RequirementAutomationService(RequirementDao requirementDao,
                                        CodingTaskDao codingTaskDao,
                                        CodingTaskScheduler codingTaskScheduler) {
        this.requirementDao = requirementDao;
        this.codingTaskDao = codingTaskDao;
        this.codingTaskScheduler = codingTaskScheduler;
    }

    @Autowired
    public void setOptionalDependencies(ExecutionPlanDao executionPlanDao,
                                        RequirementCommentService requirementCommentService,
                                        RequirementDesignContextService requirementDesignContextService,
                                        RunDao runDao,
                                        RequirementDeliveryService requirementDeliveryService,
                                        ObjectMapper objectMapper) {
        this.executionPlanDao = executionPlanDao;
        this.requirementCommentService = requirementCommentService;
        this.requirementDesignContextService = requirementDesignContextService;
        this.runDao = runDao;
        this.requirementDeliveryService = requirementDeliveryService;
        this.objectMapper = objectMapper;
    }

    /**
     * PRD 确认后的自动化入口。当前实现生成结构化初始计划并启动调度；
     * PM agent JSON 直连失败时也能保留可追踪计划和评论。
     */
    @Transactional(rollbackFor = Exception.class)
    public void startInitialLoop(String requirementId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            LOGGER.warn("startInitialLoop: requirement not found {}", requirementId);
            return;
        }
        startLoop(requirement, ExecutionPlanType.INITIAL, "PRD 已确认，启动 PM 初始执行计划。");
    }

    @Transactional(rollbackFor = Exception.class)
    public RequirementComment handleHumanComment(String requirementId, String content, String metadataJson) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        RequirementComment comment = appendComment(requirementId, requirement.getActiveLoopId(),
                RequirementCommentAuthorType.HUMAN, "human", RequirementCommentType.HUMAN_FEEDBACK,
                content, metadataJson);

        if (isActive(requirement.getAutomationStatus())) {
            interruptActiveLoop(requirement, comment);
            startLoop(requirement, ExecutionPlanType.CHANGE_REQUEST, "人类评论中断当前自动化，PM 基于完整评论历史重规划。");
        } else if (requirement.getAutomationStatus() == RequirementAutomationStatus.COMPLETED) {
            startLoop(requirement, ExecutionPlanType.CHANGE_REQUEST, "已完成需求收到人类评论，启动 CHANGE_REQUEST loop。");
        }
        return comment;
    }

    @Transactional(rollbackFor = Exception.class)
    public void onPlanTasksSettled(String requirementId) {
        if (executionPlanDao == null) {
            return;
        }
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null || requirement.getActiveLoopId() == null) {
            return;
        }
        ExecutionPlan plan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirementId, requirement.getActiveLoopId());
        if (plan == null || plan.getStatus() == ExecutionPlanStatus.ACCEPTED
                || plan.getStatus() == ExecutionPlanStatus.FAILED
                || plan.getStatus() == ExecutionPlanStatus.INTERRUPTED) {
            return;
        }
        List<CodingTask> tasks = codingTaskDao.findByRequirementId(requirementId).stream()
                .filter(t -> Objects.equals(t.getExecutionPlanId(), plan.getId()))
                .toList();
        if (tasks.isEmpty() || tasks.stream().anyMatch(t -> !isTerminal(t.getStatus()))) {
            return;
        }

        requirement.setAutomationStatus(RequirementAutomationStatus.ACCEPTING);
        requirementDao.save(requirement);
        plan.setStatus(ExecutionPlanStatus.ACCEPTING);
        executionPlanDao.save(plan);

        boolean accepted = tasks.stream().allMatch(t -> t.getStatus() == CodingTaskStatus.SUCCEEDED);
        appendComment(requirementId, requirement.getActiveLoopId(),
                RequirementCommentAuthorType.TEST_AGENT, "test-agent", RequirementCommentType.VALIDATION_RESULT,
                accepted ? "计划级验证通过：所有任务级验证均已通过。" : "计划级验证未通过：存在失败、阻塞或过期任务。",
                validationMetadata(tasks));
        if (accepted) {
            appendComment(requirementId, requirement.getActiveLoopId(),
                    RequirementCommentAuthorType.PM_AGENT, "pm-agent", RequirementCommentType.ACCEPTANCE,
                    "PM 验收通过，进入 GitLab MR 交付。", "{\"accepted\":true}");
            plan.setStatus(ExecutionPlanStatus.ACCEPTED);
            requirement.setAcceptedAt(new Date());
            requirement.setAcceptedByAgent("pm-agent");
            requirement.setAutomationStatus(RequirementAutomationStatus.DELIVERING);
            executionPlanDao.save(plan);
            requirementDao.save(requirement);
            if (requirementDeliveryService != null) {
                TransactionUtil.afterCommit(() -> requirementDeliveryService.deliver(requirementId, plan.getId()));
            }
        } else {
            appendComment(requirementId, requirement.getActiveLoopId(),
                    RequirementCommentAuthorType.PM_AGENT, "pm-agent", RequirementCommentType.REMEDIATION,
                    "PM 验收未通过；当前骨架停止在人工处理状态，后续可基于失败任务生成补救计划。",
                    "{\"accepted\":false}");
            plan.setStatus(ExecutionPlanStatus.NEEDS_REMEDIATION);
            requirement.setAutomationStatus(RequirementAutomationStatus.WAITING_HUMAN);
            executionPlanDao.save(plan);
            requirementDao.save(requirement);
        }
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
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        requirementDao.save(requirement);

        createCodingTasks(requirementId, executionPlanId, loopId, projectId, planTasks);
        codingTaskScheduler.schedule(requirementId);
    }

    private void startLoop(Requirement requirement, ExecutionPlanType planType, String summary) {
        if (executionPlanDao == null || requirementCommentService == null) {
            LOGGER.warn("startLoop skipped because automation dependencies are not initialized");
            return;
        }
        String loopId = newLoopId();
        requirement.setActiveLoopId(loopId);
        requirement.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        requirementDao.save(requirement);

        RequirementDesignContext context = prepareContext(requirement);
        List<PlanTask> planTasks = defaultPlanTasks(requirement);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setRequirementId(requirement.getId());
        plan.setLoopId(loopId);
        plan.setVersion(nextPlanVersion(requirement.getId()));
        plan.setPlanType(planType);
        plan.setStatus(ExecutionPlanStatus.READY);
        plan.setSummary(summary);
        plan.setCreatedByAgent("pm-agent");
        if (context != null) {
            plan.setMemoryContextId(context.getId());
            plan.setWorkspaceMemoryId(context.getWorkspaceMemoryId());
        }
        plan.setPlanJson(planJson(requirement, planTasks, planType));
        executionPlanDao.save(plan);

        appendComment(requirement.getId(), loopId, RequirementCommentAuthorType.PM_AGENT, "pm-agent",
                RequirementCommentType.EXECUTION_PLAN, summary + "\n\n" + renderTasks(planTasks),
                "{\"executionPlanId\":\"" + plan.getId() + "\",\"planType\":\"" + planType + "\"}");
        createCodingTasks(requirement.getId(), plan.getId(), loopId, requirement.getProjectId(), planTasks);
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        executionPlanDao.save(plan);
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        requirementDao.save(requirement);
        TransactionUtil.afterCommit(() -> codingTaskScheduler.schedule(requirement.getId()));
    }

    private void interruptActiveLoop(Requirement requirement, RequirementComment comment) {
        if (executionPlanDao != null && requirement.getActiveLoopId() != null) {
            ExecutionPlan plan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                    requirement.getId(), requirement.getActiveLoopId());
            if (plan != null) {
                plan.setStatus(ExecutionPlanStatus.INTERRUPTED);
                executionPlanDao.save(plan);
            }
        }
        if (runDao != null) {
            for (Run run : runDao.findByRequirementIdAndState(requirement.getId(), RunState.RUNNING)) {
                run.setCancelRequested(Boolean.TRUE);
                run.setInvalidatedByCommentId(comment.getId());
                runDao.save(run);
            }
        }
        requirement.setAutomationStatus(RequirementAutomationStatus.INTERRUPTED);
        requirement.setActiveLoopId(newLoopId());
        requirementDao.save(requirement);
        appendComment(requirement.getId(), requirement.getActiveLoopId(),
                RequirementCommentAuthorType.SYSTEM, "system", RequirementCommentType.INTERRUPTION,
                "人类评论已中断活跃自动化，旧 loop 的结果将被视为过期。",
                "{\"commentId\":\"" + comment.getId() + "\"}");
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

    private RequirementDesignContext prepareContext(Requirement requirement) {
        if (requirementDesignContextService == null) {
            return null;
        }
        try {
            RequirementDesignContext current = requirementDesignContextService.findCurrentByRequirement(requirement.getId());
            if (current != null && current.getContextStatus() == RequirementDesignContextStatus.READY) {
                return current;
            }
            return requirementDesignContextService.prepare(requirement.getId());
        } catch (Exception e) {
            appendComment(requirement.getId(), requirement.getActiveLoopId(),
                    RequirementCommentAuthorType.SYSTEM, "system", RequirementCommentType.CONTEXT_SUMMARY_FAILED,
                    "准备需求上下文失败：" + e.getMessage(), null);
            return null;
        }
    }

    private RequirementComment appendComment(String requirementId, String loopId,
                                             RequirementCommentAuthorType authorType,
                                             String authorName,
                                             RequirementCommentType commentType,
                                             String content,
                                             String metadataJson) {
        if (requirementCommentService == null) {
            return null;
        }
        return requirementCommentService.append(requirementId, loopId, authorType, authorName,
                commentType, content, metadataJson);
    }

    private int nextPlanVersion(String requirementId) {
        ExecutionPlan latest = executionPlanDao.findTopByRequirementIdOrderByVersionDesc(requirementId);
        return latest == null ? 1 : Objects.requireNonNullElse(latest.getVersion(), 0) + 1;
    }

    private List<PlanTask> defaultPlanTasks(Requirement requirement) {
        List<PlanTask> tasks = new ArrayList<>();
        String text = (Objects.toString(requirement.getTitle(), "") + "\n"
                + Objects.toString(requirement.getDescription(), "") + "\n"
                + Objects.toString(requirement.getPrdContent(), "")).toLowerCase();
        boolean frontend = containsAny(text, "frontend", "前端", "页面", "ui", "react", "umi", "suid");
        boolean backend = containsAny(text, "backend", "后端", "接口", "api", "数据库", "spring", "java");
        if (!frontend && !backend) {
            backend = true;
        }
        if (backend) {
            tasks.add(new PlanTask("BE-001", "实现后端能力",
                    "根据 PRD 实现后端领域模型、接口、服务逻辑、持久化和必要测试。",
                    "backend-dev-agent", "backend", List.of(), List.of("backend/")));
        }
        if (frontend) {
            tasks.add(new PlanTask("FE-001", "实现前端交互",
                    "根据 PRD 实现前端页面、服务调用、状态展示和必要校验。",
                    "frontend-dev-agent", "frontend", backend ? List.of("BE-001") : List.of(), List.of("frontend/")));
        }
        return tasks;
    }

    private String planJson(Requirement requirement, List<PlanTask> tasks, ExecutionPlanType planType) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("goal", requirement.getTitle());
            root.put("planType", planType.name());
            root.put("tasks", tasks.stream().map(task -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("taskKey", task.taskKey());
                item.put("title", task.title());
                item.put("description", task.description());
                item.put("agent", task.agent());
                item.put("area", task.area());
                item.put("dependsOn", task.dependsOn());
                item.put("fileScope", task.fileScope());
                item.put("acceptanceCriteria", List.of("开发执行成功", "任务级验证通过"));
                return item;
            }).toList());
            root.put("risks", List.of("当前 PM 计划为服务端可执行骨架，复杂拆分仍需 PM agent JSON 直连增强"));
            root.put("validation", Map.of("commands", List.of("full-stack")));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"goal\":\"" + requirement.getTitle() + "\",\"tasks\":[]}";
        }
    }

    private String validationMetadata(List<CodingTask> tasks) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tasks", tasks.stream().map(t -> Map.of(
                            "taskKey", Objects.toString(t.getPlanTaskKey(), ""),
                            "status", t.getStatus().name(),
                            "failureSummary", Objects.toString(t.getFailureSummary(), "")
                    )).toList()));
        } catch (Exception e) {
            return null;
        }
    }

    private String renderTasks(List<PlanTask> tasks) {
        StringBuilder sb = new StringBuilder();
        for (PlanTask task : tasks) {
            sb.append("- ").append(task.taskKey()).append(" [").append(task.area()).append("] ")
                    .append(task.title()).append(" -> ").append(task.agent()).append('\n');
        }
        return sb.toString();
    }

    private boolean isTerminal(CodingTaskStatus status) {
        return status == CodingTaskStatus.SUCCEEDED
                || status == CodingTaskStatus.FAILED
                || status == CodingTaskStatus.VALIDATION_FAILED
                || status == CodingTaskStatus.CANCELLED
                || status == CodingTaskStatus.STALE
                || status == CodingTaskStatus.BLOCKED;
    }

    private boolean isActive(RequirementAutomationStatus status) {
        return status == RequirementAutomationStatus.PLANNING
                || status == RequirementAutomationStatus.DEVELOPING
                || status == RequirementAutomationStatus.VALIDATING
                || status == RequirementAutomationStatus.ACCEPTING;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String newLoopId() {
        return UUID.randomUUID().toString().replace("-", "");
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
