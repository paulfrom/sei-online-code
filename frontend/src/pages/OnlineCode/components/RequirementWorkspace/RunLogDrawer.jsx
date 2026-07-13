/**
 * RunLog drawer: streams run log lines via WebSocket (per iterationId),
 * falls back to a static hint when no iterationId is available.
 */
import React, { useEffect, useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Drawer, Descriptions, Tag } from '@ead/suid';
import { subscribeRunLog } from '@/utils/run-log-socket';
import type { RunLogDrawerProps } from './types';

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

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

/**
 * @param {{ open: boolean, run: any, onClose: () => void }} props
 */
const RunLogDrawer = ({ open, run, onClose }) => {
  const { styles } = useStyles();
  const [lines, setLines] = useState([]);
  const [terminated, setTerminated] = useState(null);
  const socketRef = useRef(null);

  useEffect(() => {
    if (!open || !run) {
      setLines([]);
      setTerminated(null);
      return undefined;
    }
    setLines([]);
    setTerminated(null);

    const iterationId = run.iterationId;
    if (!iterationId) {
      return undefined;
    }

    const socket = subscribeRunLog({
      iterationId,
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
            <Descriptions.Item label="触发来源">{run.triggerSource || '-'}</Descriptions.Item>
            <Descriptions.Item label="开始时间">{formatDateTime(run.startedDate)}</Descriptions.Item>
            <Descriptions.Item label="结束时间">{formatDateTime(run.finishedDate)}</Descriptions.Item>
            <Descriptions.Item label="退出码">{run.exitCode ?? '-'}</Descriptions.Item>
          </Descriptions>

          {run.iterationId ? (
            <pre className={styles.logArea}>
              {lines.length === 0 && !terminated
                ? '等待日志…'
                : lines.join('\n')}
            </pre>
          ) : (
            <div className={styles.hint}>无运行日志通道</div>
          )}
        </React.Fragment>
      ) : (
        <div className={styles.hint}>未选择运行</div>
      )}
    </Drawer>
  );
};

export default RunLogDrawer;
