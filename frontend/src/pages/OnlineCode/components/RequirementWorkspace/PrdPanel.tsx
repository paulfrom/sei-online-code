/**
 * PRD panel: read-only preview with an editable mode and save action.
 */
import React, { useEffect, useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Space, Tag, message } from '@ead/suid';
import { EditOutlined, ReloadOutlined, SaveOutlined, CheckCircleOutlined } from '@ead/suid-icons';
// @ts-ignore JS service module has no declaration file
import { editPrd, confirmPrd, regeneratePrd } from '@/services/requirement';
// @ts-ignore JS service module has no declaration file
import { current as fetchContext } from '@/services/memoryRequirementContext';
import type { RequirementStatus } from '@/services/onlineCodeTypes';
import MarkdownEditor from '../MarkdownEditor';
import DesignContextStatusBar, { formatValidationMessage } from './DesignContextStatusBar';
import type { DesignContextStatusBarProps } from './DesignContextStatusBar';
import type { PrdPanelProps, ResultData } from './types';

const useStyles = createStyles(({ token, css }) => ({
  header: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: ${token.marginSM}px;
    margin-bottom: ${token.marginMD}px;
  `,
  statusRow: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
  `,
  actions: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
    flex-wrap: wrap;
    justify-content: flex-end;
  `,
}));

const STATUS_META: Record<RequirementStatus, { color: string; label: string }> = {
  PRD_GENERATING: { color: 'processing', label: 'PRD 生成中' },
  PRD_REVIEW: { color: 'gold', label: 'PRD 待确认' },
  PRD_CONFIRMED: { color: 'green', label: 'PRD 已确认' },
  FAILED: { color: 'error', label: '失败' },
};

const PrdPanel: React.FC<PrdPanelProps> = ({ requirement, onRefresh }) => {
  const { styles } = useStyles();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(requirement.prdContent ?? '');
  const [saving, setSaving] = useState(false);
  const [contextStatus, setContextStatus] = useState<DesignContextStatusBarProps['contextStatus']>(null);
  const validationMessage = useMemo(
    () => formatValidationMessage(requirement.memoryValidationResultJson),
    [requirement.memoryValidationResultJson],
  );

  useEffect(() => {
    if (!editing) {
      setDraft(requirement.prdContent ?? '');
    }
  }, [requirement.prdContent, editing]);

  useEffect(() => {
    fetchContext(requirement.id).then((res: ResultData<unknown>) => {
      if (res.success && res.data) {
        const ctx = res.data as { contextStatus?: DesignContextStatusBarProps['contextStatus'] };
        setContextStatus(ctx.contextStatus ?? null);
      }
    });
  }, [requirement.id, requirement.designContextId]);

  const status = STATUS_META[requirement.status] ?? { color: 'default', label: requirement.status };
  const confirmDisabled = requirement.memoryValidationStatus === 'FAILED' || contextStatus === 'STALE';

  const handleSave = async () => {
    setSaving(true);
    const res = (await editPrd(requirement.id, draft)) as ResultData<unknown>;
    setSaving(false);
    if (res.success) {
      message.success('PRD 已保存');
      setEditing(false);
      onRefresh();
    } else {
      message.error(res.message ?? '保存失败');
    }
  };

  const handleConfirm = async () => {
    const res = (await confirmPrd(requirement.id)) as ResultData<unknown>;
    if (res.success) {
      message.success('PRD 已确认');
      onRefresh();
    } else {
      message.error(res.message ?? '确认失败');
    }
  };

  const handleRegenerate = async () => {
    const prompt = window.prompt('请输入重生成提示词');
    if (prompt === null) return;
    const res = (await regeneratePrd(requirement.id, prompt)) as ResultData<unknown>;
    if (res.success) {
      message.success('PRD 重生成已启动');
      onRefresh();
    } else {
      message.error(res.message ?? '重生成失败');
    }
  };

  return (
    <Card>
      <div className={styles.header}>
        <div className={styles.statusRow}>
          <span>状态：</span>
          <Tag color={status.color}>{status.label}</Tag>
          {requirement.prdVersion > 0 && <span>版本：{requirement.prdVersion}</span>}
        </div>
        <div className={styles.actions}>
          {editing ? (
            <Space>
              <Button onClick={() => setEditing(false)}>取消</Button>
              <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
                保存
              </Button>
            </Space>
          ) : (
            <Space>
              {requirement.status === 'PRD_REVIEW' && (
                <>
                  <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                    编辑
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={handleRegenerate}>
                    重生成
                  </Button>
                  <Button type="primary" icon={<CheckCircleOutlined />} onClick={handleConfirm} disabled={confirmDisabled}>
                    确认
                  </Button>
                </>
              )}
            </Space>
          )}
        </div>
      </div>

      {requirement.failureSummary && (
        <p style={{ color: '#cf1322', marginBottom: 16 }}>失败摘要：{requirement.failureSummary}</p>
      )}

      <DesignContextStatusBar
        designContextId={requirement.designContextId}
        memoryValidationStatus={requirement.memoryValidationStatus}
        contextStatus={contextStatus}
        validationMessage={validationMessage}
      />

      <MarkdownEditor
        value={draft}
        onChange={setDraft}
        readOnly={!editing}
        height={480}
        placeholder="暂无 PRD 内容"
      />
    </Card>
  );
};

export default PrdPanel;
