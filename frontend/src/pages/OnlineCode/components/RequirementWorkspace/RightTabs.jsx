/**
 * Right-hand tab container: plan / task / run / delivery.
 * Uses forwardRef to expose `switchTo(tab, taskKey?)` for cross-column navigation.
 */
import React, { forwardRef, useImperativeHandle, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Tabs } from '@ead/suid';
import type { RightTabsProps, RightTab } from './types';

const useStyles = createStyles(({ token, css }) => ({
  tabs: css`
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    & .ead-tabs-content {
      flex: 1;
      min-height: 0;
      padding: ${token.paddingMD}px;
      overflow: auto;
    }
  `,
}));

export interface RightTabsRef {
  switchTo: (tab: RightTab, taskKey?: string) => void;
}

const RightTabs = forwardRef<RightTabsRef, RightTabsProps>(
  (
    {
      plan,
      tasks,
      runs,
      delivery,
      onRunLog,
      onRun,
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
    const [activeTab, setActiveTab] = useState<RightTab>('plan');
    const [pendingTaskKey, setPendingTaskKey] = useState<string | null | undefined>(highlightTaskKey);

    useImperativeHandle(ref, () => ({
      switchTo: (tab, taskKey) => {
        setActiveTab(tab);
        if (tab === 'task' && taskKey) {
          setPendingTaskKey(taskKey);
        }
      },
    }));

    const handleTabChange = (key: string) => {
      setActiveTab(key as RightTab);
      if (key !== 'task' && pendingTaskKey) {
        setPendingTaskKey(null);
        onHighlightTaskConsumed?.();
      }
    };

    const consumeTaskHighlight = () => {
      setPendingTaskKey(null);
      onHighlightTaskConsumed?.();
    };

    const tabItems = [
      {
        key: 'plan' as RightTab,
        label: '执行计划',
        children: (
          <div>
            <p>ExecutionPlanTab placeholder</p>
            {plan && <p>{plan.summary || plan.planType}</p>}
          </div>
        ),
      },
      {
        key: 'task' as RightTab,
        label: '任务',
        children: (
          <div>
            <p>TaskTab placeholder</p>
            {pendingTaskKey && <p>Highlight: {pendingTaskKey}</p>}
            <p>Tasks: {tasks.length}</p>
          </div>
        ),
      },
      {
        key: 'run' as RightTab,
        label: '运行',
        children: (
          <div>
            <p>RunTab placeholder</p>
            <p>Runs: {runs.length}</p>
          </div>
        ),
      },
      {
        key: 'delivery' as RightTab,
        label: '交付',
        children: (
          <div>
            <p>DeliveryTab placeholder</p>
            <p>Status: {delivery.status}</p>
          </div>
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
