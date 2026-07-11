/**
 * Overview design panel: title/status/content with MarkdownEditor editing.
 */
import React, { useEffect, useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Empty, Space, Tag, message } from '@ead/suid';
import { EditOutlined, ReloadOutlined, SaveOutlined, CheckCircleOutlined } from '@ead/suid-icons';
// @ts-ignore JS service module has no declaration file
import { editOverviewDesign, confirmOverviewDesign, regenerateOverviewDesign } from '@/services/overviewDesign';
// @ts-ignore JS service module has no declaration file
import { current as fetchContext } from '@/services/memoryRequirementContext';
import type { OverviewDesignStatus } from '@/services/onlineCodeTypes';
import MarkdownEditor from '../MarkdownEditor';
import DesignContextStatusBar, { formatValidationMessage } from './DesignContextStatusBar';
import type { DesignContextStatusBarProps } from './DesignContextStatusBar';
import type { OverviewDesignPanelProps, ResultData } from './types';

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

const STATUS_META: Record<OverviewDesignStatus, { color: string; label: string }> = {
  GENERATING: { color: 'processing', label: '生成中' },
  DRAFT: { color: 'default', label: '草稿' },
  CONFIRMED: { color: 'green', label: '已确认' },
  FAILED: { color: 'error', label: '失败' },
};

const OverviewDesignPanel: React.FC<OverviewDesignPanelProps> = ({
  overviewDesign,
  onRefresh,
}) => {
  const { styles } = useStyles();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(overviewDesign?.content ?? '');
  const [saving, setSaving] = useState(false);
  const [contextStatus, setContextStatus] = useState<DesignContextStatusBarProps['contextStatus']>(null);
  const validationMessage = useMemo(
    () => formatValidationMessage(overviewDesign.memoryValidationResultJson),
    [overviewDesign.memoryValidationResultJson],
  );

  useEffect(() => {
    if (!editing) {
      setDraft(overviewDesign?.content ?? '');
    }
  }, [overviewDesign?.content, editing]);

  useEffect(() => {
    if (overviewDesign?.requirementId) {
      fetchContext(overviewDesign.requirementId).then((res: ResultData<unknown>) => {
        if (res.success && res.data) {
          const ctx = res.data as { contextStatus?: DesignContextStatusBarProps['contextStatus'] };
          setContextStatus(ctx.contextStatus ?? null);
        }
      });
    }
  }, [overviewDesign?.requirementId, overviewDesign?.designContextId]);

  if (!overviewDesign) {
    return (
      <Card>
        <Empty description="概览设计尚未生成" />
      </Card>
    );
  }

  const status = STATUS_META[overviewDesign.status] ?? { color: 'default', label: overviewDesign.status };
  const confirmDisabled = overviewDesign.memoryValidationStatus === 'FAILED' || contextStatus === 'STALE';

  const handleSave = async () => {
    setSaving(true);
    const res = (await editOverviewDesign(overviewDesign.id, draft)) as ResultData<unknown>;
    setSaving(false);
    if (res.success) {
      message.success('概览设计已保存');
      setEditing(false);
      onRefresh();
    } else {
      message.error(res.message ?? '保存失败');
    }
  };

  const handleConfirm = async () => {
    const res = (await confirmOverviewDesign(overviewDesign.id)) as ResultData<unknown>;
    if (res.success) {
      message.success('概览设计已确认');
      onRefresh();
    } else {
      message.error(res.message ?? '确认失败');
    }
  };

  const handleRegenerate = async () => {
    const prompt = window.prompt('请输入重生成提示词');
    if (prompt === null) return;
    const res = (await regenerateOverviewDesign(overviewDesign.id, prompt)) as ResultData<unknown>;
    if (res.success) {
      message.success('概览设计重生成已启动');
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
          {overviewDesign.version > 0 && <span>版本：{overviewDesign.version}</span>}
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
              <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                编辑
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleRegenerate}>
                重生成
              </Button>
              {overviewDesign.status === 'DRAFT' && (
                <Button type="primary" icon={<CheckCircleOutlined />} onClick={handleConfirm} disabled={confirmDisabled}>
                  确认
                </Button>
              )}
            </Space>
          )}
        </div>
      </div>

      {overviewDesign.failureSummary && (
        <p style={{ color: '#cf1322', marginBottom: 16 }}>失败摘要：{overviewDesign.failureSummary}</p>
      )}

      <DesignContextStatusBar
        designContextId={overviewDesign.designContextId}
        memoryValidationStatus={overviewDesign.memoryValidationStatus}
        contextStatus={contextStatus}
        validationMessage={validationMessage}
      />

      <MarkdownEditor
        value={draft}
        onChange={setDraft}
        readOnly={!editing}
        height={480}
        placeholder="暂无概览设计内容"
      />
    </Card>
  );
};

export default OverviewDesignPanel;
