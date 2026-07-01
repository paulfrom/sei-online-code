/**
 * MSW request handlers for Phase 1 endpoints 1–9 (docs/contracts/API-CONTRACT.md §3).
 * Every response is wrapped in the EADP `ResultData<T>` envelope; list responses
 * wrap a `PageResult<T>` as the `data` payload.
 */
import { http, HttpResponse } from 'msw';
import type { Search } from '@/services/onlineCode';
import {
  acceptIteration,
  attachAgentSkills,
  cancelIteration,
  confirmSpec,
  createProject,
  db,
  deleteAgent,
  deleteSkill,
  deployIteration,
  dispatchIteration,
  importSkill,
  iterationsOf,
  mergeIteration,
  optimizeProject,
  readIteration,
  readRun,
  refineSpec,
  retryIteration,
  runsOf,
  saveAgent,
  seed,
  specsOf,
  tasksOf,
} from './db';

seed();

/** ResultData<T> success envelope (contract §1.1) */
function ok<T>(data: T, message: string | null = null) {
  return HttpResponse.json({ success: true, message, data });
}

/** ResultData failure envelope (contract §1.1) */
function fail(message: string) {
  return HttpResponse.json({ success: false, message, data: null });
}

/**
 * Apply a `Search` body to a row set: quickSearch LIKE + filters EQ, then
 * paginate into the `PageResult` shape (contract §1.2 — records=total rows,
 * total=total pages, rows=page slice).
 */
function paginate<T extends Record<string, any>>(rows: T[], search: Search) {
  let filtered = rows;

  const kw = search?.quickSearchValue?.trim();
  const props = search?.quickSearchProperties ?? [];
  if (kw && props.length) {
    const lower = kw.toLowerCase();
    filtered = filtered.filter((r) =>
      props.some((p) => String(r[p] ?? '').toLowerCase().includes(lower)),
    );
  }

  (search?.filters ?? []).forEach((f) => {
    if (f.operator === 'EQ') {
      filtered = filtered.filter((r) => String(r[f.fieldName]) === String(f.value));
    } else if (f.operator === 'LK') {
      filtered = filtered.filter((r) =>
        String(r[f.fieldName] ?? '')
          .toLowerCase()
          .includes(String(f.value).toLowerCase()),
      );
    }
  });

  (search?.sortOrders ?? []).forEach((s) => {
    filtered = [...filtered].sort((a, b) => {
      const av = a[s.property];
      const bv = b[s.property];
      const cmp = av < bv ? -1 : av > bv ? 1 : 0;
      return s.direction === 'DESC' ? -cmp : cmp;
    });
  });

  const page = search?.pageInfo?.page ?? 1;
  const size = search?.pageInfo?.rows ?? 15;
  const records = filtered.length;
  const total = Math.max(1, Math.ceil(records / size));
  const start = (page - 1) * size;
  const slice = filtered.slice(start, start + size);

  return { page, records, total, rows: slice };
}

export const handlers = [
  // #1 create project
  http.post('*/api/project/save', async ({ request }) => {
    const body = (await request.json()) as { name?: string; design?: string };
    if (!body?.name) return fail('name is required');
    const project = createProject(body.name, body.design ?? '');
    return ok(project, '创建成功');
  }),

  // #2 load one project
  http.get('*/api/project/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const project = db.projects.get(id);
    return project ? ok(project) : fail(`project ${id} not found`);
  }),

  // #3 list projects (Search body → PageResult)
  http.post('*/api/project/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const rows = Array.from(db.projects.values()).sort((a, b) =>
      a.createdDate < b.createdDate ? 1 : -1,
    );
    return ok(paginate(rows, search));
  }),

  // #4 refine design → Spec
  http.post('*/api/project/refineSpec', async ({ request }) => {
    const body = (await request.json()) as { projectId?: string };
    const spec = refineSpec(body?.projectId ?? '');
    return spec ? ok(spec, '需求已解析为 Spec') : fail('project not found');
  }),

  // #5 load a Spec
  http.get('*/api/spec/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const spec = db.specs.get(id);
    return spec ? ok(spec) : fail(`spec ${id} not found`);
  }),

  // #6 confirm Spec → start iteration
  http.post('*/api/spec/confirm', async ({ request }) => {
    const body = (await request.json()) as { specId?: string };
    const iteration = confirmSpec(body?.specId ?? '');
    return iteration ? ok(iteration, 'Spec 已确认，迭代已启动') : fail('spec not found');
  }),

  // #7 deploy iteration
  http.post('*/api/iteration/deploy', async ({ request }) => {
    const body = (await request.json()) as { iterationId?: string };
    const iteration = deployIteration(body?.iterationId ?? '');
    return iteration ? ok(iteration, '开始部署') : fail('iteration not found');
  }),

  // #8 poll iteration state / previewUrl
  http.get('*/api/iteration/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const iteration = readIteration(id);
    return iteration ? ok(iteration) : fail(`iteration ${id} not found`);
  }),

  // #9 poll project lifecycle
  http.get('*/api/project/state', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const project = db.projects.get(id);
    if (!project) return fail(`project ${id} not found`);
    // advance iteration state if a deploy is in flight so state stays coherent
    if (project.currentIterationId) readIteration(project.currentIterationId);
    return ok({ state: project.state, iterationId: project.currentIterationId });
  }),

  // #10 dispatch: confirmed Spec → disjoint tasks (state DISPATCHING→DEVELOPING)
  http.post('*/api/iteration/dispatch', async ({ request }) => {
    const body = (await request.json()) as { iterationId?: string };
    const tasks = dispatchIteration(body?.iterationId ?? '');
    return tasks ? ok(tasks, `已派发 ${tasks.length} 个任务`) : fail('iteration not found');
  }),

  // #11 list tasks (Search body → PageResult), filter by iterationId
  http.post('*/api/task/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const iterationId = search?.filters?.find((f) => f.fieldName === 'iterationId')?.value;
    let rows = Array.from(db.tasks.values());
    if (iterationId) rows = rows.filter((t) => t.iterationId === String(iterationId));
    rows = rows.sort((a, b) => a.seq - b.seq);
    return ok(paginate(rows, search));
  }),

  // #12 load one task
  http.get('*/api/task/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const task = db.tasks.get(id);
    return task ? ok(task) : fail(`task ${id} not found`);
  }),

  // #13 list runs (Search body → PageResult), filter by iterationId / taskId
  http.post('*/api/run/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const iterationId = search?.filters?.find((f) => f.fieldName === 'iterationId')?.value;
    const taskId = search?.filters?.find((f) => f.fieldName === 'taskId')?.value;
    // advance runs for the scoped iteration so states stay coherent while polling
    if (iterationId) runsOf(String(iterationId), taskId ? String(taskId) : undefined);
    let rows = Array.from(db.runs.values());
    if (iterationId) rows = rows.filter((r) => r.iterationId === String(iterationId));
    if (taskId) rows = rows.filter((r) => r.taskId === String(taskId));
    return ok(paginate(rows, search));
  }),

  // #14 poll one run's state / exitCode
  http.get('*/api/run/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const run = readRun(id);
    return run ? ok(run) : fail(`run ${id} not found`);
  }),

  // #15 merge all task worktrees back (state MERGING→DEPLOYING)
  http.post('*/api/iteration/merge', async ({ request }) => {
    const body = (await request.json()) as { iterationId?: string };
    const iteration = mergeIteration(body?.iterationId ?? '');
    return iteration ? ok(iteration, '开始合并') : fail('iteration not found');
  }),

  // #16 import + hash-lock a skill; idempotent by hash
  http.post('*/api/skill/import', async ({ request }) => {
    const body = (await request.json()) as {
      name?: string;
      description?: string;
      source?: string;
      sourceType?: 'GITHUB' | 'LOCAL' | 'INLINE';
      content?: string;
    };
    if (!body?.name) return fail('name is required');
    if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(body.name)) {
      return fail('name 必须匹配 ^[a-z0-9][a-z0-9-]{0,63}$');
    }
    if (!body?.content) return fail('content is required');
    const skill = importSkill({
      name: body.name,
      description: body.description ?? '',
      source: body.source ?? `inline:${body.name}`,
      sourceType: body.sourceType ?? 'INLINE',
      content: body.content,
    });
    return ok(skill, '技能已导入');
  }),

  // #17 list skills (Search body → PageResult)
  http.post('*/api/skill/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const rows = Array.from(db.skills.values()).sort((a, b) =>
      a.createdDate < b.createdDate ? 1 : -1,
    );
    return ok(paginate(rows, search));
  }),

  // #18 load one skill
  http.get('*/api/skill/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const skill = db.skills.get(id);
    return skill ? ok(skill) : fail(`skill ${id} not found`);
  }),

  // #19 delete a skill (rejected if bound to any agent)
  http.delete('*/api/skill/delete', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const res = deleteSkill(id);
    return res.ok ? ok(null, res.message) : fail(res.message);
  }),

  // #20 create/update a custom agent (no id = create)
  http.post('*/api/agent/save', async ({ request }) => {
    const body = (await request.json()) as {
      id?: string;
      name?: string;
      description?: string;
      instructions?: string;
      model?: string;
    };
    if (!body?.name) return fail('name is required');
    if (body.id) {
      const existing = db.agents.get(body.id);
      if (existing?.builtin) return fail('内置 Agent 不可编辑');
    }
    const agent = saveAgent({
      id: body.id,
      name: body.name,
      description: body.description ?? '',
      instructions: body.instructions ?? '',
      model: body.model ?? '',
    });
    return ok(agent, '保存成功');
  }),

  // #21 list agents (built-in + custom)
  http.post('*/api/agent/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    // built-in first, then custom by newest
    const rows = Array.from(db.agents.values()).sort((a, b) => {
      if (a.builtin !== b.builtin) return a.builtin ? -1 : 1;
      return a.createdDate < b.createdDate ? 1 : -1;
    });
    return ok(paginate(rows, search));
  }),

  // #22 load one agent
  http.get('*/api/agent/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const agent = db.agents.get(id);
    return agent ? ok(agent) : fail(`agent ${id} not found`);
  }),

  // #23 delete a custom agent (built-in rejected)
  http.delete('*/api/agent/delete', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const res = deleteAgent(id);
    return res.ok ? ok(null, res.message) : fail(res.message);
  }),

  // #24 attach/replace an agent's bound skills
  http.post('*/api/agent/skills', async ({ request }) => {
    const body = (await request.json()) as { agentId?: string; skillIds?: string[] };
    const agent = attachAgentSkills(body?.agentId ?? '', body?.skillIds ?? []);
    return agent ? ok(agent, '技能已绑定') : fail('agent not found');
  }),

  // --- Phase 4: Full Build Loop (contract eps #25–30) ---

  // #25 accept: PREVIEW → ACCEPTED (sets finishedDate)
  http.post('*/api/iteration/accept', async ({ request }) => {
    const body = (await request.json()) as { iterationId?: string };
    const res = acceptIteration(body?.iterationId ?? '');
    return res.ok ? ok(res.iteration, '已验收') : fail(res.message);
  }),

  // #26 optimize: PREVIEW → new Spec version + new iteration (round+1) → SPEC_REVIEW
  http.post('*/api/project/optimize', async ({ request }) => {
    const body = (await request.json()) as { projectId?: string; feedback?: string };
    const res = optimizeProject(body?.projectId ?? '', body?.feedback ?? '');
    return res.ok ? ok(res.iteration, '已生成新一轮 Spec，请评审确认') : fail(res.message);
  }),

  // #27 timeline: list a project's iterations (filter by projectId, order by round)
  http.post('*/api/iteration/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const projectId = search?.filters?.find((f) => f.fieldName === 'projectId')?.value;
    let rows = Array.from(db.iterations.values());
    if (projectId) {
      rows = iterationsOf(String(projectId));
    } else {
      rows = rows.sort((a, b) => a.round - b.round);
    }
    return ok(paginate(rows, search));
  }),

  // #28 cancel: abort active iteration → CANCELLED (cascade RUNNING tasks/runs)
  http.post('*/api/iteration/cancel', async ({ request }) => {
    const body = (await request.json()) as { iterationId?: string };
    const res = cancelIteration(body?.iterationId ?? '');
    return res.ok ? ok(res.iteration, '迭代已取消') : fail(res.message);
  }),

  // #29 retry: from FAILED, re-dispatch the same Spec version → DISPATCHING
  http.post('*/api/iteration/retry', async ({ request }) => {
    const body = (await request.json()) as { iterationId?: string };
    const res = retryIteration(body?.iterationId ?? '');
    return res.ok ? ok(res.iteration, '迭代已重新派发') : fail(res.message);
  }),

  // #30 spec version history for a project (ordered by version)
  http.get('*/api/spec/findByProject', ({ request }) => {
    const projectId = new URL(request.url).searchParams.get('projectId') ?? '';
    if (!projectId) return fail('projectId is required');
    return ok(specsOf(projectId));
  }),
];
