/**
 * OverviewPanel — right-column summary surface for the requirement workspace.
 *
 * Replaces the former AutomationStatusBar + RightTabs dense layout with a
 * concise stack of stat cards. Each actionable card is fully clickable and
 * opens a Drawer (see OverviewDrawer) carrying the full detail of that
 * dimension. The automation status card is display-only.
 *
 * Stats are derived purely from the workspace data already loaded by the
 * container (plan / tasks / runs / delivery); no extra fetch happens here.
 */
import React, { useMemo } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Space, Tag, Tooltip } from '@ead/suid';
import { ArrowRightOutlined, StopOutlined } from '@ead/suid-icons';
import { parsePlanJson } from './parsePlanJson';
import { AUTOMATION_STATUS_META } from './AutomationStatusBar';

const useStyles = createStyles(({ token, css }) => ({
  panel: css`
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: ${token.marginSM}px;
    overflow: auto;
  `,
  statusCard: css`
    display: flex;
    flex-direction: column;
    gap: ${token.marginXS}px;
    padding: ${token.paddingMD}px;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
  `,
  statusMain: css`
    display: flex;
    align-items: center;
    gap: ${token.marginXS}px;
  `,
  statusSub: css`
    display: flex;
    flex-wrap: wrap;
    gap: ${token.marginSM}px;
    color: ${token.colorTextSecondary};
    font-size: ${token.fontSizeSM}px;
  `,
  card: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: ${token.marginSM}px;
    padding: ${token.paddingMD}px;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    transition: background-color 0.2s, border-color 0.2s;
    cursor: pointer;
    text-align: left;
    width: 100%;
    &:hover {
      background: ${token.colorFillQuaternary};
      border-color: ${token.colorPrimary};
    }
    &:focus-visible {
      outline: 2px solid ${token.colorPrimary};
      outline-offset: 2px;
    }
  `,
  cardBody: css`
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: ${token.marginXS}px;
  `,
  cardTitle: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: ${token.marginXS}px;
    color: ${token.colorText};
    font-weight: ${token.fontWeightStrong};
    font-size: ${token.fontSize}px;
  `,
  cardCount: css`
    color: ${token.colorTextSecondary};
    font-weight: normal;
    font-size: ${token.fontSizeSM}px;
  `,
  cardMeta: css`
    display: flex;
    flex-wrap: wrap;
    gap: ${token.marginXS}px;
  `,
  cardArrow: css`
    color: ${token.colorTextTertiary};
    flex-shrink: 0;
  `,
  stopBtn: css`
    margin-top: ${token.marginXS}px;
    width: 100%;
  `,
}));

const shorten = (id) => (id ? id.slice(0, 8) : '-');

const TASK_STATUS_META = {
  PENDING: { color: 'default', label: '待执行' },
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  VALIDATION_FAILED: { color: 'red', label: '验证失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
  STALE: { color: 'default', label: '已过期' },
  BLOCKED: { color: 'orange', label: '阻塞' },
};

const RUN_STATE_META = {
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
};

const DELIVERY_STATUS_META = {
  NONE: { color: 'default', label: '无交付' },
  PENDING: { color: 'processing', label: '交付中' },
  CREATED: { color: 'blue', label: 'MR 已创建' },
  UPDATED: { color: 'cyan', label: 'MR 已更新' },
  FAILED: { color: 'error', label: '交付失败' },
};

/**
 * MR merge status (overview.mrStatus) — intentionally distinct from automation
 * status: "automation COMPLETED + MR OPEN" must read as "MR 待合入", never as
 * merged. Unknown enum values fall through to the raw value (no silent mapping).
 */
const MR_STATUS_META = {
  OPEN: { color: 'blue', label: 'MR 待合入' },
  MERGED: { color: 'green', label: 'MR 已合入' },
  CLOSED: { color: 'default', label: 'MR 已关闭' },
};

/**
 * Tiny progress phrase: completed / total, derived from task statuses.
 * "completed" counts terminal-success only; others stay in the remaining bucket.
 */
const planProgress = (tasks) => {
  const total = tasks.length;
  const done = tasks.filter((t) => t.status === 'SUCCEEDED').length;
  return { total, done };
};

/**
 * @param {{
 *   requirement: any,
 *   plan: any,
 *   tasks: any[],
 *   runs: any[],
 *   overview?: any,
 *   stale?: boolean,
 *   delivery: any,
 *   activeLoopId?: string | null,
 *   planVersion?: number | null,
 *   autoStopEnabled: boolean,
 *   stopping?: boolean,
 *   onStop?: () => void,
 *   onOpenPanel?: (key: 'plan'|'task'|'run'|'delivery'|'progress') => void,
 * }} props
 */
const OverviewPanel = ({
  requirement,
  plan,
  tasks,
  runs,
  overview,
  stale = false,
  delivery,
  activeLoopId,
  planVersion,
  autoStopEnabled,
  stopping = false,
  onStop,
  onOpenPanel,
}) => {
  const { styles } = useStyles();

  const automationMeta = requirement?.automationStatus
    ? AUTOMATION_STATUS_META[requirement.automationStatus]
    : { color: 'default', label: '-' };

  const parsedPlan = useMemo(() => parsePlanJson(plan?.planJson), [plan?.planJson]);

  const taskStats = useMemo(() => {
    const map = {};
    tasks.forEach((t) => {
      map[t.status] = (map[t.status] || 0) + 1;
    });
    return map;
  }, [tasks]);

  const runStats = useMemo(() => {
    const map = {};
    runs.forEach((r) => {
      const key = r.state || 'UNKNOWN';
      map[key] = (map[key] || 0) + 1;
    });
    return map;
  }, [runs]);

  const progress = useMemo(() => planProgress(tasks), [tasks]);

  const deliveryMeta = DELIVERY_STATUS_META[delivery?.status] || DELIVERY_STATUS_META.NONE;

  // MR merge status from the authoritative overview (null when not yet delivered).
  const mrStatus = overview?.mrStatus;
  const mrMeta = mrStatus
    ? MR_STATUS_META[mrStatus] || { color: 'default', label: mrStatus }
    : null;

  // Step summary from the overview — surfaces non-zero exception counts so the
  // progress card signals UNKNOWN/BLOCKED/APPLIED at a glance.
  const stepSummary = overview?.stepSummary || null;
  const stepSummaryTags = stepSummary
    ? [
        { key: 'applied', color: 'blue', label: '已应用' },
        { key: 'unknown', color: 'warning', label: '待对账' },
        { key: 'blocked', color: 'orange', label: '阻塞' },
      ]
        .filter((m) => (stepSummary[m.key] ?? 0) > 0)
        .map((m) => ({ ...m, count: stepSummary[m.key] }))
    : [];

  const open = (key) => () => onOpenPanel && onOpenPanel(key);

  // Compact status-distribution Tags for the task card (only non-zero).
  const taskTags = ['RUNNING', 'FAILED', 'VALIDATION_FAILED', 'BLOCKED', 'SUCCEEDED']
    .filter((s) => taskStats[s] > 0)
    .map((s) => (
      <Tag key={s} color={TASK_STATUS_META[s].color}>
        {TASK_STATUS_META[s].label} {taskStats[s]}
      </Tag>
    ));

  const runTags = ['RUNNING', 'SUCCEEDED', 'FAILED']
    .filter((s) => runStats[s] > 0)
    .map((s) => (
      <Tag key={s} color={RUN_STATE_META[s].color}>
        {RUN_STATE_META[s].label} {runStats[s]}
      </Tag>
    ));

  return (
    <div className={styles.panel}>
      {/* Automation status — display only. MR merge status is shown
          separately so "自动化已完成 + MR OPEN" never reads as merged. */}
      <div className={styles.statusCard}>
        <div className={styles.statusMain}>
          <span>自动化：</span>
          <Tag color={automationMeta.color}>{automationMeta.label}</Tag>
          {mrMeta && (
            <span>
              MR：<Tag color={mrMeta.color}>{mrMeta.label}</Tag>
            </span>
          )}
          {stale && (
            <Tooltip title="实时通道断开或快照已过期，正在通过轮询恢复">
              <Tag color="warning">数据可能过期</Tag>
            </Tooltip>
          )}
        </div>
        <div className={styles.statusSub}>
          <span>Loop：{shorten(activeLoopId)}</span>
          {typeof planVersion === 'number' && <span>Plan v{planVersion}</span>}
        </div>
      </div>

      {/* Execution progress — authoritative ledger view */}
      <button type="button" className={styles.card} onClick={open('progress')}>
        <div className={styles.cardBody}>
          <div className={styles.cardTitle}>
            <span>执行进度</span>
            <span className={styles.cardCount}>
              {stepSummary ? `${stepSummary.verified ?? 0}/${stepSummary.required ?? 0} 已验证` : '-'}
            </span>
          </div>
          <div className={styles.cardMeta}>
            {stepSummaryTags.length > 0
              ? stepSummaryTags.map((m) => (
                  <Tag key={m.key} color={m.color}>
                    {m.label} {m.count}
                  </Tag>
                ))
              : <span className={styles.cardCount}>阶段 / 步骤 / checkpoint</span>}
          </div>
        </div>
        <ArrowRightOutlined className={styles.cardArrow} />
      </button>

      {/* Execution plan */}
      <button type="button" className={styles.card} onClick={open('plan')}>
        <div className={styles.cardBody}>
          <div className={styles.cardTitle}>
            <span>执行计划</span>
            <span className={styles.cardCount}>{parsedPlan.tasks.length} 步</span>
          </div>
          <div className={styles.cardMeta}>
            <Tag>{plan?.planType || '-'}</Tag>
            <Tag color="processing">{plan?.status || '-'}</Tag>
            <span className={styles.cardCount}>
              {progress.done}/{progress.total} 完成
            </span>
          </div>
        </div>
        <ArrowRightOutlined className={styles.cardArrow} />
      </button>

      {/* Coding tasks */}
      <button type="button" className={styles.card} onClick={open('task')}>
        <div className={styles.cardBody}>
          <div className={styles.cardTitle}>
            <span>编码任务</span>
            <span className={styles.cardCount}>{tasks.length} 个</span>
          </div>
          <div className={styles.cardMeta}>
            {taskTags.length > 0 ? taskTags : <span className={styles.cardCount}>暂无任务</span>}
          </div>
        </div>
        <ArrowRightOutlined className={styles.cardArrow} />
      </button>

      {/* Runs */}
      <button type="button" className={styles.card} onClick={open('run')}>
        <div className={styles.cardBody}>
          <div className={styles.cardTitle}>
            <span>运行</span>
            <span className={styles.cardCount}>{runs.length} 次</span>
          </div>
          <div className={styles.cardMeta}>
            {runTags.length > 0 ? runTags : <span className={styles.cardCount}>暂无运行</span>}
          </div>
        </div>
        <ArrowRightOutlined className={styles.cardArrow} />
      </button>

      {/* Delivery */}
      <button type="button" className={styles.card} onClick={open('delivery')}>
        <div className={styles.cardBody}>
          <div className={styles.cardTitle}>
            <span>交付</span>
            <Tag color={deliveryMeta.color}>{deliveryMeta.label}</Tag>
          </div>
          <div className={styles.cardMeta}>
            <Space size={4} wrap>
              <span className={styles.cardCount}>
                {delivery?.branch ? `${delivery.branch}` : '-'}
                {delivery?.targetBranch ? ` → ${delivery.targetBranch}` : ''}
              </span>
              {delivery?.commitHash && (
                <span className={styles.cardCount}>
                  · {String(delivery.commitHash).slice(0, 8)}
                </span>
              )}
            </Space>
          </div>
        </div>
        <ArrowRightOutlined className={styles.cardArrow} />
      </button>

      {/* Stop automation — always visible, gated by autoStopEnabled */}
      <Tooltip
        title={
          autoStopEnabled
            ? '中断当前计划并取消所有活跃 Run'
            : '当前自动化状态不可停止'
        }
      >
        <Button
          className={styles.stopBtn}
          danger
          icon={<StopOutlined />}
          disabled={!autoStopEnabled}
          loading={stopping}
          onClick={onStop}
        >
          停止自动化
        </Button>
      </Tooltip>
    </div>
  );
};

export default OverviewPanel;