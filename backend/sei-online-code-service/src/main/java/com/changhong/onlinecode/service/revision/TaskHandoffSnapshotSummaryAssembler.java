package com.changhong.onlinecode.service.revision;

import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskHandoffSnapshot;
import com.changhong.onlinecode.service.revision.contract.PlanRevisionInput;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将持久化的任务交接快照组装成 PM 修订输入。
 */
@Component
public class TaskHandoffSnapshotSummaryAssembler {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    /**
     * 组装不包含日志、提示词和原始 usage 的最小成果摘要。
     *
     * @param snapshot 交接快照
     * @param run      快照关联的 Run；无 Run 时为 null
     * @return PM 可消费的摘要
     */
    public PlanRevisionInput.HandoffSnapshot assemble(TaskHandoffSnapshot snapshot, Run run) {
        if (snapshot == null) {
            throw new IllegalArgumentException("task handoff snapshot is required");
        }
        return new PlanRevisionInput.HandoffSnapshot(
                snapshot.getCodingTaskId(),
                snapshot.getRunId(),
                run == null || run.getState() == null ? null : run.getState().name(),
                snapshot.getRunSummary(),
                parseChangedFiles(snapshot.getChangedFilesJson()),
                snapshot.getDiffSummary(),
                snapshot.getProgressSnapshotJson());
    }

    private List<String> parseChangedFiles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> files = JsonUtils.mapper().readValue(json, STRING_LIST);
            return files == null ? List.of() : List.copyOf(files);
        } catch (Exception ignored) {
            // 历史脏数据不能阻断计划修订；原始 JSON 不进入异常或日志。
            return List.of();
        }
    }
}
