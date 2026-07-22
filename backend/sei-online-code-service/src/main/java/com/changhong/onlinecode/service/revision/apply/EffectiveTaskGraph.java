package com.changhong.onlinecode.service.revision.apply;

import com.changhong.onlinecode.entity.CodingTask;

import java.util.List;
import java.util.Map;

/** Effective task rows for exactly one applied plan revision. */
public record EffectiveTaskGraph(long revisionSeq, List<CodingTask> tasks, Map<String, CodingTask> tasksByKey) {

    public EffectiveTaskGraph {
        tasks = List.copyOf(tasks);
        tasksByKey = Map.copyOf(tasksByKey);
    }
}
