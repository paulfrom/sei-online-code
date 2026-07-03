/**
 * F5: dva model for Plan + FeatureDesign state management (D6)
 * Aggregates projectState client-side per contract §4.1
 */
import { Effect, Reducer } from 'umi';
import { getLatest } from '@/services/plan';
import { findByPage } from '@/services/featureDesign';
import { buildProject } from '@/services/onlineCode';
import type { PlanDto } from '@/services/plan';
import type { FeatureDesignDto, FeatureDesignBuildStatus } from '@/services/featureDesign';
import type { Search } from '@/services/onlineCode';

/** ProjectState enum (contract §4.1) */
export type ProjectState = 'DRAFTING' | 'PLANNING' | 'DESIGNING' | 'READY_TO_BUILD' | 'FAILED';

export interface PlanFeatureDesignModelState {
  projectId?: string;
  projectState?: ProjectState;
  plan?: PlanDto;
  featureDesigns: FeatureDesignDto[];
  loading: boolean;
}

export interface PlanFeatureDesignModelType {
  namespace: 'planFeatureDesign';
  state: PlanFeatureDesignModelState;
  effects: {
    fetchProjectState: Effect;
    fetchPlan: Effect;
    fetchFeatureDesigns: Effect;
    buildProject: Effect;
  };
  reducers: {
    saveState: Reducer<PlanFeatureDesignModelState>;
  };
}

/**
 * Compute projectState by aggregating plan + featureDesigns per contract §4.1
 * including D7 FAILED branch and D15 empty FD → DESIGNING
 */
function computeProjectState(
  plan?: PlanDto,
  featureDesigns?: FeatureDesignDto[],
): ProjectState | undefined {
  if (!plan) {
    return undefined;
  }

  // D7: FAILED if plan is FAILED or any FD is FAILED
  if (plan.status === 'FAILED') {
    return 'FAILED';
  }
  if (featureDesigns?.some(fd => fd.status === 'FAILED')) {
    return 'FAILED';
  }

  if (plan.status === 'GENERATING' || plan.status === 'DRAFT') {
    return 'PLANNING';
  }

  if (plan.status === 'CONFIRMED') {
    // D15: empty FD → DESIGNING
    if (!featureDesigns || featureDesigns.length === 0) {
      return 'DESIGNING';
    }

    const allConfirmed = featureDesigns.every(fd => fd.status === 'CONFIRMED');
    if (allConfirmed) {
      return 'READY_TO_BUILD';
    }

    return 'DESIGNING';
  }

  return undefined;
}

const PlanFeatureDesignModel: PlanFeatureDesignModelType = {
  namespace: 'planFeatureDesign',

  state: {
    projectId: undefined,
    projectState: undefined,
    plan: undefined,
    featureDesigns: [],
    loading: false,
  },

  effects: {
    *fetchProjectState({ payload }: { payload: { projectId: string } }, { call, put }) {
      yield put({
        type: 'saveState',
        payload: { projectId: payload.projectId, loading: true },
      });

      // Fetch both plan and feature designs in parallel
      const [planRes, fdsRes] = yield [
        call(getLatest, payload.projectId),
        call(findByPage, {
          filters: [{ fieldName: 'projectId', operator: 'EQ', value: payload.projectId }],
          pageInfo: { page: 1, rows: 1000 },
        } as Search),
      ];

      const plan = planRes.success ? planRes.data : undefined;
      const featureDesigns = fdsRes.success && fdsRes.data ? fdsRes.data.rows : [];
      const projectState = computeProjectState(plan, featureDesigns);

      yield put({
        type: 'saveState',
        payload: { plan, featureDesigns, projectState, loading: false },
      });
    },

    *fetchPlan({ payload }: { payload: { projectId: string } }, { call, put, select }) {
      const res = yield call(getLatest, payload.projectId);
      if (res.success && res.data) {
        const { featureDesigns } = yield select(
          (state: any) => state.planFeatureDesign,
        );
        const projectState = computeProjectState(res.data, featureDesigns);
        yield put({
          type: 'saveState',
          payload: { plan: res.data, projectState },
        });
      }
    },

    *fetchFeatureDesigns({ payload }: { payload: { projectId: string } }, { call, put, select }) {
      const res = yield call(findByPage, {
        filters: [{ fieldName: 'projectId', operator: 'EQ', value: payload.projectId }],
        pageInfo: { page: 1, rows: 1000 },
      } as Search);

      if (res.success && res.data) {
        const { plan } = yield select((state: any) => state.planFeatureDesign);
        const projectState = computeProjectState(plan, res.data.rows);
        yield put({
          type: 'saveState',
          payload: { featureDesigns: res.data.rows, projectState },
        });
      }
    },

    *buildProject({ payload }: { payload: { projectId: string } }, { call, put }) {
      const res = yield call(buildProject, payload.projectId);
      if (res.success) {
        // Refresh feature designs to get updated buildStatus
        yield put({ type: 'fetchFeatureDesigns', payload: { projectId: payload.projectId } });
      }
      return res;
    },
  },

  reducers: {
    saveState(state, { payload }) {
      return { ...state, ...payload };
    },
  },
};

export default PlanFeatureDesignModel;
