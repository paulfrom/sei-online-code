import React, { useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Descriptions, Tag } from '@ead/suid';

export interface FailureInfoLike {
  failureCode?: string | null;
  failureStage?: string | null;
  failureSummary?: string | null;
  failureDetail?: string | null;
  lastFailedAt?: string | null;
  lastRetryAt?: string | null;
  retryCount?: number | null;
  nextRetryAt?: string | null;
  lastTriggerSource?: string | null;
}

const useStyles = createStyles(({ token, css }) => ({
  panel: css`
    border: 1px solid ${token.colorErrorBorder};
    background: ${token.colorErrorBg};
    border-radius: ${token.borderRadius}px;
    padding: ${token.paddingMD}px;
  `,
  header: css`
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: ${token.marginSM}px;
    margin-bottom: ${token.marginSM}px;
  `,
  title: css`
    display: flex;
    flex-direction: column;
    gap: ${token.marginXXS}px;
  `,
  summary: css`
    white-space: pre-wrap;
    word-break: break-word;
    color: ${token.colorText};
    margin-bottom: ${token.marginSM}px;
  `,
  detail: css`
    white-space: pre-wrap;
    word-break: break-word;
    color: ${token.colorTextSecondary};
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadiusSM}px;
    padding: ${token.paddingSM}px;
    margin-top: ${token.marginSM}px;
  `,
  tagRow: css`
    display: flex;
    flex-wrap: wrap;
    gap: ${token.marginXS}px;
  `,
}));

interface FailureInfoPanelProps {
  info: FailureInfoLike | null | undefined;
  title?: string;
}

const formatDateTime = (value?: string | null) => value || '-';

const FailureInfoPanel: React.FC<FailureInfoPanelProps> = ({
  info,
  title = '失败信息',
}) => {
  const { styles } = useStyles();
  const [expanded, setExpanded] = useState(false);

  const visible = useMemo(() => {
    if (!info) return false;
    return Boolean(
      info.failureCode ||
        info.failureSummary ||
        info.failureDetail ||
        info.lastFailedAt ||
        info.retryCount,
    );
  }, [info]);

  if (!visible || !info) {
    return null;
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.title}>
          <strong>{title}</strong>
          <div className={styles.tagRow}>
            {info.failureStage && <Tag color="error">{info.failureStage}</Tag>}
            {info.failureCode && <Tag color="error">{info.failureCode}</Tag>}
            {typeof info.retryCount === 'number' && (
              <Tag color="processing">重试 {info.retryCount} 次</Tag>
            )}
            {info.lastTriggerSource && <Tag>{info.lastTriggerSource}</Tag>}
          </div>
        </div>
        {info.failureDetail && (
          <Button type="link" onClick={() => setExpanded((prev) => !prev)}>
            {expanded ? '收起详情' : '查看详情'}
          </Button>
        )}
      </div>

      {info.failureSummary && <div className={styles.summary}>{info.failureSummary}</div>}

      <Descriptions size="small" column={2}>
        <Descriptions.Item label="最近失败">
          {formatDateTime(info.lastFailedAt)}
        </Descriptions.Item>
        <Descriptions.Item label="最近重试">
          {formatDateTime(info.lastRetryAt)}
        </Descriptions.Item>
        <Descriptions.Item label="下次重试">
          {formatDateTime(info.nextRetryAt)}
        </Descriptions.Item>
        <Descriptions.Item label="触发来源">
          {info.lastTriggerSource || '-'}
        </Descriptions.Item>
      </Descriptions>

      {expanded && info.failureDetail && (
        <div className={styles.detail}>{info.failureDetail}</div>
      )}
    </div>
  );
};

export default FailureInfoPanel;
