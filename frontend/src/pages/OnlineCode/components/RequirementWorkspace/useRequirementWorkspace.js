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
  submitMr,
  refreshRequirementWorkspace,
  syncRequirementWorkspace,
  editPrd,
  confirmPrd,
  confirmRequirementCompletion,
  reopenRequirement as reopenRequirementRequest,
  regeneratePrd,
  resumeRequirementAutomation,
  stopRequirementAutomation,
  retryRequirementRevision,
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
// @ts-ignore JS service module has no declaration file
import { findOverview } from '@/services/executionProgress';
import { subscribeRequirementProgress } from '@/utils/requirement-progress-socket';

const FAST_STATUSES = new Set([
  'PLANNING',
  'DEVELOPING',
  'VALIDATING',
  'ACCEPTING',
  'DELIVERING',
]);
const FAST_INTERVAL = 5000;
const SLOW_INTERVAL = 30000;
const ACTIVE_REVISION_STATES = new Set(['PENDING', 'SNAPSHOTTING', 'PLANNING', 'APPLYING']);
const REVISION_STATE_ORDER = {
  PENDING: 1,
  SNAPSHOTTING: 2,
  PLANNING: 3,
  APPLYING: 4,
  NONE: 5,
  FAILED: 5,
};

const MR_STATUS_TYPES = new Set(['MR_CREATED', 'MR_UPDATED', 'MR_MERGED', 'MR_FAILED']);

/**
 * Optimistically project a revision progress event onto a fetched snapshot.
 * Events from an older loop/revision are ignored; the next overview request
 * remains authoritative and fills fields (such as appliedRevisionSeq) that are
 * intentionally absent from the notification payload.
 */
export function mergeRevisionProgressEvent(snapshot, event) {
  if (!snapshot || !event || typeof event !== 'object') return snapshot;
  if (snapshot.activeLoopId && event.loopId && snapshot.activeLoopId !== event.loopId) {
    return snapshot;
  }
  const incomingSeq = typeof event.revisionSeq === 'number' ? event.revisionSeq : null;
  const currentSeq = typeof snapshot.revisionSeq === 'number' ? snapshot.revisionSeq : 0;
  if (incomingSeq === null || incomingSeq < currentSeq) return snapshot;

  const next = { ...snapshot, revisionSeq: incomingSeq };
  if (typeof event.revisionState === 'string') {
    next.revisionState = event.revisionState;
    if (event.revisionState !== 'FAILED') {
      next.revisionFailureReason = null;
    }
  }
  if (event.revisionState === 'FAILED'
    && Object.prototype.hasOwnProperty.call(event, 'revisionFailureReason')) {
    next.revisionFailureReason = event.revisionFailureReason;
  }
  return next;
}

/**
 * Reject an out-of-order event before optimistic state is updated. Revision
 * states move forward within one token; the only legal restart is
 * FAILED -> PENDING when the user retries that same token.
 */
export function shouldApplyRevisionProgressEvent(cursor, event) {
  if (!event || typeof event.revisionSeq !== 'number' || !event.revisionState) return false;
  if (!cursor) return true;
  if (cursor.loopId && event.loopId && cursor.loopId !== event.loopId) return true;
  if (event.revisionSeq !== cursor.revisionSeq) return event.revisionSeq > cursor.revisionSeq;

  const incomingTime = Date.parse(event.occurredAt || '');
  const currentTime = Date.parse(cursor.occurredAt || '');
  const hasOrderedTime = Number.isFinite(incomingTime) && Number.isFinite(currentTime);
  if (hasOrderedTime && incomingTime < currentTime) return false;

  const currentState = cursor.revisionState;
  const incomingState = event.revisionState;
  if (currentState === 'FAILED' && incomingState === 'PENDING') {
    return !hasOrderedTime || incomingTime >= currentTime;
  }
  if (currentState === 'NONE' || currentState === 'FAILED') {
    return incomingState === currentState && (!hasOrderedTime || incomingTime >= currentTime);
  }
  const currentOrder = REVISION_STATE_ORDER[currentState] || 0;
  const incomingOrder = REVISION_STATE_ORDER[incomingState] || 0;
  if (incomingOrder < currentOrder) return false;
  return !hasOrderedTime || incomingTime >= currentTime;
}

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
    else if (latest.commentType === 'MR_MERGED') status = 'MERGED';
    else if (latest.commentType === 'MR_UPDATED') status = 'UPDATED';
    else if (latest.commentType === 'MR_CREATED') status = 'CREATED';
  }

  return {
    mrUrl: req ? req.deliveryMrUrl : null,
    branch: req ? req.deliveryBranch : null,
    commitHash: req ? req.deliveryCommitHash : null,
    targetBranch: req ? req.deliveryTargetBranch : null,
    status: req?.deliveryMrStatus === 'MERGED' ? 'MERGED' : status,
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
  const [retryingRevision, setRetryingRevision] = useState(false);
  const [workspaceStatus, setWorkspaceStatus] = useState(null);
  // Authoritative aggregated progress snapshot (ExecutionProgressApi.findOverview).
  const [overview, setOverview] = useState(null);
  // True when the snapshot is past staleAfter or the progress WS is down.
  const [stale, setStale] = useState(false);

  // Guards re-entrancy of refresh so overlapping polls can't fight each other.
  const inFlightRef = useRef(false);
  // Guards teardown so a late-settling refresh after unmount can't setState.
  const cancelledRef = useRef(false);
  const timerRef = useRef(null);
  const commentInFlightRef = useRef(false);
  const revisionRetryInFlightRef = useRef(false);
  const revisionEventCursorRef = useRef(null);
  // Last overview snapshotVersion applied to the UI. Gates WS-triggered
  // refetch so a stale / duplicate / late event cannot roll the view back.
  const appliedSnapshotVersionRef = useRef(null);
  // Progress WebSocket subscription handle.
  const progressSocketRef = useRef(null);

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
        filters: [{ fieldName: 'requirementId', value: requirementId, operator: 'EQ' }, {
          fieldName: 'status',
          operator: 'NE',
          value: 'STALE',
        }],
        pageInfo: { page: 1, rows: 30 }
      });
      const tasks =
        ctRes && ctRes.success && ctRes.data && ctRes.data.rows ? ctRes.data.rows : [];
      safeSet(setCodingTasks, tasks);

      const runRes = await findRunsByRequirement(requirementId);
      const requirementRuns = runRes && runRes.success && runRes.data ? runRes.data : [];
      safeSet(setRuns, requirementRuns);

      // Authoritative progress overview. Read-only here; the UI must not
      // infer status from summaries/logs elsewhere.
      const ovRes = await findOverview(requirementId);
      const ov = ovRes && ovRes.success ? ovRes.data : null;
      safeSet(setOverview, ov);
      if (ov) {
        if (typeof ov.snapshotVersion === 'number') {
          appliedSnapshotVersionRef.current = ov.snapshotVersion;
        }
        const staleAfterMs = ov.staleAfter ? Date.parse(ov.staleAfter) : NaN;
        // Clear stale on a fresh snapshot unless it is already past staleAfter.
        safeSet(setStale, Number.isNaN(staleAfterMs) ? false : Date.now() > staleAfterMs);
      }

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
    const revisionState = requirement ? requirement.revisionState : null;
    const interval = (status && FAST_STATUSES.has(status))
      || (revisionState && ACTIVE_REVISION_STATES.has(revisionState))
      ? FAST_INTERVAL
      : SLOW_INTERVAL;

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
  }, [requirement?.automationStatus, requirement?.revisionState, refresh]);

  // Version-gated refresh: only refetch when an event carries a higher
  // snapshotVersion than what the UI has already applied. Events without a
  // version are treated as a conservative refresh signal. refresh() coalesces
  // overlapping calls via inFlightRef, so the polling loop and WS cannot stampede.
  const maybeRefreshOnVersion = useCallback(
    (version) => {
      if (cancelledRef.current) return;
      const applied = appliedSnapshotVersionRef.current;
      // Refresh when the event carries no usable version (conservative), or
      // when nothing has been applied yet, or when the event is strictly newer.
      const shouldRefresh =
        typeof version !== 'number' || applied === null || version > applied;
      if (shouldRefresh) {
        refresh();
      }
    },
    [refresh],
  );

  // Progress WebSocket — notification only; authoritative state comes from the
  // overview refetch it triggers. On disconnect we mark stale and rely on the
  // polling loop above to converge. Reconnect is handled inside the socket util.
  useEffect(() => {
    if (cancelledRef.current) return;
    if (!requirementId) return undefined;
    const socket = subscribeRequirementProgress({
      requirementId,
      onEvent: (evt) => {
        if (shouldApplyRevisionProgressEvent(revisionEventCursorRef.current, evt)) {
          revisionEventCursorRef.current = {
            loopId: evt.loopId,
            revisionSeq: evt.revisionSeq,
            revisionState: evt.revisionState,
            occurredAt: evt.occurredAt,
          };
          safeSet(setRequirement, (current) => mergeRevisionProgressEvent(current, evt));
          safeSet(setOverview, (current) => mergeRevisionProgressEvent(current, evt));
        }
        maybeRefreshOnVersion(evt && evt.snapshotVersion);
      },
      onDisconnect: () => {
        safeSet(setStale, true);
      },
    });
    progressSocketRef.current = socket;
    return () => {
      socket.close();
      progressSocketRef.current = null;
      revisionEventCursorRef.current = null;
    };
  }, [requirementId, maybeRefreshOnVersion, safeSet]);

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
      async resumeAutomation() {
        const res = await resumeRequirementAutomation(requirementId);
        if (!res || !res.success) {
          message.error((res && res.message) || '恢复执行计划失败');
          return;
        }
        message.success('已重新触发当前执行计划');
        await refresh();
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
      async submitMr() {
        const res = await submitMr(requirementId);
        if (!res || !res.success) {
          message.error((res && res.message) || '手动提交交付物失败');
          return;
        }
        if (res.data?.automationStatus === 'WAITING_HUMAN') {
          message.error('交付未完成，请查看交付事件中的失败原因');
          await refresh();
          return;
        }
        message.success('交付物已提交，GitLab MR 已创建或更新');
        await refresh();
      },
      async refreshWorkspace() {
        const res = await refreshRequirementWorkspace(requirementId);
        if (!res || !res.success || !res.data) {
          message.error((res && res.message) || '刷新工作区失败');
          return;
        }
        safeSet(setWorkspaceStatus, res.data);
        message.success('工作区状态已刷新');
        await refresh();
      },
      async syncWorkspace() {
        const res = await syncRequirementWorkspace(requirementId);
        if (!res || !res.success || !res.data) {
          message.error((res && res.message) || '同步基线分支失败');
          return;
        }
        safeSet(setWorkspaceStatus, res.data);
        message.success(`已更新基线分支 ${res.data.baseBranch || 'main'} 并合并到当前需求分支`);
      },
      async confirmCompletion() {
        const res = await confirmRequirementCompletion(requirementId);
        if (!res || !res.success) {
          message.error((res && res.message) || '确认需求完成失败');
          return;
        }
        message.success('需求已完成');
        await refresh();
      },
      async reopenRequirement() {
        const res = await reopenRequirementRequest(requirementId);
        if (!res || !res.success) {
          message.error((res && res.message) || '重新打开需求失败');
          return;
        }
        message.success('需求已重新打开，可发送评论开启下一轮变更');
        await refresh();
      },
      async retryRevision() {
        if (!requirementId || revisionRetryInFlightRef.current) return;
        revisionRetryInFlightRef.current = true;
        safeSet(setRetryingRevision, true);
        try {
          const res = await retryRequirementRevision(requirementId);
          if (!res || !res.success) {
            throw new Error((res && res.message) || '重试计划修订失败');
          }
          message.success('已重新提交当前计划修订');
          await refresh();
        } catch (err) {
          message.error((err && err.message) || '重试计划修订失败');
        } finally {
          revisionRetryInFlightRef.current = false;
          safeSet(setRetryingRevision, false);
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
    overview,
    stale,
    delivery,
    workspaceStatus,
    loading,
    error,
    sendingComment,
    retryingRevision,
    activeLoopId,
    planVersion,
    actions,
  };
}

export default useRequirementWorkspace;
