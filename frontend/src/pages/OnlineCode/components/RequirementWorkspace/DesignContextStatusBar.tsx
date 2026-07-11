/**
 * Design context status bar shown in PRD / overview / detailed design panels.
 * Phase 3 first version: shows designContextId and memoryValidationStatus.
 */
import React from 'react';
import { createStyles } from '@ead/antd-style';
import { Alert, Tag } from '@ead/suid';

const useStyles = createStyles(({ token, css }) => ({
  bar: css`
    margin-bottom: ${token.marginMD}px;
    padding: ${token.paddingSM}px ${token.paddingMD}px;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    display: flex;
    align-items: center;
    gap: ${token.marginMD}px;
    flex-wrap: wrap;
  `,
}));

const validationColor = {
  NOT_RUN: 'default',
  PASSED: 'success',
  WARNING: 'warning',
  FAILED: 'error',
};

const validationLabel = {
  NOT_RUN: '未校验',
  PASSED: '通过',
  WARNING: '警告',
  FAILED: '失败',
};

const contextStatusColor = {
  READY: 'success',
  STALE: 'warning',
  FAILED: 'error',
};

const contextStatusLabel = {
  READY: 'READY',
  STALE: 'STALE',
  FAILED: 'FAILED',
};

export interface DesignContextStatusBarProps {
  designContextId?: string | null;
  memoryValidationStatus?: 'NOT_RUN' | 'PASSED' | 'WARNING' | 'FAILED' | null;
  contextStatus?: 'READY' | 'STALE' | 'FAILED' | null;
  validationMessage?: string | null;
}

const DesignContextStatusBar: React.FC<DesignContextStatusBarProps> = ({
  designContextId,
  memoryValidationStatus,
  contextStatus,
  validationMessage,
}) => {
  const { styles } = useStyles();
  const status = memoryValidationStatus ?? 'NOT_RUN';
  const ctxStatus = contextStatus ?? 'READY';

  return (
    <>
      <div className={styles.bar}>
        <span>
          设计依据：
          {designContextId ? (
            <Tag>Context {designContextId.slice(0, 8)}</Tag>
          ) : (
            <Tag>未生成</Tag>
          )}
        </span>
        <span>
          上下文状态：
          <Tag color={contextStatusColor[ctxStatus]}>{contextStatusLabel[ctxStatus]}</Tag>
        </span>
        <span>
          校验状态：
          <Tag color={validationColor[status]}>{validationLabel[status]}</Tag>
        </span>
      </div>
      {ctxStatus === 'STALE' && (
        <Alert
          type="warning"
          showIcon
          message="设计上下文已过期"
          description="请重新生成文档以刷新上下文"
          style={{ marginBottom: 16 }}
        />
      )}
      {status === 'FAILED' && (
        <Alert
          type="error"
          showIcon
          message="记忆校验未通过"
          description={validationMessage ?? '请修改文档或重新生成后再次校验'}
          style={{ marginBottom: 16 }}
        />
      )}
      {status === 'WARNING' && (
        <Alert
          type="warning"
          showIcon
          message="记忆校验存在警告"
          description={validationMessage ?? '仍可确认，但建议检查警告项'}
          style={{ marginBottom: 16 }}
        />
      )}
    </>
  );
};

export function formatValidationMessage(json?: string | null): string | null {
  if (!json) {
    return null;
  }
  try {
    const result = JSON.parse(json) as {
      findings?: Array<{ severity: string; message: string; suggestedAction?: string }>;
    };
    if (!result.findings || result.findings.length === 0) {
      return null;
    }
    return result.findings
      .map(
        (f, i) =>
          `${i + 1}. [${f.severity}] ${f.message}${
            f.suggestedAction ? `（建议：${f.suggestedAction}）` : ''
          }`,
      )
      .join('\n');
  } catch {
    return null;
  }
}

export default DesignContextStatusBar;
