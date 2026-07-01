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
  /** internal: epoch ms when deploy was triggered (drives the polling flip) */
  _deployAt?: number;
}

const now = () => new Date().toISOString().slice(0, 19);

let seq = 1;
const nextId = (prefix: string) => `${prefix}${String(seq++).padStart(4, '0')}`;

/** simulated deploy duration in ms — findOne flips DEPLOYING → PREVIEW after this */
export const DEPLOY_DURATION_MS = 4000;

/** per-project static preview port base (contract §2.3 example uses 41001) */
let previewPort = 41001;

interface Db {
  projects: Map<string, ProjectDto>;
  specs: Map<string, SpecDto>;
  iterations: Map<string, IterationDto>;
}

export const db: Db = {
  projects: new Map(),
  specs: new Map(),
  iterations: new Map(),
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
 * duration has elapsed. This is the polling fallback (contract §3.1 / F2):
 * the client polls findOne until state === 'PREVIEW'.
 */
export function readIteration(iterationId: string): IterationDto | null {
  const iteration = db.iterations.get(iterationId);
  if (!iteration) return null;
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

/** seed a couple of projects so the list is not empty on first load */
export function seed(): void {
  if (db.projects.size > 0) return;
  const p1 = createProject('库存管理台', '需要一个库存管理台：库存列表分页、关键字搜索、新增入库单。');
  const p2 = createProject('设备巡检系统', '设备巡检：巡检计划、巡检记录、异常上报与统计看板。');
  // advance p2 into SPEC_REVIEW so reviewers have something to open immediately
  refineSpec(p2.id);
}
