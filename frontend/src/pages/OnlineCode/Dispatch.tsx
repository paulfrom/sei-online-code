/**
 * Track F9 + F10 + F11 — Dispatch view (concurrency / ADR-0001).
 * F11: trigger `/api/iteration/dispatch` (ep #10), show tasks + their runs;
 *      merge button → `/api/iteration/merge` (ep #15).
 * F9:  task list (ExtTable) filtered by `iterationId` on `/api/task/findByPage` (#11).
 * F10: multi-agent live log panel — tabs keyed by `runId`, each tab tails the
 *      WS/poll frames (contract §3) filtered by that runId (taskId/runId carried).
 *
 * WS is mocked as a polling fallback (contract §3, F10): each poll of
 * `/api/run/findByPage` yields per-run state, which we turn into run-log frames.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'umi';
import { createStyles } from '@ead/antd-style';
import {
  BannerTitle,
  Button,
  Empty,
  ExtTable,
  Popconfirm,
  Spin,
  Tabs,
  Tag,
  message,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import { MergeCellsOutlined, SendOutlined } from '@ead/suid-icons';
import {
  TASK_FIND_BY_PAGE_URL,
  dispatchIteration,
  findOneProject,
  findProjectState,
  findRunsByPage,
  mergeIteration,
} from '@/services/onlineCode';
import type {
  IterationDto,
  LifecycleState,
  ProjectDto,
  RunDto,
  RunState,
  TaskDto,
  TaskState,
} from '@/services/onlineCode';
import LifecycleBadge from './components/LifecycleBadge';
import WorkspaceSourceIndicator from './components/WorkspaceSourceIndicator';

const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    gap: ${token.marginSM}px;
  `,
  header: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
  `,
  headerActions: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
  `,
  body: css`
    flex: 1;
    display: flex;
    gap: ${token.marginSM}px;
    min-height: 0;
  `,
  tablePane: css`
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  `,
  logPane: css`
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    overflow: hidden;
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

const POLL_MS = 1200;

/** states that mean parallel runs are actively in flight (keep polling) */
const IN_FLIGHT: LifecycleState[] = ['DISPATCHING', 'DEVELOPING', 'MERGING', 'DEPLOYING'];

const TASK_STATE_COLOR: Record<TaskState, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  MERGING: 'cyan',
  MERGED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
};

const RUN_STATE_COLOR: Record<RunState, string> = {
  RUNNING: 'processing',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
};

const ts = () => new Date().toISOString().slice(11, 19);

const Dispatch: React.FC = () => {
  const { styles } = useStyles();
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('id') ?? '';

  const [project, setProject] = useState<ProjectDto | null>(null);
  const [iterationId, setIterationId] = useState<string | null>(null);
  const [tasks, setTasks] = useState<TaskDto[]>([]);
  const [runs, setRuns] = useState<RunDto[]>([]);
  /** per-run synthetic log frames (WS polling fallback), keyed by runId */
  const [runLogs, setRunLogs] = useState<Record<string, string[]>>({});
  const [activeRunId, setActiveRunId] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [dispatching, setDispatching] = useState(false);
  const [merging, setMerging] = useState(false);

  const tableRef = useRef<ExtTableRef>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const runStateRef = useRef<Record<string, RunState>>({});
  const logBodyRef = useRef<HTMLDivElement>(null);

  const clearTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  /** append a run-log frame line (carries taskId/runId per contract §3) */
  const appendFrame = useCallback((run: RunDto, line: string) => {
    setRunLogs((prev) => {
      const next = { ...prev };
      const key = run.id;
      const framed = `[${ts()}] [${run.taskId}] ${line}`;
      next[key] = [...(next[key] ?? []), framed];
      return next;
    });
  }, []);

  useEffect(() => {
    const el = logBodyRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [runLogs, activeRunId]);

  /**
   * Poll loop: refresh project state + runs; synthesize per-run frames on state
   * change; stop when the iteration leaves the in-flight set.
   */
  const poll = useCallback(
    async (iterId: string) => {
      const runRes = await findRunsByPage({
        filters: [{ fieldName: 'iterationId', value: iterId, operator: 'EQ' }],
        pageInfo: { page: 1, rows: 100 },
      });
      if (runRes.success && runRes.data) {
        const fresh = runRes.data.rows;
        setRuns(fresh);
        setActiveRunId((cur) => cur ?? fresh[0]?.id);
        fresh.forEach((run) => {
          const prevState = runStateRef.current[run.id];
          if (prevState !== run.state) {
            runStateRef.current[run.id] = run.state;
            if (run.state === 'RUNNING') appendFrame(run, 'vite building…');
            if (run.state === 'SUCCEEDED')
              appendFrame(run, `DONE — exit ${run.exitCode ?? 0} (SUCCEEDED)`);
            if (run.state === 'FAILED') appendFrame(run, `FAILED — exit ${run.exitCode ?? 1}`);
          }
        });
      }

      const stateRes = await findProjectState(projectId);
      if (stateRes.success && stateRes.data) {
        setProject((prev) => (prev ? { ...prev, state: stateRes.data!.state } : prev));
        // keep the task table in sync as states advance
        tableRef.current?.reloadData();
        if (IN_FLIGHT.includes(stateRes.data.state)) {
          timerRef.current = setTimeout(() => poll(iterId), POLL_MS);
        }
      }
    },
    [projectId, appendFrame],
  );

  // initial load
  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      const res = await findOneProject(projectId);
      if (!alive) return;
      if (res.success && res.data) {
        setProject(res.data);
        const iterId = res.data.currentIterationId;
        setIterationId(iterId);
        if (iterId) {
          // if already dispatched, start polling to hydrate tasks/runs
          if (IN_FLIGHT.includes(res.data.state)) poll(iterId);
        }
      }
      setLoading(false);
    })();
    return () => {
      alive = false;
      clearTimer();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const handleDispatch = async () => {
    if (!iterationId) return;
    setDispatching(true);
    try {
      const res = await dispatchIteration(iterationId);
      if (!res.success || !res.data) {
        message.error(res.message ?? '派发失败');
        return;
      }
      setTasks(res.data);
      message.success(res.message ?? '已派发');
      tableRef.current?.reloadData();
      poll(iterationId);
    } finally {
      setDispatching(false);
    }
  };

  const handleMerge = async () => {
    if (!iterationId) return;
    setMerging(true);
    try {
      const res = await mergeIteration(iterationId);
      if (!res.success || !res.data) {
        message.error(res.message ?? '合并失败');
        return;
      }
      message.success(res.message ?? '开始合并');
      poll(iterationId);
    } finally {
      setMerging(false);
    }
  };

  const cascade = useMemo(
    () => (iterationId ? { iterationId } : {}),
    [iterationId],
  );

  const columns: ExtTableProps<TaskDto>['columns'] = [
    { title: 'Seq', dataIndex: 'seq', width: 60 },
    { title: '任务', dataIndex: 'title', width: 160 },
    {
      title: '分支',
      dataIndex: 'worktreeBranch',
      width: 180,
      render: (b: string | null) => b ?? '-',
    },
    {
      title: '文件范围',
      dataIndex: 'fileScope',
      expandUnusedSpace: true,
      render: (scope: string[]) => (scope ?? []).join(', '),
    },
    {
      title: '状态',
      dataIndex: 'state',
      width: 100,
      render: (state: TaskState) => <Tag color={TASK_STATE_COLOR[state]}>{state}</Tag>,
    },
  ];

  const hasRuns = tasks.length > 0 || runs.length > 0;
  const canDispatch = project?.state === 'DISPATCHING';
  // merge allowed once dev work is running/finished and not already merging
  const allRunsDone = runs.length > 0 && runs.every((r) => r.state === 'SUCCEEDED');
  const canMerge = project?.state === 'DEVELOPING' && allRunsDone;

  const runTabs = useMemo(
    () =>
      runs.map((run) => ({
        key: run.id,
        label: (
          <span>
            {run.id} <Tag color={RUN_STATE_COLOR[run.state]}>{run.state}</Tag>
          </span>
        ),
        children: (
          <div ref={run.id === activeRunId ? logBodyRef : undefined} className={styles.logBody}>
            {(runLogs[run.id] ?? []).join('\n') || '等待日志…'}
          </div>
        ),
      })),
    [runs, runLogs, activeRunId, styles.logBody],
  );

  if (loading) {
    return (
      <div className={styles.page}>
        <Spin spinning />
      </div>
    );
  }

  if (!project) {
    return (
      <div className={styles.page}>
        <Empty description="项目不存在" />
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <BannerTitle title={project.name} subTitle="任务派发与并发执行" />
        <div className={styles.headerActions}>
          <WorkspaceSourceIndicator projectId={projectId} />
          <LifecycleBadge state={project.state} />
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={dispatching}
            disabled={!canDispatch}
            onClick={handleDispatch}
            style={{ marginInlineStart: 8 }}
          >
            派发任务
          </Button>
          <Popconfirm title="将所有任务工作树合并回主干？" onConfirm={handleMerge} disabled={!canMerge}>
            <Button
              icon={<MergeCellsOutlined />}
              loading={merging}
              disabled={!canMerge}
              style={{ marginInlineStart: 8 }}
            >
              合并
            </Button>
          </Popconfirm>
        </div>
      </div>

      <div className={styles.body}>
        <div className={styles.tablePane}>
          <ExtTable
            ref={tableRef}
            rowKey="id"
            columns={columns}
            store={{ url: TASK_FIND_BY_PAGE_URL, type: 'POST' }}
            cascade={cascade}
            remotePaging
            showQuickSearch={false}
          />
        </div>

        <div className={styles.logPane}>
          {hasRuns ? (
            <Tabs
              activeKey={activeRunId}
              onChange={setActiveRunId}
              type="card"
              items={runTabs}
              style={{ height: '100%' }}
            />
          ) : (
            <Empty description={canDispatch ? '点击「派发任务」开始并发执行' : '尚无运行'} />
          )}
        </div>
      </div>
    </div>
  );
};

export default Dispatch;
