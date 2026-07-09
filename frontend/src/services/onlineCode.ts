/**
 * Service layer for the sei-online-code platform.
 * Calls target the current Project Description -> Overview Design -> Module Detailed Design
 * -> Feature Design -> Coding Execution flow.
 * `request` returns the parsed `ResultData` body;
 * call sites read `res.data`.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';
import type { PlanDto } from './plan';
import type { FailureInfoFields } from './plan';

/**
 * API base. In Phase 1 MSW intercepts by path suffix (`*​/...`), so any
 * prefix works; we keep the gateway/service prefix for zero-change backend
 * cutover (ADR-0002).
 */
const API = `${PROJECT_SERVER_PATH}`;

/** Lifecycle state tokens — verbatim uppercase per contract §4. */
export type LifecycleState =
  | 'DRAFTING'
  | 'SPEC_REFINING'
  | 'SPEC_REVIEW'
  | 'PLANNING'
  | 'DESIGNING'
  | 'READY_TO_BUILD'
  | 'FAILED'
  | string;

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
  gitUrl?: string;
  workspacePath?: string;
  autoRunCodingTask?: boolean;
  state: LifecycleState;
  currentSpecId: string | null;
  createdDate: string;
  lastEditedDate: string;
}

export interface SpecDto extends FailureInfoFields {
  id: string;
  projectId: string;
  version: number;
  state: 'GENERATING' | 'DRAFT' | 'SPEC_REVIEW' | 'CONFIRMED' | 'FAILED';
  moduleId?: string | null;
  moduleTitle?: string | null;
  moduleSummary?: string | null;
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
  modifyHint?: string | null;
  createdDate: string;
}

/** SkillConfig — origin-bearing config (Phase 4, multica dim d: source→config JSONB). */
export interface SkillConfig {
  origin: string;
}

/** SkillFileDto — an auxiliary file attached to a skill (Phase 5 §1.1: path + content). */
export interface SkillFileDto {
  path: string;
  content: string;
}

/** SkillDto — an importable, hash-locked instruction bundle (Phase 3 §1.1). */
export interface SkillDto {
  id: string;
  name: string;
  description: string;
  config: SkillConfig;
  content: string;
  /** auxiliary files (references/**); not part of the §6 hash lock */
  files: SkillFileDto[];
  /** server-authoritative lock; FE never recomputes (contract §6) */
  computedHash: string;
  createdDate: string;
}

/** AgentDto — a user-defined dev agent (instructions + bound skills) (Phase 3 §1.2). */
export interface AgentDto {
  id: string;
  name: string;
  description: string;
  instructions: string;
  /** "" = let CLI resolve its own default */
  model: string;
  /** CLI 工具：claude/codex，"" = 默认 claude（multica runtime profile protocol_family 维度） */
  cliTool: string;
  /** MCP server 配置 JSON（Claude 风格 {"mcpServers":{...}}）；"" = 不托管，沿用 CLI 默认 */
  mcpConfig: string;
  /** true for the 3 seeded agents (requirement/dispatch/deploy), non-deletable */
  builtin: boolean;
  skillIds: string[];
  createdDate: string;
}

/**
 * Built-in skills (multica dim g). Vendored to the backend classpath
 * (`resources/skills/<name>/`) and resolved via `builtin:<name>` synthetic ids —
 * they are NOT oc_skill rows and never appear in /skill/findByPage. Agents bind
 * them through the same skillIds[] field; the backend routes `builtin:` to
 * BuiltInSkillRegistry at materialize time. Surfaced here so the Agents
 * multi-select can offer them alongside user-imported skills.
 */
export const BUILTIN_SKILLS: ReadonlyArray<{
  id: string;
  name: string;
  description: string;
}> = [
  { id: 'builtin:suid', name: 'suid', description: '@ead/suid 组件库开发技能' },
  { id: 'builtin:eadp-backend', name: 'eadp-backend', description: 'sei-core 分层架构后端开发技能' },
  { id: 'builtin:project-planning', name: 'project-planning', description: '概要设计生成 skill' },
  { id: 'builtin:feature-design', name: 'feature-design', description: '功能设计生成 skill' },
];

/** Store URL used directly by ExtTable remotePaging (contract ep #3). */
export const PROJECT_FIND_BY_PAGE_URL = `${API}/project/findByPage`;

/** Store URL for the skills list ExtTable (contract ep #17). */
export const SKILL_FIND_BY_PAGE_URL = `${API}/skill/findByPage`;

/** Store URL for the agents list ExtTable (contract ep #21). */
export const AGENT_FIND_BY_PAGE_URL = `${API}/agent/findByPage`;

/** #1 create/update project */
export async function saveProject(params: {
  id?: string;
  name: string;
  design: string;
  gitUrl?: string;
  workspacePath?: string;
  autoRunCodingTask?: boolean;
}): Promise<ResultData<ProjectDto>> {
  return request({ url: `${API}/project/save`, method: 'POST', data: params });
}

/** #2 load one project */
export async function findOneProject(id: string): Promise<ResultData<ProjectDto>> {
  return request({ url: `${API}/project/findOne`, method: 'GET', params: { id } });
}

/** #4 compatibility endpoint: legacy refineSpec path now starts overview design generation. */
export async function refineSpec(projectId: string): Promise<ResultData<PlanDto>> {
  return request({ url: `${API}/project/refineSpec`, method: 'POST', data: { projectId } });
}

/** #5 load a legacy Spec / current detailed design */
export async function findOneSpec(id: string): Promise<ResultData<SpecDto>> {
  return request({ url: `${API}/spec/findOne`, method: 'GET', params: { id } });
}

export async function findOneDetailedDesign(id: string): Promise<ResultData<SpecDto>> {
  return findOneSpec(id);
}

/** #6 confirm legacy Spec / current detailed design → generate overview design */
export async function confirmSpec(specId: string): Promise<ResultData<PlanDto>> {
  return request({ url: `${API}/spec/confirm`, method: 'POST', data: { specId } });
}

export async function confirmDetailedDesign(specId: string): Promise<ResultData<PlanDto>> {
  return confirmSpec(specId);
}

/** #R regenerate legacy Spec / current detailed design — version+1, immutable history */
export async function regenerateSpec(
  projectId: string,
  modifyHint?: string,
): Promise<ResultData<SpecDto>> {
  return request({
    url: `${API}/spec/${projectId}/regenerate`,
    method: 'POST',
    data: { modifyHint },
  });
}

export async function regenerateDetailedDesign(
  projectId: string,
  modifyHint?: string,
): Promise<ResultData<SpecDto>> {
  return regenerateSpec(projectId, modifyHint);
}

// --- Phase 3: Skills + Custom Agents (contract eps #16–24) ---

/** #16 import a skill; dedup by name (server returns computedHash) */
export async function importSkill(params: {
  name: string;
  description: string;
  config: SkillConfig;
  content: string;
  files?: SkillFileDto[];
}): Promise<ResultData<SkillDto>> {
  return request({ url: `${API}/skill/import`, method: 'POST', data: params });
}

/** #16a import a skill from GitHub */
export async function importGithubSkill(url: string): Promise<ResultData<SkillDto>> {
  return request({ url: `${API}/skill/import/github`, method: 'POST', data: { url } });
}

/** #16b import a skill from an uploaded archive */
export async function importSkillArchive(file: File): Promise<ResultData<SkillDto>> {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: `${API}/skill/import/archive`,
    method: 'POST',
    data: formData,
    requestType: 'form',
  });
}

/** #17 list skills */
export async function findSkillsByPage(search: Search): Promise<ResultData<PageResult<SkillDto>>> {
  return request({ url: `${API}/skill/findByPage`, method: 'POST', data: search });
}

/** #18 load one skill */
export async function findOneSkill(id: string): Promise<ResultData<SkillDto>> {
  return request({ url: `${API}/skill/findOne`, method: 'GET', params: { id } });
}

/** #19 delete a skill (rejected if bound to any agent) */
export async function deleteSkill(id: string): Promise<ResultData<void>> {
  return request({ url: `${API}/skill/delete`, method: 'DELETE', params: { id } });
}

/** #20 create/update a custom agent (no id = create) */
export async function saveAgent(params: {
  id?: string;
  name: string;
  description: string;
  instructions: string;
  model: string;
  cliTool: string;
  mcpConfig: string;
}): Promise<ResultData<AgentDto>> {
  return request({ url: `${API}/agent/save`, method: 'POST', data: params });
}

/** #21 list agents (built-in + custom) */
export async function findAgentsByPage(search: Search): Promise<ResultData<PageResult<AgentDto>>> {
  return request({ url: `${API}/agent/findByPage`, method: 'POST', data: search });
}

/** #22 load one agent */
export async function findOneAgent(id: string): Promise<ResultData<AgentDto>> {
  return request({ url: `${API}/agent/findOne`, method: 'GET', params: { id } });
}

/** #23 delete a custom agent (built-in rejected) */
export async function deleteAgent(id: string): Promise<ResultData<void>> {
  return request({ url: `${API}/agent/delete`, method: 'DELETE', params: { id } });
}

/** #24 attach/replace an agent's bound skills */
export async function attachAgentSkills(params: {
  agentId: string;
  skillIds: string[];
}): Promise<ResultData<AgentDto>> {
  return request({ url: `${API}/agent/skills`, method: 'POST', data: params });
}

/** #30 detailed design version history for a project (legacy spec endpoint) */
export async function findSpecsByProject(projectId: string): Promise<ResultData<SpecDto[]>> {
  return request({ url: `${API}/spec/findByProject`, method: 'GET', params: { projectId } });
}

export async function findDetailedDesignsByProject(
  projectId: string,
): Promise<ResultData<SpecDto[]>> {
  return findSpecsByProject(projectId);
}

// --- Phase 5: Config Surface + Workspace resolve (contract eps #31–33) ---

/** PlatformConfigDto — the single platform config row (Phase 5 §1.1). */
export interface PlatformConfigDto {
  /** singleton row, fixed id `CONFIG` */
  id: string;
  /** default `${OS temp}/sei-online-code`; env override `oc.workspace.root` */
  workspaceRoot: string;
  /** "" = no template → scaffold-generate path (no default) */
  templateGitlabUrl: string;
  createdDate: string;
}

/** Workspace provisioning source — CLONE when a template URL is set, else SCAFFOLD. */
export type WorkspaceSource = 'CLONE' | 'SCAFFOLD';

/** WorkspaceResolveResult — resolved per-project workspace dir (Phase 5 §2 ep #33). */
export interface WorkspaceResolveResult {
  /** resolved workspace dir: `<workspaceRoot>/<projectId>` */
  path: string;
  /** true when the dir already existed (clone-once reuse — never re-clone) */
  provisioned: boolean;
  source: WorkspaceSource;
}

/** #31 read platform config (creates default singleton if absent) */
export async function getConfig(): Promise<ResultData<PlatformConfigDto>> {
  return request({ url: `${API}/config/get`, method: 'GET' });
}

/** #32 upsert platform config (Workspace Root + Template GitLab URL) */
export async function saveConfig(params: {
  workspaceRoot: string;
  templateGitlabUrl: string;
}): Promise<ResultData<PlatformConfigDto>> {
  return request({ url: `${API}/config/save`, method: 'POST', data: params });
}

/** #33 resolve a project's workspace dir → { path, provisioned, source } */
export async function resolveWorkspace(
  projectId: string,
): Promise<ResultData<WorkspaceResolveResult>> {
  return request({ url: `${API}/workspace/resolve`, method: 'GET', params: { projectId } });
}

// --- Pre-Build Phase: Plan + FeatureDesign (P12) ---

/** #P12 build project (all confirmed FeatureDesigns) */
export async function buildProject(projectId: string) {
  return request({ url: `${API}/project/${projectId}/build`, method: 'POST' });
}
