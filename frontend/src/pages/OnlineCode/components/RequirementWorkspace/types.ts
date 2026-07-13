/**
 * Local panel props and shared types for the requirement workspace.
 *
 * DTO types here are an overriding local declaration for the PRD-loop contract;
 * they intentionally supersede the older shapes previously imported from
 * `@/services/onlineCodeTypes`. Consumers of the removed legacy Props
 * (OverviewDesignPanel / DetailedDesignPanel / CodingTaskPanel / RunHistoryPanel /
 * PrdPanel / index.tsx WorkspaceTab) are cleaned up in a later task.
 */

/** Generic EADP ResultData<T> envelope returned by the JS service layer. */
export interface ResultData<T> {
  success: boolean;
  message: string | null;
  data: T | null;
}

// ───────────────────────── DTO types (overriding) ─────────────────────────

export type RequirementAutomationStatus =
  | 'IDLE' | 'PLANNING' | 'DEVELOPING' | 'VALIDATING'
  | 'ACCEPTING' | 'DELIVERING' | 'INTERRUPTED' | 'WAITING_HUMAN'
  | 'COMPLETED' | 'FAILED';

export type RequirementStatus = 'PRD_GENERATING' | 'PRD_REVIEW' | 'PRD_CONFIRMED' | 'FAILED';

export interface RequirementDto {
  id: string; projectId: string; title: string; description?: string | null;
  status: RequirementStatus; automationStatus?: RequirementAutomationStatus | null;
  prdVersion?: number; prdContent?: string | null; designContextId?: string | null;
  memoryValidationStatus?: 'NOT_RUN'|'PASSED'|'WARNING'|'FAILED' | null;
  memoryValidationResultJson?: string | null;
  activeLoopId?: string | null;
  acceptedAt?: string | null; acceptedByAgent?: string | null;
  deliveryBranch?: string | null; deliveryCommitHash?: string | null;
  deliveryMrUrl?: string | null; deliveryTargetBranch?: string | null;
  failureSummary?: string | null; createdDate: string; lastEditedDate: string;
}

export type RequirementCommentAuthorType =
  | 'HUMAN' | 'PM_AGENT' | 'FRONTEND_AGENT' | 'BACKEND_AGENT' | 'TEST_AGENT' | 'SYSTEM';
export type RequirementCommentType =
  | 'HUMAN_FEEDBACK' | 'EXECUTION_PLAN' | 'DEV_RESULT' | 'VALIDATION_RESULT'
  | 'ACCEPTANCE' | 'REMEDIATION' | 'INTERRUPTION' | 'FAILURE'
  | 'MR_CREATED' | 'MR_UPDATED' | 'MR_FAILED'
  | 'MEMORY_UPDATED' | 'MEMORY_UPDATE_FAILED' | 'CONTEXT_SUMMARY_FAILED';

export interface RequirementCommentDto {
  id: string; requirementId: string; loopId?: string | null;
  authorType: RequirementCommentAuthorType; authorName?: string | null;
  commentType: RequirementCommentType; content?: string | null;
  metadataJson?: string | null; createdDate: string;
}

export type ExecutionPlanType = 'INITIAL' | 'REMEDIATION' | 'CHANGE_REQUEST';
export type ExecutionPlanStatus =
  | 'PLANNING' | 'READY' | 'DEVELOPING' | 'ACCEPTING'
  | 'NEEDS_REMEDIATION' | 'ACCEPTED' | 'INTERRUPTED' | 'FAILED';

export interface ExecutionPlanTask {
  taskKey: string; title: string; description?: string | null;
  agent: string; area: string; dependsOn?: string[];
  fileScope?: string[]; acceptanceCriteria?: string[];
}
export interface ExecutionPlanJson {
  goal?: string | null;
  tasks?: ExecutionPlanTask[];
  risks?: string[];
  validation?: string | null;
}
export interface ExecutionPlanDto {
  id: string; requirementId: string; loopId?: string | null;
  version?: number; planType: ExecutionPlanType; status: ExecutionPlanStatus;
  planJson?: string | null; summary?: string | null; createdByAgent?: string | null;
  memoryContextId?: string | null; workspaceMemoryId?: string | null; createdDate: string;
}

export type CodingTaskStatus =
  | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  | 'VALIDATION_FAILED' | 'CANCELLED' | 'STALE' | 'BLOCKED';
export interface CodingTaskDto {
  id: string; projectId: string; requirementId: string; status: CodingTaskStatus;
  title: string; description?: string | null; fileScope?: string[] | null;
  area?: string | null; dependsOn?: string[] | null;
  executionPlanId?: string | null; planTaskKey?: string | null;
  assignedAgent?: string | null; loopId?: string | null;
  failureSummary?: string | null; createdDate: string; lastEditedDate: string;
}

export type RunType =
  | 'DEVELOPMENT' | 'VALIDATION_COMMAND' | 'TEST_REVIEW'
  | 'PM_PLANNING' | 'PM_ACCEPTANCE' | 'DELIVERY';
export type RunState = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export interface RunDto {
  id: string; taskId?: string | null; codingTaskId?: string | null; requirementId?: string | null;
  runNo?: number; triggerSource?: string | null; runType?: RunType | null; loopId?: string | null;
  cancelRequested?: boolean | null; invalidatedByCommentId?: string | null;
  memoryContextId?: string | null; workspaceMemoryId?: string | null;
  userPrompt?: string | null; failureSummary?: string | null; failureReason?: string | null;
  iterationId?: string | null; state?: RunState; worktreePath?: string | null;
  exitCode?: number | null; startedDate?: string | null; finishedDate?: string | null;
}

export interface DeliverySummary {
  mrUrl?: string | null; branch?: string | null; commitHash?: string | null;
  targetBranch?: string | null; status: 'NONE' | 'PENDING' | 'CREATED' | 'UPDATED' | 'FAILED';
}

// ───────────────────────── Local panel Props ─────────────────────────

/** Tabs rendered by the right-hand pane. */
export type RightTab = 'plan' | 'task' | 'run' | 'delivery';

export interface PrdSectionProps {
  requirement: RequirementDto;
  onConfirm: () => Promise<void>;
  onEdit: (c: string) => Promise<void>;
  onRegenerate: (p: string) => Promise<void>;
}

export interface CommentStreamProps {
  comments: RequirementCommentDto[];
  activeLoopId?: string | null;
  requirement: RequirementDto;
  onSend: (content: string) => Promise<void>;
  sending: boolean;
  onJumpPlan?: () => void;
  onHighlightTask?: (taskKey: string) => void;
}

export interface LoopGroupProps {
  loopId: string;
  comments: RequirementCommentDto[];
  active: boolean;
  planVersion?: number | null;
  onJumpPlan?: () => void;
  onHighlightTask?: (taskKey: string) => void;
}

export interface CommentItemProps {
  comment: RequirementCommentDto;
  onJumpPlan?: () => void;
  onHighlightTask?: (taskKey: string) => void;
}

export interface CommentComposerProps {
  requirement: RequirementDto;
  onSend: (c: string) => Promise<void>;
  sending: boolean;
}

export interface AutomationStatusBarProps {
  status?: RequirementAutomationStatus | null;
  activeLoopId?: string | null;
  planVersion?: number | null;
}

export interface RightTabsProps {
  plan: ExecutionPlanDto | null;
  tasks: CodingTaskDto[];
  runs: RunDto[];
  delivery: DeliverySummary;
  onRunLog: (run: RunDto) => void;
  onRun: (t: CodingTaskDto) => Promise<void>;
  onRerun: (t: CodingTaskDto, p: string) => Promise<void>;
  onStop: () => Promise<void>;
  onRetryMr: () => Promise<void>;
  autoStopEnabled: boolean;
  highlightTaskKey?: string | null;
  onHighlightTaskConsumed?: () => void;
}

export interface ExecutionPlanTabProps {
  plan: ExecutionPlanDto | null;
  tasks: CodingTaskDto[];
  onJumpTask?: (taskKey: string) => void;
}

export interface TaskTabProps {
  tasks: CodingTaskDto[];
  onRun: (t: CodingTaskDto) => Promise<void>;
  onRerun: (t: CodingTaskDto, p: string) => Promise<void>;
  onViewRun: (run: RunDto) => void;
  onStop: () => Promise<void>;
  stopEnabled: boolean;
  highlightTaskKey?: string | null;
  onHighlightTaskConsumed?: () => void;
}

export interface RunTabProps {
  runs: RunDto[];
  onOpenLog: (run: RunDto) => void;
}

export interface DeliveryTabProps {
  delivery: DeliverySummary;
  comments: RequirementCommentDto[];
  onRetryMr: () => Promise<void>;
}

export interface RunLogDrawerProps {
  open: boolean;
  run: RunDto | null;
  onClose: () => void;
}