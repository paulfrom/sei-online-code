/**
 * Requirement workspace container — PRD single-page collaboration surface.
 *
 * Layout (spec §2/§3):
 *   ┌──────────────────────────────────────┬──────────────────────┐
 *   │  LeftColumn 75%                       │  RightColumn 25%     │
 *   │  ┌──────────────────────────────────┐ │  ┌─────────────────┐ │
 *   │  │ PrdSection                       │ │  │ AutomationStatus│ │
 *   │  ├──────────────────────────────────┤ │  ├─────────────────┤ │
 *   │  │ CommentStream                    │ │  │ RightTabs       │ │
 *   │  │   (LoopGroup + CommentComposer)  │ │  │  plan/task/run/ │ │
 *   │  └──────────────────────────────────┘ │  │  delivery       │ │
 *   └──────────────────────────────────────┴──────────────────────┘
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Button, Drawer, message } from '@ead/suid';
import { ArrowLeftOutlined, MenuOutlined } from '@ead/suid-icons';
// @ts-ignore JS module has no declaration file
import { useRequirementWorkspace } from './useRequirementWorkspace';
import { PageContainer, PageHeader, PageState } from '../PageLayout';
// @ts-ignore JS module has no declaration file
import PrdSection from './PrdSection';
// @ts-ignore JS module has no declaration file
import CommentStream from './CommentStream';
// @ts-ignore JS module has no declaration file
import AutomationStatusBar from './AutomationStatusBar';
// @ts-ignore JS module has no declaration file
import RightTabs from './RightTabs';
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
  prdSectionWrap: css`
    flex-shrink: 0;
  `,
  commentStreamWrap: css`
    flex: 1;
    min-height: 300px;
    display: flex;
    flex-direction: column;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    overflow: auto;
  `,
  drawerBtn: css`
    position: fixed;
    right: ${token.marginMD}px;
    bottom: ${token.marginMD}px;
    z-index: 1000;
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
    delivery,
    loading,
    error,
    sendingComment,
    activeLoopId,
    planVersion,
    actions,
  } = useRequirementWorkspace(requirementId);

  const [drawerRun, setDrawerRun] = useState<RunDto | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [rightDrawerOpen, setRightDrawerOpen] = useState(false);
  const [narrow, setNarrow] = useState(false);
  const [highlightTaskKey, setHighlightTaskKey] = useState<string | null>(null);
  const rightTabsRef = useRef<any>(null);

  useEffect(() => {
    const onResize = () => setNarrow(typeof window !== 'undefined' && window.innerWidth < 1280);
    onResize();
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', onResize);
    }
    return () => {
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', onResize);
      }
    };
  }, []);

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

  const handleJumpPlan = useCallback(() => {
    rightTabsRef.current?.switchTo?.('plan');
  }, []);

  const handleHighlightTask = useCallback((taskKey: string) => {
    setHighlightTaskKey(taskKey);
    rightTabsRef.current?.switchTo?.('task', taskKey);
  }, []);

  const handleHighlightTaskConsumed = useCallback(() => {
    setHighlightTaskKey(null);
  }, []);

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

  const rightColumn = (
    <>
      <AutomationStatusBar
        status={requirement.automationStatus}
        activeLoopId={activeLoopId}
        planVersion={planVersion}
      />
      <RightTabs
        ref={rightTabsRef}
        plan={executionPlan}
        tasks={codingTasks}
        runs={runs}
        delivery={delivery}
        comments={comments}
        onRunLog={handleRunLogOpen}
        onRerun={actions.rerunTask}
        onStop={actions.stopAutomation}
        onRetryMr={actions.retryMr}
        autoStopEnabled={autoStopEnabled}
        highlightTaskKey={highlightTaskKey}
        onHighlightTaskConsumed={handleHighlightTaskConsumed}
      />
    </>
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

        {!narrow && <div className={styles.rightColumn}>{rightColumn}</div>}

        {narrow && (
          <>
            <Button
              className={styles.drawerBtn}
              icon={<MenuOutlined />}
              onClick={() => setRightDrawerOpen(true)}
              type="primary"
            >
              执行面板
            </Button>
            <Drawer
              title="执行面板"
              open={rightDrawerOpen}
              onClose={() => setRightDrawerOpen(false)}
              width={360}
              styles={{ body: { display: 'flex', flexDirection: 'column' } }}
            >
              {rightColumn}
            </Drawer>
          </>
        )}
      </div>

      <RunLogDrawer
        open={drawerOpen}
        run={drawerRun}
        onClose={handleRunLogClose}
      />
    </PageContainer>
  );
};

export default RequirementWorkspace;
