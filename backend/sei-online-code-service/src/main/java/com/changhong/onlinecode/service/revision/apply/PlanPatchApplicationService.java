package com.changhong.onlinecode.service.revision.apply;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.dto.revision.PlanPatchOperation;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.revision.contract.PlanPatchValidator;
import com.changhong.onlinecode.service.revision.contract.PlanRevisionInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Applies a validated PM plan patch as one database unit of work. */
@Service
public class PlanPatchApplicationService {

    private static final PlanPatchValidator VALIDATOR = new PlanPatchValidator();

    private final RequirementDao requirementDao;
    private final ExecutionPlanDao executionPlanDao;
    private final CodingTaskDao codingTaskDao;
    private final EffectiveTaskGraphResolver graphResolver;
    private final ObjectMapper objectMapper;

    public PlanPatchApplicationService(RequirementDao requirementDao,
                                       ExecutionPlanDao executionPlanDao,
                                       CodingTaskDao codingTaskDao,
                                       EffectiveTaskGraphResolver graphResolver,
                                       ObjectMapper objectMapper) {
        this.requirementDao = requirementDao;
        this.executionPlanDao = executionPlanDao;
        this.codingTaskDao = codingTaskDao;
        this.graphResolver = graphResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * APPLYING is the only accepted state. PLANNING must first validate the PM output and
     * explicitly transition to APPLYING, which makes the transaction boundary observable.
     */
    @Transactional
    public PlanPatchApplyResult apply(String requirementId, String expectedLoopId,
                                      long expectedRevisionSeq, PlanPatch patch) {
        Requirement requirement = requirementDao.findOne(requirementId);
        require(requirement != null, "Requirement does not exist: " + requirementId);
        require(Objects.equals(expectedLoopId, requirement.getActiveLoopId()),
                "Plan patch loop is stale");
        require(Objects.equals(expectedRevisionSeq, value(requirement.getRevisionSeq())),
                "Plan patch revision is stale");
        require(requirement.getRevisionState() == RequirementRevisionState.APPLYING,
                "Requirement revision must be APPLYING");

        ExecutionPlan basePlan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirementId, expectedLoopId);
        require(basePlan != null, "No base execution plan exists for the active loop");
        require(patch != null, "Plan patch is required");
        require(Objects.equals(basePlan.getId(), patch.getBasePlanId())
                        && Objects.equals(basePlan.getVersion(), patch.getBasePlanVersion()),
                "Plan patch base plan is stale");

        EffectiveTaskGraph baseGraph = graphResolver.resolve(basePlan);
        PlanRevisionInput validationInput = validationInput(requirement, basePlan, baseGraph.tasks(),
                expectedRevisionSeq);
        VALIDATOR.validate(validationInput, patch);

        int claimed = requirementDao.applyRevisionIfCurrent(requirementId, expectedLoopId,
                expectedRevisionSeq, RequirementRevisionState.APPLYING, RequirementRevisionState.NONE);
        require(claimed == 1, "Plan patch lost the revision compare-and-set race");

        int nextVersion = Math.addExact(basePlan.getVersion(), 1);
        ExecutionPlan newPlan = createPlan(requirement, basePlan, patch, nextVersion);
        executionPlanDao.save(newPlan);

        Map<String, CodingTask> sources = baseGraph.tasks().stream()
                .collect(Collectors.toMap(CodingTask::getId, Function.identity()));
        List<String> kept = new ArrayList<>();
        List<String> created = new ArrayList<>();
        List<String> runsToSettle = new ArrayList<>();
        List<String> superseded = new ArrayList<>();
        Map<String, CodingTask> effectiveByKey = new LinkedHashMap<>();

        for (PlanPatchOperation operation : patch.getOperations()) {
            CodingTask source = operation.getSourceTaskId() == null
                    ? null : sources.get(operation.getSourceTaskId());
            switch (operation.getAction()) {
                case KEEP -> {
                    kept.add(source.getId());
                    putEffective(effectiveByKey, source);
                }
                case AMEND -> {
                    disposeSource(source, operation.getReason(), runsToSettle, superseded);
                    CodingTask amended = createTask(requirement, newPlan, operation, source.getId());
                    codingTaskDao.save(amended);
                    created.add(amended.getId());
                    putEffective(effectiveByKey, amended);
                }
                case ADD -> {
                    CodingTask added = createTask(requirement, newPlan, operation, null);
                    codingTaskDao.save(added);
                    created.add(added.getId());
                    putEffective(effectiveByKey, added);
                }
                case SUPERSEDE -> disposeSource(source, operation.getReason(), runsToSettle, superseded);
                case REVALIDATE -> {
                    CodingTask validation = createTask(requirement, newPlan, operation, source.getId());
                    codingTaskDao.save(validation);
                    created.add(validation.getId());
                    putEffective(effectiveByKey, validation);
                }
            }
        }

        newPlan.setPlanJson(writePlanJson(patch.getSummary(), effectiveByKey.values()));
        newPlan.setStatus(ExecutionPlanStatus.DEVELOPING);
        executionPlanDao.save(newPlan);
        return new PlanPatchApplyResult(newPlan.getId(), nextVersion, expectedRevisionSeq,
                kept, created, runsToSettle, superseded);
    }

    private ExecutionPlan createPlan(Requirement requirement, ExecutionPlan basePlan,
                                     PlanPatch patch, int version) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setRequirementId(requirement.getId());
        plan.setLoopId(requirement.getActiveLoopId());
        plan.setVersion(version);
        plan.setPlanType(ExecutionPlanType.CHANGE_REQUEST);
        plan.setStatus(ExecutionPlanStatus.READY);
        plan.setSummary(patch.getSummary());
        plan.setCreatedByAgent("pm-agent");
        plan.setMemoryContextId(basePlan.getMemoryContextId());
        plan.setWorkspaceMemoryId(basePlan.getWorkspaceMemoryId());
        plan.setBasePlanId(basePlan.getId());
        plan.setTriggerCommentId(requirement.getRevisionTriggerCommentId());
        plan.setRevisionSeq(patch.getRevisionSeq());
        plan.setChangeSetJson(writeJson(patch));
        return plan;
    }

    private CodingTask createTask(Requirement requirement, ExecutionPlan plan,
                                  PlanPatchOperation operation, String supersedesTaskId) {
        CodingTask task = new CodingTask();
        task.setProjectId(requirement.getProjectId());
        task.setRequirementId(requirement.getId());
        task.setExecutionPlanId(plan.getId());
        task.setLoopId(plan.getLoopId());
        task.setRevisionSeq(plan.getRevisionSeq());
        task.setPlanTaskKey(operation.getTaskKey());
        task.setTitle(operation.getTitle());
        task.setDescription(operation.getDescription());
        task.setArea(operation.getArea());
        task.setFileScope(copy(operation.getFileScope()));
        task.setDependsOn(copy(operation.getDependsOn()));
        task.setAssignedAgent(operation.getAssignedAgent());
        task.setSupersedesTaskId(supersedesTaskId);
        task.setDispositionReason(operation.getReason());
        task.setStatus(CodingTaskStatus.PENDING);
        return task;
    }

    private void disposeSource(CodingTask source, String reason, List<String> runsToSettle,
                               List<String> superseded) {
        if (source.getStatus() == CodingTaskStatus.RUNNING
                || source.getStatus() == CodingTaskStatus.VALIDATING) {
            runsToSettle.add(source.getId());
            return;
        }
        source.setStatus(CodingTaskStatus.SUPERSEDED);
        source.setDispositionReason(reason);
        codingTaskDao.save(source);
        superseded.add(source.getId());
    }

    private PlanRevisionInput validationInput(Requirement requirement, ExecutionPlan basePlan,
                                               List<CodingTask> tasks, long revisionSeq) {
        List<PlanRevisionInput.TaskSnapshot> snapshots = tasks.stream()
                .map(task -> new PlanRevisionInput.TaskSnapshot(
                        task.getId(), task.getPlanTaskKey(), task.getTitle(), task.getDescription(),
                        task.getAssignedAgent(), task.getArea(), copy(task.getDependsOn()),
                        copy(task.getFileScope()), List.of(), task.getStatus().name(),
                        task.getFailureSummary()))
                .toList();
        return new PlanRevisionInput(requirement.getId(), requirement.getActiveLoopId(), revisionSeq,
                basePlan.getId(), basePlan.getVersion(), requirement.getTitle(),
                requirement.getDescription(), requirement.getPrdContent(), basePlan.getPlanJson(),
                List.of(), snapshots, List.of());
    }

    private String writePlanJson(String summary, Iterable<CodingTask> tasks) {
        List<Map<String, Object>> taskJson = new ArrayList<>();
        for (CodingTask task : tasks) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("taskKey", task.getPlanTaskKey());
            value.put("title", task.getTitle());
            value.put("description", task.getDescription());
            value.put("agent", task.getAssignedAgent());
            value.put("area", task.getArea());
            value.put("dependsOn", copy(task.getDependsOn()));
            value.put("fileScope", copy(task.getFileScope()));
            taskJson.add(value);
        }
        return writeJson(Map.of("summary", summary, "tasks", taskJson));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new PlanPatchApplyException("Cannot serialize applied plan: " + ex.getMessage());
        }
    }

    private void putEffective(Map<String, CodingTask> effectiveByKey, CodingTask task) {
        if (effectiveByKey.putIfAbsent(task.getPlanTaskKey(), task) != null) {
            throw new PlanPatchApplyException(
                    "Effective task graph contains duplicate task key: " + task.getPlanTaskKey());
        }
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new PlanPatchApplyException(message);
    }
}
