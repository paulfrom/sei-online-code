/**
 * MSW handlers for FeatureDesign endpoints (API-CONTRACT-PRE-BUILD.md §5 P6-P11, P12a, P14, P12)
 */
import { http, HttpResponse } from 'msw';
import type { FeatureDesignDto, FeatureDesignContent } from '../db';
import { db, nextId, now } from '../db';

// Add featureDesigns Map to db if not exists
if (!db.featureDesigns) {
  (db as any).featureDesigns = new Map<string, FeatureDesignDto>();
}

/** ResultData<T> success envelope (contract §1.1) */
function ok<T>(data: T, message: string | null = null) {
  return HttpResponse.json({ success: true, message, data });
}

/** ResultData failure envelope (contract §1.1) */
function fail(message: string) {
  return HttpResponse.json({ success: false, message, data: null });
}

/**
 * Apply a Search body to a row set: quickSearch LIKE + filters EQ, then
 * paginate into the PageResult shape.
 */
function paginate<T extends Record<string, any>>(rows: T[], search: any) {
  let filtered = rows;

  const kw = search?.quickSearchValue?.trim();
  const props = search?.quickSearchProperties ?? [];
  if (kw && props.length) {
    const lower = kw.toLowerCase();
    filtered = filtered.filter((r) =>
      props.some((p: string) => String(r[p] ?? '').toLowerCase().includes(lower))
    );
  }

  (search?.filters ?? []).forEach((f: any) => {
    if (f.operator === 'EQ') {
      filtered = filtered.filter((r) => String(r[f.fieldName]) === String(f.value));
    }
  });

  (search?.sortOrders ?? []).forEach((s: any) => {
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

// Seed mock FeatureDesign data
function seedFeatureDesigns() {
  const plansMap = (db as any).plans as Map<string, any>;
  const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;

  if (featureDesignsMap.size === 0 && plansMap.size > 0) {
    const plan = Array.from(plansMap.values()).find(p => p.isLatest);
    if (plan) {
      // Create 3 FeatureDesigns with different status and buildStatus combinations
      const fds: FeatureDesignDto[] = [
        {
          id: nextId('FDES'),
          projectId: plan.projectId,
          featureId: 'FEAT-001',
          version: 1,
          status: 'DRAFT',
          buildStatus: 'IDLE',
          content: {
            featureId: 'FEAT-001',
            goal: 'As a user, I want to see a dashboard so that I can quickly understand the project status.',
            design: {
              layout: 'grid',
              widgets: ['projectOverview', 'recentActivity', 'statusChart'],
            },
            acceptance: [
              'Dashboard loads within 2 seconds',
              'Shows key metrics prominently',
              'Responsive on mobile devices',
            ],
            fileScope: ['src/pages/Dashboard/index.tsx', 'src/pages/Dashboard/components/ProjectOverview.tsx'],
          },
          isLatest: true,
          createdDate: now(),
          lastEditedDate: now(),
        },
        {
          id: nextId('FDES'),
          projectId: plan.projectId,
          featureId: 'FEAT-002',
          version: 1,
          status: 'CONFIRMED',
          buildStatus: 'BUILT',
          content: {
            featureId: 'FEAT-002',
            goal: 'As a product manager, I want to manage feature designs so that I can track their progress.',
            design: {
              listView: true,
              detailView: true,
              statusFilter: true,
            },
            acceptance: [
              'Can create new feature designs',
              'Can edit existing designs',
              'Can filter by status',
            ],
            fileScope: ['src/pages/FeatureDesign/index.tsx', 'src/pages/FeatureDesign/service.ts'],
          },
          isLatest: true,
          createdDate: now(),
          lastEditedDate: now(),
        },
        {
          id: nextId('FDES'),
          projectId: plan.projectId,
          featureId: 'FEAT-003',
          version: 1,
          status: 'CONFIRMED',
          buildStatus: 'IDLE',
          content: {
            featureId: 'FEAT-003',
            goal: '作为开发者，我希望触发编码执行，以查看已实现的功能。',
            design: {
              codingExecutionButton: true,
              statusIndicator: true,
              logView: true,
            },
            acceptance: [
              '可对已确认的功能触发编码执行',
              '可查看实时编码执行状态',
              '可查看编码执行日志',
            ],
            fileScope: ['src/pages/Build/index.tsx', 'src/pages/Build/components/BuildLog.tsx'],
          },
          isLatest: true,
          createdDate: now(),
          lastEditedDate: now(),
        },
      ];

      fds.forEach(fd => featureDesignsMap.set(fd.id, fd));
    }
  }
}

seedFeatureDesigns();

export const featureDesignHandlers = [
  // P6: POST /featureDesign/findByPage - Find FeatureDesign by page
  http.post('*/sei-online-code/featureDesign/findByPage', async ({ request }) => {
    const search = await request.json().catch(() => ({}));
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const rows = Array.from(featureDesignsMap.values()).sort((a, b) =>
      a.createdDate && b.createdDate ? a.createdDate.localeCompare(b.createdDate) : -1
    );
    return ok(paginate(rows, search));
  }),

  // P7: GET /featureDesign/{id} - Get latest FeatureDesign
  http.get('*/sei-online-code/featureDesign/:id', ({ params }) => {
    const { id } = params;
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fd = featureDesignsMap.get(id as string);

    if (fd) {
      return ok(fd);
    }
    return fail(`功能设计 ${id} 不存在`);
  }),

  // P8: PUT /featureDesign/{id} - Edit FeatureDesign
  http.put('*/sei-online-code/featureDesign/:id', async ({ params, request }) => {
    const { id } = params;
    const body = await request.json() as { content: FeatureDesignContent };
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fd = featureDesignsMap.get(id as string);

    if (!fd) {
      return fail(`功能设计 ${id} 不存在`);
    }

    if (fd.buildStatus === 'BUILDING') {
      // Return 409 Conflict when BUILDING
      return new HttpResponse(
        JSON.stringify({ success: false, message: '该功能正在编码执行中', data: null }),
        { status: 409, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // Update to DRAFT
    fd.content = body.content;
    fd.status = 'DRAFT';
    if (fd.buildStatus === 'BUILT' || fd.buildStatus === 'BUILD_FAILED') {
      fd.buildStatus = 'STALE';
    }
    fd.lastEditedDate = now();

    return ok(fd, '功能设计已更新');
  }),

  // P9: POST /featureDesign/{id}/regenerate - Regenerate FeatureDesign
  http.post('*/sei-online-code/featureDesign/:id/regenerate', async ({ params, request }) => {
    const { id } = params;
    const body = await request.json() as { modifyHint?: string };
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fd = featureDesignsMap.get(id as string);

    if (!fd) {
      return fail(`功能设计 ${id} 不存在`);
    }

    if (fd.buildStatus === 'BUILDING') {
      // Return 409 Conflict when BUILDING
      return new HttpResponse(
        JSON.stringify({ success: false, message: '该功能正在编码执行中', data: null }),
        { status: 409, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // Mark old one as not latest
    fd.isLatest = false;

    // Create new version with GENERATING status
    const newVersion = fd.version + 1;
    const newFd: FeatureDesignDto = {
      ...fd,
      id: nextId('FDES'),
      version: newVersion,
      status: 'GENERATING',
      modifyHint: body.modifyHint,
      isLatest: true,
      createdDate: now(),
      lastEditedDate: now(),
    };
    if (newFd.buildStatus === 'BUILT' || newFd.buildStatus === 'BUILD_FAILED') {
      newFd.buildStatus = 'STALE';
    }
    featureDesignsMap.set(newFd.id, newFd);

    return ok(newFd, '功能设计重新生成已开始');
  }),

  // P10: POST /featureDesign/confirm - Batch confirm FeatureDesigns
  http.post('*/sei-online-code/featureDesign/confirm', async ({ request }) => {
    const body = await request.json() as { ids: string[] };
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const results: FeatureDesignDto[] = [];

    for (const id of body.ids) {
      const fd = featureDesignsMap.get(id);
      if (fd) {
        if (fd.status === 'STALE') {
          return fail(`功能设计 ${id} 已过期，需重新生成`);
        }
        fd.status = 'CONFIRMED';
        fd.lastEditedDate = now();
        results.push(fd);
      }
    }

    return ok(results, '功能设计已批量确认');
  }),

  // P11: POST /featureDesign/{id}/confirm - Confirm one FeatureDesign
  http.post('*/sei-online-code/featureDesign/:id/confirm', ({ params }) => {
    const { id } = params;
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fd = featureDesignsMap.get(id as string);

    if (!fd) {
      return fail(`功能设计 ${id} 不存在`);
    }

    if (fd.status === 'STALE') {
      return fail('功能设计已过期，需重新生成');
    }

    fd.status = 'CONFIRMED';
    fd.lastEditedDate = now();

    return ok(fd, '功能设计已确认');
  }),

  // P12a: POST /featureDesign/{id}/build - Build one FeatureDesign
  http.post('*/sei-online-code/featureDesign/:id/build', ({ params }) => {
    const { id } = params;
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fd = featureDesignsMap.get(id as string);

    if (!fd) {
      return fail(`功能设计 ${id} 不存在`);
    }

    if (fd.status !== 'CONFIRMED') {
      return fail('仅已确认的功能设计可执行编码');
    }

    if (fd.buildStatus === 'BUILDING') {
      // Return 409 Conflict when already BUILDING
      return new HttpResponse(
        JSON.stringify({ success: false, message: '该功能正在编码执行中', data: null }),
        { status: 409, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // Update to BUILDING
    fd.buildStatus = 'BUILDING';
    fd.lastEditedDate = now();

    const runId = nextId('RUN');
    return ok({ runId }, '编码执行已开始');
  }),

  // P14: GET /featureDesign/{id}/history - Get FeatureDesign history
  http.get('*/sei-online-code/featureDesign/:id/history', ({ params }) => {
    const { id } = params;
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fd = featureDesignsMap.get(id as string);

    if (!fd) {
      return fail(`功能设计 ${id} 不存在`);
    }

    // Find all versions for this featureId and projectId
    const history = Array.from(featureDesignsMap.values())
      .filter(h => h.projectId === fd.projectId && h.featureId === fd.featureId)
      .sort((a, b) => b.version - a.version);

    return ok(history);
  }),

  // P12: POST /project/{projectId}/build - Build project (all confirmed FeatureDesigns)
  http.post('*/sei-online-code/project/:projectId/build', ({ params }) => {
    const { projectId } = params;
    const featureDesignsMap = (db as any).featureDesigns as Map<string, FeatureDesignDto>;
    const fds = Array.from(featureDesignsMap.values()).filter(
      fd => fd.projectId === projectId && fd.status === 'CONFIRMED'
    );

    const results = fds.map(fd => {
      if (fd.buildStatus === 'BUILDING') {
        return { id: fd.id, skipped: true, reason: '该功能正在编码执行中' };
      }

      fd.buildStatus = 'BUILDING';
      fd.lastEditedDate = now();
      return { id: fd.id, runId: nextId('RUN') };
    });

    return ok(results, '项目编码执行已开始');
  }),
];
