/**
 * Track F6 + F7 — Preview page.
 * Drives the tail of the walking skeleton: deploy the confirmed iteration,
 * poll `/api/project/state` + `/api/iteration/findOne` (polling fallback for
 * the WS run-log per contract §3.1 / F2), stream synthetic run-log lines while
 * DEPLOYING, then render the built product in an iframe at previewUrl.
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Button, Result } from '@ead/suid';
import { HistoryOutlined, RocketOutlined } from '@ead/suid-icons';
import {
  deployIteration,
  findOneIteration,
  findOneProject,
  findProjectState,
} from '@/services/onlineCode';
import type { IterationDto, LifecycleState, ProjectDto } from '@/services/onlineCode';
import LifecycleBadge from './components/LifecycleBadge';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';

const useStyles = createStyles(({ token, css }) => ({
  body: css`
    flex: 1;
    display: flex;
    gap: ${token.marginSM}px;
    min-height: 0;
  `,
  previewPane: css`
    flex: 3;
    min-width: 0;
    border: 1px solid ${token.colorBorder};
    border-radius: ${token.borderRadius}px;
    overflow: hidden;
    background: ${token.colorBgContainer};
    iframe {
      width: 100%;
      height: 100%;
      border: 0;
    }
  `,
  logPane: css`
    flex: 2;
    min-width: 0;
    display: flex;
    flex-direction: column;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    overflow: hidden;
  `,
  logHead: css`
    padding: ${token.paddingXS}px ${token.paddingSM}px;
    font-weight: ${token.fontWeightStrong};
    border-bottom: 1px solid ${token.colorBorder};
  `,
  logBody: css`
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding: ${token.paddingSM}px;
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
  `,
}));

/** states that mean a build is actively in flight (keep polling + log) */
const IN_FLIGHT: LifecycleState[] = ['DISPATCHING', 'DEVELOPING', 'MERGING', 'DEPLOYING'];

const POLL_MS = 1200;

const Preview: React.FC = () => {
  const { styles } = useStyles();
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('id') ?? '';

  const [project, setProject] = useState<ProjectDto | null>(null);
  const [iteration, setIteration] = useState<IterationDto | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [deploying, setDeploying] = useState(false);
  const logBodyRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastStateRef = useRef<LifecycleState | null>(null);

  const appendLog = useCallback((line: string) => {
    const ts = new Date().toISOString().slice(11, 19);
    setLogs((prev) => [...prev, `[${ts}] ${line}`]);
  }, []);

  // auto-scroll the log panel to the bottom on new lines
  useEffect(() => {
    const el = logBodyRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [logs]);

  const clearTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  /**
   * Poll loop: fetch project state + current iteration, emit run-log lines on
   * state changes (WS polling fallback), and stop once we reach a terminal
   * (PREVIEW / ACCEPTED / FAILED / CANCELLED) state.
   */
  const poll = useCallback(async () => {
    const stateRes = await findProjectState(projectId);
    if (!stateRes.success || !stateRes.data) return;
    const { state, iterationId } = stateRes.data;

    if (state !== lastStateRef.current) {
      lastStateRef.current = state;
      appendLog(`state → ${state}`);
      if (state === 'DEPLOYING') appendLog('vite v5 building for production…');
    }

    setProject((prev) => (prev ? { ...prev, state } : prev));

    if (iterationId) {
      const iterRes = await findOneIteration(iterationId);
      if (iterRes.success && iterRes.data) {
        setIteration(iterRes.data);
        if (iterRes.data.state === 'PREVIEW' && iterRes.data.previewUrl) {
          appendLog(`DONE — preview at ${iterRes.data.previewUrl}`);
        }
      }
    }

    if (IN_FLIGHT.includes(state)) {
      timerRef.current = setTimeout(poll, POLL_MS);
    }
  }, [projectId, appendLog]);

  // initial load
  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      const res = await findOneProject(projectId);
      if (!alive) return;
      if (res.success && res.data) {
        setProject(res.data);
        lastStateRef.current = res.data.state;
        if (res.data.currentIterationId) {
          const iterRes = await findOneIteration(res.data.currentIterationId);
          if (alive && iterRes.success && iterRes.data) setIteration(iterRes.data);
        }
        if (IN_FLIGHT.includes(res.data.state)) poll();
      }
      setLoading(false);
    })();
    return () => {
      alive = false;
      clearTimer();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const handleDeploy = async () => {
    if (!iteration) return;
    setDeploying(true);
    try {
      const res = await deployIteration(iteration.id);
      if (res.success && res.data) {
        setIteration(res.data);
        appendLog('deploy triggered');
        lastStateRef.current = null; // force next poll to emit the DEPLOYING line
        poll();
      }
    } finally {
      setDeploying(false);
    }
  };

  if (loading) {
    return (
      <PageContainer>
        <PageState loading />
      </PageContainer>
    );
  }

  if (!project) {
    return (
      <PageContainer>
        <PageState error="项目不存在" />
      </PageContainer>
    );
  }

  const canDeploy =
    !!iteration && (iteration.state === 'DISPATCHING' || project.state === 'FAILED');
  const previewUrl = iteration?.previewUrl;

  return (
    <PageContainer>
      <PageHeader
        title={project.name}
        subTitle="迭代预览"
        extra={<LifecycleBadge state={project.state} />}
        actions={
          <>
            <Button
              icon={<HistoryOutlined />}
              onClick={() => history.push(`/online-code/timeline?id=${project.id}`)}
            >
              迭代时间线
            </Button>
            <Button
              type="primary"
              icon={<RocketOutlined />}
              loading={deploying}
              disabled={!canDeploy}
              onClick={handleDeploy}
            >
              部署
            </Button>
          </>
        }
      />

      <div className={styles.body}>
        <div className={styles.previewPane}>
          {previewUrl ? (
            <iframe title="preview" src={previewUrl} />
          ) : (
            <Result
              status="info"
              title="尚无预览"
              subTitle={
                canDeploy ? '点击右上角「部署」以构建并生成预览' : '等待迭代进入可部署状态…'
              }
            />
          )}
        </div>

        <div className={styles.logPane}>
          <div className={styles.logHead}>运行日志</div>
          <div ref={logBodyRef} className={styles.logBody}>
            {logs.length ? logs.join('\n') : '暂无日志'}
          </div>
        </div>
      </div>
    </PageContainer>
  );
};

export default Preview;
