/**
 * PRD section: read-only preview with editable mode and save/confirm/regenerate actions.
 * Behaviour mirrors the legacy PrdPanel but drops the Card shell and the
 * DesignContextStatusBar dependency (the underlying file has been removed).
 * The container is responsible for `message` feedback; this component only
 * toggles `editing` and exposes a `saving` state on the Save button.
 */
import React, { useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Space, Tag } from '@ead/suid';
import {
  CheckCircleOutlined,
  EditOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ead/suid-icons';
import MarkdownEditor from '../MarkdownEditor';

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

const STATUS_META = {
  PRD_GENERATING: { color: 'processing', label: 'PRD 生成中' },
  PRD_REVIEW: { color: 'gold', label: 'PRD 待确认' },
  PRD_CONFIRMED: { color: 'green', label: 'PRD 已确认' },
  WAITING_FEEDBACK: { color: 'blue', label: '等待反馈' },
  COMPLETED: { color: 'green', label: '需求已完成' },
  FAILED: { color: 'error', label: '失败' },
};

const PrdSection = ({
  requirement,
  onConfirm,
  onEdit,
  onRegenerate,
}) => {
  const { styles } = useStyles();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(requirement.prdContent ?? '');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!editing) {
      setDraft(requirement.prdContent ?? '');
    }
  }, [requirement.prdContent, editing]);

  const status = STATUS_META[requirement.status] ?? {
    color: 'default',
    label: requirement.status,
  };

  const handleEdit = async () => {
    setSaving(true);
    try {
      await onEdit(draft);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  };

  const handleConfirm = async () => {
    setSaving(true);
    try {
      await onConfirm();
    } finally {
      setSaving(false);
    }
  };

  const handleRegenerate = () => {
    const promptText = window.prompt('请输入重生成提示词');
    if (promptText === null) return;
    void onRegenerate(promptText);
  };

  return (
    <div>
      <div className={styles.header}>
        <div className={styles.statusRow}>
          <span>状态：</span>
          <Tag color={status.color}>{status.label}</Tag>
          {requirement.prdVersion && requirement.prdVersion > 0 && (
            <span>版本：{requirement.prdVersion}</span>
          )}
        </div>
        <div className={styles.actions}>
          {editing ? (
            <Space>
              <Button onClick={() => setEditing(false)}>取消</Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                onClick={handleEdit}
              >
                保存
              </Button>
            </Space>
          ) : (
            requirement.status === 'PRD_REVIEW' && (
              <Space>
                <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                  编辑
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleRegenerate}>
                  重生成
                </Button>
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  loading={saving}
                  onClick={handleConfirm}
                >
                  确认
                </Button>
              </Space>
            )
          )}
        </div>
      </div>

      {requirement.failureSummary && (
        <p style={{ color: '#cf1322', marginBottom: 16 }}>
          失败摘要：{requirement.failureSummary}
        </p>
      )}

      <MarkdownEditor
        value={draft}
        onChange={setDraft}
        readOnly={!editing}
        height={editing ? 480 : 'auto'}
        placeholder="暂无 PRD 内容"
      />
    </div>
  );
};

export default PrdSection;
