/**
 * RunLog drawer: streams run log lines via WebSocket (per logStreamKey),
 * falls back to a static hint when no logStreamKey is available.
 */
import React, { useEffect, useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Drawer, Descriptions, Tag } from '@ead/suid';
import { findRunUsage } from '@/services/run';
import { subscribeRunLog } from '@/utils/run-log-socket';

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

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

const formatTokens = (value) => (value == null ? '-' : value);

/**
 * @param {{ open: boolean, run: any, onClose: () => void }} props
 */
const RunLogDrawer = ({ open, run, onClose }) => {
  const { styles } = useStyles();
  const [lines, setLines] = useState([]);
  const [terminated, setTerminated] = useState(null);
  const [usage, setUsage] = useState(null);
  const socketRef = useRef(null);

  useEffect(() => {
    if (!open || !run) {
      setLines([]);
      setTerminated(null);
      setUsage(null);
      return undefined;
    }
    setLines([]);
    setTerminated(null);
    setUsage(null);

    findRunUsage(run.id).then((res) => {
      if (res && res.success && res.data) {
        setUsage(res.data);
      }
    });

    const logStreamKey = run.logStreamKey;
    if (!logStreamKey) {
      return undefined;
    }

    const socket = subscribeRunLog({
      logStreamKey,
      runId: run.id,
      onLine: (frame) => {
        setLines((prev) => [...prev, frame.line]);
      },
      onTerminal: (state) => {
        setTerminated(state);
      },
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
    };
  }, [open, run]);

  const stateMeta = run && run.state ? STATE_META[run.state] : { color: 'default', label: '-' };
  const runTypeMeta = run && run.runType
    ? RUN_TYPE_META[run.runType] || { color: 'default', label: run.runType }
    : { color: 'default', label: '-' };
  const usageSnapshot = usage || run || {};

  return (
    <Drawer
      title="运行日志"
      open={open}
      onClose={onClose}
      width={720}
      destroyOnClose
    >
      {run ? (
        <React.Fragment>
          <Descriptions column={2} size="small" style={{ marginBottom: 12 }}>
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
            <Descriptions.Item label="摘要" span={2}>
              {run.summary || run.failureSummary || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="用户提示词" span={2}>
              {run.userPrompt || '-'}
            </Descriptions.Item>
          </Descriptions>
        </React.Fragment>
      ) : (
        <div className={styles.hint}>未选择运行</div>
      )}
    </Drawer>
  );
};

export default RunLogDrawer;
