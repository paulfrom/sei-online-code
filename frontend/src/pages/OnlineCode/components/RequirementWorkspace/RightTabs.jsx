/**
 * Right-hand tab container: plan / task / run / delivery.
 * Uses forwardRef to expose `switchTo(tab, taskKey?)` for cross-column navigation.
 */
import React, { forwardRef, useImperativeHandle, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Tabs } from '@ead/suid';
import ExecutionPlanTab from './ExecutionPlanTab';
import TaskTab from './TaskTab';
import RunTab from './RunTab';
import DeliveryTab from './DeliveryTab';

const useStyles = createStyles(({ token, css }) => ({
  tabs: css`
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    & .ead-tabs-content-holder {
      flex: 1;
      min-height: 0;
      overflow: auto;
    }
    & .ead-tabs-content {
      height: 100%;
    }
    & .ead-tabs-nav {
      padding: 0 ${token.paddingMD}px;
      margin-bottom: 0;
    }
    & .ead-tabs-tabpane {
      padding: ${token.paddingMD}px;
      height: 100%;
      overflow: auto;
    }
  `,
}));

/**
 * @param {{
 *   plan?: any, tasks: any[], runs: any[], delivery: any,
 *   comments: any[], onRunLog?: (run: any) => void,
 *   onRerun?: (t: any, p: string) => Promise<void>,
 *   onStop: () => Promise<void>, onRetryMr?: () => Promise<void>,
 *   autoStopEnabled: boolean, highlightTaskKey?: string | null,
 *   onHighlightTaskConsumed?: () => void,
 * }} props
 * @param {React.Ref<{ switchTo: (tab: string, taskKey?: string) => void }>} ref
 */
const RightTabs = forwardRef(
  (
    {
      plan,
      tasks,
      runs,
      delivery,
      comments,
      onRunLog,
      onRerun,
      onStop,
      onRetryMr,
      autoStopEnabled,
      highlightTaskKey,
      onHighlightTaskConsumed,
    },
    ref,
  ) => {
    const { styles } = useStyles();
    const [activeTab, setActiveTab] = useState('plan');
    const [pendingTaskKey, setPendingTaskKey] = useState(null);
    const [taskFilterId, setTaskFilterId] = useState(null);

    useImperativeHandle(ref, () => ({
      switchTo: (tab, taskKey) => {
        setActiveTab(tab);
        if (tab === 'task' && taskKey) {
          setPendingTaskKey(taskKey);
        }
      },
    }));

    const consumeTaskHighlight = () => {
      if (pendingTaskKey) {
        setPendingTaskKey(null);
      }
      if (onHighlightTaskConsumed) onHighlightTaskConsumed();
    };

    const handleTabChange = (key) => {
      setActiveTab(key);
      if (key !== 'task' && pendingTaskKey) {
        setPendingTaskKey(null);
        if (onHighlightTaskConsumed) onHighlightTaskConsumed();
      }
    };

    const jumpToTask = (taskKey) => {
      setActiveTab('task');
      if (taskKey) setPendingTaskKey(taskKey);
    };

    const jumpToRun = (task) => {
      setActiveTab('run');
      setTaskFilterId(task && task.id ? task.id : null);
    };

    const tabItems = [
      {
        key: 'plan',
        label: '执行计划',
        children: <ExecutionPlanTab plan={plan} tasks={tasks} comments={comments} onJumpTask={jumpToTask} />,
      },
      {
        key: 'task',
        label: '任务',
        children: (
          <TaskTab
            tasks={tasks}
            runs={runs}
            comments={comments}
            onRerun={onRerun}
            onViewRun={jumpToRun}
            onStop={onStop}
            stopEnabled={autoStopEnabled}
            highlightTaskKey={pendingTaskKey || highlightTaskKey}
            onHighlightTaskConsumed={consumeTaskHighlight}
          />
        ),
      },
      {
        key: 'run',
        label: '运行',
        children: (
          <RunTab
            runs={runs}
            taskFilterId={taskFilterId}
            onClearTaskFilter={() => setTaskFilterId(null)}
            onOpenLog={onRunLog}
          />
        ),
      },
      {
        key: 'delivery',
        label: '交付',
        children: (
          <DeliveryTab
            delivery={delivery}
            comments={comments}
            onRetryMr={onRetryMr}
          />
        ),
      },
    ];

    return (
      <Tabs
        activeKey={activeTab}
        items={tabItems}
        className={styles.tabs}
        onChange={handleTabChange}
      />
    );
  },
);

RightTabs.displayName = 'RightTabs';

export default RightTabs;
