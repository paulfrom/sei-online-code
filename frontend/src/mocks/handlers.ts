/**
 * MSW request handlers for Phase 1 endpoints 1–9 (docs/contracts/API-CONTRACT.md §3).
 * Every response is wrapped in the EADP `ResultData<T>` envelope; list responses
 * wrap a `PageResult<T>` as the `data` payload.
 */
import { http, HttpResponse } from 'msw';
import type { Search } from '@/services/onlineCode';
import {
  confirmSpec,
  createProject,
  db,
  deployIteration,
  dispatchIteration,
  mergeIteration,
  readIteration,
  readRun,
  refineSpec,
  runsOf,
  seed,
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
];
