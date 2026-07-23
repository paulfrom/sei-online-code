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
import ExecutionProgressTab from './ExecutionProgressTab';

const useStyles = createStyles(({ token, css }) => ({
  body: css`
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: ${token.paddingMD}px;
  `,
}));

const TITLE_BY_PANEL = {
  plan: '执行计划',
  task: '编码任务',
  run: '运行',
  delivery: '交付',
  progress: '执行进度',
};

/**
 * @param {{
 *   panelKey: 'plan'|'task'|'run'|'delivery'|'progress'|null,
 *   onClose: () => void,
 *   titleOverride?: string,
 *   width?: number,
 *   overview?: any,
 *   plan: any, tasks: any[], runs: any[], delivery: any, comments: any[],
 *   workspaceStatus?: any,
 *   taskFilterId?: string|null,
 *   onClearTaskFilter?: () => void,
 *   onBackToTask?: () => void,
 *   taskPanelFromPlan?: boolean,
 *   onBackToPlan?: () => void,
 *   highlightTaskKey?: string|null,
 *   onHighlightTaskConsumed?: () => void,
 *   onRunLog?: (run: any) => void,
 *   onRerun?: (t: any, p: string) => Promise<void>,
 *   onStop: () => Promise<void>,
 *   autoStopEnabled: boolean,
 *   onRetryMr?: () => Promise<void>,
 *   onSubmitMr?: () => Promise<void>, onRefreshWorkspace?: () => Promise<void>,
 *   manualDeliveryEnabled?: boolean,
 *   onViewRunFromTask?: (task: any) => void,
 *   onJumpTaskFromPlan?: (taskKey: string) => void,
 * }} props
 */
const OverviewDrawer = ({
  panelKey,
  onClose,
  titleOverride,
  width = 960,
  overview,
  plan,
  tasks,
  runs,
  delivery,
  workspaceStatus,
  comments,
  taskFilterId,
  onClearTaskFilter,
  onBackToTask,
  taskPanelFromPlan,
  onBackToPlan,
  highlightTaskKey,
  onHighlightTaskConsumed,
  onRunLog,
  onRerun,
  onStop,
  autoStopEnabled,
  onRetryMr,
  onSubmitMr,
  onRefreshWorkspace,
  manualDeliveryEnabled,
  onViewRunFromTask,
  onJumpTaskFromPlan,
}) => {
  const { styles } = useStyles();
  const open = Boolean(panelKey);
  const title = titleOverride || (panelKey ? TITLE_BY_PANEL[panelKey] : '');

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
            showBackToPlan={taskPanelFromPlan}
            onBackToPlan={onBackToPlan}
          />
        );
      case 'run':
        return (
          <RunTab
            runs={runs}
            overview={overview}
            taskFilterId={taskFilterId}
            onClearTaskFilter={onClearTaskFilter}
            onBackToTask={onBackToTask}
            onOpenLog={onRunLog}
          />
        );
      case 'delivery':
        return (
          <DeliveryTab
            delivery={delivery}
            comments={comments}
            workspaceStatus={workspaceStatus}
            onRetryMr={onRetryMr}
            onSubmitMr={onSubmitMr}
            onRefreshWorkspace={onRefreshWorkspace}
            manualDeliveryEnabled={manualDeliveryEnabled}
          />
        );
      case 'progress':
        return <ExecutionProgressTab overview={overview} />;
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
