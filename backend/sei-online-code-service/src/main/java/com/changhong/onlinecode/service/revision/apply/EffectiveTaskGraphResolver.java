package com.changhong.onlinecode.service.revision.apply;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.dto.revision.PlanPatchOperation;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the task graph represented by an execution-plan revision without flattening
 * historical rows. New tasks belong to the revision while KEEP operations point to their
 * original rows.
 */
@Service
public class EffectiveTaskGraphResolver {

    private final ExecutionPlanDao executionPlanDao;
    private final CodingTaskDao codingTaskDao;
    private final ObjectMapper objectMapper;

    public EffectiveTaskGraphResolver(ExecutionPlanDao executionPlanDao,
                                      CodingTaskDao codingTaskDao,
                                      ObjectMapper objectMapper) {
        this.executionPlanDao = executionPlanDao;
        this.codingTaskDao = codingTaskDao;
        this.objectMapper = objectMapper;
    }

    public EffectiveTaskGraph resolveLatest(String requirementId, String loopId) {
        ExecutionPlan plan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirementId, loopId);
        if (plan == null) {
            throw new PlanPatchApplyException("No execution plan exists for the active loop");
        }
        return resolve(plan);
    }

    public EffectiveTaskGraph resolve(String requirementId, String loopId, long revisionSeq) {
        // Pre-migration plans all have revision_seq=0, so that value is not unique. Resolve
        // their latest row explicitly instead of invoking a single-result derived query.
        if (revisionSeq == 0L) {
            ExecutionPlan latest = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                    requirementId, loopId);
            if (latest == null || (latest.getRevisionSeq() != null && latest.getRevisionSeq() != 0L)) {
                throw new PlanPatchApplyException("Execution plan revision does not exist: 0");
            }
            return resolve(latest);
        }
        ExecutionPlan plan = executionPlanDao.findByRequirementIdAndLoopIdAndRevisionSeq(
                        requirementId, loopId, revisionSeq)
                .orElseThrow(() -> new PlanPatchApplyException(
                        "Execution plan revision does not exist: " + revisionSeq));
        return resolve(plan);
    }

    public EffectiveTaskGraph resolve(ExecutionPlan plan) {
        long revisionSeq = plan.getRevisionSeq() == null ? 0L : plan.getRevisionSeq();
        if (revisionSeq == 0L || plan.getChangeSetJson() == null || plan.getChangeSetJson().isBlank()) {
            return graph(revisionSeq, codingTaskDao.findByExecutionPlanId(plan.getId()));
        }

        PlanPatch patch = readPatch(plan.getChangeSetJson());
        Map<String, CodingTask> createdByKey = codingTaskDao
                .findByRequirementIdAndLoopIdAndRevisionSeq(
                        plan.getRequirementId(), plan.getLoopId(), revisionSeq)
                .stream()
                .collect(Collectors.toMap(CodingTask::getPlanTaskKey, Function.identity(),
                        (left, right) -> {
                            throw new PlanPatchApplyException(
                                    "Duplicate task key in revision " + revisionSeq + ": "
                                            + left.getPlanTaskKey());
                        }, LinkedHashMap::new));
        Map<String, CodingTask> historicalById = codingTaskDao
                .findByRequirementIdAndLoopId(plan.getRequirementId(), plan.getLoopId())
                .stream()
                .collect(Collectors.toMap(CodingTask::getId, Function.identity(), (left, right) -> left));

        List<CodingTask> effective = new ArrayList<>();
        for (PlanPatchOperation operation : list(patch.getOperations())) {
            CodingTask task;
            if (operation.getAction() == PlanPatchAction.KEEP) {
                task = historicalById.get(operation.getSourceTaskId());
                if (task == null) {
                    throw new PlanPatchApplyException(
                            "KEEP source task no longer exists: " + operation.getSourceTaskId());
                }
            } else if (operation.getAction() == PlanPatchAction.SUPERSEDE) {
                continue;
            } else {
                task = createdByKey.get(operation.getTaskKey());
                if (task == null) {
                    throw new PlanPatchApplyException(
                            "Revision task no longer exists: " + operation.getTaskKey());
                }
            }
            effective.add(task);
        }
        return graph(revisionSeq, effective);
    }

    private EffectiveTaskGraph graph(long revisionSeq, List<CodingTask> tasks) {
        Map<String, CodingTask> byKey = new LinkedHashMap<>();
        for (CodingTask task : tasks) {
            CodingTask duplicate = byKey.putIfAbsent(task.getPlanTaskKey(), task);
            if (duplicate != null) {
                throw new PlanPatchApplyException(
                        "Effective task graph contains duplicate task key: " + task.getPlanTaskKey());
            }
        }
        return new EffectiveTaskGraph(revisionSeq, new ArrayList<>(byKey.values()), byKey);
    }

    private PlanPatch readPatch(String json) {
        try {
            return objectMapper.readValue(json, PlanPatch.class);
        } catch (JsonProcessingException ex) {
            throw new PlanPatchApplyException("Stored plan change set is invalid JSON: " + ex.getMessage());
        }
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }
}
