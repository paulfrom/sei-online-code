/**
 * Shared DTO types for the requirement-workspace redesign.
 * Mirrors the API contract for the new flow:
 * Requirement(PRD) -> ExecutionPlan -> CodingTask -> Run -> GitLab MR.
 */

/** Requirement lifecycle states (contract §3.1). */
export type RequirementStatus =
  | 'PRD_GENERATING'
  | 'PRD_REVIEW'
  | 'PRD_CONFIRMED'
  | 'FAILED';

export type RequirementAutomationStatus =
  | 'IDLE' | 'PLANNING' | 'DEVELOPING' | 'VALIDATING' | 'ACCEPTING'
  | 'DELIVERING' | 'INTERRUPTED' | 'WAITING_HUMAN' | 'COMPLETED' | 'FAILED';

/** A product requirement document attached to a project. */
export interface RequirementDto {
  id: string;
  projectId: string;
  title: string;
  description?: string | null;
  status: RequirementStatus;
  automationStatus?: RequirementAutomationStatus | null;
  prdVersion: number;
  prdContent?: string | null;
  designContextId?: string | null;
  memoryValidationStatus?: 'NOT_RUN' | 'PASSED' | 'WARNING' | 'FAILED' | null;
  memoryValidationResultJson?: string | null;
  activeLoopId?: string | null;
  acceptedAt?: string | null;
  acceptedByAgent?: string | null;
  deliveryBranch?: string | null;
  deliveryCommitHash?: string | null;
  deliveryMrUrl?: string | null;
  deliveryTargetBranch?: string | null;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

/** Coding task lifecycle states (contract §3.4). */
export type CodingTaskStatus =
  | 'PENDING'
  | 'BLOCKED'
  | 'RUNNING'
  | 'VALIDATING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'VALIDATION_FAILED'
  | 'CANCELLED'
  | 'STALE';

/** A concrete coding task generated from an execution plan. */
export interface CodingTaskDto {
  id: string;
  projectId: string;
  requirementId: string;
  executionPlanId?: string | null;
  planTaskKey?: string | null;
  assignedAgent?: string | null;
  loopId?: string | null;
  area?: string | null;
  dependsOn?: string[] | null;
  status: CodingTaskStatus;
  title: string;
  description?: string | null;
  fileScope: string[];
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

/** Run lifecycle states (contract §3.5). */
export type RunState =
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED';

/** A single execution run of a coding task. */
export interface RunDto {
  id: string;
  codingTaskId?: string | null;
  requirementId?: string | null;
  runNo?: number;
  loopId?: string | null;
  cancelRequested?: boolean | null;
  invalidatedByCommentId?: string | null;
  triggerSource: string;
  state: RunState;
  userPrompt?: string | null;
  failureSummary?: string | null;
  failureReason?: string | null;
  worktreePath?: string | null;
  startedDate: string;
  finishedDate?: string | null;
}
