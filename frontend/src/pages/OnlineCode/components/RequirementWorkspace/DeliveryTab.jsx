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
  FAILED: { color: 'error', label: '交付失败' },
};

const EVENT_TYPES = [
  'MR_CREATED',
  'MR_UPDATED',
  'MR_FAILED',
  'MEMORY_UPDATED',
  'MEMORY_UPDATE_FAILED',
];

const EVENT_META = {
  MR_CREATED: { color: 'blue', label: 'MR 创建' },
  MR_UPDATED: { color: 'cyan', label: 'MR 更新' },
  MR_FAILED: { color: 'error', label: 'MR 失败' },
  MEMORY_UPDATED: { color: 'green', label: '内存更新' },
  MEMORY_UPDATE_FAILED: { color: 'warning', label: '内存更新失败' },
  CONTEXT_SUMMARY_FAILED: { color: 'error', label: '上下文摘要失败' },
};

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

/**
 * @param {{ delivery: any, comments: any[], workspaceStatus?: any,
 * onRetryMr?: () => void, onSubmitMr?: () => void, onRefreshWorkspace?: () => void,
 * manualDeliveryEnabled?: boolean }} props
 */
const DeliveryTab = ({
  delivery,
  comments,
  workspaceStatus,
  onRetryMr,
  onSubmitMr,
  onRefreshWorkspace,
  manualDeliveryEnabled,
}) => {
  const { styles } = useStyles();
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);

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
              title="确认提交当前已完成交付物？"
              description="系统将提交工作区修改、推送需求分支并创建或更新 GitLab MR。"
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
          </Space>
          {!manualDeliveryEnabled && (
            <span style={{ color: '#999' }}>执行计划验收通过且当前未在交付时可手动提交。</span>
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
