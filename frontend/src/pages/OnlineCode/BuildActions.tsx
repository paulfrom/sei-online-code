/**
 * F5: BuildActions component with "执行编码" button and build_status badges
 * Uses WS client for real-time logs, falls back to polling on WS failure (P2)
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Badge, Button, Space, Tooltip, message, Modal } from '@ead/suid';
import { PlayCircleFilled } from '@ead/suid-icons';
import type { FeatureDesignDto } from '@/services/featureDesign';
import { subscribeRunLog, type RunLogFrame } from '@/utils/run-log-socket';

const useStyles = createStyles(({ token, css }) => ({
  container: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: ${token.paddingSM}px ${token.paddingMD}px;
    background: ${token.colorBgContainer};
    border-bottom: 1px solid ${token.colorBorder};
  `,
  badges: css`
    display: flex;
    gap: ${token.marginSM}px;
    flex-wrap: wrap;
  `,
  logPanel: css`
    max-height: 400px;
    overflow: auto;
    padding: ${token.paddingSM}px;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
  `,
}));

interface BuildActionsProps {
  projectId: string;
}

const buildStatusColorMap: Record<string, string> = {
  IDLE: 'default',
  BUILDING: 'processing',
  BUILT: 'success',
  BUILD_FAILED: 'error',
  STALE: 'warning',
};

const buildStatusTextMap: Record<string, string> = {
  IDLE: '未执行',
  BUILDING: '编码执行中',
  BUILT: '已执行',
  BUILD_FAILED: '执行失败',
  STALE: '已过期',
};

const projectStateTextMap: Record<string, string> = {
  DRAFTING: '项目描述待处理',
  SPEC_REFINING: '概要设计生成中',
  SPEC_REVIEW: '详细设计待确认',
  PLANNING: '概要设计中',
  DESIGNING: '功能设计中',
  READY_TO_BUILD: '准备就绪',
  FAILED: '失败',
};

const BuildActions: React.FC<BuildActionsProps> = ({ projectId }) => {
  const { styles } = useStyles();
  const dispatch = useDispatch();
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const wsCloseRef = useRef<(() => void) | null>(null);

  const { projectState, featureDesigns, loading } = useSelector(
    (state: any) => state.planFeatureDesign,
  );

  // Log panel state
  const [logVisible, setLogVisible] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const logBodyRef = useRef<HTMLDivElement>(null);

  // Auto-scroll logs to bottom
  useEffect(() => {
    if (logBodyRef.current) {
      logBodyRef.current.scrollTop = logBodyRef.current.scrollHeight;
    }
  }, [logs]);

  const appendLog = useCallback((frame: RunLogFrame) => {
    const logLine = `[${new Date(frame.ts).toLocaleTimeString()}] [${frame.stream}] ${frame.line}`;
    setLogs((prev) => [...prev, logLine]);
  }, []);

  const clearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  // Start polling fallback (when WS fails
  const startPollingFallback = useCallback(() => {
    if (!pollingIntervalRef.current) {
      pollingIntervalRef.current = setInterval(() => {
        dispatch({
          type: 'planFeatureDesign/fetchFeatureDesigns',
          payload: { projectId },
        });
      }, 5000);
    }
  }, [dispatch, projectId]);

  const stopPollingFallback = useCallback(() => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  }, []);

  // Initial fetch on mount
  useEffect(() => {
    dispatch({ type: 'planFeatureDesign/fetchProjectState', payload: { projectId } });
  }, [dispatch, projectId]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopPollingFallback();
      if (wsCloseRef.current) {
        wsCloseRef.current();
        wsCloseRef.current = null;
      }
    };
  }, [stopPollingFallback]);

  const handleBuild = useCallback(async () => {
    clearLogs();
    setLogVisible(true);

    const res = await dispatch({
      type: 'planFeatureDesign/buildProject',
      payload: { projectId },
    });

    if (res?.success) {
      const results = res.data || [];
      const startedCount = results.filter((r: any) => !r.skipped).length;
      const skippedCount = results.filter((r: any) => r.skipped).length;

      let msg = `已开始 ${startedCount} 个功能的编码`;
      if (skippedCount > 0) {
        msg += `（跳过 ${skippedCount} 个正在编码执行的功能）`;
      }
      message.success(msg);

      // Subscribe to WS for each started FeatureDesign build.
      const startedBuilds = results.filter((r: any) => !r.skipped && r.runId && r.id);
      if (startedBuilds.length > 0) {
        // For simplicity, subscribe to the first build's logs
        // TODO: support multiple concurrent builds in separate tabs
        const { id: featureDesignId, runId } = startedBuilds[0];

        try {
          const { close } = subscribeRunLog({
            featureDesignId,
            runId,
            onLine: appendLog,
            onTerminal: () => {
              // Refresh build status on terminal frame
              dispatch({
                type: 'planFeatureDesign/fetchFeatureDesigns',
                payload: { projectId },
              });
              // Keep log visible for review
            },
            onError: () => {
              // WS failed, fall back to polling
              message.warning('实时日志连接失败，已降级为轮询模式');
              startPollingFallback();
              if (wsCloseRef.current) {
                wsCloseRef.current();
                wsCloseRef.current = null;
              }
            },
          });
          wsCloseRef.current = close;
        } catch {
          // WS connection failed, use polling fallback
          message.warning('实时日志连接失败，已降级为轮询模式');
          startPollingFallback();
        }
      }
    } else {
      message.error(res?.message || '启动编码失败');
      setLogVisible(false);
    }
  }, [dispatch, projectId, appendLog, clearLogs, startPollingFallback]);

  const isReady = projectState === 'READY_TO_BUILD';
  const isBuilding = featureDesigns?.some(
    (fd: FeatureDesignDto) => fd.buildStatus === 'BUILDING',
  );

  // Aggregate counts for badges
  const buildStatusCounts = featureDesigns?.reduce(
    (acc: Record<string, number>, fd: FeatureDesignDto) => {
      acc[fd.buildStatus] = (acc[fd.buildStatus] || 0) + 1;
      return acc;
    },
    {},
  ) || {};

  return (
    <>
      <div className={styles.container}>
        <Space>
          {Object.entries(buildStatusCounts).map(([status, count]) => (
            <Badge
              key={status}
              status={buildStatusColorMap[status] as any}
              text={`${buildStatusTextMap[status]}: ${count}`}
            />
          ))}
          {isBuilding && <span style={{ color: '#faad14' }}>（刷新中...）</span>}
        </Space>

        <Space>
          {isBuilding && (
            <Button size="small" onClick={() => setLogVisible(true)}>
              查看日志
            </Button>
          )}
          <Tooltip title={!isReady ? projectStateTextMap[projectState || ''] : ''}>
            <Button
              type="primary"
              icon={<PlayCircleFilled />}
              onClick={handleBuild}
              disabled={!isReady || loading}
              loading={loading}
            >
              执行编码
            </Button>
          </Tooltip>
        </Space>
      </div>

      <Modal
        title="编码执行日志"
        open={logVisible}
        onCancel={() => setLogVisible(false)}
        footer={[
          <Button key="close" onClick={() => setLogVisible(false)}>
            关闭
          </Button>,
        ]}
        width={800}
      >
        <div ref={logBodyRef} className={styles.logPanel}>
          {logs.length === 0 ? (
            <span style={{ color: '#8c8c8c' }}>等待编码执行开始…</span>
          ) : (
            logs.map((line, index) => <div key={index}>{line}</div>)
          )}
        </div>
      </Modal>
    </>
  );
};

export default BuildActions;
