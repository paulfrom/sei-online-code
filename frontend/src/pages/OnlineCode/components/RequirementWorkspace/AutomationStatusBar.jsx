/**
 * Automation status bar for the right column.
 * Shows the current automation status, active loop id and plan version.
 */
import React from 'react';
import { createStyles } from '@ead/antd-style';
import { Tag } from '@ead/suid';

const useStyles = createStyles(({ token, css }) => ({
  bar: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
    padding: ${token.paddingSM}px ${token.paddingMD}px;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    flex-wrap: wrap;
  `,
  item: css`
    display: flex;
    align-items: center;
    gap: ${token.marginXS}px;
    color: ${token.colorTextSecondary};
    font-size: ${token.fontSizeSM}px;
  `,
}));

const AUTOMATION_STATUS_META = {
  IDLE: { color: 'default', label: '闲置' },
  PLANNING: { color: 'processing', label: '规划中' },
  DEVELOPING: { color: 'processing', label: '开发中' },
  VALIDATING: { color: 'processing', label: '验证中' },
  ACCEPTING: { color: 'processing', label: '验收中' },
  DELIVERING: { color: 'processing', label: '交付中' },
  INTERRUPTED: { color: 'warning', label: '已中断' },
  WAITING_HUMAN: { color: 'warning', label: '等待人工' },
  COMPLETED: { color: 'green', label: '已完成' },
  FAILED: { color: 'error', label: '失败' },
};

const shorten = (id) => (id ? id.slice(0, 8) : '-');

const AutomationStatusBar = ({ status, activeLoopId, planVersion }) => {
  const { styles } = useStyles();
  const meta = status ? AUTOMATION_STATUS_META[status] : { color: 'default', label: '-' };

  return (
    <div className={styles.bar}>
      <span className={styles.item}>
        自动化：
        <Tag color={meta.color}>{meta.label}</Tag>
      </span>
      <span className={styles.item}>
        Loop：{shorten(activeLoopId)}
      </span>
      {typeof planVersion === 'number' && (
        <span className={styles.item}>
          Plan v{planVersion}
        </span>
      )}
    </div>
  );
};

export default AutomationStatusBar;
export { AUTOMATION_STATUS_META };
