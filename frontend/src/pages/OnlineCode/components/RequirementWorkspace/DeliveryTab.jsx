/**
 * Delivery tab: MR status card + delivery event list + retry-MR action.
 */
import React, { useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Collapse, Descriptions, Popconfirm, Space, Tag, message } from '@ead/suid';
import { CloudUploadOutlined, RedoOutlined, ReloadOutlined } from '@ead/suid-icons';

const useStyles = createStyles(({ token, css }) => ({
  mrUrl: css`
    word-break: break-all;
  `,
  eventList: css`
    margin-top: ${token.marginMD}px;
  `,
}));

const DELIVERY_STATUS_META = {
  NONE: { color: 'default', label: '无交付' },
  PENDING: { color: 'processing', label: '交付中' },
  CREATED: { color: 'blue', label: 'MR 已创建' },
  UPDATED: { color: 'cyan', label: 'MR 已更新' },
  MERGED: { color: 'green', label: 'MR 已合并' },
  FAILED: { color: 'error', label: '交付失败' },
};

const EVENT_TYPES = [
  'MR_CREATED',
  'MR_UPDATED',
  'MR_MERGED',
  'MR_FAILED',
  'WORKSPACE_SYNCED',
  'WORKSPACE_SYNC_FAILED',
  'REQUIREMENT_COMPLETED',
  'REQUIREMENT_REOPENED',
  'MEMORY_UPDATED',
  'MEMORY_UPDATE_FAILED',
];

const EVENT_META = {
  MR_CREATED: { color: 'blue', label: 'MR 创建' },
  MR_UPDATED: { color: 'cyan', label: 'MR 更新' },
  MR_MERGED: { color: 'green', label: 'MR 合并' },
  MR_FAILED: { color: 'error', label: 'MR 失败' },
  WORKSPACE_SYNCED: { color: 'green', label: '工作区同步' },
  WORKSPACE_SYNC_FAILED: { color: 'error', label: '工作区同步失败' },
  REQUIREMENT_COMPLETED: { color: 'green', label: '需求完成' },
  REQUIREMENT_REOPENED: { color: 'blue', label: '需求重新打开' },
  MEMORY_UPDATED: { color: 'green', label: '内存更新' },
  MEMORY_UPDATE_FAILED: { color: 'warning', label: '内存更新失败' },
  CONTEXT_SUMMARY_FAILED: { color: 'error', label: '上下文摘要失败' },
};

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

/**
 * @param {{ delivery: any, comments: any[], workspaceStatus?: any,
 * requirement?: any,
 * onRetryMr?: () => void, onSubmitMr?: () => void, onRefreshWorkspace?: () => void,
 * onSyncWorkspace?: () => void,
 * onConfirmCompletion?: () => void, onReopenRequirement?: () => void,
 * manualDeliveryEnabled?: boolean }} props
 */
const DeliveryTab = ({
  requirement,
  delivery,
  comments,
  workspaceStatus,
  onRetryMr,
  onSubmitMr,
  onRefreshWorkspace,
  onSyncWorkspace,
  onConfirmCompletion,
  onReopenRequirement,
  manualDeliveryEnabled,
}) => {
  const { styles } = useStyles();
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [changingRequirementStatus, setChangingRequirementStatus] = useState(false);

  const deliveryEvents = useMemo(
    () =>
      comments
        .filter((c) => EVENT_TYPES.includes(c.commentType))
        .sort((a, b) => (a.createdDate < b.createdDate ? 1 : -1)),
    [comments],
  );

  const handleRetry = async () => {
    // retryMr needs the requirement id; we don't have it here directly — the
    // container wires onRetryMr. If provided, delegate; otherwise call service.
    if (onRetryMr) {
      await onRetryMr();
      return;
    }
    // Fallback path is not reached in the wired layout; kept for type safety.
    message.info('请在需求工作区重试 MR');
  };

  const statusMeta = DELIVERY_STATUS_META[delivery.status] || DELIVERY_STATUS_META.NONE;

  const handleRefreshWorkspace = async () => {
    if (!onRefreshWorkspace) return;
    setRefreshing(true);
    try {
      await onRefreshWorkspace();
    } finally {
      setRefreshing(false);
    }
  };

  const handleSubmit = async () => {
    if (!onSubmitMr) return;
    setSubmitting(true);
    try {
      await onSubmitMr();
    } finally {
      setSubmitting(false);
    }
  };

  const handleSyncWorkspace = async () => {
    if (!onSyncWorkspace) return;
    setSyncing(true);
    try {
      await onSyncWorkspace();
    } finally {
      setSyncing(false);
    }
  };

  const handleRequirementStatusChange = async () => {
    const action = requirement?.status === 'COMPLETED'
      ? onReopenRequirement
      : onConfirmCompletion;
    if (!action) return;
    setChangingRequirementStatus(true);
    try {
      await action();
    } finally {
      setChangingRequirementStatus(false);
    }
  };

  const requirementCompleted = requirement?.status === 'COMPLETED';
  const completionEnabled = requirement?.automationStatus === 'COMPLETED'
    && Boolean(delivery.mrUrl);

  return (
    <div>
      <Card size="small">
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Space>
            <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
          </Space>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="分支">{delivery.branch || '-'}</Descriptions.Item>
            <Descriptions.Item label="目标分支">{delivery.targetBranch || '-'}</Descriptions.Item>
            <Descriptions.Item label="Commit">
              {delivery.commitHash ? String(delivery.commitHash).slice(0, 8) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="MR URL">
              {delivery.mrUrl ? (
                <a
                  className={styles.mrUrl}
                  href={delivery.mrUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  {delivery.mrUrl}
                </a>
              ) : (
                '-'
              )}
            </Descriptions.Item>
          </Descriptions>
          {delivery.status === 'FAILED' && (
            <div>
              <Button icon={<RedoOutlined />} onClick={handleRetry}>
                重试 MR
              </Button>
            </div>
          )}
        </Space>
      </Card>

      <Card size="small" title="当前工作区" className={styles.eventList}>
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Descriptions column={1} size="small">
            <Descriptions.Item label="工作区分支">
              {workspaceStatus?.branchName || delivery.branch || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="更新基线分支">
              {workspaceStatus?.baseBranch || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="交付目标分支">
              {workspaceStatus?.deliveryTargetBranch || delivery.targetBranch || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="当前 HEAD">
              {workspaceStatus?.currentHead ? String(workspaceStatus.currentHead).slice(0, 12) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="未提交修改">
              {workspaceStatus ? (
                <Tag color={workspaceStatus.dirty ? 'warning' : 'success'}>
                  {workspaceStatus.dirty ? `${workspaceStatus.changedFiles?.length || 0} 个文件` : '工作区干净'}
                </Tag>
              ) : '-'}
            </Descriptions.Item>
          </Descriptions>
          <Space wrap>
            <Button
              icon={<ReloadOutlined />}
              loading={refreshing}
              onClick={handleRefreshWorkspace}
            >
              刷新工作区
            </Button>
            <Popconfirm
              title="确认更新基线分支并合并到当前需求分支？"
              description="工作区必须没有未提交修改；发生合并冲突时不会启动新的 Loop。"
              onConfirm={handleSyncWorkspace}
            >
              <Button icon={<ReloadOutlined />} loading={syncing}>
                同步基线分支
              </Button>
            </Popconfirm>
            <Popconfirm
              title="确认提交当前已完成交付物？"
              description="系统将提交工作区当前分支上的修改、推送该分支并创建或更新 GitLab MR，不会切换分支。"
              onConfirm={handleSubmit}
              disabled={!manualDeliveryEnabled}
            >
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                loading={submitting}
                disabled={!manualDeliveryEnabled}
              >
                手动提交交付物
              </Button>
            </Popconfirm>
            <Popconfirm
              title={requirementCompleted ? '确认重新打开需求？' : '确认整个需求已经完成？'}
              description={requirementCompleted
                ? '重新打开后，下一条评论会先同步基线分支，再开启新的变更 Loop。'
                : '系统会校验 MR 已合并、没有运行中任务或计划修订，并在完成前同步基线分支。'}
              onConfirm={handleRequirementStatusChange}
              disabled={!requirementCompleted && !completionEnabled}
            >
              <Button
                icon={<RedoOutlined />}
                loading={changingRequirementStatus}
                disabled={!requirementCompleted && !completionEnabled}
              >
                {requirementCompleted ? '重新打开需求' : '完成需求'}
              </Button>
            </Popconfirm>
          </Space>
          {!manualDeliveryEnabled && (
            <span style={{ color: '#999' }}>
              {!workspaceStatus
                ? '请先刷新工作区。'
                : !workspaceStatus.dirty
                  ? '当前工作区没有未提交修改。'
                  : requirementCompleted
                    ? '需求已完成，请先重新打开需求。'
                    : '交付正在进行中，请稍后再试。'}
            </span>
          )}
          {!requirementCompleted && !completionEnabled && (
            <span style={{ color: '#999' }}>当前 Loop 交付完成后可确认整个需求完成。</span>
          )}
        </Space>
      </Card>

      <div className={styles.eventList}>
        <Collapse
          ghost
          defaultActiveKey={['events']}
          items={[
            {
              key: 'events',
              label: `交付事件（${deliveryEvents.length}）`,
              children:
                deliveryEvents.length === 0 ? (
                  <span>暂无交付事件</span>
                ) : (
                  deliveryEvents.map((ev) => {
                    const meta = EVENT_META[ev.commentType] || { color: 'default', label: ev.commentType };
                    return (
                      <div key={ev.id} style={{ marginBottom: 8 }}>
                        <Space>
                          <Tag color={meta.color}>{meta.label}</Tag>
                          <span style={{ fontSize: 12, color: '#999' }}>
                            {formatDateTime(ev.createdDate)}
                          </span>
                        </Space>
                        {ev.content && (
                          <pre style={{ margin: '4px 0 0', whiteSpace: 'pre-wrap' }}>{ev.content}</pre>
                        )}
                      </div>
                    );
                  })
                ),
            },
          ]}
        />
      </div>
    </div>
  );
};

export default DeliveryTab;
