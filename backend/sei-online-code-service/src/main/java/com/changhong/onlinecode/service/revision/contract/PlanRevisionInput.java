package com.changhong.onlinecode.service.revision.contract;

import java.util.List;

/**
 * Persistence-independent snapshot supplied to pm-agent while revising a plan.
 * It deliberately contains the complete comment history, base plan and task handoff results.
 */
public record PlanRevisionInput(
        String requirementId,
        String loopId,
        long revisionSeq,
        String basePlanId,
        int basePlanVersion,
        String title,
        String description,
        String prdContent,
        String basePlanJson,
        List<CommentSnapshot> comments,
        List<TaskSnapshot> tasks,
        List<HandoffSnapshot> handoffs) {

    public PlanRevisionInput {
        comments = comments == null ? List.of() : List.copyOf(comments);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        handoffs = handoffs == null ? List.of() : List.copyOf(handoffs);
    }

    public record CommentSnapshot(String authorType, String commentType, String authorName, String content) {
    }

    public record TaskSnapshot(
            String taskId,
            String taskKey,
            String title,
            String description,
            String assignedAgent,
            String area,
            List<String> dependsOn,
            List<String> fileScope,
            List<String> acceptanceCriteria,
            String status,
            String resultSummary) {

        public TaskSnapshot {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            fileScope = fileScope == null ? List.of() : List.copyOf(fileScope);
            acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        }
    }

    public record HandoffSnapshot(
            String taskId,
            String runId,
            String runState,
            String runSummary,
            List<String> changedFiles,
            String diffSummary,
            String progressSummary) {

        public HandoffSnapshot {
            changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        }
    }
}
