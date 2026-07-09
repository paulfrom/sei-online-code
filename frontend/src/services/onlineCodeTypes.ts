/**
 * Shared DTO types for the requirement-workspace redesign.
 * Mirrors the API contract for the new flow:
 * Requirement -> Overview Design -> Detailed Design -> Coding Task -> Run.
 */

/** Requirement lifecycle states (contract §3.1). */
export type RequirementStatus =
  | 'PRD_GENERATING'
  | 'PRD_REVIEW'
  | 'PRD_CONFIRMED'
  | 'FAILED';

/** A product requirement document attached to a project. */
export interface RequirementDto {
  id: string;
  projectId: string;
  title: string;
  description?: string | null;
  status: RequirementStatus;
  prdVersion: number;
  prdContent?: string | null;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

/** Overview design lifecycle states (contract §3.2). */
export type OverviewDesignStatus =
  | 'GENERATING'
  | 'DRAFT'
  | 'CONFIRMED'
  | 'FAILED';

/** High-level overview design derived from a requirement. */
export interface OverviewDesignDto {
  id: string;
  projectId: string;
  requirementId: string;
  status: OverviewDesignStatus;
  version: number;
  content?: string | null;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

/** Detailed design lifecycle states (contract §3.3). */
export type DetailedDesignStatus =
  | 'GENERATING'
  | 'REVIEW'
  | 'CONFIRMED'
  | 'FAILED';

/** Module/feature scoped detailed design. */
export interface DetailedDesignDto {
  id: string;
  projectId: string;
  requirementId: string;
  overviewDesignId: string;
  moduleId: string;
  moduleTitle: string;
  featureId: string;
  featureTitle: string;
  status: DetailedDesignStatus;
  version: number;
  content?: string | null;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

/** Coding task lifecycle states (contract §3.4). */
export type CodingTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'STALE';

/** A concrete coding task generated from a detailed design. */
export interface CodingTaskDto {
  id: string;
  projectId: string;
  requirementId: string;
  detailedDesignId: string;
  detailedDesignVersion: number;
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
  codingTaskId: string;
  runNo: number;
  triggerSource: string;
  state: RunState;
  userPrompt?: string | null;
  failureSummary?: string | null;
  failureReason?: string | null;
  worktreePath?: string | null;
  startedDate: string;
  finishedDate?: string | null;
}
