/**
 * F5: BuildActions component with "执行编码" button and build_status badges
 * Polls for BUILDING status updates (no WS client per pre-verified facts)
 */
import React, { useCallback, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Badge, Button, Space, Tooltip, message } from '@ead/suid';
import { PlayCircleOutlined } from '@ead/suid-icons';
import type { FeatureDesignDto } from '@/services/featureDesign';

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
  IDLE: '未构建',
  BUILDING: '构建中',
  BUILT: '已构建',
  BUILD_FAILED: '构建失败',
  STALE: '已过期',
};

const projectStateTextMap: Record<string, string> = {
  DRAFTING: '项目初始化中',
  PLANNING: '计划制定中',
  DESIGNING: '功能设计中',
  READY_TO_BUILD: '准备就绪',
  FAILED: '失败',
};

const BuildActions: React.FC<BuildActionsProps> = ({ projectId }) => {
  const { styles } = useStyles();
  const dispatch = useDispatch();
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);

  const { projectState, featureDesigns, loading } = useSelector(
    (state: any) => state.planFeatureDesign,
  );

  // Initial fetch on mount
  useEffect(() => {
    dispatch({ type: 'planFeatureDesign/fetchProjectState', payload: { projectId } });
  }, [dispatch, projectId]);

  // Poll when any FD is BUILDING
  useEffect(() => {
    const hasBuilding = featureDesigns?.some(
      (fd: FeatureDesignDto) => fd.buildStatus === 'BUILDING',
    );

    if (hasBuilding && !pollingIntervalRef.current) {
      pollingIntervalRef.current = setInterval(() => {
        dispatch({
          type: 'planFeatureDesign/fetchFeatureDesigns',
          payload: { projectId },
        });
      }, 5000);
    } else if (!hasBuilding && pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }

    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
  }, [dispatch, projectId, featureDesigns]);

  const handleBuild = useCallback(async () => {
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
        msg += `（跳过 ${skippedCount} 个已在构建中的功能）`;
      }
      message.success(msg);
    } else {
      message.error(res?.message || '启动编码失败');
    }
  }, [dispatch, projectId]);

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

      <Tooltip title={!isReady ? projectStateTextMap[projectState || ''] : ''}>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          onClick={handleBuild}
          disabled={!isReady || loading}
          loading={loading}
        >
          执行编码
        </Button>
      </Tooltip>
    </div>
  );
};

export default BuildActions;
