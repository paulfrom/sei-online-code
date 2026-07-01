/**
 * Service layer for the sei-online-code Phase 1 platform.
 * All calls target the frozen contract (docs/contracts/API-CONTRACT.md §3) and
 * are served by MSW in Phase 1. `request` returns the parsed `ResultData` body;
 * call sites read `res.data`.
 */
import { request } from '@ead/suid-utils-react';
import { AMS_SERVER_PATH } from '@/utils/constants';

/**
 * API base. In Phase 1 MSW intercepts by path suffix (`*​/api/...`), so any
 * prefix works; we keep the gateway/service prefix for zero-change backend
 * cutover (ADR-0002).
 */
const API = `${AMS_SERVER_PATH}/api`;

/** Lifecycle state tokens — verbatim uppercase per contract §4. */
export type LifecycleState =
  | 'DRAFTING'
  | 'SPEC_REFINING'
  | 'SPEC_REVIEW'
  | 'DISPATCHING'
  | 'DEVELOPING'
  | 'MERGING'
  | 'DEPLOYING'
  | 'PREVIEW'
  | 'ACCEPTED'
  | 'FAILED'
  | 'CANCELLED';

/** ResultData<T> envelope (contract §1.1) */
export interface ResultData<T> {
  success: boolean;
  message: string | null;
  data: T | null;
}

/** PageResult<T> — records=total rows, total=total pages, rows=data (contract §1.2) */
export interface PageResult<T> {
  page: number;
  records: number;
  total: number;
  rows: T[];
}

/** Search request body (contract §1.3) */
export interface Search {
  pageInfo?: { page: number; rows: number };
  quickSearchValue?: string;
  quickSearchProperties?: string[];
  filters?: Array<{ fieldName: string; value: any; operator: string }>;
  sortOrders?: Array<{ property: string; direction: 'ASC' | 'DESC' }>;
}

export interface ProjectDto {
  id: string;
  name: string;
  design: string;
  state: LifecycleState;
  currentSpecId: string | null;
  currentIterationId: string | null;
  createdDate: string;
  lastEditedDate: string;
}

export interface SpecDto {
  id: string;
  projectId: string;
  version: number;
  state: 'DRAFT' | 'SPEC_REVIEW' | 'CONFIRMED';
  pages: Array<{ key: string; title: string; route: string; description: string }>;
  components: Array<{ key: string; type: string; page: string; description: string }>;
  entities: Array<{
    key: string;
    fields: Array<{ name: string; type: string; description: string }>;
  }>;
  apiContract: Array<{
    method: string;
    path: string;
    requestShape: string;
    responseShape: string;
    description: string;
  }>;
  createdDate: string;
}

export interface IterationDto {
  id: string;
  projectId: string;
  specId: string;
  specVersion: number;
  state: LifecycleState;
  previewUrl: string | null;
  createdDate: string;
}

/** Task-level state tokens — verbatim per contract §1.1 / §4. */
export type TaskState = 'PENDING' | 'RUNNING' | 'MERGING' | 'MERGED' | 'FAILED' | 'CANCELLED';

/** Run-level state tokens — verbatim per contract §1.2 / §4. */
export type RunState = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

/** TaskDto — one non-overlapping unit of work cut by the Dispatch Agent (contract §1.1). */
export interface TaskDto {
  id: string;
  iterationId: string;
  title: string;
  description: string;
  fileScope: string[];
  assignedAgent: string;
  state: TaskState;
  worktreeBranch: string | null;
  seq: number;
  createdDate: string;
}

/** RunDto — one ClaudeRunner execution of a Task in its worktree (contract §1.2). */
export interface RunDto {
  id: string;
  taskId: string;
  iterationId: string;
  state: RunState;
  worktreePath: string;
  exitCode: number | null;
  startedDate: string;
  finishedDate: string | null;
}

/** Store URL used directly by ExtTable remotePaging (contract ep #3). */
export const PROJECT_FIND_BY_PAGE_URL = `${API}/project/findByPage`;

/** Store URL for the task list ExtTable (contract ep #11). */
export const TASK_FIND_BY_PAGE_URL = `${API}/task/findByPage`;

/** #1 create project */
export async function saveProject(params: {
  name: string;
  design: string;
}): Promise<ResultData<ProjectDto>> {
  return request({ url: `${API}/project/save`, method: 'POST', data: params });
}

/** #2 load one project */
export async function findOneProject(id: string): Promise<ResultData<ProjectDto>> {
  return request({ url: `${API}/project/findOne`, method: 'GET', params: { id } });
}

/** #4 refine design → Spec */
export async function refineSpec(projectId: string): Promise<ResultData<SpecDto>> {
  return request({ url: `${API}/project/refineSpec`, method: 'POST', data: { projectId } });
}

/** #5 load a Spec */
export async function findOneSpec(id: string): Promise<ResultData<SpecDto>> {
  return request({ url: `${API}/spec/findOne`, method: 'GET', params: { id } });
}

/** #6 confirm Spec → start iteration */
export async function confirmSpec(specId: string): Promise<ResultData<IterationDto>> {
  return request({ url: `${API}/spec/confirm`, method: 'POST', data: { specId } });
}

/** #7 deploy iteration */
export async function deployIteration(iterationId: string): Promise<ResultData<IterationDto>> {
  return request({ url: `${API}/iteration/deploy`, method: 'POST', data: { iterationId } });
}

/** #8 poll iteration state / previewUrl */
export async function findOneIteration(id: string): Promise<ResultData<IterationDto>> {
  return request({ url: `${API}/iteration/findOne`, method: 'GET', params: { id } });
}

/** #9 poll project lifecycle */
export async function findProjectState(
  id: string,
): Promise<ResultData<{ state: LifecycleState; iterationId: string | null }>> {
  return request({ url: `${API}/project/state`, method: 'GET', params: { id } });
}

/** #10 dispatch: confirmed Spec → disjoint tasks (state DISPATCHING→DEVELOPING) */
export async function dispatchIteration(iterationId: string): Promise<ResultData<TaskDto[]>> {
  return request({ url: `${API}/iteration/dispatch`, method: 'POST', data: { iterationId } });
}

/** #12 load one task */
export async function findOneTask(id: string): Promise<ResultData<TaskDto>> {
  return request({ url: `${API}/task/findOne`, method: 'GET', params: { id } });
}

/** #13 list runs (filter by iterationId / taskId) */
export async function findRunsByPage(search: Search): Promise<ResultData<PageResult<RunDto>>> {
  return request({ url: `${API}/run/findByPage`, method: 'POST', data: search });
}

/** #14 poll one run's state / exitCode */
export async function findOneRun(id: string): Promise<ResultData<RunDto>> {
  return request({ url: `${API}/run/findOne`, method: 'GET', params: { id } });
}

/** #15 merge all task worktrees back (state MERGING→DEPLOYING) */
export async function mergeIteration(iterationId: string): Promise<ResultData<IterationDto>> {
  return request({ url: `${API}/iteration/merge`, method: 'POST', data: { iterationId } });
}
