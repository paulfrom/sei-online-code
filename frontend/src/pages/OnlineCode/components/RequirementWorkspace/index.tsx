/**
 * Requirement workspace container — PRD single-page collaboration surface.
 *
 * Layout (spec §2/§3):
 *   ┌──────────────────────────────────────┬──────────────────────┐
 *   │  LeftColumn 75%                       │  RightColumn 25%     │
 *   │  ┌──────────────────────────────────┐ │  ┌─────────────────┐ │
 *   │  │ PrdSection                       │ │  │ OverviewPanel    │ │
 *   │  ├──────────────────────────────────┤ │  │ (status + stat  │ │
 *   │  │ CommentStream                    │ │  │  cards; click →  │ │
 *   │  │   (LoopGroup + CommentComposer)  │ │  │  detail drawer) │ │
 *   │  └──────────────────────────────────┘ │  └─────────────────┘ │
 *   └──────────────────────────────────────┴──────────────────────┘
 */
import React, { useCallback, useState } from 'react';
import { history } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Button } from '@ead/suid';
import { ArrowLeftOutlined } from '@ead/suid-icons';
import { PageContainer, PageHeader, PageState } from '../PageLayout';
// @ts-ignore JS module has no declaration file
import { useRequirementWorkspace } from './useRequirementWorkspace';
// @ts-ignore JS module has no declaration file
import PrdSection from './PrdSection';
// @ts-ignore JS module has no declaration file
import CommentStream from './CommentStream';
// @ts-ignore JS module has no declaration file
import OverviewPanel from './OverviewPanel';
// @ts-ignore JS module has no declaration file
import OverviewDrawer from './OverviewDrawer';
// @ts-ignore JS module has no declaration file
import RunLogDrawer from './RunLogDrawer';
import type { RunDto } from './types';

const useStyles = createStyles(({ token, css }) => ({
  layout: css`
    flex: 1;
    min-height: 0;
    display: flex;
    gap: ${token.marginMD}px;
    overflow: hidden;
  `,
  leftColumn: css`
    flex: 3;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: ${token.marginMD}px;
    overflow: auto;
  `,
  rightColumn: css`
    flex: 1;
    min-width: 280px;
    max-width: 360px;
    display: flex;
    flex-direction: column;
    gap: ${token.marginMD}px;
    overflow: hidden;
  `,
  commentStreamWrap: css`
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
  `,
  prdSectionWrap: css`
    flex-shrink: 0;
  `,
}));

interface RequirementWorkspaceProps {
  requirementId: string;
}

const RequirementWorkspace: React.FC<RequirementWorkspaceProps> = ({ requirementId }) => {
  const { styles } = useStyles();
  const {
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
  } = useRequirementWorkspace(requirementId);

  const [drawerRun, setDrawerRun] = useState<RunDto | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detailPanel, setDetailPanel] = useState<'plan' | 'task' | 'run' | 'delivery' | 'progress' | null>(null);
  const [taskFilterId, setTaskFilterId] = useState<string | null>(null);
  const [highlightTaskKey, setHighlightTaskKey] = useState<string | null>(null);
  // Tracks whether the task panel was reached from the execution plan, so a
  // "back to plan" affordance can be offered. Reset by other entry points.
  const [taskPanelFromPlan, setTaskPanelFromPlan] = useState(false);
  const [resuming, setResuming] = useState(false);
  const [stopping, setStopping] = useState(false);

  const handleBack = useCallback(() => {
    if (requirement?.projectId) {
      history.push(`/online-code/project?projectId=${requirement.projectId}`);
    } else {
      history.back();
    }
  }, [requirement]);

  const handleRunLogOpen = useCallback((run: RunDto) => {
    setDrawerRun(run);
    setDrawerOpen(true);
  }, []);

  const handleRunLogClose = useCallback(() => {
    setDrawerOpen(false);
    setDrawerRun(null);
  }, []);

  // Cross-column navigation from the comment stream: open the drawer and
  // focus the relevant panel/row instead of switching a tab.
  const handleJumpPlan = useCallback(() => {
    setDetailPanel('plan');
  }, []);

  const handleHighlightTask = useCallback((taskKey: string) => {
    setHighlightTaskKey(taskKey);
    setDetailPanel('task');
    setTaskPanelFromPlan(false);
  }, []);

  const handleHighlightTaskConsumed = useCallback(() => {
    setHighlightTaskKey(null);
  }, []);

  const handleOpenPanel = useCallback(
    (key: 'plan' | 'task' | 'run' | 'delivery' | 'progress') => {
      setDetailPanel(key);
      setTaskFilterId(null);
      setHighlightTaskKey(null);
      setTaskPanelFromPlan(false);
    },
    [],
  );

  const handleDetailClose = useCallback(() => {
    setDetailPanel(null);
    setTaskFilterId(null);
    setHighlightTaskKey(null);
    setTaskPanelFromPlan(false);
  }, []);

  // Inside the run panel, a task row's "查看运行" jumps to the run panel
  // filtered to that task.
  const handleViewRunFromTask = useCallback((task: any) => {
    setTaskFilterId(task && task.id ? task.id : null);
    setDetailPanel('run');
  }, []);

  // From the run panel (filtered by a task), go back to the coding task panel.
  // taskFilterId being set is the signal that we arrived here from a task.
  const handleBackToTaskFromRun = useCallback(() => {
    setDetailPanel('task');
    setTaskFilterId(null);
  }, []);

  // Inside the plan panel, a task's "查看任务" jumps to the task panel and
  // highlights that row.
  const handleJumpTaskFromPlan = useCallback((taskKey: string) => {
    setHighlightTaskKey(taskKey);
    setDetailPanel('task');
    setTaskPanelFromPlan(true);
  }, []);

  // From the task panel, go back to the execution plan panel.
  const handleBackToPlanFromTask = useCallback(() => {
    setHighlightTaskKey(null);
    setDetailPanel('plan');
    setTaskPanelFromPlan(false);
  }, []);

  const handleStop = useCallback(async () => {
    setStopping(true);
    try {
      await actions.stopAutomation();
    } finally {
      setStopping(false);
    }
  }, [actions]);

  const handleResume = useCallback(async () => {
    setResuming(true);
    try {
      await actions.resumeAutomation();
    } finally {
      setResuming(false);
    }
  }, [actions]);

  if (loading) {
    return (
      <PageContainer>
        <PageHeader title="需求工作区" subTitle="加载中…" />
        <PageState loading />
      </PageContainer>
    );
  }

  if (!requirement) {
    return (
      <PageContainer>
        <PageHeader title="需求工作区" subTitle="错误" />
        <PageState error={error || '需求不存在或加载失败'} />
      </PageContainer>
    );
  }

  const autoStopEnabled = ['PLANNING', 'DEVELOPING', 'VALIDATING', 'ACCEPTING']
    .includes(requirement.automationStatus);
  const resumeEnabled = requirement.status === 'PRD_CONFIRMED'
    && requirement.automationStatus === 'DEVELOPING'
    && ['READY', 'DEVELOPING'].includes(executionPlan?.status);
  const manualDeliveryEnabled = executionPlan?.status === 'ACCEPTED'
    && requirement.automationStatus !== 'DELIVERING';

  const rightColumn = (
    <OverviewPanel
      requirement={requirement}
      plan={executionPlan}
      tasks={codingTasks}
      runs={runs}
      overview={overview}
      stale={stale}
      delivery={delivery}
      activeLoopId={activeLoopId}
      planVersion={planVersion}
      autoStopEnabled={autoStopEnabled}
      resumeEnabled={resumeEnabled}
      resuming={resuming}
      stopping={stopping}
      retryingRevision={retryingRevision}
      onResume={handleResume}
      onStop={handleStop}
      onRetryRevision={actions.retryRevision}
      onOpenPanel={handleOpenPanel}
    />
  );

  return (
    <PageContainer>
      <PageHeader
        title={requirement.title}
        subTitle="需求工作区"
        actions={
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>
            返回
          </Button>
        }
      />
      <div className={styles.layout}>
        <div className={styles.leftColumn}>
          <div className={styles.prdSectionWrap}>
            <PrdSection
              requirement={requirement}
              onConfirm={actions.confirmPrd}
              onEdit={actions.editPrd}
              onRegenerate={actions.regeneratePrd}
            />
          </div>
          <div className={styles.commentStreamWrap}>
            <CommentStream
              comments={comments}
              activeLoopId={activeLoopId}
              requirement={requirement}
              onSend={actions.sendComment}
              sending={sendingComment}
              onJumpPlan={handleJumpPlan}
              onHighlightTask={handleHighlightTask}
            />
          </div>
        </div>

        <div className={styles.rightColumn}>{rightColumn}</div>
      </div>

      <OverviewDrawer
        panelKey={detailPanel}
        onClose={handleDetailClose}
        overview={overview}
        requirement={requirement}
        plan={executionPlan}
        tasks={codingTasks}
        runs={runs}
        delivery={delivery}
        workspaceStatus={workspaceStatus}
        comments={comments}
        taskFilterId={taskFilterId}
        onClearTaskFilter={() => setTaskFilterId(null)}
        onBackToTask={handleBackToTaskFromRun}
        taskPanelFromPlan={taskPanelFromPlan}
        onBackToPlan={handleBackToPlanFromTask}
        highlightTaskKey={highlightTaskKey}
        onHighlightTaskConsumed={handleHighlightTaskConsumed}
        onRunLog={handleRunLogOpen}
        onRerun={actions.rerunTask}
        onStop={actions.stopAutomation}
        autoStopEnabled={autoStopEnabled}
        onRetryMr={actions.retryMr}
        onSubmitMr={actions.submitMr}
        onRefreshWorkspace={actions.refreshWorkspace}
        onSyncWorkspace={actions.syncWorkspace}
        onConfirmCompletion={actions.confirmCompletion}
        onReopenRequirement={actions.reopenRequirement}
        manualDeliveryEnabled={manualDeliveryEnabled}
        onViewRunFromTask={handleViewRunFromTask}
        onJumpTaskFromPlan={handleJumpTaskFromPlan}
      />

      <RunLogDrawer
        open={drawerOpen}
        run={drawerRun}
        onClose={handleRunLogClose}
        executionId={
          drawerRun?.id
            ? overview?.recentRuns?.find(
                (r: { runId?: string; executionId?: string | null }) => r.runId === drawerRun.id,
              )?.executionId ?? null
            : null
        }
      />
    </PageContainer>
  );
};

export default RequirementWorkspace;
