package com.changhong.onlinecode.service.revision.contract;

import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.dto.revision.PlanPatchOperation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict trust-boundary validation for plan patches returned by pm-agent. */
public final class PlanPatchValidator {

    private static final Set<String> AREAS = Set.of("frontend", "backend", "full-stack", "validation");
    private static final Set<String> AGENTS = Set.of("frontend-dev-agent", "backend-dev-agent", "test-agent");

    public PlanPatch validate(PlanRevisionInput input, PlanPatch patch) {
        require(input != null, "revision input is required");
        require(patch != null, "plan patch is required");
        require(Objects.equals(input.requirementId(), patch.getRequirementId()),
                "requirementId does not match revision input");
        require(Objects.equals(input.loopId(), patch.getLoopId()), "loopId does not match revision input");
        require(input.revisionSeq() > 0 && Objects.equals(input.revisionSeq(), patch.getRevisionSeq()),
                "revisionSeq does not match revision input");
        require(Objects.equals(input.basePlanId(), patch.getBasePlanId()), "basePlanId does not match revision input");
        require(Objects.equals(input.basePlanVersion(), patch.getBasePlanVersion()),
                "basePlanVersion does not match revision input");
        require(nonBlank(patch.getSummary()), "summary is required");
        require(!list(patch.getOperations()).isEmpty(), "at least one operation is required");

        Map<String, PlanRevisionInput.TaskSnapshot> sourcesById = validateSources(input.tasks());
        Map<String, PlanPatchOperation> outputByKey = new HashMap<>();
        Set<String> operationKeys = new HashSet<>();
        Set<String> disposedSourceIds = new HashSet<>();

        for (PlanPatchOperation operation : patch.getOperations()) {
            require(operation != null, "operation must not be null");
            require(operation.getAction() != null, "operation action is required");
            require(nonBlank(operation.getTaskKey()), "operation taskKey is required");
            require(operationKeys.add(operation.getTaskKey()), "duplicate taskKey: " + operation.getTaskKey());
            require(nonBlank(operation.getReason()), "operation reason is required: " + operation.getTaskKey());

            PlanRevisionInput.TaskSnapshot source = null;
            if (operation.getAction() == PlanPatchAction.ADD) {
                require(!nonBlank(operation.getSourceTaskId()),
                        "ADD must not specify sourceTaskId: " + operation.getTaskKey());
            } else {
                require(nonBlank(operation.getSourceTaskId()),
                        operation.getAction() + " requires sourceTaskId: " + operation.getTaskKey());
                source = sourcesById.get(operation.getSourceTaskId());
                require(source != null, "unknown sourceTaskId: " + operation.getSourceTaskId());
            }

            switch (operation.getAction()) {
                case KEEP -> {
                    require(disposedSourceIds.add(operation.getSourceTaskId()),
                            "source task has multiple dispositions: " + operation.getSourceTaskId());
                    require(operation.getTaskKey().equals(source.taskKey()), "KEEP must preserve source taskKey");
                    requireNoRedefinition(operation);
                    outputByKey.put(operation.getTaskKey(), operation);
                }
                case SUPERSEDE -> {
                    require(disposedSourceIds.add(operation.getSourceTaskId()),
                            "source task has multiple dispositions: " + operation.getSourceTaskId());
                    require(operation.getTaskKey().equals(source.taskKey()),
                            "SUPERSEDE must identify the source taskKey");
                    requireNoRedefinition(operation);
                }
                case AMEND -> {
                    require(disposedSourceIds.add(operation.getSourceTaskId()),
                            "source task has multiple dispositions: " + operation.getSourceTaskId());
                    validateExecutable(operation);
                    outputByKey.put(operation.getTaskKey(), operation);
                }
                case ADD -> {
                    validateExecutable(operation);
                    outputByKey.put(operation.getTaskKey(), operation);
                }
                case REVALIDATE -> {
                    validateExecutable(operation);
                    require("test-agent".equals(operation.getAssignedAgent()), "REVALIDATE must use test-agent");
                    outputByKey.put(operation.getTaskKey(), operation);
                }
            }
        }

        validateDag(outputByKey, sourcesById);
        return patch;
    }

    private Map<String, PlanRevisionInput.TaskSnapshot> validateSources(List<PlanRevisionInput.TaskSnapshot> tasks) {
        Map<String, PlanRevisionInput.TaskSnapshot> byId = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (PlanRevisionInput.TaskSnapshot task : tasks) {
            require(task != null && nonBlank(task.taskId()) && nonBlank(task.taskKey()),
                    "source task id and key are required");
            require(byId.putIfAbsent(task.taskId(), task) == null, "duplicate source taskId: " + task.taskId());
            require(keys.add(task.taskKey()), "duplicate source taskKey: " + task.taskKey());
        }
        return byId;
    }

    private void requireNoRedefinition(PlanPatchOperation operation) {
        require(!nonBlank(operation.getTitle()) && !nonBlank(operation.getDescription())
                        && !nonBlank(operation.getArea()) && !nonBlank(operation.getAssignedAgent())
                        && list(operation.getFileScope()).isEmpty() && list(operation.getDependsOn()).isEmpty(),
                operation.getAction() + " must not redefine task fields: " + operation.getTaskKey());
    }

    private void validateExecutable(PlanPatchOperation operation) {
        require(nonBlank(operation.getTitle()), "title is required: " + operation.getTaskKey());
        require(nonBlank(operation.getDescription()), "description is required: " + operation.getTaskKey());
        require(AREAS.contains(operation.getArea()), "invalid area: " + operation.getArea());
        require(AGENTS.contains(operation.getAssignedAgent()), "invalid assignedAgent: " + operation.getAssignedAgent());
        require(validAssignment(operation.getArea(), operation.getAssignedAgent()),
                "assignedAgent is incompatible with area: " + operation.getTaskKey());
        require(!list(operation.getFileScope()).isEmpty(), "fileScope is required: " + operation.getTaskKey());
        for (String path : operation.getFileScope()) {
            require(validPath(path), "unsafe fileScope path: " + path);
            require(pathMatchesArea(path, operation.getArea()),
                    "fileScope crosses area boundary: " + operation.getTaskKey() + " -> " + path);
        }
    }

    private boolean validAssignment(String area, String agent) {
        return ("frontend".equals(area) && ("frontend-dev-agent".equals(agent) || "test-agent".equals(agent)))
                || ("backend".equals(area) && ("backend-dev-agent".equals(agent) || "test-agent".equals(agent)))
                || (("full-stack".equals(area) || "validation".equals(area)) && "test-agent".equals(agent));
    }

    private boolean validPath(String path) {
        return nonBlank(path) && path.equals(path.trim()) && !path.startsWith("/")
                && !path.contains("\\") && !List.of(path.split("/")).contains("..");
    }

    private boolean pathMatchesArea(String path, String area) {
        if ("frontend".equals(area)) return path.startsWith("frontend/");
        if ("backend".equals(area)) return path.startsWith("backend/");
        return path.startsWith("frontend/") || path.startsWith("backend/");
    }

    private void validateDag(Map<String, PlanPatchOperation> outputByKey,
                             Map<String, PlanRevisionInput.TaskSnapshot> sourcesById) {
        Map<String, List<String>> dependencies = new HashMap<>();
        for (PlanPatchOperation operation : outputByKey.values()) {
            List<String> dependsOn = list(operation.getDependsOn());
            if (operation.getAction() == PlanPatchAction.KEEP) {
                dependsOn = sourcesById.get(operation.getSourceTaskId()).dependsOn();
            }
            for (String dependency : dependsOn) {
                require(outputByKey.containsKey(dependency),
                        "dependency does not refer to an output task: " + dependency);
                require(!dependency.equals(operation.getTaskKey()),
                        "task cannot depend on itself: " + operation.getTaskKey());
            }
            dependencies.put(operation.getTaskKey(), dependsOn);
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String key : dependencies.keySet()) {
            require(!hasCycle(key, dependencies, visiting, visited), "plan patch contains a dependency cycle");
        }
    }

    private boolean hasCycle(String key, Map<String, List<String>> dependencies,
                             Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return false;
        if (!visiting.add(key)) return true;
        for (String dependency : dependencies.getOrDefault(key, List.of())) {
            if (hasCycle(dependency, dependencies, visiting, visited)) return true;
        }
        visiting.remove(key);
        visited.add(key);
        return false;
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new PlanPatchValidationException(message);
    }
}
