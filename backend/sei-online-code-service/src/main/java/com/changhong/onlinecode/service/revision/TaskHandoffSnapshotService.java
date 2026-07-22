package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.TaskHandoffSnapshotDao;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskHandoffSnapshot;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.service.memory.CodingTaskChangeResult;
import com.changhong.onlinecode.service.progress.ProgressService;
import com.changhong.onlinecode.service.revision.contract.PlanRevisionInput;
import com.changhong.sei.core.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 采集并持久化计划修订所需的任务现场。
 *
 * <p>服务只执行只读 git 命令，不会 reset、checkout 或修改工作区。每个 task/revision 只保存一份快照，
 * 重试直接返回已保存结果。采集异常会降级为空变更信息，确保修订编排可以继续并保留进度账本。</p>
 */
@Service
@Slf4j
@AllArgsConstructor
public class TaskHandoffSnapshotService {

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?-----END [^-\\r\\n]*PRIVATE KEY-----");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization)"
                    + "(\\s*[=:]\\s*[\\\"]?)([^\\s\\\",;'}]+)");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b");

    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final TaskHandoffSnapshotDao snapshotDao;
    private final CodingTaskChangeCollector changeCollector;
    private final ProgressService progressService;
    private final TaskHandoffSnapshotSummaryAssembler summaryAssembler;

    /**
     * 创建任务现场快照，或在相同任务/修订重复调用时返回原快照。
     */
    public CaptureResult capture(String requirementId,
                                 String taskId,
                                 long revisionSeq,
                                 String triggerCommentId) {
        validateInput(requirementId, taskId, revisionSeq, triggerCommentId);
        CodingTask task = codingTaskDao.findOne(taskId);
        if (task == null) {
            throw new IllegalArgumentException("编码任务不存在: " + taskId);
        }
        if (!requirementId.equals(task.getRequirementId())) {
            throw new IllegalArgumentException("编码任务不属于需求: " + taskId);
        }

        Optional<TaskHandoffSnapshot> existing = snapshotDao.findByCodingTaskIdAndRevisionSeq(taskId, revisionSeq);
        if (existing.isPresent()) {
            return toResult(existing.get());
        }

        Run run = latestRun(runDao.findByCodingTaskId(taskId));
        TaskHandoffSnapshot snapshot = new TaskHandoffSnapshot();
        snapshot.setRequirementId(requirementId);
        snapshot.setCodingTaskId(taskId);
        snapshot.setRunId(run == null ? null : run.getId());
        snapshot.setRevisionSeq(revisionSeq);
        snapshot.setTriggerCommentId(triggerCommentId);
        snapshot.setChangedFilesJson("[]");

        if (run != null) {
            snapshot.setBaseCommit(run.getBaseCommit());
            snapshot.setRunSummary(sanitizeAndTruncate(run.getSummary(),
                    TaskHandoffSnapshot.MAX_RUN_SUMMARY_LENGTH));
            captureChanges(snapshot, run);
            captureProgress(snapshot, run);
        }

        try {
            TaskHandoffSnapshot saved = snapshotDao.saveAndFlush(snapshot);
            return new CaptureResult(saved, summaryAssembler.assemble(saved, run));
        } catch (DataIntegrityViolationException race) {
            // 唯一索引是并发幂等的最终防线；不暴露数据库异常中的参数内容。
            TaskHandoffSnapshot winner = snapshotDao.findByCodingTaskIdAndRevisionSeq(taskId, revisionSeq)
                    .orElseThrow(() -> race);
            return toResult(winner);
        }
    }

    /**
     * 在计划补丁已生成、运行即将取消前刷新同一修订的快照。
     *
     * <p>刷新复用唯一的 task/revision 行，因此重复的修订事件不会制造重复快照；它会重新读取
     * Run、git 差异和进度账本，把规划期间新增的成果也交给替代任务。</p>
     */
    public CaptureResult refresh(String requirementId,
                                 String taskId,
                                 long revisionSeq,
                                 String triggerCommentId) {
        validateInput(requirementId, taskId, revisionSeq, triggerCommentId);
        CodingTask task = codingTaskDao.findOne(taskId);
        if (task == null || !requirementId.equals(task.getRequirementId())) {
            throw new IllegalArgumentException("编码任务不存在或不属于需求: " + taskId);
        }
        TaskHandoffSnapshot snapshot = snapshotDao.findByCodingTaskIdAndRevisionSeq(taskId, revisionSeq)
                .orElseGet(TaskHandoffSnapshot::new);
        Run run = latestRun(runDao.findByCodingTaskId(taskId));
        reset(snapshot, requirementId, taskId, revisionSeq, triggerCommentId, run);
        TaskHandoffSnapshot saved = snapshotDao.saveAndFlush(snapshot);
        return new CaptureResult(saved, summaryAssembler.assemble(saved, run));
    }

    private void reset(TaskHandoffSnapshot snapshot, String requirementId, String taskId,
                       long revisionSeq, String triggerCommentId, Run run) {
        snapshot.setRequirementId(requirementId);
        snapshot.setCodingTaskId(taskId);
        snapshot.setRevisionSeq(revisionSeq);
        snapshot.setTriggerCommentId(triggerCommentId);
        snapshot.setRunId(run == null ? null : run.getId());
        snapshot.setBaseCommit(run == null ? null : run.getBaseCommit());
        snapshot.setHeadCommit(null);
        snapshot.setChangedFilesJson("[]");
        snapshot.setDiffStat(null);
        snapshot.setDiffSummary(null);
        snapshot.setRunSummary(run == null ? null : sanitizeAndTruncate(run.getSummary(),
                TaskHandoffSnapshot.MAX_RUN_SUMMARY_LENGTH));
        snapshot.setProgressSnapshotJson(null);
        if (run != null) {
            captureChanges(snapshot, run);
            captureProgress(snapshot, run);
        }
    }

    private CaptureResult toResult(TaskHandoffSnapshot snapshot) {
        Run run = snapshot.getRunId() == null ? null : runDao.findOne(snapshot.getRunId());
        return new CaptureResult(snapshot, summaryAssembler.assemble(snapshot, run));
    }

    private void captureChanges(TaskHandoffSnapshot snapshot, Run run) {
        if (run.getWorktreePath() == null || run.getWorktreePath().isBlank()) {
            return;
        }
        try {
            CodingTaskChangeResult changes = changeCollector.collect(run.getWorktreePath(), run.getBaseCommit());
            if (changes == null || !changes.isSuccess()) {
                log.warn("task-handoff: git 变更采集失败，已降级为空快照 taskId={}, runId={}",
                        snapshot.getCodingTaskId(), run.getId());
                return;
            }
            snapshot.setHeadCommit(changes.getHeadCommit());
            snapshot.setBaseCommit(changes.getBaseCommit() == null ? run.getBaseCommit() : changes.getBaseCommit());
            List<String> safeFiles = safeChangedFiles(changes.getChangedFiles());
            snapshot.setChangedFilesJson(toBoundedJsonList(safeFiles,
                    TaskHandoffSnapshot.MAX_CHANGED_FILES_JSON_LENGTH));
            snapshot.setDiffStat(sanitizeAndTruncate(changes.getDiffStat(),
                    TaskHandoffSnapshot.MAX_DIFF_STAT_LENGTH));
            snapshot.setDiffSummary(sanitizeAndTruncate(changes.getDiffSummary(),
                    TaskHandoffSnapshot.MAX_DIFF_SUMMARY_LENGTH));
        } catch (RuntimeException exception) {
            log.warn("task-handoff: git 变更采集异常，已降级为空快照 taskId={}, runId={}, type={}",
                    snapshot.getCodingTaskId(), run.getId(), exception.getClass().getSimpleName());
        }
    }

    private void captureProgress(TaskHandoffSnapshot snapshot, Run run) {
        if (run.getExecutionId() == null || run.getExecutionId().isBlank()) {
            return;
        }
        try {
            ExecutionProgressSnapshot progress = progressService.generateSnapshot(run.getExecutionId());
            if (progress != null) {
                snapshot.setProgressSnapshotJson(sanitizeAndTruncate(toJson(progress),
                        TaskHandoffSnapshot.MAX_PROGRESS_SNAPSHOT_JSON_LENGTH));
            }
        } catch (RuntimeException exception) {
            log.warn("task-handoff: 进度快照采集异常，已跳过 taskId={}, runId={}, type={}",
                    snapshot.getCodingTaskId(), run.getId(), exception.getClass().getSimpleName());
        }
    }

    private Run latestRun(List<Run> runs) {
        if (runs == null || runs.isEmpty()) {
            return null;
        }
        Comparator<Run> comparator = Comparator
                .comparing(Run::getCreatedDate, Comparator.nullsFirst(Date::compareTo))
                .thenComparing(Run::getRunNo, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(Run::getId, Comparator.nullsFirst(String::compareTo));
        return runs.stream().filter(java.util.Objects::nonNull).max(comparator).orElse(null);
    }

    private List<String> safeChangedFiles(List<String> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> safe = new LinkedHashSet<>();
        changedFiles.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .filter(path -> !isSensitivePath(path))
                .sorted()
                .forEach(safe::add);
        return new ArrayList<>(safe);
    }

    private boolean isSensitivePath(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return fileName.equals(".env") || fileName.startsWith(".env.")
                || fileName.endsWith(".pem") || fileName.endsWith(".key")
                || fileName.equals("credentials") || fileName.equals("credentials.json")
                || fileName.equals("secrets.yml") || fileName.equals("secrets.yaml");
    }

    private String toJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化任务交接快照", exception);
        }
    }

    /** 保证 changed_files_json 在截断后仍是合法 JSON，而不是切断字符串。 */
    private String toBoundedJsonList(List<String> values, int maxCodePoints) {
        List<String> bounded = new ArrayList<>();
        for (String value : values) {
            bounded.add(value);
            String json = toJson(bounded);
            if (json.codePointCount(0, json.length()) > maxCodePoints) {
                bounded.remove(bounded.size() - 1);
                break;
            }
        }
        return toJson(bounded);
    }

    private String sanitizeAndTruncate(String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }
        String sanitized = PRIVATE_KEY.matcher(value).replaceAll("[REDACTED PRIVATE KEY]");
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = NAMED_SECRET.matcher(sanitized).replaceAll("$1$2[REDACTED]");
        sanitized = AWS_ACCESS_KEY.matcher(sanitized).replaceAll("[REDACTED AWS ACCESS KEY]");
        return truncate(sanitized, maxCodePoints);
    }

    private String truncate(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        String marker = "\n...[TRUNCATED]";
        int contentLimit = Math.max(0, maxCodePoints - marker.codePointCount(0, marker.length()));
        int endIndex = value.offsetByCodePoints(0, contentLimit);
        return value.substring(0, endIndex) + marker;
    }

    private void validateInput(String requirementId, String taskId, long revisionSeq, String triggerCommentId) {
        if (requirementId == null || requirementId.isBlank()) {
            throw new IllegalArgumentException("requirementId 不能为空");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (revisionSeq <= 0) {
            throw new IllegalArgumentException("revisionSeq 必须大于 0");
        }
        if (triggerCommentId == null || triggerCommentId.isBlank()) {
            throw new IllegalArgumentException("triggerCommentId 不能为空");
        }
    }

    /** 快照实体及其最小 PM 摘要。 */
    public record CaptureResult(TaskHandoffSnapshot snapshot, PlanRevisionInput.HandoffSnapshot summary) {
    }
}
