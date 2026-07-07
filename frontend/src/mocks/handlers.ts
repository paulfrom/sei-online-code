/**
 * MSW request handlers for Phase 1 endpoints 1–9 (docs/contracts/API-CONTRACT.md §3).
 * Every response is wrapped in the EADP `ResultData<T>` envelope; list responses
 * wrap a `PageResult<T>` as the `data` payload.
 */
import { http, HttpResponse } from 'msw';
import type { Search } from '@/services/onlineCode';
import {
  attachAgentSkills,
  confirmDetailedDesignMock,
  confirmOverviewDesignMock,
  confirmRequirementPrd,
  confirmSpec,
  createProject,
  createRequirement,
  db,
  deleteAgent,
  deleteSkill,
  generateOverviewDesign,
  getConfig,
  importSkill,
  now,
  regenerateSpec,
  resolveWorkspace,
  runCodingTaskMock,
  rerunCodingTaskMock,
  saveAgent,
  saveConfig,
  saveProject,
  seed,
  specsOf,
} from './db';
import { planHandlers } from './handlers/plan';
import { featureDesignHandlers } from './handlers/featureDesign';

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
      props.some((p) => String(r[p] ?? '').toLowerCase().includes(lower))
    );
  }

  (search?.filters ?? []).forEach((f) => {
    if (f.operator === 'EQ') {
      filtered = filtered.filter((r) => String(r[f.fieldName]) === String(f.value));
    } else if (f.operator === 'LK') {
      filtered = filtered.filter((r) =>
        String(r[f.fieldName] ?? '')
          .toLowerCase()
          .includes(String(f.value).toLowerCase())
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
  ...planHandlers,
  ...featureDesignHandlers,

  // TODO(cleanup): sibling spec handlers below use stale */api/ prefix, broken in mock mode; migrate separately

  // #R regenerate detailed design — new version from SPEC_REVIEW
  http.post('*/sei-online-code/spec/:projectId/regenerate', async ({ params, request }) => {
    const body = (await request.json().catch(() => ({}))) as { modifyHint?: string };
    const res = regenerateSpec(String(params.projectId), body?.modifyHint);
    return res.ok ? ok(res.spec, '已重新生成详细设计') : fail(res.message);
  }),

  // #1 create/update project
  http.post('*/api/project/save', async ({ request }) => {
    const body = (await request.json()) as {
      id?: string;
      name?: string;
      design?: string;
      gitUrl?: string;
      workspacePath?: string;
      autoRunCodingTask?: boolean;
    };
    if (!body?.name) return fail('name is required');
    const project = saveProject({
      id: body.id,
      name: body.name,
      design: body.design ?? '',
      gitUrl: body.gitUrl,
      workspacePath: body.workspacePath,
      autoRunCodingTask: body.autoRunCodingTask,
    });
    return ok(project, body.id ? '保存成功' : '创建成功');
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
      a.createdDate && b.createdDate ? b.createdDate.localeCompare(a.createdDate) : -1
    );
    return ok(paginate(rows, search));
  }),

  // #4 legacy endpoint path; semantically starts overview design generation
  http.post('*/api/project/refineSpec', async ({ request }) => {
    const body = (await request.json()) as { projectId?: string };
    const res = generateOverviewDesign(body?.projectId ?? '');
    return res.ok ? ok(res.plan, '概要设计生成已启动') : fail(res.message);
  }),

  // #5 load a detailed design (legacy spec endpoint)
  http.get('*/api/spec/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const spec = db.specs.get(id);
    return spec ? ok(spec) : fail(`详细设计 ${id} 不存在`);
  }),

  // #6 confirm detailed design → start overview design generation
  http.post('*/api/spec/confirm', async ({ request }) => {
    const body = (await request.json()) as { specId?: string };
    const plan = confirmSpec(body?.specId ?? '');
    return plan ? ok(plan, '详细设计已确认，概要设计生成已启动') : fail('详细设计不存在');
  }),

  // #9 poll project lifecycle
  http.get('*/api/project/state', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const project = db.projects.get(id);
    if (!project) return fail(`project ${id} not found`);
    return ok({ state: project.state });
  }),

  // #16 import a skill; dedup by name (409 on conflict)
  http.post('*/api/skill/import', async ({ request }) => {
    const body = (await request.json()) as {
      name?: string;
      description?: string;
      config?: { origin?: string };
      content?: string;
      files?: Array<{ path?: string; content?: string }>;
    };
    if (!body?.name) return fail('name is required');
    if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(body.name)) {
      return fail('name 必须匹配 ^[a-z0-9][a-z0-9-]{0,63}$');
    }
    if (!body?.content) return fail('content is required');
    const files = (body.files ?? [])
      .filter((f) => f && f.path)
      .map((f) => ({ path: f.path as string, content: f.content ?? '' }));
    const skill = importSkill({
      name: body.name,
      description: body.description ?? '',
      config: { origin: body.config?.origin ?? `inline:${body.name}` },
      content: body.content,
      files,
    });
    return skill ? ok(skill, '技能已导入') : fail(`技能名已存在: ${body.name}`);
  }),

  // #17 list skills (Search body → PageResult)
  http.post('*/api/skill/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const rows = Array.from(db.skills.values()).sort((a, b) =>
      a.createdDate && b.createdDate ? b.createdDate.localeCompare(a.createdDate) : -1
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
      cliTool?: string;
      mcpConfig?: string;
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
      cliTool: body.cliTool ?? '',
      mcpConfig: body.mcpConfig ?? '',
    });
    return ok(agent, '保存成功');
  }),

  // #21 list agents (built-in + custom)
  http.post('*/api/agent/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    // built-in first, then custom by newest
    const rows = Array.from(db.agents.values()).sort((a, b) => {
      if (a.builtin !== b.builtin) return a.builtin ? -1 : 1;
      return a.createdDate && b.createdDate ? b.createdDate.localeCompare(a.createdDate) : -1;
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

  // #30 detailed design version history for a project (ordered by version)
  http.get('*/api/spec/findByProject', ({ request }) => {
    const projectId = new URL(request.url).searchParams.get('projectId') ?? '';
    if (!projectId) return fail('projectId is required');
    return ok(specsOf(projectId));
  }),

  // --- Phase 5: Config Surface + Workspace resolve (contract eps #31–33) ---

  // #31 read platform config (creates default singleton if absent)
  http.get('*/api/config/get', () => {
    return ok(getConfig());
  }),

  // #32 upsert platform config (Workspace Root + Template GitLab URL)
  http.post('*/api/config/save', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as {
      workspaceRoot?: string;
      templateGitlabUrl?: string;
    };
    const config = saveConfig({
      workspaceRoot: body?.workspaceRoot,
      templateGitlabUrl: body?.templateGitlabUrl,
    });
    return ok(config, '配置已保存');
  }),

  // #33 resolve a project's workspace dir → { path, provisioned, source }
  http.get('*/api/workspace/resolve', ({ request }) => {
    const projectId = new URL(request.url).searchParams.get('projectId') ?? '';
    if (!projectId) return fail('projectId is required');
    const result = resolveWorkspace(projectId);
    return result ? ok(result) : fail('workspace resolve failed');
  }),

  // --- Requirement-driven flow mocks ---

  http.post('*/api/requirement/save', async ({ request }) => {
    const body = (await request.json()) as { projectId?: string; title?: string; description?: string };
    if (!body?.projectId) return fail('projectId is required');
    if (!body?.title) return fail('title is required');
    const requirement = createRequirement(body.projectId, body.title, body.description ?? '');
    return ok(requirement, '创建成功');
  }),

  http.get('*/api/requirement/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const requirement = db.requirements.get(id);
    return requirement ? ok(requirement) : fail(`requirement ${id} not found`);
  }),

  http.post('*/api/requirement/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const rows = Array.from(db.requirements.values()).sort((a, b) =>
      a.createdDate && b.createdDate ? b.createdDate.localeCompare(a.createdDate) : -1,
    );
    return ok(paginate(rows, search));
  }),

  http.post('*/api/requirement/:id/confirmPrd', ({ params }) => {
    const overview = confirmRequirementPrd(String(params.id));
    return overview ? ok(db.requirements.get(String(params.id))) : fail('需求不存在或状态不允许确认');
  }),

  http.post('*/api/requirement/:id/regeneratePrd', ({ params }) => {
    const requirement = db.requirements.get(String(params.id));
    if (!requirement) return fail('requirement not found');
    requirement.status = 'PRD_GENERATING';
    requirement.prdVersion += 1;
    setTimeout(() => {
      requirement.status = 'PRD_REVIEW';
      requirement.lastEditedDate = now();
    }, 500);
    return ok(requirement);
  }),

  http.post('*/api/requirement/:id/editPrd', async ({ params, request }) => {
    const body = (await request.json()) as { prdContent?: string };
    const requirement = db.requirements.get(String(params.id));
    if (!requirement) return fail('requirement not found');
    requirement.prdContent = body?.prdContent ?? '';
    requirement.lastEditedDate = now();
    return ok(requirement);
  }),

  http.get('*/api/overview-design/findOne', ({ request }) => {
    const requirementId = new URL(request.url).searchParams.get('requirementId') ?? '';
    const overview = Array.from(db.overviewDesigns.values()).find(
      (o) => o.requirementId === requirementId,
    );
    return overview ? ok(overview) : ok(null);
  }),

  http.post('*/api/overview-design/:id/confirm', ({ params }) => {
    const designs = confirmOverviewDesignMock(String(params.id));
    const overview = db.overviewDesigns.get(String(params.id));
    return overview ? ok(overview) : fail('overview not found');
  }),

  http.post('*/api/overview-design/:id/regenerate', ({ params }) => {
    const overview = db.overviewDesigns.get(String(params.id));
    if (!overview) return fail('overview not found');
    overview.status = 'GENERATING';
    overview.version += 1;
    setTimeout(() => {
      overview.status = 'DRAFT';
      overview.lastEditedDate = now();
    }, 500);
    return ok(overview);
  }),

  http.post('*/api/overview-design/:id/edit', async ({ params, request }) => {
    const body = (await request.json()) as { content?: string };
    const overview = db.overviewDesigns.get(String(params.id));
    if (!overview) return fail('overview not found');
    overview.content = body?.content ?? '';
    overview.lastEditedDate = now();
    return ok(overview);
  }),

  http.get('*/api/detailed-design/findOne', ({ request }) => {
    const id = new URL(request.url).searchParams.get('id') ?? '';
    const design = db.detailedDesigns.get(id);
    return design ? ok(design) : fail(`detailed design ${id} not found`);
  }),

  http.get('*/api/detailed-design/findByOverview', ({ request }) => {
    const overviewDesignId = new URL(request.url).searchParams.get('overviewDesignId') ?? '';
    const rows = Array.from(db.detailedDesigns.values()).filter(
      (d) => d.overviewDesignId === overviewDesignId,
    );
    return ok(rows);
  }),

  http.post('*/api/detailed-design/:id/confirm', ({ params }) => {
    const task = confirmDetailedDesignMock(String(params.id));
    const design = db.detailedDesigns.get(String(params.id));
    return design ? ok(design) : fail('detailed design not found');
  }),

  http.post('*/api/detailed-design/batchConfirm', async ({ request }) => {
    const body = (await request.json()) as { ids?: string[] };
    const ids = body?.ids ?? [];
    const list: import('./db').DetailedDesignDto[] = [];
    ids.forEach((id) => {
      const design = db.detailedDesigns.get(id);
      if (design && design.status === 'REVIEW') {
        confirmDetailedDesignMock(id);
        list.push(db.detailedDesigns.get(id)!);
      }
    });
    return ok(list);
  }),

  http.post('*/api/coding-task/:id/run', async ({ params, request }) => {
    const body = (await request.json()) as { userPrompt?: string };
    const res = runCodingTaskMock(String(params.id), body?.userPrompt);
    return res.ok ? ok(res.task) : fail(res.message ?? 'run failed');
  }),

  http.post('*/api/coding-task/:id/rerun', async ({ params, request }) => {
    const body = (await request.json()) as { rerunPrompt?: string };
    const res = rerunCodingTaskMock(String(params.id), body?.rerunPrompt ?? '');
    return res.ok ? ok(res.task) : fail(res.message ?? 'rerun failed');
  }),

  http.post('*/api/coding-task/findByPage', async ({ request }) => {
    const search = (await request.json().catch(() => ({}))) as Search;
    const rows = Array.from(db.codingTasks.values()).sort((a, b) =>
      a.createdDate && b.createdDate ? b.createdDate.localeCompare(a.createdDate) : -1,
    );
    return ok(paginate(rows, search));
  }),

  http.get('*/api/run/findByCodingTask', ({ request }) => {
    const codingTaskId = new URL(request.url).searchParams.get('codingTaskId') ?? '';
    const rows = Array.from(db.runs.values())
      .filter((r) => r.codingTaskId === codingTaskId)
      .sort((a, b) => a.runNo - b.runNo);
    return ok(rows);
  }),
];
