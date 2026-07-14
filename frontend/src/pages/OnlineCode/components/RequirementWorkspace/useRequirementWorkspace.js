/**
 * useRequirementWorkspace — central data hook for the requirement workspace.
 *
 * Owns all server reads (requirement / comments / executionPlan / codingTasks /
 * runs), the polling cadence keyed off `automationStatus`, and the action
 * surface consumed by the workspace UI. WebSocket subscription for live run
 * logs is intentionally NOT handled here — RunLogDrawer manages its own WS.
 *
 * Polling cadence:
 *   automationStatus ∈ {PLANNING,DEVELOPING,VALIDATING,ACCEPTING,DELIVERING} → 5s
 *   otherwise (IDLE/WAITING_HUMAN/INTERRUPTED/COMPLETED/FAILED)             → 30s
 * Suspended while `document.hidden`.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { message } from '@ead/suid';
// @ts-ignore JS service module has no declaration file
import {
  findOneRequirement,
  addRequirementComment,
  retryMr,
  editPrd,
  confirmPrd,
  regeneratePrd,
  stopRequirementAutomation,
} from '@/services/requirement';
// @ts-ignore JS service module has no declaration file
import { findCommentsByRequirement } from '@/services/requirementComment';
// @ts-ignore JS service module has no declaration file
import { findLatestPlanByRequirement } from '@/services/executionPlan';
// @ts-ignore JS service module has no declaration file
import {
  findCodingTasksByPage,
  rerunCodingTask,
} from '@/services/codingTask';
// @ts-ignore JS service module has no declaration file
import { findRunsByRequirement } from '@/services/run';

const FAST_STATUSES = new Set([
  'PLANNING',
  'DEVELOPING',
  'VALIDATING',
  'ACCEPTING',
  'DELIVERING',
]);
const FAST_INTERVAL = 5000;
const SLOW_INTERVAL = 30000;

const MR_STATUS_TYPES = new Set(['MR_CREATED', 'MR_UPDATED', 'MR_FAILED']);

/**
 * Pure derivation of the delivery summary from a requirement and its comment
 * stream. Exported at module top-level so it can be exercised independently
 * (tsc + manual case walkthrough); no React, no side effects.
 *
 * mrUrl / branch / commitHash / targetBranch come straight from the
 * requirement's delivery* fields. `status` is the latest MR_* comment by
 * createdDate (MR_FAILED → FAILED, MR_UPDATED → UPDATED, MR_CREATED →
 * CREATED); when no MR_* comment exists yet, fall back to NONE — even if the
 * requirement already carries a deliveryMrUrl, we only know the MR exists once
 * a CREATED comment arrives, so we stay at NONE rather than guessing PENDING.
 */
// @ts-ignore JS module — types provided via JSDoc on the export below are not checked here
export function deriveDelivery(
  requirement,
  comments,
) {
  const req = requirement || null;
  const list = Array.isArray(comments) ? comments : [];

  const mrComments = list
    .filter((c) => c && MR_STATUS_TYPES.has(c.commentType))
    .slice()
    .sort((a, b) => {
      const ta = a.createdDate ? Date.parse(a.createdDate) : 0;
      const tb = b.createdDate ? Date.parse(b.createdDate) : 0;
      if (Number.isNaN(ta)) return 1;
      if (Number.isNaN(tb)) return -1;
      return tb - ta;
    });

  let status = 'NONE';
  if (mrComments.length > 0) {
    const latest = mrComments[0];
    if (latest.commentType === 'MR_FAILED') status = 'FAILED';
    else if (latest.commentType === 'MR_UPDATED') status = 'UPDATED';
    else if (latest.commentType === 'MR_CREATED') status = 'CREATED';
  }

  return {
    mrUrl: req ? req.deliveryMrUrl : null,
    branch: req ? req.deliveryBranch : null,
    commitHash: req ? req.deliveryCommitHash : null,
    targetBranch: req ? req.deliveryTargetBranch : null,
    status,
  };
}

export function useRequirementWorkspace(requirementId) {
  const [requirement, setRequirement] = useState(null);
  const [comments, setComments] = useState([]);
  const [executionPlan, setExecutionPlan] = useState(null);
  const [codingTasks, setCodingTasks] = useState([]);
  const [runs, setRuns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [sendingComment, setSendingComment] = useState(false);

  // Guards re-entrancy of refresh so overlapping polls can't fight each other.
  const inFlightRef = useRef(false);
  // Guards teardown so a late-settling refresh after unmount can't setState.
  const cancelledRef = useRef(false);
  const timerRef = useRef(null);
  const commentInFlightRef = useRef(false);

  const safeSet = useCallback((setter, value) => {
    if (cancelledRef.current) return;
    setter(value);
  }, []);

  const refresh = useCallback(async () => {
    if (!requirementId) return;
    if (inFlightRef.current) return; // coalesce overlapping calls
    inFlightRef.current = true;
    try {
      const reqRes = await findOneRequirement(requirementId);
      const req = reqRes && reqRes.success ? reqRes.data : null;
      if (!req) {
        safeSet(setError, (reqRes && reqRes.message) || '加载需求失败');
        safeSet(setLoading, false);
        safeSet(setRequirement, null);
        return;
      }
      safeSet(setRequirement, req);

      const cmtRes = await findCommentsByRequirement(requirementId);
      safeSet(setComments, cmtRes && cmtRes.success && cmtRes.data ? cmtRes.data : []);

      const planRes = await findLatestPlanByRequirement(requirementId);
      safeSet(
        setExecutionPlan,
        planRes && planRes.success && planRes.data ? planRes.data : null,
      );

      const ctRes = await findCodingTasksByPage({
        filters: [{ fieldName: 'requirementId', value: requirementId, operator: 'EQ' }],
      });
      const tasks =
        ctRes && ctRes.success && ctRes.data && ctRes.data.rows ? ctRes.data.rows : [];
      safeSet(setCodingTasks, tasks);

      const runRes = await findRunsByRequirement(requirementId);
      const requirementRuns = runRes && runRes.success && runRes.data ? runRes.data : [];
      safeSet(setRuns, requirementRuns);

      safeSet(setError, null);
      safeSet(setLoading, false);
    } catch (err) {
      safeSet(setError, (err && err.message) || '加载失败');
      safeSet(setLoading, false);
    } finally {
      inFlightRef.current = false;
    }
  }, [requirementId, safeSet]);

  // Initial + id-change load.
  useEffect(() => {
    cancelledRef.current = false;
    inFlightRef.current = false;
    setLoading(true);
    refresh();
    return () => {
      cancelledRef.current = true;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [refresh]);

  // Polling loop — re-arms each tick based on the latest automationStatus and
  // page visibility. Skipped while the tab is hidden.
  useEffect(() => {
    if (cancelledRef.current) return;
    const status = requirement ? requirement.automationStatus : null;
    const interval = status && FAST_STATUSES.has(status) ? FAST_INTERVAL : SLOW_INTERVAL;

    const arm = () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(async () => {
        if (cancelledRef.current) return;
        if (typeof document !== 'undefined' && document.hidden) {
          // While hidden, just re-arm without fetching; the next visible tick
          // will pick data back up. Visibility change is handled below.
          arm();
          return;
        }
        await refresh();
        if (!cancelledRef.current) arm();
      }, interval);
    };
    arm();

    const onVisibility = () => {
      // When becoming visible again, refresh immediately and re-arm the timer.
      if (typeof document !== 'undefined' && !document.hidden) {
        refresh();
      }
    };
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibility);
    }
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', onVisibility);
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requirement?.automationStatus, refresh]);

  const activeLoopId = useMemo(
    () => (requirement ? requirement.activeLoopId : null),
    [requirement],
  );
  const planVersion = useMemo(
    () => (executionPlan && typeof executionPlan.version !== 'undefined' ? executionPlan.version : null),
    [executionPlan],
  );
  const delivery = useMemo(
    () => deriveDelivery(requirement, comments),
    [requirement, comments],
  );

  const actions = useMemo(
    () => ({
      async sendComment(content) {
        if (!requirementId || !content || commentInFlightRef.current) return;
        commentInFlightRef.current = true;
        safeSet(setSendingComment, true);
        try {
          const res = await addRequirementComment(requirementId, { content });
          if (!res || !res.success) throw new Error((res && res.message) || '发送失败');
          message.success('评论已发送');
          await refresh();
        } catch (err) {
          message.error((err && err.message) || '发送失败');
          throw err;
        } finally {
          commentInFlightRef.current = false;
          safeSet(setSendingComment, false);
        }
      },
      async confirmPrd() {
        const res = await confirmPrd(requirementId);
        if (res && res.success) {
          await refresh();
        } else {
          message.error((res && res.message) || '确认失败');
        }
      },
      async editPrd(c) {
        const res = await editPrd(requirementId, c);
        if (res && res.success) {
          await refresh();
        } else {
          message.error((res && res.message) || '编辑失败');
        }
      },
      async regeneratePrd(p) {
        const res = await regeneratePrd(requirementId, p);
        if (res && res.success) {
          await refresh();
        } else {
          message.error((res && res.message) || '重新生成失败');
        }
      },
      async rerunTask(t, p) {
        const res = await rerunCodingTask(t.id, p);
        if (res && res.success) {
          await refresh();
        } else {
          message.error((res && res.message) || '重跑失败');
        }
      },
      async stopAutomation() {
        const res = await stopRequirementAutomation(requirementId);
        if (!res || !res.success) {
          message.error((res && res.message) || '停止自动化失败');
          return;
        }
        message.success('自动化已停止');
        await refresh();
      },
      async retryMr() {
        const res = await retryMr(requirementId);
        if (res && res.success) {
          await refresh();
        } else {
          message.error((res && res.message) || '重试 MR 失败');
        }
      },
      refresh,
    }),
    [requirementId, refresh, safeSet],
  );

  return {
    requirement,
    comments,
    executionPlan,
    codingTasks,
    runs,
    delivery,
    loading,
    error,
    sendingComment,
    activeLoopId,
    planVersion,
    actions,
  };
}

export default useRequirementWorkspace;
