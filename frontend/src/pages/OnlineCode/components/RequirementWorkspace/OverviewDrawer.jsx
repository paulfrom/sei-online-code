/**
 * OverviewDrawer — single right-side Drawer that carries the FULL detail of
 * one dimension (plan / task / run / delivery) at a time. Reuses the existing
 * per-dimension tab components unchanged; the container decides which panel
 * is open via `panelKey`.
 *
 * Cross-column navigation (e.g. a comment's "查看任务") is driven by the
 * container, which opens this drawer with a `highlightTaskKey` so the task
 * table can highlight the relevant row.
 */
import React from 'react';
import { createStyles } from '@ead/antd-style';
import { Drawer } from '@ead/suid';
import ExecutionPlanTab from './ExecutionPlanTab';
import TaskTab from './TaskTab';
import RunTab from './RunTab';
import DeliveryTab from './DeliveryTab';

const useStyles = createStyles(({ token, css }) => ({
  body: css`
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: auto;
    padding: ${token.paddingMD}px;
  `,
}));

const TITLE_BY_PANEL = {
  plan: '执行计划',
  task: '编码任务',
  run: '运行',
  delivery: '交付',
};

/**
 * @param {{
 *   panelKey: 'plan'|'task'|'run'|'delivery'|null,
 *   onClose: () => void,
 *   width?: number,
 *   plan: any, tasks: any[], runs: any[], delivery: any, comments: any[],
 *   taskFilterId?: string|null,
 *   onClearTaskFilter?: () => void,
 *   highlightTaskKey?: string|null,
 *   onHighlightTaskConsumed?: () => void,
 *   onRunLog?: (run: any) => void,
 *   onRerun?: (t: any, p: string) => Promise<void>,
 *   onStop: () => Promise<void>,
 *   autoStopEnabled: boolean,
 *   onRetryMr?: () => Promise<void>,
 *   onViewRunFromTask?: (task: any) => void,
 *   onJumpTaskFromPlan?: (taskKey: string) => void,
 * }} props
 */
const OverviewDrawer = ({
  panelKey,
  onClose,
  width = 760,
  plan,
  tasks,
  runs,
  delivery,
  comments,
  taskFilterId,
  onClearTaskFilter,
  highlightTaskKey,
  onHighlightTaskConsumed,
  onRunLog,
  onRerun,
  onStop,
  autoStopEnabled,
  onRetryMr,
  onViewRunFromTask,
  onJumpTaskFromPlan,
}) => {
  const { styles } = useStyles();
  const open = Boolean(panelKey);
  const title = panelKey ? TITLE_BY_PANEL[panelKey] : '';

  const renderPanel = () => {
    switch (panelKey) {
      case 'plan':
        return <ExecutionPlanTab plan={plan} tasks={tasks} comments={comments} onJumpTask={onJumpTaskFromPlan} />;
      case 'task':
        return (
          <TaskTab
            tasks={tasks}
            comments={comments}
            onRerun={onRerun}
            onViewRun={onViewRunFromTask}
            onStop={onStop}
            stopEnabled={autoStopEnabled}
            highlightTaskKey={highlightTaskKey}
            onHighlightTaskConsumed={onHighlightTaskConsumed}
          />
        );
      case 'run':
        return (
          <RunTab
            runs={runs}
            taskFilterId={taskFilterId}
            onClearTaskFilter={onClearTaskFilter}
            onOpenLog={onRunLog}
          />
        );
      case 'delivery':
        return <DeliveryTab delivery={delivery} comments={comments} onRetryMr={onRetryMr} />;
      default:
        return null;
    }
  };

  return (
    <Drawer
      title={title}
      open={open}
      onClose={onClose}
      width={width}
      destroyOnClose
      styles={{ body: { padding: 0 } }}
    >
      <div className={styles.body}>{renderPanel()}</div>
    </Drawer>
  );
};

export default OverviewDrawer;