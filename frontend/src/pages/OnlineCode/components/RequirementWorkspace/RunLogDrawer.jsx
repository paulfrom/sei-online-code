/**
 * RunLog drawer — three read-only views over a single Run (EXE-008 batch4):
 *
 * 1. 执行记录: run metadata + token usage (findRunUsage).
 * 2. 原始日志: persisted history followed by live WebSocket frames.
 * 3. 证据: immutable EvidenceBundle plus paginated run observations and, when the
 *    Run is resolvable to an Execution, execution effects
 *    (executionProgress/findEffects). Evidence is server-paginated and loaded
 *    on demand; unknown enum values pass through raw. State is never inferred
 *    from logs or exit codes — the authoritative snapshot lives in the backend
 *    progress ledger (ADR-001 §9/§11).
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Drawer, Descriptions, Tag, Tabs, Button } from '@ead/suid';
import {
  findAppliedBehaviorMemories,
  findRunArtifactContent,
  findRunEvidence,
  findRunFeedback,
  findRunLogFrames,
  findRunUsage,
} from '@/services/run';
import { subscribeRunLog } from '@/utils/run-log-socket';
// @ts-ignore JS service module has no declaration file
import { findEffects } from '@/services/executionProgress';
// @ts-ignore JS service module has no declaration file
import { findByRun } from '@/services/runObservation';

const useStyles = createStyles(({ token, css }) => ({
  logArea: css`
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    padding: ${token.paddingSM}px;
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    height: calc(100% - 120px);
    overflow: auto;
  `,
  hint: css`
    color: ${token.colorTextTertiary};
    text-align: center;
    padding: ${token.paddingLG}px;
  `,
  evidenceWrap: css`
    display: flex;
    flex-direction: column;
    gap: ${token.marginMD}px;
  `,
  evSection: css`
    display: flex;
    flex-direction: column;
    gap: ${token.marginXS}px;
  `,
  evTitle: css`
    font-weight: ${token.fontWeightStrong};
    color: ${token.colorTextSecondary};
  `,
  evRow: css`
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: ${token.paddingXS}px 0;
    border-bottom: 1px solid ${token.colorBorderSecondary};
    &:last-child {
      border-bottom: none;
    }
  `,
  evHead: css`
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: ${token.marginXS}px;
  `,
  evMain: css`
    word-break: break-all;
    font-size: ${token.fontSizeSM}px;
  `,
  evMeta: css`
    color: ${token.colorTextTertiary};
    font-size: ${token.fontSizeSM}px;
  `,
  codeBlock: css`
    margin: 0;
    max-height: 320px;
    overflow: auto;
    padding: ${token.paddingSM}px;
    border-radius: ${token.borderRadius}px;
    background: ${token.colorBgContainerDisabled};
    color: ${token.colorText};
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    white-space: pre-wrap;
    word-break: break-word;
  `,
}));

const STATE_META = {
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
};

const RUN_TYPE_META = {
  AGENT: { color: 'blue', label: 'Agent' },
  SYSTEM: { color: 'default', label: '系统' },
};

const TERMINAL_REASON_LABELS = {
  SUCCEEDED: '成功',
  FAILED: '失败',
  TIMEOUT: '超时',
  CANCELLED: '取消',
  SUPERSEDED: '被替代',
};

// Verification status of a run observation; unknown values pass through raw.
const VERIFY_META = {
  CONFIRMED: { color: 'green', label: '已确认' },
  REFUTED: { color: 'error', label: '已否定' },
  PENDING: { color: 'warning', label: '待核实' },
  UNKNOWN: { color: 'default', label: '未知' },
  INCONCLUSIVE: { color: 'default', label: '不确定' },
};

// Execution effect status; unknown values pass through raw.
const EFFECT_STATUS_META = {
  PREPARED: { color: 'default', label: '已准备' },
  APPLIED: { color: 'blue', label: '已应用' },
  CONFIRMED: { color: 'green', label: '已确认' },
  UNKNOWN: { color: 'warning', label: '待对账' },
  FAILED: { color: 'error', label: '失败' },
};

const ROWS = 20;

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

const formatTokens = (value) => (value === null || value === undefined ? '-' : value);

const shorten = (id) => (id ? String(id).slice(0, 8) : '-');

const tagOf = (meta) => (meta ? <Tag color={meta.color}>{meta.label}</Tag> : null);

const verifyTag = (s) => (s ? tagOf(VERIFY_META[s] || { color: 'default', label: s }) : null);

const effectStatusTag = (s) => (s ? tagOf(EFFECT_STATUS_META[s] || { color: 'default', label: s }) : null);

const rowsOf = (res) =>
  res && res.success && res.data && Array.isArray(res.data.rows) ? res.data.rows : [];

const jsonValue = (value, fallback) => {
  if (!value) return fallback;
  try {
    return typeof value === 'string' ? JSON.parse(value) : value;
  } catch {
    return fallback;
  }
};

const frameKey = (frame) =>
  frame?.sequenceNo !== null && frame?.sequenceNo !== undefined
    ? `sequence:${frame.sequenceNo}`
    : `${frame?.ts || ''}|${frame?.stream || ''}|${frame?.line || ''}|${frame?.state || ''}`;

const mergeFrames = (current, incoming) => {
  const values = new Map();
  [...(current || []), ...(incoming || [])].forEach((frame) => {
    if (frame) values.set(frameKey(frame), frame);
  });
  return [...values.values()].sort((a, b) => {
    if (a.sequenceNo !== null && a.sequenceNo !== undefined
      && b.sequenceNo !== null && b.sequenceNo !== undefined) {
      return a.sequenceNo - b.sequenceNo;
    }
    return String(a.ts || '').localeCompare(String(b.ts || ''));
  });
};

const formatLogFrame = (frame) => {
  const sequence = frame.sequenceNo === null || frame.sequenceNo === undefined
    ? ''
    : `#${frame.sequenceNo} `;
  return `${sequence}[${frame.ts || '-'}] [${frame.stream || 'system'}] ${frame.line || ''}`;
};

/**
 * Paginated evidence loader. Has-more is inferred from a full page so it does
 * not depend on the PageResult total. Mirrors ExecutionProgressTab's
 * CheckpointList pagination.
 *
 * @param {(page: number) => Promise<any[]>} fetchPage returns the rows array
 * @param {React.DependencyList} deps reload trigger (open / run id / execution id)
 */
function useEvidencePage(fetchPage, deps) {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    (p) => {
      setLoading(true);
      fetchPage(p)
        .then((rows) => {
          const next = Array.isArray(rows) ? rows : [];
          setItems((prev) => (p === 1 ? next : [...prev, ...next]));
          setHasMore(next.length === ROWS);
        })
        .finally(() => setLoading(false));
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    deps,
  );

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const loadMore = () => {
    const next = page + 1;
    setPage(next);
    load(next);
  };

  return { items, hasMore, loading, loadMore };
}

/**
 * @param {{ open: boolean, run: any, onClose: () => void, executionId?: string | null }} props
 */
const RunLogDrawer = ({ open, run, onClose, executionId }) => {
  const { styles } = useStyles();
  const [logFrames, setLogFrames] = useState([]);
  const [logHistoryLoading, setLogHistoryLoading] = useState(false);
  const [logTruncated, setLogTruncated] = useState(false);
  const [terminated, setTerminated] = useState(null);
  const [usage, setUsage] = useState(null);
  const [evidence, setEvidence] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [behaviorMemories, setBehaviorMemories] = useState([]);
  const [artifactContent, setArtifactContent] = useState(null);
  const [selectedArtifactId, setSelectedArtifactId] = useState(null);
  const socketRef = useRef(null);

  const runId = run?.id || null;
  const execId = executionId || null;

  // Observations are keyed by runId (always available). Effects need an
  // executionId; when absent the effects section shows an honest empty state
  // rather than fabricating one.
  const observations = useEvidencePage(
    (p) => (runId ? findByRun(runId, p, ROWS).then(rowsOf) : Promise.resolve([])),
    [open, runId],
  );
  const effects = useEvidencePage(
    (p) => (execId ? findEffects(execId, { page: p, rows: ROWS }).then(rowsOf) : Promise.resolve([])),
    [open, runId, execId],
  );

  useEffect(() => {
    if (!open || !run) {
      setLogFrames([]);
      setLogHistoryLoading(false);
      setLogTruncated(false);
      setTerminated(null);
      setUsage(null);
      setEvidence(null);
      setFeedback(null);
      setBehaviorMemories([]);
      setArtifactContent(null);
      setSelectedArtifactId(null);
      return undefined;
    }
    let cancelled = false;
    setLogFrames([]);
    setLogHistoryLoading(true);
    setLogTruncated(false);
    setTerminated(null);
    setUsage(null);
    setEvidence(null);
    setFeedback(null);
    setBehaviorMemories([]);
    setArtifactContent(null);
    setSelectedArtifactId(null);

    findRunUsage(run.id).then((res) => {
      if (!cancelled && res && res.success && res.data) {
        setUsage(res.data);
      }
    });
    findRunEvidence(run.id).then((res) => {
      if (!cancelled && res && res.success && res.data) {
        setEvidence(res.data);
      }
    });
    findRunFeedback(run.id).then((res) => {
      if (!cancelled && res && res.success && res.data) {
        setFeedback(res.data);
      }
    });
    findAppliedBehaviorMemories(run.id).then((res) => {
      if (!cancelled && res && res.success && Array.isArray(res.data)) {
        setBehaviorMemories(res.data);
      }
    });
    findRunLogFrames(run.id, 0, 2000)
      .then((res) => {
        if (cancelled || !res || !res.success || !res.data) return;
        const frames = Array.isArray(res.data.frames) ? res.data.frames : [];
        setLogFrames((current) => mergeFrames(current, frames));
        setLogTruncated(Boolean(res.data.truncated));
        const terminal = [...frames].reverse().find((frame) => frame.state);
        if (terminal) setTerminated(terminal.state);
      })
      .finally(() => {
        if (!cancelled) setLogHistoryLoading(false);
      });

    const logStreamKey = run.logStreamKey;
    if (!logStreamKey) {
      return () => {
        cancelled = true;
      };
    }

    const socket = subscribeRunLog({
      logStreamKey,
      runId: run.id,
      onLine: (frame) => {
        setLogFrames((current) => mergeFrames(current, [frame]));
      },
      onTerminal: (state) => {
        setTerminated(state);
      },
    });
    socketRef.current = socket;
    return () => {
      cancelled = true;
      socket.close();
      socketRef.current = null;
    };
  }, [open, run]);

  const stateMeta = run && run.state ? STATE_META[run.state] : { color: 'default', label: '-' };
  const runTypeMeta = run && run.runType
    ? RUN_TYPE_META[run.runType] || { color: 'default', label: run.runType }
    : { color: 'default', label: '-' };
  const terminalMeta = terminated
    ? STATE_META[terminated] || { color: 'default', label: terminated }
    : null;
  const usageSnapshot = usage || run || {};
  const commandResults = jsonValue(evidence?.commandResultsJson, []);
  const acceptanceResults = jsonValue(evidence?.acceptanceCriteriaJson, []);
  const evidenceFindings = jsonValue(evidence?.findingsJson, []);
  const remediationBrief = jsonValue(feedback?.remediationBriefJson, null);
  const artifacts = Array.isArray(evidence?.artifacts) ? evidence.artifacts : [];

  const showArtifact = async (artifact) => {
    setSelectedArtifactId(artifact.id);
    setArtifactContent('加载中…');
    const res = await findRunArtifactContent(artifact.id);
    setArtifactContent(res && res.success ? res.data || '' : res?.message || '材料读取失败');
  };

  const tabItems = run
    ? [
        {
          key: 'records',
          label: '执行记录',
          children: (
            <Descriptions column={2} size="small">
              <Descriptions.Item label="Run 序号">{run.runNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={stateMeta.color}>{stateMeta.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="类型">
                <Tag color={runTypeMeta.color}>{runTypeMeta.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="尝试序号">{run.attemptNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="触发来源">{run.triggerSource || '-'}</Descriptions.Item>
              <Descriptions.Item label="终止原因">
                {TERMINAL_REASON_LABELS[run.terminalReason] || run.terminalReason || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="父 Run">{run.parentRunId || '-'}</Descriptions.Item>
              <Descriptions.Item label="补偿 Run">{run.compensatesRunId || '-'}</Descriptions.Item>
              <Descriptions.Item label="Agent">{run.agentName || '-'}</Descriptions.Item>
              <Descriptions.Item label="CLI">{run.cliTool || '-'}</Descriptions.Item>
              <Descriptions.Item label="模型">{run.model || '-'}</Descriptions.Item>
              <Descriptions.Item label="Usage 状态">{usageSnapshot.usageStatus || '-'}</Descriptions.Item>
              <Descriptions.Item label="输入 Token">{formatTokens(usageSnapshot.inputTokens)}</Descriptions.Item>
              <Descriptions.Item label="输出 Token">{formatTokens(usageSnapshot.outputTokens)}</Descriptions.Item>
              <Descriptions.Item label="缓存读 Token">{formatTokens(usageSnapshot.cacheReadTokens)}</Descriptions.Item>
              <Descriptions.Item label="缓存写 Token">{formatTokens(usageSnapshot.cacheWriteTokens)}</Descriptions.Item>
              <Descriptions.Item label="总 Token">{formatTokens(usageSnapshot.totalTokens)}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{formatDateTime(run.startedDate)}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{formatDateTime(run.finishedDate)}</Descriptions.Item>
              <Descriptions.Item label="退出码">{run.exitCode ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="失败原因" span={2}>
                {run.failureReason || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="摘要" span={2}>
                {run.summary || run.failureSummary || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="用户提示词" span={2}>
                {run.userPrompt || '-'}
              </Descriptions.Item>
            </Descriptions>
          ),
        },
        {
          key: 'log',
          label: '原始日志',
          children: (
            <React.Fragment>
              {terminalMeta && (
                <div style={{ marginBottom: 8 }}>
                  日志流已结束
                  {tagOf(terminalMeta)}
                </div>
              )}
              {logTruncated && (
                <div style={{ marginBottom: 8 }}>
                  <Tag color="warning">日志已达到保留上限</Tag>
                </div>
              )}
              <div className={styles.logArea}>
                {logFrames.length
                  ? logFrames.map(formatLogFrame).join('\n')
                  : logHistoryLoading
                    ? '正在加载历史日志…'
                    : run.logStreamKey
                      ? '等待日志输出…'
                      : '暂无持久化日志'}
              </div>
            </React.Fragment>
          ),
        },
        {
          key: 'evidence',
          label: '证据',
          children: (
            <div className={styles.evidenceWrap}>
              <section className={styles.evSection}>
                <div className={styles.evTitle}>结构化 EvidenceBundle</div>
                {evidence ? (
                  <React.Fragment>
                    <div className={styles.evHead}>
                      <Tag color={evidence.completeness === 'COMPLETE' ? 'green' : 'warning'}>
                        {evidence.completeness || 'UNKNOWN'}
                      </Tag>
                      <Tag>{evidence.status || '-'}</Tag>
                      <span className={styles.evMeta}>v{evidence.evidenceVersion || '-'}</span>
                      <span className={styles.evMeta}>exitCode={evidence.processExitCode ?? '-'}</span>
                    </div>
                    <div className={styles.evMain}>{evidence.summary || '-'}</div>
                    <div className={styles.evTitle}>实际命令</div>
                    {commandResults.length ? commandResults.map((command, index) => (
                      <div key={`${command.command || 'command'}-${index}`} className={styles.evRow}>
                        <div className={styles.evHead}>
                          <Tag color={command.exitCode === 0 ? 'green' : 'error'}>
                            exitCode={command.exitCode ?? '-'}
                          </Tag>
                          <span className={styles.evMain}>{command.command || '-'}</span>
                        </div>
                        {command.result && <div className={styles.evMeta}>{command.result}</div>}
                      </div>
                    )) : <div className={styles.hint}>未报告实际命令</div>}
                    <div className={styles.evTitle}>实际验收标准</div>
                    {acceptanceResults.length ? acceptanceResults.map((criterion, index) => (
                      <div key={`${criterion.criterion || 'criterion'}-${index}`} className={styles.evRow}>
                        <div className={styles.evHead}>
                          <Tag color={criterion.status === 'PASSED' ? 'green'
                            : criterion.status === 'FAILED' ? 'error' : 'warning'}>
                            {criterion.status || 'NOT_VERIFIED'}
                          </Tag>
                          <span className={styles.evMain}>{criterion.criterion || '-'}</span>
                        </div>
                        {Array.isArray(criterion.evidence) && criterion.evidence.length > 0 && (
                          <div className={styles.evMeta}>{criterion.evidence.join('；')}</div>
                        )}
                      </div>
                    )) : <div className={styles.hint}>任务未配置验收标准</div>}
                    {evidenceFindings.length > 0 && (
                      <React.Fragment>
                        <div className={styles.evTitle}>Findings</div>
                        {evidenceFindings.map((finding, index) => (
                          <div key={`${finding}-${index}`} className={styles.evMain}>- {String(finding)}</div>
                        ))}
                      </React.Fragment>
                    )}
                  </React.Fragment>
                ) : (
                  <div className={styles.hint}>暂无结构化 EvidenceBundle</div>
                )}
              </section>
              <section className={styles.evSection}>
                <div className={styles.evTitle}>原始材料（已脱敏）</div>
                {artifacts.length ? (
                  <React.Fragment>
                    {artifacts.map((artifact) => (
                      <div key={artifact.id} className={styles.evRow}>
                        <div className={styles.evHead}>
                          <Tag>{artifact.artifactType}</Tag>
                          <Tag color={artifact.completeness === 'COMPLETE' ? 'green' : 'warning'}>
                            {artifact.completeness}
                          </Tag>
                          <span className={styles.evMeta}>{artifact.byteLength ?? 0} bytes</span>
                          <Button size="small" type="link" onClick={() => showArtifact(artifact)}>
                            查看内容
                          </Button>
                        </div>
                      </div>
                    ))}
                    {selectedArtifactId && (
                      <pre className={styles.codeBlock}>{artifactContent || '无内容'}</pre>
                    )}
                  </React.Fragment>
                ) : <div className={styles.hint}>暂无原始材料</div>}
              </section>
              <section className={styles.evSection}>
                <div className={styles.evTitle}>PM 权威修复反馈</div>
                {feedback ? (
                  <React.Fragment>
                    <div className={styles.evHead}>
                      <Tag>{feedback.decision || '-'}</Tag>
                      <span className={styles.evMeta}>evidenceId={feedback.evidenceId || 'MISSING'}</span>
                    </div>
                    <div className={styles.evMain}>{feedback.summary || '-'}</div>
                    {remediationBrief && (
                      <pre className={styles.codeBlock}>
                        {JSON.stringify(remediationBrief, null, 2)}
                      </pre>
                    )}
                  </React.Fragment>
                ) : <div className={styles.hint}>暂无 PM 反馈</div>}
              </section>
              <section className={styles.evSection}>
                <div className={styles.evTitle}>本 Run 应用的长期行为记忆</div>
                {behaviorMemories.length ? behaviorMemories.map((memory) => (
                  <div key={memory.id} className={styles.evRow}>
                    <div className={styles.evHead}>
                      <Tag>{memory.status}</Tag>
                      <Tag>{memory.lastOutcome || 'UNKNOWN'}</Tag>
                      <span className={styles.evMeta}>{memory.scopeKey}</span>
                    </div>
                    <div className={styles.evMain}>{memory.ruleText}</div>
                  </div>
                )) : <div className={styles.hint}>本 Run 未应用长期行为记忆</div>}
              </section>
              <section className={styles.evSection}>
                <div className={styles.evTitle}>运行观察（Observation）</div>
                {observations.loading && observations.items.length === 0 ? (
                  <div className={styles.hint}>观察记录加载中…</div>
                ) : observations.items.length === 0 ? (
                  <div className={styles.hint}>暂无观察记录</div>
                ) : (
                  <React.Fragment>
                    {observations.items.map((o) => (
                      <div key={o.observationId} className={styles.evRow}>
                        <div className={styles.evHead}>
                          <Tag>#{o.sequenceNo ?? '-'}</Tag>
                          {verifyTag(o.verificationStatus)}
                          {o.observationType && <Tag>{o.observationType}</Tag>}
                          {o.sourceType && <Tag>{o.sourceType}</Tag>}
                          <span className={styles.evMeta}>{formatDateTime(o.observedAt)}</span>
                        </div>
                        <div className={styles.evMain}>{o.summary || '-'}</div>
                      </div>
                    ))}
                    {observations.hasMore && (
                      <Button
                        size="small"
                        type="link"
                        loading={observations.loading}
                        onClick={observations.loadMore}
                      >
                        加载更多
                      </Button>
                    )}
                  </React.Fragment>
                )}
              </section>
              <section className={styles.evSection}>
                <div className={styles.evTitle}>执行副作用（Effect）</div>
                {execId ? (
                  effects.loading && effects.items.length === 0 ? (
                    <div className={styles.hint}>effect 加载中…</div>
                  ) : effects.items.length === 0 ? (
                    <div className={styles.hint}>暂无 effect</div>
                  ) : (
                    <React.Fragment>
                      {effects.items.map((e) => (
                        <div key={e.effectId} className={styles.evRow}>
                          <div className={styles.evHead}>
                            {effectStatusTag(e.status)}
                            {e.effectType && <Tag color="cyan">{e.effectType}</Tag>}
                            <span className={styles.evMain}>{shorten(e.effectKey)}</span>
                            <span className={styles.evMeta}>
                              {formatDateTime(e.appliedAt || e.preparedAt)}
                            </span>
                          </div>
                          {e.externalReference && (
                            <div className={styles.evMain}>{e.externalReference}</div>
                          )}
                        </div>
                      ))}
                      {effects.hasMore && (
                        <Button
                          size="small"
                          type="link"
                          loading={effects.loading}
                          onClick={effects.loadMore}
                        >
                          加载更多
                        </Button>
                      )}
                    </React.Fragment>
                  )
                ) : (
                  <div className={styles.hint}>无 Execution 关联，无法加载 effect 证据</div>
                )}
              </section>
            </div>
          ),
        },
      ]
    : [];

  return (
    <Drawer title="运行日志" open={open} onClose={onClose} width={820} destroyOnClose>
      {run ? (
        <Tabs defaultActiveKey="records" items={tabItems} />
      ) : (
        <div className={styles.hint}>未选择运行</div>
      )}
    </Drawer>
  );
};

export default RunLogDrawer;
