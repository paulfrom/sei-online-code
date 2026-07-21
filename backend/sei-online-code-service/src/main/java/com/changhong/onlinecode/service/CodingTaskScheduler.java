package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.service.memory.CodingTaskChangeResult;
import com.changhong.onlinecode.service.validation.ValidationLoopService;
import com.changhong.sei.core.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * CodingTask 调度器。
 *
 * <p>按 DAG 依赖、fileScope 冲突、前后端 lane 并发限制执行任务。
 * 验收必须作为 PM 计划中的显式 {@code test-agent} 任务运行，不在每个开发任务后隐式触发。</p>
 */
@Service
@Slf4j
@AllArgsConstructor
public class CodingTaskScheduler {

    private static final Set<CodingTaskStatus> ACTIVE_STATUSES = EnumSet.of(
            CodingTaskStatus.RUNNING, CodingTaskStatus.VALIDATING);
    private static final Set<CodingTaskStatus> TERMINAL_STATUSES = EnumSet.of(
            CodingTaskStatus.SUCCEEDED, CodingTaskStatus.FAILED,
            CodingTaskStatus.VALIDATION_FAILED, CodingTaskStatus.CANCELLED, CodingTaskStatus.STALE);
    private static final Set<CodingTaskStatus> FAILURE_STATUSES = EnumSet.of(
            CodingTaskStatus.FAILED, CodingTaskStatus.VALIDATION_FAILED,
            CodingTaskStatus.CANCELLED, CodingTaskStatus.STALE);

    private final CodingTaskDao codingTaskDao;
    private final RequirementDao requirementDao;
    private final CodingTaskExecutionService executionService;
    private final RunDao runDao;
    private final CodingTaskChangeCollector changeCollector;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, ReentrantLock> requirementLocks = new ConcurrentHashMap<>();
    private final RequirementCommentService requirementCommentService;
    private final ValidationLoopService validationLoopService;


    /**
     * 重新调度指定需求下的所有 CodingTask。
     *
     * <p>可重入：同一 demand 的多次并发调用会被串行化。</p>
     *
     * @param requirementId 需求 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void schedule(String requirementId) {
        if (requirementId == null || requirementId.isBlank()) {
            return;
        }
        ReentrantLock lock = requirementLocks.computeIfAbsent(requirementId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.debug("schedule skipped because another pass is running for requirement {}", requirementId);
            return;
        }
        try {
            doSchedule(requirementId);
        } finally {
            lock.unlock();
        }
    }

    protected void doSchedule(String requirementId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            log.warn("schedule: requirement not found {}", requirementId);
            return;
        }
        String currentLoopId = requirement.getActiveLoopId();

        List<CodingTask> allTasks = codingTaskDao.findByRequirementId(requirementId);

        // 1. 过期 loopId 任务标记 STALE
        for (CodingTask task : allTasks) {
            if (task.getLoopId() != null && !Objects.equals(task.getLoopId(), currentLoopId)
                    && task.getStatus() != CodingTaskStatus.STALE) {
                task.setStatus(CodingTaskStatus.STALE);
                codingTaskDao.save(task);
            }
        }

        // 后续依赖解析与调度只能使用当前 loop。历史 loop 可能存在相同 planTaskKey，
        // 若混入依赖图会把当前已成功依赖误判为旧任务的 STALE 状态。
        List<CodingTask> tasks = allTasks.stream()
                .filter(task -> Objects.equals(task.getLoopId(), currentLoopId))
                .toList();
        Map<String, CodingTask> taskByKey = tasks.stream()
                .filter(task -> task.getPlanTaskKey() != null)
                .collect(Collectors.toMap(CodingTask::getPlanTaskKey, task -> task));

        // 2. 收集当前占用 lane 和 fileScope 的活动任务
        List<CodingTask> activeTasks = tasks.stream()
                .filter(t -> ACTIVE_STATUSES.contains(t.getStatus()))
                .collect(Collectors.toList());
        Set<String> occupiedAreas = activeTasks.stream()
                .map(CodingTask::getArea)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<FileScope> occupiedScopes = new ArrayList<>(activeTasks.stream()
                .map(FileScope::of)
                .filter(s -> !s.paths().isEmpty())
                .toList());

        // 3. 遍历候选任务
        for (CodingTask task : tasks) {
            if (TERMINAL_STATUSES.contains(task.getStatus()) || ACTIVE_STATUSES.contains(task.getStatus())) {
                continue;
            }
            if (task.getArea() == null || task.getArea().isBlank()) {
                continue;
            }

            List<String> deps = task.getDependsOn() == null ? List.of() : task.getDependsOn();
            boolean depsSatisfied = deps.stream().allMatch(depKey -> {
                CodingTask dep = taskByKey.get(depKey);
                return dep != null && dep.getStatus() == CodingTaskStatus.SUCCEEDED;
            });
            boolean depsFailed = deps.stream().anyMatch(depKey -> {
                CodingTask dep = taskByKey.get(depKey);
                return dep != null && FAILURE_STATUSES.contains(dep.getStatus());
            });

            if (depsFailed) {
                if (task.getStatus() != CodingTaskStatus.BLOCKED) {
                    task.setStatus(CodingTaskStatus.BLOCKED);
                    codingTaskDao.save(task);
                }
                continue;
            }
            if (!depsSatisfied) {
                continue;
            }

            // BLOCKED is reserved for failed dependencies. Once dependencies recover,
            // return the task to PENDING before applying transient lane/scope limits.
            if (task.getStatus() == CodingTaskStatus.BLOCKED) {
                task.setStatus(CodingTaskStatus.PENDING);
                codingTaskDao.save(task);
            }

            if (occupiedAreas.contains(task.getArea())) {
                continue;
            }

            FileScope candidateScope = FileScope.of(task);
            if (candidateScope.conflictsWith(occupiedScopes)) {
                continue;
            }

            // 启动任务
            if (isValidationTask(task)) {
                executeValidationTask(task);
            } else {
                String prompt = buildPrompt(task);
                executionService.executePlanTask(task.getId(), task.getAssignedAgent(), prompt);
            }
            occupiedAreas.add(task.getArea());
            occupiedScopes.add(candidateScope);
        }
        eventPublisher.publishEvent(new SchedulingPassCompletedEvent(requirementId));
    }

    /** 调度轮次完成事件，由自动化编排边界判断计划是否已全部进入终态。 */
    public record SchedulingPassCompletedEvent(String requirementId) {
    }

    /**
     * 开发 Run 结束后的回调。
     *
     * @param codingTaskId  任务 ID
     * @param success       是否成功
     * @param failureReason 失败原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void onDevelopmentRunFinished(String codingTaskId, boolean success, String failureReason) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (task == null) {
            log.warn("onDevelopmentRunFinished: task not found {}", codingTaskId);
            return;
        }
        Requirement requirement = requirementDao.findOne(task.getRequirementId());
        if (requirement == null) {
            log.warn("onDevelopmentRunFinished: requirement not found {}", task.getRequirementId());
            return;
        }
        if (!Objects.equals(task.getLoopId(), requirement.getActiveLoopId())) {
            log.info("onDevelopmentRunFinished: task {} loopId mismatch, mark stale", codingTaskId);
            task.setStatus(CodingTaskStatus.STALE);
            codingTaskDao.save(task);
            schedule(task.getRequirementId());
            return;
        }
        if (!success) {
            task.setStatus(CodingTaskStatus.FAILED);
            String failure = firstNonBlank(failureReason, "开发执行失败");
            task.setFailureSummary(failure);
            task.setFailureDetail(failure);
            task.setLastFailedAt(new Date());
            codingTaskDao.save(task);
            appendDevResult(task, false, failureReason);
            schedule(task.getRequirementId());
            return;
        }
        appendDevResult(task, true, null);
        task.setStatus(CodingTaskStatus.SUCCEEDED);
        task.setFailureSummary(null);
        task.setFailureDetail(null);
        codingTaskDao.save(task);
        schedule(task.getRequirementId());
    }

    private void executeValidationTask(CodingTask task) {
        if (validationLoopService != null) {
            task.setStatus(CodingTaskStatus.VALIDATING);
            codingTaskDao.save(task);
            ValidationLoopService.ValidationOutcome outcome = validationLoopService.validateTask(task);
            if (!isCurrentLoop(task)) {
                task.setStatus(CodingTaskStatus.STALE);
                codingTaskDao.save(task);
                schedule(task.getRequirementId());
                return;
            }
            if (outcome.passed()) {
                task.setStatus(CodingTaskStatus.SUCCEEDED);
                task.setFailureSummary(null);
                task.setFailureDetail(null);
            } else {
                task.setStatus(CodingTaskStatus.VALIDATION_FAILED);
                task.setFailureSummary("任务级验证失败");
                task.setFailureDetail("详见 VALIDATION_RESULT 评论及 Run 记录");
                task.setLastFailedAt(new Date());
            }
            codingTaskDao.save(task);
            schedule(task.getRequirementId());
            return;
        }

        task.setStatus(CodingTaskStatus.VALIDATION_FAILED);
        task.setFailureSummary("验收任务失败");
        task.setFailureDetail("ValidationLoopService 未注入，无法通过 test-agent 执行验证");
        task.setLastFailedAt(new Date());
        appendValidationUnavailable(task);
        codingTaskDao.save(task);
        schedule(task.getRequirementId());
    }

    private void appendDevResult(CodingTask task, boolean success, String failureReason) {
        if (requirementCommentService == null) {
            return;
        }
        RequirementCommentAuthorType authorType = "frontend".equals(task.getArea())
                ? RequirementCommentAuthorType.FRONTEND_AGENT
                : "backend".equals(task.getArea())
                ? RequirementCommentAuthorType.BACKEND_AGENT
                : RequirementCommentAuthorType.SYSTEM;
        Run run = runDao.findByCodingTaskId(task.getId()).stream()
                .reduce((left, right) -> Objects.requireNonNullElse(left.getRunNo(), 0)
                        > Objects.requireNonNullElse(right.getRunNo(), 0) ? left : right)
                .orElse(null);
        CodingTaskChangeResult changes = run == null ? null
                : changeCollector.collect(run.getWorktreePath(), run.getBaseCommit());
        List<String> changedFiles = changes != null && changes.isSuccess()
                ? changes.getChangedFiles() : List.of();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", task.getId());
        metadata.put("taskKey", task.getPlanTaskKey());
        metadata.put("success", success);
        metadata.put("runId", run == null ? null : run.getId());
        metadata.put("runState", run == null ? null : run.getState());
        metadata.put("changedFiles", changedFiles);
        metadata.put("failureReason", Objects.toString(failureReason, ""));
        metadata.put("remediationHint", success ? "" : "根据失败日志修复后重试");
        requirementCommentService.append(task.getRequirementId(), task.getLoopId(), authorType,
                task.getAssignedAgent(), RequirementCommentType.DEV_RESULT,
                success ? "开发任务完成：" + task.getPlanTaskKey()
                        : "开发任务失败：" + task.getPlanTaskKey() + "\n" + Objects.toString(failureReason, ""),
                toJson(metadata));
    }

    private void appendValidationUnavailable(CodingTask task) {
        if (requirementCommentService == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", "task");
        metadata.put("taskId", task.getId());
        metadata.put("taskKey", task.getPlanTaskKey());
        metadata.put("area", task.getArea());
        metadata.put("passed", false);
        metadata.put("reason", "ValidationLoopService missing");
        requirementCommentService.append(task.getRequirementId(), task.getLoopId(),
                RequirementCommentAuthorType.TEST_AGENT, "test-agent", RequirementCommentType.VALIDATION_RESULT,
                "任务级验证失败：test-agent 验证服务不可用",
                toJson(metadata));
    }

    private boolean isValidationTask(CodingTask task) {
        return "test-agent".equals(task.getAssignedAgent());
    }

    private String toJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (Exception e) {
            log.warn("serialize coding-task comment metadata failed", e);
            return "{}";
        }
    }

    private String buildPrompt(CodingTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("执行任务：").append(task.getPlanTaskKey()).append("\n");
        sb.append("标题：").append(task.getTitle()).append("\n");
        sb.append("描述：").append(task.getDescription()).append("\n");
        if (task.getFileScope() != null && !task.getFileScope().isEmpty()) {
            sb.append("文件范围：").append(String.join(", ", task.getFileScope())).append("\n");
        }
        sb.append("请在已解析的工作区中按上述描述执行编码，只修改任务范围内的文件。");
        return sb.toString();
    }

    private boolean isCurrentLoop(CodingTask task) {
        Requirement current = requirementDao.findOne(task.getRequirementId());
        return current != null && Objects.equals(task.getLoopId(), current.getActiveLoopId());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 文件范围辅助类，支持父子目录冲突检测。
     */
    private record FileScope(List<String> paths) {
        static FileScope of(CodingTask task) {
            if (task.getFileScope() == null) {
                return new FileScope(List.of());
            }
            return new FileScope(task.getFileScope().stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(FileScope::normalize)
                    .toList());
        }

        private static String normalize(String path) {
            String p = path.replace('\\', '/');
            if (p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            return p;
        }

        boolean conflictsWith(List<FileScope> others) {
            if (paths.isEmpty()) {
                return false;
            }
            for (FileScope other : others) {
                for (String a : paths) {
                    for (String b : other.paths) {
                        if (a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
