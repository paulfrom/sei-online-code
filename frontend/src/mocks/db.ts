/**
 * In-memory mock database for the sei-online-code Phase 1 walking skeleton.
 * Holds Project / Spec / Iteration entities and drives the lifecycle state
 * machine defined in docs/contracts/API-CONTRACT.md §4.
 *
 * MSW-first (ADR-0002): this is the single source of live data for the
 * frontend; no real backend is involved in Phase 1.
 */

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
  /** internal: epoch ms when merge was triggered (drives MERGING→DEPLOYING flip) */
  _mergeAt?: number;
  /** internal: epoch ms when deploy was triggered (drives DEPLOYING→PREVIEW flip) */
  _deployAt?: number;
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
  /** internal: epoch ms when this run started (drives RUNNING→SUCCEEDED flip) */
  _startAt?: number;
}

/** SkillSourceType — verbatim per Phase 3 contract §1.1. */
export type SkillSourceType = 'GITHUB' | 'LOCAL' | 'INLINE';

/** SkillDto — an importable, hash-locked instruction bundle (Phase 3 §1.1). */
export interface SkillDto {
  id: string;
  name: string;
  description: string;
  source: string;
  sourceType: SkillSourceType;
  content: string;
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
  builtin: boolean;
  skillIds: string[];
  createdDate: string;
}

const now = () => new Date().toISOString().slice(0, 19);

let seq = 1;
const nextId = (prefix: string) => `${prefix}${String(seq++).padStart(4, '0')}`;

/** simulated deploy duration in ms — findOne flips DEPLOYING → PREVIEW after this */
export const DEPLOY_DURATION_MS = 4000;

/** simulated per-run duration in ms — readRun flips RUNNING → SUCCEEDED after this */
export const RUN_DURATION_MS = 3000;

/** simulated merge duration in ms — readIteration flips MERGING → DEPLOYING after this */
export const MERGE_DURATION_MS = 2000;

/** per-project static preview port base (contract §2.3 example uses 41001) */
let previewPort = 41001;

interface Db {
  projects: Map<string, ProjectDto>;
  specs: Map<string, SpecDto>;
  iterations: Map<string, IterationDto>;
  tasks: Map<string, TaskDto>;
  runs: Map<string, RunDto>;
  skills: Map<string, SkillDto>;
  agents: Map<string, AgentDto>;
}

export const db: Db = {
  projects: new Map(),
  specs: new Map(),
  iterations: new Map(),
  tasks: new Map(),
  runs: new Map(),
  skills: new Map(),
  agents: new Map(),
};

/** Build a demo Spec structure from a project's design prose. */
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

/** create project → DRAFTING (contract ep #1) */
export function createProject(name: string, design: string): ProjectDto {
  const ts = now();
  const project: ProjectDto = {
    id: nextId('PRJ'),
    name,
    design,
    state: 'DRAFTING',
    currentSpecId: null,
    currentIterationId: null,
    createdDate: ts,
    lastEditedDate: ts,
  };
  db.projects.set(project.id, project);
  return project;
}

/** refine design → Spec, project → SPEC_REVIEW (contract ep #4) */
export function refineSpec(projectId: string): SpecDto | null {
  const project = db.projects.get(projectId);
  if (!project) return null;
  const version =
    Array.from(db.specs.values()).filter((s) => s.projectId === projectId).length + 1;
  const spec = buildSpec(project, version);
  db.specs.set(spec.id, spec);
  project.currentSpecId = spec.id;
  project.state = 'SPEC_REVIEW';
  project.lastEditedDate = now();
  return spec;
}

/** confirm Spec → start iteration, project → DISPATCHING (contract ep #6) */
export function confirmSpec(specId: string): IterationDto | null {
  const spec = db.specs.get(specId);
  if (!spec) return null;
  const project = db.projects.get(spec.projectId);
  if (!project) return null;
  spec.state = 'CONFIRMED';
  const iteration: IterationDto = {
    id: nextId('ITER'),
    projectId: project.id,
    specId: spec.id,
    specVersion: spec.version,
    state: 'DISPATCHING',
    previewUrl: null,
    createdDate: now(),
  };
  db.iterations.set(iteration.id, iteration);
  project.currentIterationId = iteration.id;
  project.state = 'DISPATCHING';
  project.lastEditedDate = now();
  return iteration;
}

/** deploy iteration → DEPLOYING (flips to PREVIEW after DEPLOY_DURATION_MS) */
export function deployIteration(iterationId: string): IterationDto | null {
  const iteration = db.iterations.get(iterationId);
  if (!iteration) return null;
  const project = db.projects.get(iteration.projectId);
  iteration.state = 'DEPLOYING';
  iteration._deployAt = Date.now();
  if (project) {
    project.state = 'DEPLOYING';
    project.lastEditedDate = now();
  }
  return iteration;
}

/**
 * Read an iteration, advancing DEPLOYING → PREVIEW once the simulated build
 * duration has elapsed, and MERGING → DEPLOYING once the merge duration has
 * elapsed. This is the polling fallback (contract §3.1 / F2 / F12).
 */
export function readIteration(iterationId: string): IterationDto | null {
  const iteration = db.iterations.get(iterationId);
  if (!iteration) return null;
  // MERGING → DEPLOYING (ep #15 completes async; contract §4)
  if (
    iteration.state === 'MERGING' &&
    iteration._mergeAt &&
    Date.now() - iteration._mergeAt >= MERGE_DURATION_MS
  ) {
    iteration.state = 'DEPLOYING';
    iteration._deployAt = Date.now();
    // all tasks finish merging back
    Array.from(db.tasks.values())
      .filter((t) => t.iterationId === iterationId)
      .forEach((t) => {
        if (t.state === 'MERGING') t.state = 'MERGED';
      });
    const project = db.projects.get(iteration.projectId);
    if (project) {
      project.state = 'DEPLOYING';
      project.lastEditedDate = now();
    }
  }
  if (
    iteration.state === 'DEPLOYING' &&
    iteration._deployAt &&
    Date.now() - iteration._deployAt >= DEPLOY_DURATION_MS
  ) {
    iteration.state = 'PREVIEW';
    iteration.previewUrl = `http://localhost:${previewPort++}`;
    const project = db.projects.get(iteration.projectId);
    if (project) {
      project.state = 'PREVIEW';
      project.lastEditedDate = now();
    }
  }
  return iteration;
}

/**
 * Dispatch Agent (contract ep #10): cut the confirmed Spec into ≥2 disjoint
 * tasks by fileScope, spawn one parallel Run per task (state RUNNING), and move
 * the project DISPATCHING → DEVELOPING. Idempotent: returns existing tasks if
 * already dispatched.
 */
export function dispatchIteration(iterationId: string): TaskDto[] | null {
  const iteration = db.iterations.get(iterationId);
  if (!iteration) return null;
  const project = db.projects.get(iteration.projectId);
  if (!project) return null;

  const existing = tasksOf(iterationId);
  if (existing.length) return existing;

  // Cut disjoint tasks from the spec's pages (fallback to a 2-task default).
  const spec = db.specs.get(iteration.specId);
  const pages = spec?.pages?.length
    ? spec.pages
    : [
        { key: 'list', title: '列表页', route: '/list', description: '分页列表' },
        { key: 'detail', title: '详情页', route: '/detail', description: '详情视图' },
      ];

  const tasks: TaskDto[] = pages.map((page, idx) => {
    const seq = idx + 1;
    const iterNum = iterationId.replace(/\D/g, '').slice(-4) || '0001';
    const task: TaskDto = {
      id: nextId('TASK'),
      iterationId,
      title: page.title,
      description: `实现 ${page.route} 页面 + 对应 mock`,
      fileScope: [`src/pages${page.route}.tsx`, `src/mocks/${page.key}.ts`],
      assignedAgent: 'dev-agent',
      state: 'RUNNING',
      worktreeBranch: `task/${iterNum}-${String(seq).padStart(4, '0')}`,
      seq,
      createdDate: now(),
    };
    db.tasks.set(task.id, task);

    // one parallel Run per task, starts RUNNING
    const run: RunDto = {
      id: nextId('RUN'),
      taskId: task.id,
      iterationId,
      state: 'RUNNING',
      worktreePath: `/tmp/rapid-app-dev/${project.id}/wt-${task.id}`,
      exitCode: null,
      startedDate: now(),
      finishedDate: null,
      _startAt: Date.now(),
    };
    db.runs.set(run.id, run);
    return task;
  });

  iteration.state = 'DEVELOPING';
  project.state = 'DEVELOPING';
  project.lastEditedDate = now();
  return tasks;
}

/** All tasks of an iteration, ordered by dispatch seq. */
export function tasksOf(iterationId: string): TaskDto[] {
  return Array.from(db.tasks.values())
    .filter((t) => t.iterationId === iterationId)
    .sort((a, b) => a.seq - b.seq);
}

/**
 * Read a run, advancing RUNNING → SUCCEEDED once the simulated run duration has
 * elapsed (also marks its task's dev work done, keeping the task RUNNING until
 * merge). This is the per-run polling fallback for the WS run-log (contract §3).
 */
export function readRun(runId: string): RunDto | null {
  const run = db.runs.get(runId);
  if (!run) return null;
  if (run.state === 'RUNNING' && run._startAt && Date.now() - run._startAt >= RUN_DURATION_MS) {
    run.state = 'SUCCEEDED';
    run.exitCode = 0;
    run.finishedDate = now();
  }
  return run;
}

/** All runs of an iteration (optionally scoped to a task), advancing each. */
export function runsOf(iterationId: string, taskId?: string): RunDto[] {
  return Array.from(db.runs.values())
    .filter((r) => r.iterationId === iterationId && (!taskId || r.taskId === taskId))
    .map((r) => readRun(r.id) as RunDto)
    .sort((a, b) => (a.id < b.id ? -1 : 1));
}

/**
 * Merge all task worktrees back (contract ep #15): move tasks RUNNING → MERGING,
 * project DEVELOPING → MERGING; readIteration then advances MERGING → DEPLOYING
 * asynchronously. Returns the iteration.
 */
export function mergeIteration(iterationId: string): IterationDto | null {
  const iteration = db.iterations.get(iterationId);
  if (!iteration) return null;
  const project = db.projects.get(iteration.projectId);
  // ensure runs have advanced before merging
  runsOf(iterationId);
  tasksOf(iterationId).forEach((t) => {
    if (t.state === 'RUNNING') t.state = 'MERGING';
  });
  iteration.state = 'MERGING';
  iteration._mergeAt = Date.now();
  if (project) {
    project.state = 'MERGING';
    project.lastEditedDate = now();
  }
  return iteration;
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
  const canonical = parts.map((p) => `${p.length} ${p}`).join('');
  let h = 5381;
  for (let i = 0; i < canonical.length; i += 1) {
    h = ((h << 5) + h + canonical.charCodeAt(i)) | 0;
  }
  return `sha256:${(h >>> 0).toString(16).padStart(8, '0')}`;
}

/** compute a skill's lock over ("v1", source, name, description, content) — §6 order */
function computeSkillHash(s: {
  source: string;
  name: string;
  description: string;
  content: string;
}): string {
  return digest(['v1', s.source, s.name, s.description, s.content]);
}

/**
 * Import + hash-lock a skill (contract ep #16). Idempotent by hash: if a skill
 * with the same `computedHash` already exists, return it unchanged (no new row).
 */
export function importSkill(input: {
  name: string;
  description: string;
  source: string;
  sourceType: SkillSourceType;
  content: string;
}): SkillDto {
  const computedHash = computeSkillHash({
    source: input.source,
    name: input.name,
    description: input.description,
    content: input.content,
  });
  const existing = Array.from(db.skills.values()).find((s) => s.computedHash === computedHash);
  if (existing) return existing;

  const skill: SkillDto = {
    id: nextId('SKIL'),
    name: input.name,
    description: input.description,
    source: input.source,
    sourceType: input.sourceType,
    content: input.content,
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
}): AgentDto {
  if (input.id) {
    const existing = db.agents.get(input.id);
    if (existing) {
      existing.name = input.name;
      existing.description = input.description;
      existing.instructions = input.instructions;
      existing.model = input.model;
      return existing;
    }
  }
  const agent: AgentDto = {
    id: nextId('AGNT'),
    name: input.name,
    description: input.description,
    instructions: input.instructions,
    model: input.model ?? '',
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
  // keep only ids that resolve to real skills
  agent.skillIds = (skillIds ?? []).filter((sid) => db.skills.has(sid));
  return agent;
}

/**
 * Seed the LOCAL designated skills (`suid`, `eadp-backend`) and the 3 built-in
 * agents (contract §4). Content is a short pointer stub per §4.
 */
function seedSkillsAndAgents(): void {
  if (db.skills.size > 0 || db.agents.size > 0) return;

  const suid = importSkill({
    name: 'suid',
    description: '@ead/suid 组件库开发技能',
    source: 'local:suid',
    sourceType: 'LOCAL',
    content: '# SUID Skill\n\n本地指针存根：完整技能位于操作机 ~/.claude/skills/suid。',
  });
  importSkill({
    name: 'eadp-backend',
    description: 'sei-core 分层架构后端开发技能',
    source: 'local:eadp-backend',
    sourceType: 'LOCAL',
    content: '# EADP Backend Skill\n\n本地指针存根：完整技能位于操作机 ~/.claude/skills/eadp-backend。',
  });

  const seedAgent = (name: string, description: string): void => {
    const agent: AgentDto = {
      id: nextId('AGNT'),
      name,
      description,
      instructions: '',
      model: '',
      builtin: true,
      skillIds: [],
      createdDate: now(),
    };
    db.agents.set(agent.id, agent);
  };
  seedAgent('requirement-agent', '内置：需求解析 Agent');
  seedAgent('dispatch-agent', '内置：任务派发 Agent');
  seedAgent('deploy-agent', '内置：部署 Agent');

  // one custom dev agent bound to suid, so the two-step flow has a live example
  const devAgent = saveAgent({
    name: 'suid-dev',
    description: '按 EADP 契约实现 SUID 页面',
    instructions: '你负责实现单个页面，遵循 @ead/suid 组件库规范。',
    model: '',
  });
  attachAgentSkills(devAgent.id, [suid.id]);
}
