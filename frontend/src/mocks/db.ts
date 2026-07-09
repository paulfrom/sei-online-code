/**
 * In-memory mock database for the sei-online-code Phase 1 walking skeleton.
 * Holds Project / Spec / Plan / FeatureDesign entities for the current
 * Project Description -> Overview Design -> Module Detailed Design -> Feature Design
 * -> Coding Execution flow.
 *
 * MSW-first (ADR-0002): this is the single source of live data for the
 * frontend; no real backend is involved in Phase 1.
 */

/** Lifecycle state tokens — verbatim uppercase per contract §4. */
export type LifecycleState =
  | 'DRAFTING'
  | 'SPEC_REFINING'
  | 'SPEC_REVIEW'
  | 'PLANNING'
  | 'DESIGNING'
  | 'READY_TO_BUILD'
  | 'FAILED';

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

export interface SpecDto {
  id: string;
  projectId: string;
  version: number;
  state: 'GENERATING' | 'DRAFT' | 'SPEC_REVIEW' | 'CONFIRMED' | 'FAILED';
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

/** SkillConfig — origin-bearing config (Phase 4, multica dim d). */
export interface SkillConfig {
  origin: string;
}

/** SkillFileDto — an auxiliary file attached to a skill (Phase 5 §1.1). */
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
  files: SkillFileDto[];
  computedHash: string;
  createdDate: string;
}

/** AgentDto — a user-defined dev agent (instructions + bound skills) (Phase 3 §1.2). */
export interface AgentDto {
  id: string;
  name: string;
  description: string;
  instructions: string;
  model: string;
  cliTool: string;
  builtin: boolean;
  skillIds: string[];
  createdDate: string;
}

/** PlatformConfigDto — the single platform config row (Phase 5 §1.1). */
export interface PlatformConfigDto {
  id: string;
  workspaceRoot: string;
  templateGitlabUrl: string;
  createdDate: string;
}

/** Workspace provisioning source — CLONE (template URL set) | SCAFFOLD (empty). */
export type WorkspaceSource = 'CLONE' | 'SCAFFOLD';

/** WorkspaceResolveResult — resolved per-project workspace dir (Phase 5 §2 ep #33). */
export interface WorkspaceResolveResult {
  path: string;
  provisioned: boolean;
  source: WorkspaceSource;
}

/** PlanStatus enum (contract §3.1) */
export type PlanStatus = 'GENERATING' | 'DRAFT' | 'CONFIRMED' | 'FAILED';

/** PlanFeature — part of PlanContent (contract §2.2) */
export interface PlanFeature {
  featureId: string;
  title: string;
  outline: string;
}

/** PlanContent (contract §2.2) */
export interface PlanContent {
  summary: string;
  techAssumptions: string[];
  features: PlanFeature[];
  nonGoals: string[];
}

/** PlanDto (contract §2.1) */
export interface PlanDto {
  id: string;
  projectId: string;
  version: number;
  status: PlanStatus;
  content: PlanContent;
  modifyHint?: string;
  isLatest: boolean;
  creatorId?: string;
  creatorAccount?: string;
  creatorName?: string;
  createdDate?: string;
  lastEditorId?: string;
  lastEditorAccount?: string;
  lastEditorName?: string;
  lastEditedDate?: string;
}

/** FeatureDesignStatus enum (contract §3.2) */
export type FeatureDesignStatus =
  | 'PENDING'
  | 'GENERATING'
  | 'DRAFT'
  | 'CONFIRMED'
  | 'STALE'
  | 'FAILED';

/** FeatureDesignBuildStatus enum (contract §3.3) */
export type FeatureDesignBuildStatus =
  | 'IDLE'
  | 'BUILDING'
  | 'BUILT'
  | 'BUILD_FAILED'
  | 'STALE';

/** FeatureDesignContent (contract §2.4) */
export interface FeatureDesignContent {
  featureId: string;
  goal: string;
  design: Record<string, any>;
  acceptance: string[];
  fileScope: string[];
}

/** FeatureDesignDto (contract §2.3) */
export interface FeatureDesignDto {
  id: string;
  projectId: string;
  featureId: string;
  version: number;
  status: FeatureDesignStatus;
  buildStatus: FeatureDesignBuildStatus;
  content?: FeatureDesignContent;
  modifyHint?: string;
  isLatest: boolean;
  creatorId?: string;
  creatorAccount?: string;
  creatorName?: string;
  createdDate?: string;
  lastEditorId?: string;
  lastEditorAccount?: string;
  lastEditorName?: string;
  lastEditedDate?: string;
}

export const now = () => new Date().toISOString().slice(0, 19);

let seq = 1;
export const nextId = (prefix: string) => `${prefix}${String(seq++).padStart(4, '0')}`;

/** Fixed singleton id for the platform config row (Phase 5 §1.1). */
export const CONFIG_ID = 'CONFIG';

/** Workspace Root default when unset (contract §1.1 — OS temp + "/sei-online-code"). */
export const DEFAULT_WORKSPACE_ROOT = '/tmp/sei-online-code';

interface Db {
  projects: Map<string, ProjectDto>;
  specs: Map<string, SpecDto>;
  skills: Map<string, SkillDto>;
  agents: Map<string, AgentDto>;
  config: PlatformConfigDto | null;
  workspaces: Map<string, WorkspaceSource>;
  plans?: Map<string, PlanDto>;
  featureDesigns?: Map<string, FeatureDesignDto>;
  requirements: Map<string, RequirementDto>;
  overviewDesigns: Map<string, OverviewDesignDto>;
  detailedDesigns: Map<string, DetailedDesignDto>;
  codingTasks: Map<string, CodingTaskDto>;
  runs: Map<string, RunDto>;
}

export interface RequirementDto {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: 'PRD_GENERATING' | 'PRD_REVIEW' | 'PRD_CONFIRMED' | 'FAILED';
  prdVersion: number;
  prdContent: string;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

export interface OverviewDesignDto {
  id: string;
  projectId: string;
  requirementId: string;
  status: 'GENERATING' | 'DRAFT' | 'CONFIRMED' | 'FAILED';
  version: number;
  content: string;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

export interface DetailedDesignDto {
  id: string;
  projectId: string;
  requirementId: string;
  overviewDesignId: string;
  moduleId: string;
  moduleTitle: string;
  status: 'GENERATING' | 'REVIEW' | 'CONFIRMED' | 'FAILED';
  version: number;
  content: string;
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

export interface CodingTaskDto {
  id: string;
  projectId: string;
  requirementId: string;
  detailedDesignId: string;
  detailedDesignVersion: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'STALE';
  title: string;
  description: string;
  fileScope: string[];
  failureSummary?: string | null;
  createdDate: string;
  lastEditedDate: string;
}

export interface RunDto {
  id: string;
  codingTaskId: string;
  runNo: number;
  triggerSource: string;
  state: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
  userPrompt?: string | null;
  failureSummary?: string | null;
  failureReason?: string | null;
  worktreePath?: string | null;
  startedDate: string;
  finishedDate?: string | null;
}
export const db: Db = {
  projects: new Map(),
  specs: new Map(),
  skills: new Map(),
  agents: new Map(),
  config: null,
  workspaces: new Map(),
  plans: new Map(),
  featureDesigns: new Map(),
  requirements: new Map(),
  overviewDesigns: new Map(),
  detailedDesigns: new Map(),
  codingTasks: new Map(),
  runs: new Map(),
};

/** Build a demo detailed design structure from a project's description prose. */
function buildSpec(project: ProjectDto, version: number): SpecDto {
  return {
    id: nextId('SPEC'),
    projectId: project.id,
    version,
    state: 'SPEC_REVIEW',
    pages: [
      {
        key: 'list',
        title: `${project.name}列表`,
        route: '/list',
        description: '分页表格 + 关键字搜索',
      },
      {
        key: 'detail',
        title: `${project.name}详情`,
        route: '/detail',
        description: '只读详情视图',
      },
    ],
    components: [
      {
        key: 'MainTable',
        type: 'ExtTable',
        page: 'list',
        description: '远程分页表格，列: 编码/名称/状态',
      },
      {
        key: 'CreateModal',
        type: 'ExtModal',
        page: 'list',
        description: '新增/编辑弹窗表单',
      },
    ],
    entities: [
      {
        key: 'MainEntity',
        fields: [
          { name: 'code', type: 'string', description: '业务编码' },
          { name: 'name', type: 'string', description: '名称' },
          { name: 'qty', type: 'number', description: '数量' },
        ],
      },
    ],
    apiContract: [
      {
        method: 'POST',
        path: '/api/main/findByPage',
        requestShape: 'Search',
        responseShape: 'ResultData<PageResult<MainDto>>',
        description: '主实体分页查询',
      },
    ],
    createdDate: now(),
  };
}

/** Build demo overview design content from a project description. */
function buildOverviewDesign(project: ProjectDto): PlanContent {
  return {
    summary: `${project.name}概要设计：基于项目描述「${project.design}」梳理核心范围、模块边界和后续功能设计输入。`,
    techAssumptions: ['React', 'TypeScript', '@ead/suid', 'Umi'],
    features: [
      {
        featureId: 'FEAT-001',
        title: '基础列表与查询',
        outline: '提供主数据列表、关键字搜索和基础分页能力。',
      },
      {
        featureId: 'FEAT-002',
        title: '详情与编辑',
        outline: '提供详情查看、新增和编辑入口，并保持表单校验一致。',
      },
      {
        featureId: 'FEAT-003',
        title: '状态流转与编码执行',
        outline: '串联功能设计确认后的编码执行状态展示与日志查看。',
      },
    ],
    nonGoals: ['复杂权限模型', '跨系统集成', '高级数据分析'],
  };
}

/** Upsert a mock overview design so the project detail overview tab has content. */
function upsertOverviewDesign(project: ProjectDto): PlanDto {
  const plans = Array.from(db.plans?.values() ?? []).filter((p) => p.projectId === project.id);
  plans.forEach((p) => {
    p.isLatest = false;
  });
  const latestVersion = plans.reduce((max, p) => Math.max(max, p.version), 0);
  const plan: PlanDto = {
    id: nextId('PLAN'),
    projectId: project.id,
    version: latestVersion + 1,
    status: 'DRAFT',
    content: buildOverviewDesign(project),
    modifyHint: undefined,
    isLatest: true,
    createdDate: now(),
    lastEditedDate: now(),
  };
  db.plans?.set(plan.id, plan);
  return plan;
}

/** create project description → DRAFTING (contract ep #1) */
export function createProject(
  name: string,
  design: string,
  gitUrl?: string,
  workspacePath?: string,
  autoRunCodingTask?: boolean,
): ProjectDto {
  const ts = now();
  const project: ProjectDto = {
    id: nextId('PRJ'),
    name,
    design,
    gitUrl: gitUrl || '',
    workspacePath: workspacePath || '',
    autoRunCodingTask: autoRunCodingTask ?? false,
    state: 'DRAFTING',
    currentSpecId: null,
    createdDate: ts,
    lastEditedDate: ts,
  };
  db.projects.set(project.id, project);
  return project;
}

/** create/update project */
export function saveProject(input: {
  id?: string;
  name: string;
  design: string;
  gitUrl?: string;
  workspacePath?: string;
  autoRunCodingTask?: boolean;
}): ProjectDto {
  if (input.id) {
    const existing = db.projects.get(input.id);
    if (existing) {
      existing.name = input.name;
      existing.design = input.design;
      existing.gitUrl = input.gitUrl || existing.gitUrl;
      existing.workspacePath = input.workspacePath || existing.workspacePath;
      existing.autoRunCodingTask = input.autoRunCodingTask ?? existing.autoRunCodingTask;
      existing.lastEditedDate = now();
      return existing;
    }
  }
  return createProject(input.name, input.design, input.gitUrl, input.workspacePath, input.autoRunCodingTask);
}

/** create requirement and auto-move to PRD_REVIEW */
export function createRequirement(projectId: string, title: string, description: string): RequirementDto {
  const ts = now();
  const requirement: RequirementDto = {
    id: nextId('REQ'),
    projectId,
    title,
    description,
    status: 'PRD_GENERATING',
    prdVersion: 1,
    prdContent: '',
    createdDate: ts,
    lastEditedDate: ts,
  };
  db.requirements.set(requirement.id, requirement);
  setTimeout(() => {
    requirement.status = 'PRD_REVIEW';
    requirement.prdContent = `# PRD: ${title}

## 1. 需求概述

${description}

## 2. 业务目标

- 明确需求目标
- 为概览设计和模块详细设计提供依据

## 3. 功能需求

- 待补充

## 4. 验收标准

- 待补充
`;
    requirement.lastEditedDate = now();
  }, 500);
  return requirement;
}

/** confirm PRD and create overview design */
export function confirmRequirementPrd(id: string): OverviewDesignDto | null {
  const requirement = db.requirements.get(id);
  if (!requirement || requirement.status !== 'PRD_REVIEW') return null;
  requirement.status = 'PRD_CONFIRMED';
  requirement.lastEditedDate = now();

  const ts = now();
  const overview: OverviewDesignDto = {
    id: nextId('OVD'),
    projectId: requirement.projectId,
    requirementId: requirement.id,
    status: 'GENERATING',
    version: 1,
    content: '',
    createdDate: ts,
    lastEditedDate: ts,
  };
  db.overviewDesigns.set(overview.id, overview);
  setTimeout(() => {
    overview.status = 'DRAFT';
    overview.content = `# 概览设计

## 1. 设计目标

基于 PRD 将需求拆分为按模块落地的实现方案。

## 2. 模块清单

| moduleId | moduleTitle | summary |
| --- | --- | --- |
| default-module | 默认模块 | 默认模块的职责待补充 |

## 3. 总体架构

待补充。
`;
    overview.lastEditedDate = now();
  }, 500);
  return overview;
}

/** confirm overview design and create detailed designs */
export function confirmOverviewDesignMock(id: string): DetailedDesignDto[] {
  const overview = db.overviewDesigns.get(id);
  if (!overview || overview.status !== 'DRAFT') return [];
  overview.status = 'CONFIRMED';
  overview.lastEditedDate = now();

  const ts = now();
  const design: DetailedDesignDto = {
    id: nextId('DDD'),
    projectId: overview.projectId,
    requirementId: overview.requirementId,
    overviewDesignId: overview.id,
    moduleId: 'default-module',
    moduleTitle: '默认模块',
    status: 'REVIEW',
    version: 1,
    content: `# 详细设计: 默认模块

## 1. 模块目标

默认模块的详细设计待补充。

## 2. 职责边界

- 待补充

## 3. 接口设计

- 待补充
`,
    createdDate: ts,
    lastEditedDate: ts,
  };
  db.detailedDesigns.set(design.id, design);
  return [design];
}

/** confirm detailed design and create coding task */
export function confirmDetailedDesignMock(id: string): CodingTaskDto | null {
  const design = db.detailedDesigns.get(id);
  if (!design || design.status !== 'REVIEW') return null;
  design.status = 'CONFIRMED';
  design.lastEditedDate = now();

  const ts = now();
  const task: CodingTaskDto = {
    id: nextId('CDT'),
    projectId: design.projectId,
    requirementId: design.requirementId,
    detailedDesignId: design.id,
    detailedDesignVersion: design.version,
    status: 'PENDING',
    title: design.moduleTitle,
    description: design.content,
    fileScope: [],
    createdDate: ts,
    lastEditedDate: ts,
  };
  db.codingTasks.set(task.id, task);
  return task;
}

/** run coding task */
export function runCodingTaskMock(id: string, userPrompt?: string | null): { ok: boolean; message?: string; task?: CodingTaskDto; run?: RunDto } {
  const task = db.codingTasks.get(id);
  if (!task) return { ok: false, message: 'task not found' };
  if (task.status === 'RUNNING') return { ok: false, message: 'task already running' };

  const existingRuns = Array.from(db.runs.values()).filter((r) => r.codingTaskId === id);
  const runNo = existingRuns.length + 1;
  const ts = now();
  const run: RunDto = {
    id: nextId('RUN'),
    codingTaskId: id,
    runNo,
    triggerSource: userPrompt ? 'USER_ACTION' : 'AUTO',
    state: 'RUNNING',
    userPrompt: userPrompt || null,
    startedDate: ts,
  };
  db.runs.set(run.id, run);
  task.status = 'RUNNING';
  task.lastEditedDate = ts;

  setTimeout(() => {
    run.state = 'SUCCEEDED';
    run.finishedDate = now();
    task.status = 'SUCCEEDED';
    task.lastEditedDate = now();
  }, 500);

  return { ok: true, task, run };
}

/** rerun coding task */
export function rerunCodingTaskMock(id: string, rerunPrompt: string): { ok: boolean; message?: string; task?: CodingTaskDto; run?: RunDto } {
  if (!rerunPrompt || !rerunPrompt.trim()) {
    return { ok: false, message: 'rerunPrompt is required' };
  }
  const task = db.codingTasks.get(id);
  if (!task) return { ok: false, message: 'task not found' };

  const previousFailure = Array.from(db.runs.values())
    .filter((r) => r.codingTaskId === id && r.state === 'FAILED')
    .sort((a, b) => b.runNo - a.runNo)[0];
  const prompt = previousFailure?.failureReason
    ? `${rerunPrompt}\n\n上一次失败原因：${previousFailure.failureReason}`
    : rerunPrompt;
  return runCodingTaskMock(id, prompt);
}

/** compatibility implementation: legacy refine design → detailed design, project → SPEC_REVIEW. */
export function refineSpec(
  projectId: string,
): { ok: true; spec: SpecDto } | { ok: false; message: string } {
  const project = db.projects.get(projectId);
  if (!project) return { ok: false, message: `project ${projectId} not found` };
  if (project.state !== 'DRAFTING' && project.state !== 'FAILED') {
    return { ok: false, message: `仅 DRAFTING/FAILED 状态可生成概要设计，当前为 ${project.state}` };
  }
  const version =
    Array.from(db.specs.values()).filter((s) => s.projectId === projectId).length + 1;
  const spec = buildSpec(project, version);
  db.specs.set(spec.id, spec);
  project.currentSpecId = spec.id;
  project.state = 'SPEC_REVIEW';
  project.lastEditedDate = now();
  return { ok: true, spec };
}

/** Semantic mock wrapper for UI-facing overview design generation. */
export function generateOverviewDesign(
  projectId: string,
): { ok: true; spec: SpecDto; plan: PlanDto } | { ok: false; message: string } {
  const res = refineSpec(projectId);
  if (!res.ok) return res;
  const project = db.projects.get(projectId);
  let plan: PlanDto | null = null;
  if (project) {
    plan = upsertOverviewDesign(project);
    project.state = 'PLANNING';
    project.lastEditedDate = now();
  }
  return plan ? { ...res, plan } : { ok: false, message: `project ${projectId} not found` };
}

/** confirm detailed design → generate overview design for task approval (contract ep #6) */
export function confirmSpec(specId: string): PlanDto | null {
  const spec = db.specs.get(specId);
  if (!spec) return null;
  const project = db.projects.get(spec.projectId);
  if (!project) return null;
  spec.state = 'CONFIRMED';

  const plans = Array.from(db.plans?.values() ?? []).filter((p) => p.projectId === project.id);
  plans.forEach((p) => {
    p.isLatest = false;
  });
  const latestVersion = plans.reduce((max, p) => Math.max(max, p.version), 0);
  const plan: PlanDto = {
    id: nextId('PLAN'),
    projectId: project.id,
    version: latestVersion + 1,
    status: 'GENERATING',
    content: {
      summary: '',
      techAssumptions: [],
      features: [],
      nonGoals: [],
    },
    modifyHint: undefined,
    isLatest: true,
    createdDate: now(),
    lastEditedDate: now(),
  };
  db.plans?.set(plan.id, plan);
  project.currentSpecId = spec.id;
  project.lastEditedDate = now();
  return plan;
}

/** Detailed design version history for a project, ordered by version (legacy endpoint). */
export function specsOf(projectId: string): SpecDto[] {
  return Array.from(db.specs.values())
    .filter((s) => s.projectId === projectId)
    .sort((a, b) => a.version - b.version);
}

/**
 * regenerate detailed design — version+1, latest → GENERATING（契约 #R，镜像 Plan regenerate）。
 * mock 异步：2s 后填充内容并翻 SPEC_REVIEW，让前端轮询能拿到结果。
 */
export function regenerateSpec(
  projectId: string,
  modifyHint?: string,
): { ok: true; spec: SpecDto } | { ok: false; message: string } {
  const project = db.projects.get(projectId);
  if (!project) return { ok: false, message: `project ${projectId} not found` };
  if (project.state !== 'SPEC_REVIEW') {
    return { ok: false, message: `仅 SPEC_REVIEW 状态可重新生成详细设计，当前为 ${project.state}` };
  }
  const latest = Array.from(db.specs.values())
    .filter((s) => s.projectId === projectId)
    .sort((a, b) => b.version - a.version)[0];
  if (latest && latest.state === 'GENERATING') {
    return { ok: false, message: '详细设计正在生成中，不可重复发起' };
  }
  const version = (latest?.version ?? 0) + 1;
  const spec: SpecDto = {
    id: nextId('SPEC'),
    projectId,
    version,
    state: 'GENERATING',
    pages: [],
    components: [],
    entities: [],
    apiContract: [],
    modifyHint: modifyHint ?? null,
    createdDate: now(),
  };
  db.specs.set(spec.id, spec);
  project.currentSpecId = spec.id;
  project.lastEditedDate = now();
  // project 保持 SPEC_REVIEW（不经过 SPEC_REFINING）；2s 后填充内容并翻 SPEC_REVIEW。
  setTimeout(() => {
    const built = buildSpec(project, version);
    spec.pages = built.pages;
    spec.components = built.components;
    spec.entities = built.entities;
    spec.apiContract = built.apiContract;
    spec.state = 'SPEC_REVIEW';
  }, 2000);
  return { ok: true, spec };
}

// --- Phase 5: Config Surface + Workspace resolve (contract §1–§3) ---

/**
 * Read the singleton platform config (contract ep #31), creating the default row
 * on first access. `workspaceRoot` defaults to the OS temp path; `templateGitlabUrl`
 * has no default (empty → scaffold path is the day-one path).
 */
export function getConfig(): PlatformConfigDto {
  if (!db.config) {
    db.config = {
      id: CONFIG_ID,
      workspaceRoot: DEFAULT_WORKSPACE_ROOT,
      templateGitlabUrl: '',
      createdDate: now(),
    };
  }
  return db.config;
}

/**
 * Upsert the singleton platform config (contract ep #32). Empty `workspaceRoot`
 * falls back to the default; the fixed id and createdDate are preserved across saves.
 */
export function saveConfig(input: {
  workspaceRoot?: string;
  templateGitlabUrl?: string;
}): PlatformConfigDto {
  const current = getConfig();
  current.workspaceRoot = input.workspaceRoot?.trim() || DEFAULT_WORKSPACE_ROOT;
  current.templateGitlabUrl = input.templateGitlabUrl?.trim() ?? '';
  return current;
}

/**
 * Resolve a project's workspace dir (contract ep #33 / §3). `source` = CLONE when
 * a template URL is configured, else SCAFFOLD. `provisioned` = true when the dir
 * already existed (clone-once reuse — the source of the FIRST provisioning is
 * kept, never re-decided). Path is `<workspaceRoot>/<projectId>`.
 */
export function resolveWorkspace(projectId: string): WorkspaceResolveResult | null {
  if (!projectId) return null;
  const config = getConfig();
  const path = `${config.workspaceRoot}/${projectId}`;
  const prior = db.workspaces.get(projectId);
  if (prior) {
    return { path, provisioned: true, source: prior };
  }
  const source: WorkspaceSource = config.templateGitlabUrl.trim() ? 'CLONE' : 'SCAFFOLD';
  db.workspaces.set(projectId, source);
  return { path, provisioned: false, source };
}

/** seed a couple of projects so the list is not empty on first load */
export function seed(): void {
  if (db.projects.size > 0) return;
  const p1 = createProject('库存管理台', '需要一个库存管理台：库存列表分页、关键字搜索、新增入库单。');
  const p2 = createProject('设备巡检系统', '设备巡检：巡检计划、巡检记录、异常上报与统计看板。');
  // advance p2 into SPEC_REVIEW so reviewers have something to open immediately
  refineSpec(p2.id);
  seedSkillsAndAgents();
}

// --- Phase 3: Skills + Custom Agents (contract §1, §4, §6) ---

/**
 * djb2 string hash → 8 hex chars. This mock plays the SERVER role, so it (not
 * the FE) produces `computedHash`. The real backend uses the §6 sha256
 * length-prefixed recipe; here we only need a *deterministic* digest over the
 * same canonical parts so that re-importing identical content is idempotent
 * (same hash → dedupe, no new row). The FE never recomputes either way.
 */
function digest(parts: string[]): string {
  // length-prefixed join mirrors §6's boundary-safe concatenation
  const canonical = parts.map((p) => `${p.length}${p}`).join('');
  let h = 5381;
  for (let i = 0; i < canonical.length; i += 1) {
    h = ((h << 5) + h + canonical.charCodeAt(i)) | 0;
  }
  return `sha256:${(h >>> 0).toString(16).padStart(8, '0')}`;
}

/** compute a skill's lock over ("v1", config.origin, name, description, content) — §6 order */
function computeSkillHash(s: {
  config: SkillConfig;
  name: string;
  description: string;
  content: string;
}): string {
  return digest(['v1', s.config.origin, s.name, s.description, s.content]);
}

/**
 * Import + hash-lock a skill (contract ep #16). Dedup by name: returns null when
 * a skill with the same name already exists (handler maps to 409). Auxiliary
 * `files` are stored but excluded from the §6 hash lock (import is immutable).
 */
export function importSkill(input: {
  name: string;
  description: string;
  config: SkillConfig;
  content: string;
  files?: SkillFileDto[];
}): SkillDto | null {
  const existing = Array.from(db.skills.values()).find((s) => s.name === input.name);
  if (existing) return null;

  const computedHash = computeSkillHash({
    config: input.config,
    name: input.name,
    description: input.description,
    content: input.content,
  });
  const skill: SkillDto = {
    id: nextId('SKIL'),
    name: input.name,
    description: input.description,
    config: input.config,
    content: input.content,
    files: input.files ?? [],
    computedHash,
    createdDate: now(),
  };
  db.skills.set(skill.id, skill);
  return skill;
}

/** Delete a skill (contract ep #19); rejected if bound to any agent. */
export function deleteSkill(id: string): { ok: boolean; message: string } {
  if (!db.skills.has(id)) return { ok: false, message: `skill ${id} not found` };
  const boundBy = Array.from(db.agents.values()).filter((a) => a.skillIds.includes(id));
  if (boundBy.length) {
    return {
      ok: false,
      message: `技能被 ${boundBy.length} 个 Agent 绑定，无法删除`,
    };
  }
  db.skills.delete(id);
  return { ok: true, message: '删除成功' };
}

/** Create/update a custom agent (contract ep #20). No id = create. */
export function saveAgent(input: {
  id?: string;
  name: string;
  description: string;
  instructions: string;
  model: string;
  cliTool: string;
}): AgentDto {
  if (input.id) {
    const existing = db.agents.get(input.id);
    if (existing) {
      existing.name = input.name;
      existing.description = input.description;
      existing.instructions = input.instructions;
      existing.model = input.model;
      existing.cliTool = input.cliTool;
      return existing;
    }
  }
  const agent: AgentDto = {
    id: nextId('AGNT'),
    name: input.name,
    description: input.description,
    instructions: input.instructions,
    model: input.model ?? '',
    cliTool: input.cliTool ?? '',
    builtin: false,
    skillIds: [],
    createdDate: now(),
  };
  db.agents.set(agent.id, agent);
  return agent;
}

/** Delete a custom agent (contract ep #23); built-in agents are rejected. */
export function deleteAgent(id: string): { ok: boolean; message: string } {
  const agent = db.agents.get(id);
  if (!agent) return { ok: false, message: `agent ${id} not found` };
  if (agent.builtin) return { ok: false, message: '内置 Agent 不可删除' };
  db.agents.delete(id);
  return { ok: true, message: '删除成功' };
}

/** Attach/replace an agent's bound skills (contract ep #24). */
export function attachAgentSkills(agentId: string, skillIds: string[]): AgentDto | null {
  const agent = db.agents.get(agentId);
  if (!agent) return null;
  // keep ids that resolve to real skills, plus builtin:<name> synthetic ids (multica dim g —
  // builtins live on the classpath, not in db.skills)
  agent.skillIds = (skillIds ?? []).filter((sid) => sid.startsWith('builtin:') || db.skills.has(sid));
  return agent;
}

/**
 * Seed the built-in agents (contract §4). Built-in skills (suid/eadp-backend/
 * project-planning/feature-design) are NOT seeded as oc_skill rows — they live
 * on the backend classpath and bind via builtin:<name> synthetic ids (multica
 * dim g). One custom dev agent is seeded bound to builtin:suid as a live example.
 */
function seedSkillsAndAgents(): void {
  if (db.skills.size > 0 || db.agents.size > 0) return;

  const seedAgent = (name: string, description: string): void => {
    const agent: AgentDto = {
      id: nextId('AGNT'),
      name,
      description,
      instructions: '',
      model: '',
      cliTool: 'claude',
      builtin: true,
      skillIds: [],
      createdDate: now(),
    };
    db.agents.set(agent.id, agent);
  };
  seedAgent('requirement-agent', '内置：概要设计 Agent');
  seedAgent('dispatch-agent', '内置：任务派发 Agent');
  seedAgent('deploy-agent', '内置：部署 Agent');

  // one custom dev agent bound to the built-in suid skill, so the two-step flow has a live example
  const devAgent = saveAgent({
    name: 'suid-dev',
    description: '按 EADP 契约实现 SUID 页面',
    instructions: '你负责实现单个页面，遵循 @ead/suid 组件库规范。',
    model: '',
    cliTool: 'claude',
  });
  attachAgentSkills(devAgent.id, ['builtin:suid']);
}
