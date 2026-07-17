/**
 * ExecutionProgressTab — authoritative execution progress view (EXE-008).
 *
 * Renders the server-aggregated overview (workspace / step summary / current
 * step / latest checkpoint / next action) plus the full step list from
 * findSteps, with per-step checkpoint pagination (findCheckpoints). Step
 * statuses use distinct visuals so APPLIED / VERIFIED / UNKNOWN / BLOCKED are
 * never confused; unknown enum values pass through raw. Read-only on the
 * client — state changes happen via controlled reconcile commands backend-side.
 *
 * Source of truth: ExecutionProgressApi (ADR-001 §11). Never infer status from
 * logs, exit codes or comments.
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Descriptions, Tag, Button } from '@ead/suid';
// @ts-ignore JS service module has no declaration file
import { findSteps, findCheckpoints } from '@/services/executionProgress';

const useStyles = createStyles(({ token, css }) => ({
  wrap: css`
    display: flex;
    flex-direction: column;
    gap: ${token.marginMD}px;
  `,
  section: css`
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    padding: ${token.paddingMD}px;
  `,
  sectionTitle: css`
    font-weight: ${token.fontWeightStrong};
    margin-bottom: ${token.marginSM}px;
  `,
  nextAction: css`
    background: ${token.colorPrimaryBg};
    border: 1px solid ${token.colorPrimaryBorder};
    border-radius: ${token.borderRadius}px;
    padding: ${token.paddingSM}px ${token.paddingMD}px;
    color: ${token.colorPrimaryText};
  `,
  stepRow: css`
    display: flex;
    align-items: flex-start;
    gap: ${token.marginSM}px;
    padding: ${token.paddingSM}px 0;
    border-bottom: 1px solid ${token.colorBorderSecondary};
    &:last-child {
      border-bottom: none;
    }
  `,
  stepMain: css`
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  `,
  stepHead: css`
    display: flex;
    align-items: center;
    gap: ${token.marginXS}px;
    flex-wrap: wrap;
  `,
  stepTitle: css`
    font-weight: ${token.fontWeightStrong};
    word-break: break-all;
  `,
  stepMeta: css`
    color: ${token.colorTextSecondary};
    font-size: ${token.fontSizeSM}px;
    display: flex;
    flex-wrap: wrap;
    gap: ${token.marginXS}px;
  `,
  checkpoints: css`
    margin-top: ${token.marginSM}px;
    padding-left: ${token.paddingMD}px;
    display: flex;
    flex-direction: column;
    gap: ${token.marginXS}px;
  `,
  ckRow: css`
    font-size: ${token.fontSizeSM}px;
    color: ${token.colorTextSecondary};
    display: flex;
    flex-wrap: wrap;
    gap: ${token.marginXS}px;
    align-items: center;
  `,
  hint: css`
    color: ${token.colorTextTertiary};
    padding: ${token.paddingMD}px;
    text-align: center;
  `,
}));

// Distinct color per status so APPLIED/VERIFIED/UNKNOWN/BLOCKED differ at a
// glance. Unknown enum values fall through to a neutral tag with the raw value.
const STEP_STATUS_META = {
  PENDING: { color: 'default', label: '待处理' },
  IN_PROGRESS: { color: 'processing', label: '进行中' },
  APPLIED: { color: 'blue', label: '已应用' },
  VERIFIED: { color: 'green', label: '已验证' },
  UNKNOWN: { color: 'warning', label: '待对账' },
  BLOCKED: { color: 'orange', label: '阻塞' },
  FAILED: { color: 'error', label: '失败' },
  SUPERSEDED: { color: 'default', label: '已取代' },
};

const PHASE_LABEL = {
  DISCOVER: '需求发现',
  PLAN: '计划',
  IMPLEMENT: '实现',
  VERIFY: '验证',
  DELIVER: '交付',
};

const SUMMARY_META = [
  { key: 'required', label: '必填', color: 'default' },
  { key: 'verified', label: '已验证', color: 'green' },
  { key: 'applied', label: '已应用', color: 'blue' },
  { key: 'unknown', label: '待对账', color: 'warning' },
  { key: 'blocked', label: '阻塞', color: 'orange' },
];

const PHASE_ORDER = ['DISCOVER', 'PLAN', 'IMPLEMENT', 'VERIFY', 'DELIVER'];
const CHECKPOINT_ROWS = 10;

const shorten = (id) => (id ? String(id).slice(0, 8) : '-');
const formatDateTime = (v) => (v ? new Date(v).toLocaleString() : '-');
const statusMeta = (s) => (s ? STEP_STATUS_META[s] || { color: 'default', label: s } : null);

/**
 * Per-step paginated checkpoint loader. Lazy-loaded only when the step is
 * expanded ("证据/checkpoint 分页按需加载"). Has-more is inferred from a full
 * page so it does not depend on the PageResult total field.
 */
function CheckpointList({ stepId }) {
  const { styles } = useStyles();
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    (p) => {
      setLoading(true);
      findCheckpoints(stepId, p, CHECKPOINT_ROWS)
        .then((res) => {
          const data = res && res.success && res.data ? res.data : null;
          const rowsData = (data && data.rows) || [];
          setItems((prev) => (p === 1 ? rowsData : [...prev, ...rowsData]));
          setHasMore(rowsData.length === CHECKPOINT_ROWS);
        })
        .finally(() => setLoading(false));
    },
    [stepId],
  );

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stepId]);

  const loadMore = () => {
    const next = page + 1;
    setPage(next);
    load(next);
  };

  if (loading && items.length === 0) {
    return <div className={styles.hint}>checkpoint 加载中…</div>;
  }
  if (items.length === 0) {
    return <div className={styles.hint}>暂无 checkpoint</div>;
  }
  return (
    <div className={styles.checkpoints}>
      {items.map((c) => (
        <div key={c.checkpointId} className={styles.ckRow}>
          <Tag>#{c.sequenceNo ?? '-'}</Tag>
          <Tag color="cyan">{c.checkpointType || '-'}</Tag>
          <span>HEAD {shorten(c.gitHead)}</span>
          {c.parentGitHead && <span>← {shorten(c.parentGitHead)}</span>}
          <span>{formatDateTime(c.occurredAt)}</span>
        </div>
      ))}
      {hasMore && (
        <Button size="small" type="link" loading={loading} onClick={loadMore}>
          加载更多
        </Button>
      )}
    </div>
  );
}

/**
 * @param {{ overview?: any }} props
 */
const ExecutionProgressTab = ({ overview }) => {
  const { styles } = useStyles();
  const [steps, setSteps] = useState([]);
  const [stepsLoading, setStepsLoading] = useState(false);
  const [expandedStepId, setExpandedStepId] = useState(null);

  const ov = overview || null;
  const workspace = ov?.workspace || null;
  const stepSummary = ov?.stepSummary || null;
  const currentStep = ov?.currentStep || null;
  const latestCheckpoint = ov?.latestCheckpoint || null;
  const executionId = workspace?.ownerExecutionId || null;

  useEffect(() => {
    if (!executionId) {
      setSteps([]);
      return undefined;
    }
    let cancelled = false;
    setStepsLoading(true);
    findSteps(executionId)
      .then((res) => {
        if (!cancelled) setSteps(res && res.success && res.data ? res.data : []);
      })
      .finally(() => {
        if (!cancelled) setStepsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [executionId]);

  const sortedSteps = useMemo(() => {
    const rank = (ph) => {
      const i = PHASE_ORDER.indexOf(ph);
      return i < 0 ? PHASE_ORDER.length : i;
    };
    return [...steps].sort((a, b) => {
      const byPhase = rank(a?.phase) - rank(b?.phase);
      if (byPhase !== 0) return byPhase;
      return String(a?.stepKey || '').localeCompare(String(b?.stepKey || ''));
    });
  }, [steps]);

  const toggleStep = (id) => setExpandedStepId((cur) => (cur === id ? null : id));

  if (!ov) {
    return <div className={styles.hint}>暂无执行进度数据</div>;
  }

  return (
    <div className={styles.wrap}>
      {ov.nextAction && (
        <div className={styles.nextAction}>下一步：{ov.nextAction}</div>
      )}

      {workspace && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>Workspace</div>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="分支">{workspace.branch || '-'}</Descriptions.Item>
            <Descriptions.Item label="当前 HEAD">{shorten(workspace.currentHead)}</Descriptions.Item>
            <Descriptions.Item label="owner Run">{shorten(workspace.ownerRunId)}</Descriptions.Item>
            <Descriptions.Item label="owner Execution">{shorten(workspace.ownerExecutionId)}</Descriptions.Item>
            <Descriptions.Item label="lease 到期">{formatDateTime(workspace.leaseExpiresAt)}</Descriptions.Item>
            <Descriptions.Item label="lease 状态">
              {workspace.stale ? (
                <Tag color="warning">已过期</Tag>
              ) : (
                <Tag color="green">有效</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>
        </div>
      )}

      {stepSummary && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>步骤汇总</div>
          <div className={styles.stepHead}>
            {SUMMARY_META.map((m) => (
              <Tag key={m.key} color={m.color}>
                {m.label} {stepSummary[m.key] ?? 0}
              </Tag>
            ))}
          </div>
        </div>
      )}

      {(currentStep || latestCheckpoint) && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>当前进度</div>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="当前步骤">
              {currentStep ? (
                <React.Fragment>
                  <span>{currentStep.stepKey || '-'}</span>
                  {currentStep.phase && (
                    <Tag style={{ marginLeft: 8 }}>{PHASE_LABEL[currentStep.phase] || currentStep.phase}</Tag>
                  )}
                  {(() => {
                    const sm = statusMeta(currentStep.status);
                    return sm ? <Tag color={sm.color}>{sm.label}</Tag> : null;
                  })()}
                </React.Fragment>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            {latestCheckpoint && (
              <Descriptions.Item label="最新 checkpoint">
                <Tag>#{latestCheckpoint.sequenceNo ?? '-'}</Tag>
                <Tag color="cyan">{latestCheckpoint.checkpointType || '-'}</Tag>
                <span>HEAD {shorten(latestCheckpoint.gitHead)}</span>
                <span style={{ marginLeft: 8 }}>{formatDateTime(latestCheckpoint.occurredAt)}</span>
              </Descriptions.Item>
            )}
          </Descriptions>
        </div>
      )}

      <div className={styles.section}>
        <div className={styles.sectionTitle}>步骤</div>
        {!executionId && <div className={styles.hint}>暂无活跃 Execution</div>}
        {executionId && stepsLoading && <div className={styles.hint}>步骤加载中…</div>}
        {executionId && !stepsLoading && sortedSteps.length === 0 && (
          <div className={styles.hint}>暂无步骤</div>
        )}
        {sortedSteps.map((s) => {
          const sm = statusMeta(s.status);
          const isCurrent = currentStep && s.stepId === currentStep.stepId;
          const expanded = expandedStepId === s.stepId;
          return (
            <div key={s.stepId}>
              <div className={styles.stepRow}>
                <div className={styles.stepMain}>
                  <div className={styles.stepHead}>
                    {s.phase && <Tag>{PHASE_LABEL[s.phase] || s.phase}</Tag>}
                    <span className={styles.stepTitle}>{s.title || s.stepKey || '-'}</span>
                    {isCurrent && <Tag color="processing">当前</Tag>}
                    {sm && <Tag color={sm.color}>{sm.label}</Tag>}
                  </div>
                  <div className={styles.stepMeta}>
                    <span>{s.stepKey || '-'}</span>
                    <span>owner {shorten(s.ownerRunId)}</span>
                    {typeof s.attemptCount === 'number' && <span>尝试 {s.attemptCount}</span>}
                    {typeof s.progressPercent === 'number' && <span>{s.progressPercent}%</span>}
                    {s.leaseExpiresAt && <span>lease {formatDateTime(s.leaseExpiresAt)}</span>}
                  </div>
                </div>
                <Button size="small" type="link" onClick={() => toggleStep(s.stepId)}>
                  {expanded ? '收起' : 'checkpoint'}
                </Button>
              </div>
              {expanded && <CheckpointList stepId={s.stepId} />}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default ExecutionProgressTab;
