package com.changhong.onlinecode.service.revision.contract;

import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.dto.revision.PlanPatchOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanPatchValidatorTest {

    private final PlanPatchValidator validator = new PlanPatchValidator();

    @Test
    void validate_acceptsKeepAmendAndExplicitValidationDag() {
        PlanPatch patch = patch(List.of(
                operation("BE-1", PlanPatchAction.KEEP, "task-be", null, null, null,
                        List.of(), List.of(), null),
                operation("FE-2", PlanPatchAction.AMEND, "task-fe", "调整表单", "复用现有成果并调整交互",
                        "frontend", List.of("frontend/src/pages/Form.jsx"), List.of("BE-1"),
                        "frontend-dev-agent"),
                operation("VAL-2", PlanPatchAction.REVALIDATE, "task-fe", "重新验收", "验证调整后的交互",
                        "validation", List.of("frontend/"), List.of("FE-2"), "test-agent")));

        assertDoesNotThrow(() -> validator.validate(input(), patch));
    }

    @Test
    void validate_rejectsUnknownSourceAndAddWithSource() {
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("FE-2", PlanPatchAction.AMEND, "missing", "t", "d", "frontend",
                        List.of("frontend/"), List.of(), "frontend-dev-agent")))));
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("NEW", PlanPatchAction.ADD, "task-fe", "t", "d", "frontend",
                        List.of("frontend/"), List.of(), "frontend-dev-agent")))));
    }

    @Test
    void validate_rejectsDuplicateKeysInvalidAgentAndCrossAreaScope() {
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("NEW", PlanPatchAction.ADD, null, "t", "d", "backend",
                        List.of("backend/"), List.of(), "backend-dev-agent"),
                operation("NEW", PlanPatchAction.ADD, null, "t2", "d2", "backend",
                        List.of("backend/"), List.of(), "backend-dev-agent")))));
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("NEW", PlanPatchAction.ADD, null, "t", "d", "backend",
                        List.of("backend/"), List.of(), "frontend-dev-agent")))));
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("NEW", PlanPatchAction.ADD, null, "t", "d", "frontend",
                        List.of("backend/src/X.java"), List.of(), "frontend-dev-agent")))));
    }

    @Test
    void validate_rejectsEmptyScopeMissingDependencyAndCycle() {
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("NEW", PlanPatchAction.ADD, null, "t", "d", "backend",
                        List.of(), List.of(), "backend-dev-agent")))));
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("NEW", PlanPatchAction.ADD, null, "t", "d", "backend",
                        List.of("backend/"), List.of("MISSING"), "backend-dev-agent")))));
        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch(List.of(
                operation("A", PlanPatchAction.ADD, null, "a", "a", "backend",
                        List.of("backend/a"), List.of("B"), "backend-dev-agent"),
                operation("B", PlanPatchAction.ADD, null, "b", "b", "backend",
                        List.of("backend/b"), List.of("A"), "backend-dev-agent")))));
    }

    @Test
    void validate_rejectsMismatchedRevisionIdentity() {
        PlanPatch patch = patch(List.of(operation("BE-1", PlanPatchAction.KEEP, "task-be",
                null, null, null, List.of(), List.of(), null)));
        patch.setRevisionSeq(8L);

        assertThrows(PlanPatchValidationException.class, () -> validator.validate(input(), patch));
    }

    private PlanRevisionInput input() {
        return new PlanRevisionInput("req-1", "loop-1", 7, "plan-3", 3,
                "需求", "描述", "PRD", "{\"goal\":\"old\"}",
                List.of(new PlanRevisionInput.CommentSnapshot("HUMAN", "HUMAN_FEEDBACK", "human", "保留后端，调整前端")),
                List.of(
                        task("task-be", "BE-1", "backend", "backend-dev-agent", List.of()),
                        task("task-fe", "FE-1", "frontend", "frontend-dev-agent", List.of("BE-1"))),
                List.of(new PlanRevisionInput.HandoffSnapshot("task-fe", "run-1", "RUNNING", "部分完成",
                        List.of("frontend/src/pages/Form.jsx"), "+ form", "已完成表单骨架")));
    }

    private PlanRevisionInput.TaskSnapshot task(String id, String key, String area, String agent,
                                                  List<String> dependencies) {
        return new PlanRevisionInput.TaskSnapshot(id, key, key, "description", agent, area,
                dependencies, List.of(area + "/"), List.of("通过"), "RUNNING", "result");
    }

    private PlanPatch patch(List<PlanPatchOperation> operations) {
        PlanPatch patch = new PlanPatch();
        patch.setRequirementId("req-1");
        patch.setLoopId("loop-1");
        patch.setRevisionSeq(7L);
        patch.setBasePlanId("plan-3");
        patch.setBasePlanVersion(3);
        patch.setSummary("增量调整");
        patch.setOperations(operations);
        return patch;
    }

    private PlanPatchOperation operation(String key, PlanPatchAction action, String sourceTaskId,
                                         String title, String description, String area, List<String> scope,
                                         List<String> dependencies, String agent) {
        PlanPatchOperation operation = new PlanPatchOperation();
        operation.setTaskKey(key);
        operation.setAction(action);
        operation.setSourceTaskId(sourceTaskId);
        operation.setTitle(title);
        operation.setDescription(description);
        operation.setArea(area);
        operation.setFileScope(scope);
        operation.setDependsOn(dependencies);
        operation.setAssignedAgent(agent);
        operation.setReason("用户评论影响");
        return operation;
    }
}
