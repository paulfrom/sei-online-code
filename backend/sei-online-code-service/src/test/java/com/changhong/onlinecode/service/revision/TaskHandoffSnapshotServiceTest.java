package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.TaskHandoffSnapshotDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskHandoffSnapshot;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.service.memory.CodingTaskChangeResult;
import com.changhong.onlinecode.service.progress.ProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TaskHandoffSnapshotService 的任务现场采集与安全边界测试。 */
class TaskHandoffSnapshotServiceTest {

    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private TaskHandoffSnapshotDao snapshotDao;
    private CodingTaskChangeCollector changeCollector;
    private ProgressService progressService;
    private TaskHandoffSnapshotService service;

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        snapshotDao = mock(TaskHandoffSnapshotDao.class);
        changeCollector = mock(CodingTaskChangeCollector.class);
        progressService = mock(ProgressService.class);
        service = new TaskHandoffSnapshotService(codingTaskDao, runDao, snapshotDao, changeCollector,
                progressService, new TaskHandoffSnapshotSummaryAssembler());
        when(snapshotDao.saveAndFlush(any(TaskHandoffSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void capture_runningRun_collectsWorkspaceAndProgress() {
        CodingTask task = task("task-1");
        Run run = run("run-1", RunState.RUNNING, 1_000L);
        run.setExecutionId("execution-1");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(run));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 2L)).thenReturn(Optional.empty());
        when(changeCollector.collect("/worktree", "base-1")).thenReturn(changes());
        ExecutionProgressSnapshot progress = new ExecutionProgressSnapshot();
        progress.setExecutionId("execution-1");
        progress.setNextAction("continue");
        when(progressService.generateSnapshot("execution-1")).thenReturn(progress);

        TaskHandoffSnapshotService.CaptureResult result = service.capture("req-1", "task-1", 2L, "comment-1");

        assertEquals("run-1", result.snapshot().getRunId());
        assertEquals("head-1", result.snapshot().getHeadCommit());
        assertTrue(result.snapshot().getChangedFilesJson().contains("backend/A.java"));
        assertTrue(result.snapshot().getProgressSnapshotJson().contains("execution-1"));
        assertEquals("RUNNING", result.summary().runState());
        assertEquals(List.of("backend/A.java"), result.summary().changedFiles());
    }

    @Test
    void capture_choosesLatestSucceededRunDeterministically() {
        CodingTask task = task("task-1");
        Run olderRunning = run("run-z", RunState.RUNNING, 1_000L);
        Run latestSucceeded = run("run-a", RunState.SUCCEEDED, 2_000L);
        latestSucceeded.setSummary("done");
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(latestSucceeded, olderRunning));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 3L)).thenReturn(Optional.empty());
        when(changeCollector.collect("/worktree", "base-1")).thenReturn(changes());

        TaskHandoffSnapshotService.CaptureResult result = service.capture("req-1", "task-1", 3L, "comment-1");

        assertEquals("run-a", result.snapshot().getRunId());
        assertEquals("SUCCEEDED", result.summary().runState());
        assertEquals("done", result.summary().runSummary());
    }

    @Test
    void capture_withoutRun_persistsEmptySnapshot() {
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of());
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 4L)).thenReturn(Optional.empty());

        TaskHandoffSnapshotService.CaptureResult result = service.capture("req-1", "task-1", 4L, "comment-1");

        assertNull(result.snapshot().getRunId());
        assertEquals("[]", result.snapshot().getChangedFilesJson());
        assertTrue(result.summary().changedFiles().isEmpty());
        verify(changeCollector, never()).collect(any(), any());
    }

    @Test
    void capture_repeatedCallReturnsExistingSnapshotWithoutCollection() {
        TaskHandoffSnapshot existing = new TaskHandoffSnapshot();
        existing.setCodingTaskId("task-1");
        existing.setRequirementId("req-1");
        existing.setRevisionSeq(5L);
        existing.setChangedFilesJson("[]");
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 5L)).thenReturn(Optional.of(existing));

        TaskHandoffSnapshotService.CaptureResult result = service.capture("req-1", "task-1", 5L, "comment-1");

        assertSame(existing, result.snapshot());
        verify(snapshotDao, never()).saveAndFlush(any());
        verify(runDao, never()).findByCodingTaskId(any());
        verify(changeCollector, never()).collect(any(), any());
    }

    @Test
    void refreshUpdatesExistingSnapshotWithLatestRunState() {
        TaskHandoffSnapshot existing = new TaskHandoffSnapshot();
        existing.setCodingTaskId("task-1");
        existing.setRequirementId("req-1");
        existing.setRevisionSeq(5L);
        existing.setRunSummary("old");
        existing.setChangedFilesJson("[]");
        Run latest = run("run-latest", RunState.RUNNING, 3_000L);
        latest.setSummary("new progress");
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 5L)).thenReturn(Optional.of(existing));
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(latest));
        when(changeCollector.collect("/worktree", "base-1")).thenReturn(changes());

        TaskHandoffSnapshotService.CaptureResult result = service.refresh(
                "req-1", "task-1", 5L, "comment-2");

        assertSame(existing, result.snapshot());
        assertEquals("run-latest", existing.getRunId());
        assertEquals("new progress", existing.getRunSummary());
        assertEquals("comment-2", existing.getTriggerCommentId());
        assertEquals("head-1", existing.getHeadCommit());
        verify(snapshotDao).saveAndFlush(existing);
    }

    @Test
    void capture_collectionFailureStillPersistsAndDoesNotRequestProgressWithoutExecution() {
        Run run = run("run-1", RunState.RUNNING, 1_000L);
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(run));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 6L)).thenReturn(Optional.empty());
        when(changeCollector.collect("/worktree", "base-1"))
                .thenReturn(CodingTaskChangeResult.failure("git failed: token=must-not-persist"));

        TaskHandoffSnapshotService.CaptureResult result = service.capture("req-1", "task-1", 6L, "comment-1");

        assertEquals("[]", result.snapshot().getChangedFilesJson());
        assertNull(result.snapshot().getDiffSummary());
        verify(snapshotDao).saveAndFlush(result.snapshot());
        verify(progressService, never()).generateSnapshot(any());
    }

    @Test
    void capture_truncatesFieldsAtEntityLimits() {
        Run run = run("run-1", RunState.SUCCEEDED, 1_000L);
        run.setSummary("s".repeat(TaskHandoffSnapshot.MAX_RUN_SUMMARY_LENGTH + 100));
        CodingTaskChangeResult changes = changes();
        changes.setDiffStat("d".repeat(TaskHandoffSnapshot.MAX_DIFF_STAT_LENGTH + 100));
        changes.setDiffSummary("x".repeat(TaskHandoffSnapshot.MAX_DIFF_SUMMARY_LENGTH + 100));
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(run));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 7L)).thenReturn(Optional.empty());
        when(changeCollector.collect("/worktree", "base-1")).thenReturn(changes);

        TaskHandoffSnapshot snapshot = service.capture("req-1", "task-1", 7L, "comment-1").snapshot();

        assertEquals(TaskHandoffSnapshot.MAX_RUN_SUMMARY_LENGTH, codePoints(snapshot.getRunSummary()));
        assertEquals(TaskHandoffSnapshot.MAX_DIFF_STAT_LENGTH, codePoints(snapshot.getDiffStat()));
        assertEquals(TaskHandoffSnapshot.MAX_DIFF_SUMMARY_LENGTH, codePoints(snapshot.getDiffSummary()));
        assertTrue(snapshot.getDiffSummary().endsWith("...[TRUNCATED]"));
    }

    @Test
    void capture_truncatesChangedFilesAsValidJson() throws Exception {
        Run run = run("run-1", RunState.RUNNING, 1_000L);
        CodingTaskChangeResult changes = changes();
        List<String> files = new ArrayList<>();
        for (int index = 0; index < 2_000; index++) {
            files.add("backend/very-long-directory-name/ChangedFile" + index + "WithLongName.java");
        }
        changes.setChangedFiles(files);
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(run));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 9L)).thenReturn(Optional.empty());
        when(changeCollector.collect("/worktree", "base-1")).thenReturn(changes);

        String json = service.capture("req-1", "task-1", 9L, "comment-1")
                .snapshot().getChangedFilesJson();

        assertTrue(codePoints(json) <= TaskHandoffSnapshot.MAX_CHANGED_FILES_JSON_LENGTH);
        assertTrue(com.changhong.sei.core.util.JsonUtils.mapper().readTree(json).isArray());
        assertTrue(com.changhong.sei.core.util.JsonUtils.mapper().readTree(json).size() < files.size());
    }

    @Test
    void capture_redactsSecretsAndDoesNotCopyRawLogsOrSensitiveFileNames() {
        Run run = run("run-1", RunState.RUNNING, 1_000L);
        run.setSummary("password=super-secret Bearer abcdefghijklmnop");
        run.setRawUsageJson("RAW_LOG_MUST_NOT_APPEAR");
        run.setUserPrompt("PROMPT_MUST_NOT_APPEAR");
        CodingTaskChangeResult changes = changes();
        changes.setChangedFiles(List.of(".env", "backend/A.java", "private.key"));
        changes.setDiffStat("api_key=top-secret");
        changes.setDiffSummary("authorization: abcdefghijklmnop AKIAABCDEFGHIJKLMNOP");
        when(codingTaskDao.findOne("task-1")).thenReturn(task("task-1"));
        when(runDao.findByCodingTaskId("task-1")).thenReturn(List.of(run));
        when(snapshotDao.findByCodingTaskIdAndRevisionSeq("task-1", 8L)).thenReturn(Optional.empty());
        when(changeCollector.collect("/worktree", "base-1")).thenReturn(changes);

        TaskHandoffSnapshot snapshot = service.capture("req-1", "task-1", 8L, "comment-1").snapshot();
        String persisted = snapshot.getChangedFilesJson() + snapshot.getDiffStat()
                + snapshot.getDiffSummary() + snapshot.getRunSummary();

        assertFalse(persisted.contains("super-secret"));
        assertFalse(persisted.contains("top-secret"));
        assertFalse(persisted.contains("abcdefghijklmnop"));
        assertFalse(persisted.contains("AKIAABCDEFGHIJKLMNOP"));
        assertFalse(persisted.contains(".env"));
        assertFalse(persisted.contains("private.key"));
        assertFalse(persisted.contains("RAW_LOG_MUST_NOT_APPEAR"));
        assertFalse(persisted.contains("PROMPT_MUST_NOT_APPEAR"));
        assertTrue(persisted.contains("[REDACTED]"));
    }

    private CodingTask task(String id) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setRequirementId("req-1");
        return task;
    }

    private Run run(String id, RunState state, long createdAt) {
        Run run = new Run();
        run.setId(id);
        run.setCodingTaskId("task-1");
        run.setState(state);
        run.setCreatedDate(new Date(createdAt));
        run.setWorktreePath("/worktree");
        run.setBaseCommit("base-1");
        return run;
    }

    private CodingTaskChangeResult changes() {
        CodingTaskChangeResult changes = new CodingTaskChangeResult();
        changes.setSuccess(true);
        changes.setHeadCommit("head-1");
        changes.setBaseCommit("base-1");
        changes.setChangedFiles(List.of("backend/A.java"));
        changes.setDiffStat("1 file changed");
        changes.setDiffSummary("diff --git a/backend/A.java b/backend/A.java");
        return changes;
    }

    private int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
