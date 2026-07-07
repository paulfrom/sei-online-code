/**
 * MSW handlers for Plan endpoints (API-CONTRACT-PRE-BUILD.md §5 P2-P5, P13)
 */
import { http, HttpResponse } from 'msw';
import type { PlanDto, PlanContent, PlanStatus } from '../db';
import { db, nextId, now } from '../db';

// Add plans Map to db if not exists
if (!db.plans) {
  (db as any).plans = new Map<string, PlanDto>();
}

/** ResultData<T> success envelope (contract §1.1) */
function ok<T>(data: T, message: string | null = null) {
  return HttpResponse.json({ success: true, message, data });
}

/** ResultData failure envelope (contract §1.1) */
function fail(message: string) {
  return HttpResponse.json({ success: false, message, data: null });
}

function featuresFromContent(content: PlanContent) {
  if (content.modules?.length) {
    return content.modules.flatMap((module) => module.features ?? []);
  }
  return content.features ?? [];
}

// Seed mock overview design data for existing projects.
function seedPlans() {
  const projects = Array.from(db.projects.values());
  const plansMap = (db as any).plans as Map<string, PlanDto>;

  if (plansMap.size === 0 && projects.length > 0) {
    // Add a plan for the first project
    const project = projects[0];
    const plan: PlanDto = {
      id: nextId('PLAN'),
      projectId: project.id,
      version: 1,
      status: 'DRAFT',
      content: {
        summary: '这是一个示例概要设计，用于说明主要功能和范围。',
        techAssumptions: ['React 18', 'TypeScript', '@ead/suid', 'MSW', 'Umi 4'],
        features: [
          {
            featureId: 'FEAT-001',
            title: '项目看板',
            outline: '展示项目概览、状态和关键指标的看板。',
          },
          {
            featureId: 'FEAT-002',
            title: '功能设计管理',
            outline: '管理功能设计及其状态的增删改查能力。',
          },
          {
            featureId: 'FEAT-003',
            title: '编码执行',
            outline: '触发并监控功能编码执行的入口。',
          },
        ],
        nonGoals: [
          '多租户支持',
          '高级分析',
          '第三方集成',
        ],
      },
      isLatest: true,
      createdDate: now(),
      lastEditedDate: now(),
    };
    plansMap.set(plan.id, plan);
  }
}

seedPlans();

export const planHandlers = [
  // P2: GET /plan/{projectId} - Get latest Plan
  http.get('*/sei-online-code/plan/:projectId', ({ params }) => {
    const { projectId } = params;
    const plansMap = (db as any).plans as Map<string, PlanDto>;
    const plans = Array.from(plansMap.values()).filter(p => p.projectId === projectId);
    const latestPlan = plans.find(p => p.isLatest);

    if (latestPlan) {
      return ok(latestPlan);
    }
    return fail(`项目 ${projectId} 暂无概要设计`);
  }),

  // P3: PUT /plan/{projectId} - Edit Plan
  http.put('*/sei-online-code/plan/:projectId', async ({ params, request }) => {
    const { projectId } = params;
    const body = await request.json() as { content: PlanContent };
    const plansMap = (db as any).plans as Map<string, PlanDto>;
    const plans = Array.from(plansMap.values()).filter(p => p.projectId === projectId);
    const latestPlan = plans.find(p => p.isLatest);

    if (!latestPlan) {
      return fail(`项目 ${projectId} 暂无概要设计`);
    }

    // Update the latest plan to DRAFT
    latestPlan.content = body.content;
    latestPlan.status = 'DRAFT';
    latestPlan.lastEditedDate = now();

    // Mark all related FeatureDesigns as STALE
    const featureDesignsMap = (db as any).featureDesigns as Map<string, any>;
    if (featureDesignsMap) {
      Array.from(featureDesignsMap.values())
        .filter(fd => fd.projectId === projectId)
        .forEach(fd => {
          fd.status = 'STALE';
          if (fd.buildStatus === 'BUILT' || fd.buildStatus === 'BUILD_FAILED') {
            fd.buildStatus = 'STALE';
          }
        });
    }

    return ok(latestPlan, '概要设计已更新');
  }),

  // P4: POST /plan/{projectId}/regenerate - Regenerate Plan
  http.post('*/sei-online-code/plan/:projectId/regenerate', async ({ params, request }) => {
    const { projectId } = params;
    const body = await request.json() as { modifyHint?: string };
    const plansMap = (db as any).plans as Map<string, PlanDto>;
    const plans = Array.from(plansMap.values()).filter(p => p.projectId === projectId);
    const latestPlan = plans.find(p => p.isLatest);

    if (!latestPlan) {
      return fail(`项目 ${projectId} 暂无概要设计`);
    }

    // Mark old plan as not latest
    latestPlan.isLatest = false;

    // Create new version with GENERATING status
    const newVersion = latestPlan.version + 1;
    const newPlan: PlanDto = {
      ...latestPlan,
      id: nextId('PLAN'),
      version: newVersion,
      status: 'GENERATING',
      modifyHint: body.modifyHint,
      isLatest: true,
      createdDate: now(),
      lastEditedDate: now(),
    };
    plansMap.set(newPlan.id, newPlan);

    // Mark all related FeatureDesigns as STALE
    const featureDesignsMap = (db as any).featureDesigns as Map<string, any>;
    if (featureDesignsMap) {
      Array.from(featureDesignsMap.values())
        .filter(fd => fd.projectId === projectId)
        .forEach(fd => {
          fd.status = 'STALE';
          if (fd.buildStatus === 'BUILT' || fd.buildStatus === 'BUILD_FAILED') {
            fd.buildStatus = 'STALE';
          }
        });
    }

    // Simulate async generation - in a real mock, we'd flip to DRAFT after some time
    // For this static mock, we'll just return GENERATING
    return ok(newPlan, '概要设计重新生成已开始');
  }),

  // P5: POST /plan/{projectId}/confirm - Confirm Plan
  http.post('*/sei-online-code/plan/:projectId/confirm', ({ params }) => {
    const { projectId } = params;
    const plansMap = (db as any).plans as Map<string, PlanDto>;
    const plans = Array.from(plansMap.values()).filter(p => p.projectId === projectId);
    const latestPlan = plans.find(p => p.isLatest);

    if (!latestPlan) {
      return fail(`项目 ${projectId} 暂无概要设计`);
    }

    if (latestPlan.status !== 'DRAFT') {
      return fail(`仅草稿概要设计可确认，当前状态：${latestPlan.status}`);
    }

    // Update plan to CONFIRMED
    latestPlan.status = 'CONFIRMED';
    latestPlan.lastEditedDate = now();

    // Create PENDING FeatureDesigns for each feature in the plan
    const featureDesignsMap = (db as any).featureDesigns as Map<string, any>;
    if (featureDesignsMap) {
      featuresFromContent(latestPlan.content).forEach(feature => {
        // Check if FeatureDesign already exists for this featureId
        const existing = Array.from(featureDesignsMap.values()).find(
          fd => fd.projectId === projectId && fd.featureId === feature.featureId && fd.isLatest
        );
        if (!existing) {
          const fd: any = {
            id: nextId('FDES'),
            projectId,
            featureId: feature.featureId,
            version: 1,
            status: 'PENDING',
            buildStatus: 'IDLE',
            isLatest: true,
            createdDate: now(),
            lastEditedDate: now(),
          };
          featureDesignsMap.set(fd.id, fd);
        }
      });
    }

    return ok(latestPlan, '概要设计已确认');
  }),

  // P13: GET /plan/{projectId}/history - Get Plan history
  http.get('*/sei-online-code/plan/:projectId/history', ({ params }) => {
    const { projectId } = params;
    const plansMap = (db as any).plans as Map<string, PlanDto>;
    const plans = Array.from(plansMap.values())
      .filter(p => p.projectId === projectId)
      .sort((a, b) => b.version - a.version);

    return ok(plans);
  }),
];
