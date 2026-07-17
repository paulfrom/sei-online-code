/**
 * Local panel props and shared types for the requirement workspace.
 *
 * DTO types here describe the PRD comment-driven execution loop.
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
  validation?: string | { commands?: Array<string | { area?: string; command?: string }> } | null;
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

export type RunState = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type RunType = 'AGENT' | 'SYSTEM';
export type RunTerminalReason = 'SUCCEEDED' | 'FAILED' | 'TIMEOUT' | 'CANCELLED' | 'SUPERSEDED';
export type TriggerSource =
  | 'USER_ACTION' | 'AUTO' | 'RETRY' | 'REMEDIATION'
  | 'SCHEDULED_COMPENSATION' | 'CHAIN_COMPENSATION';
export type UsageStatus = 'UNAVAILABLE' | 'PARTIAL' | 'COMPLETE' | string;
export interface RunDto {
  id: string; taskId?: string | null; codingTaskId?: string | null; requirementId?: string | null;
  runNo?: number; runType?: RunType | null; parentRunId?: string | null;
  compensatesRunId?: string | null; attemptNo?: number | null;
  triggerSource?: TriggerSource | string | null; loopId?: string | null;
  cancelRequested?: boolean | null; invalidatedByCommentId?: string | null;
  memoryContextId?: string | null; workspaceMemoryId?: string | null;
  userPrompt?: string | null; summary?: string | null;
  failureSummary?: string | null; failureReason?: string | null;
  terminalReason?: RunTerminalReason | string | null;
  logStreamKey?: string | null; state?: RunState; worktreePath?: string | null;
  exitCode?: number | null; agentId?: string | null; agentName?: string | null;
  cliTool?: string | null; model?: string | null;
  inputTokens?: number | null; outputTokens?: number | null;
  cacheReadTokens?: number | null; cacheWriteTokens?: number | null;
  totalTokens?: number | null; usageStatus?: UsageStatus | null;
  startedDate?: string | null; finishedDate?: string | null;
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
  comments: RequirementCommentDto[];
  onRunLog: (run: RunDto) => void;
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
  comments: RequirementCommentDto[];
  onJumpTask?: (taskKey: string) => void;
}

export interface TaskTabProps {
  tasks: CodingTaskDto[];
  runs: RunDto[];
  comments: RequirementCommentDto[];
  onRerun: (t: CodingTaskDto, p: string) => Promise<void>;
  onViewRun: (task: CodingTaskDto) => void;
  onStop: () => Promise<void>;
  stopEnabled: boolean;
  highlightTaskKey?: string | null;
  onHighlightTaskConsumed?: () => void;
}

export interface RunTabProps {
  runs: RunDto[];
  taskFilterId?: string | null;
  onClearTaskFilter?: () => void;
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
  /** Execution id for the effects view; resolved from overview.recentRuns. Null when the caller has no overview (CodingTaskTab). */
  executionId?: string | null;
}
