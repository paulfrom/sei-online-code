/**
 * Detailed design panel: module-scoped designs with an editable drawer.
 */
import React, { useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Drawer, Form, Input, Select, Space, Table, Tag, message } from '@ead/suid';
import { EditOutlined, SaveOutlined, CheckCircleOutlined, CheckSquareOutlined } from '@ead/suid-icons';
// @ts-ignore JS service module has no declaration file
import { editDetailedDesign, confirmDetailedDesign, batchConfirmDetailedDesign } from '@/services/detailedDesign';
import type { DetailedDesignDto, DetailedDesignStatus } from '@/services/onlineCodeTypes';
import MarkdownEditor from '../MarkdownEditor';
import type { DetailedDesignPanelProps, ResultData } from './types';

const useStyles = createStyles(({ token, css }) => ({
  toolbar: css`
    display: flex;
    justify-content: flex-end;
    margin-bottom: ${token.marginMD}px;
  `,
  drawerBody: css`
    display: flex;
    flex-direction: column;
    gap: ${token.marginMD}px;
  `,
}));

const STATUS_META: Record<DetailedDesignStatus, { color: string; label: string }> = {
  GENERATING: { color: 'processing', label: '生成中' },
  REVIEW: { color: 'gold', label: '待确认' },
  CONFIRMED: { color: 'green', label: '已确认' },
  FAILED: { color: 'error', label: '失败' },
};

const STATUS_OPTIONS: { value: DetailedDesignStatus; label: string }[] = [
  { value: 'GENERATING', label: '生成中' },
  { value: 'REVIEW', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'FAILED', label: '失败' },
];

const DetailedDesignPanel: React.FC<DetailedDesignPanelProps> = ({ detailedDesigns, onRefresh }) => {
  const { styles } = useStyles();
  const [editing, setEditing] = useState<DetailedDesignDto | null>(null);
  const [draftTitle, setDraftTitle] = useState('');
  const [draftContent, setDraftContent] = useState('');
  const [draftStatus, setDraftStatus] = useState<DetailedDesignStatus>('REVIEW');
  const [saving, setSaving] = useState(false);

  const reviewIds = useMemo(
    () => detailedDesigns.filter((d) => d.status === 'REVIEW').map((d) => d.id),
    [detailedDesigns],
  );

  const openEdit = (record: DetailedDesignDto) => {
    setEditing(record);
    setDraftTitle(record.moduleTitle || '');
    setDraftContent(record.content ?? '');
    setDraftStatus(record.status);
  };

  const closeEdit = () => {
    setEditing(null);
    setDraftTitle('');
    setDraftContent('');
    setDraftStatus('REVIEW');
  };

  const handleSave = async () => {
    if (!editing) return;
    setSaving(true);
    const res = (await editDetailedDesign(editing.id, draftContent)) as ResultData<unknown>;
    setSaving(false);
    if (res.success) {
      message.success('详细设计已保存');
      closeEdit();
      onRefresh();
    } else {
      message.error(res.message ?? '保存失败');
    }
  };

  const handleConfirm = async (id: string) => {
    const res = (await confirmDetailedDesign(id)) as ResultData<unknown>;
    if (res.success) {
      message.success('详细设计已确认');
      onRefresh();
    } else {
      message.error(res.message ?? '确认失败');
    }
  };

  const handleBatchConfirm = async () => {
    if (reviewIds.length === 0) {
      message.info('没有可确认的详细设计');
      return;
    }
    const res = (await batchConfirmDetailedDesign(reviewIds)) as ResultData<unknown>;
    if (res.success) {
      message.success(`已确认 ${reviewIds.length} 条详细设计`);
      onRefresh();
    } else {
      message.error(res.message ?? '批量确认失败');
    }
  };

  const columns = [
    {
      title: '模块',
      dataIndex: 'moduleTitle',
      render: (value: string | null | undefined, record: DetailedDesignDto) =>
        value || record.moduleId || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status: DetailedDesignStatus) => {
        const meta = STATUS_META[status] ?? { color: 'default', label: status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '版本', dataIndex: 'version', width: 90 },
    {
      title: '失败摘要',
      dataIndex: 'failureSummary',
      render: (value: string | null | undefined) => value || '-',
    },
    {
      title: '操作',
      dataIndex: 'id',
      width: 180,
      render: (_id: string, record: DetailedDesignDto) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          {record.status === 'REVIEW' && (
            <Button type="link" icon={<CheckCircleOutlined />} onClick={() => handleConfirm(record.id)}>
              确认
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <div className={styles.toolbar}>
        <Button type="primary" icon={<CheckSquareOutlined />} onClick={handleBatchConfirm}>
          批量确认
        </Button>
      </div>

      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={detailedDesigns}
        columns={columns}
        locale={{ emptyText: '暂无详细设计' }}
      />

      <Drawer
        title="编辑详细设计"
        width={720}
        open={Boolean(editing)}
        onClose={closeEdit}
        footer={
          <Space>
            <Button onClick={closeEdit}>取消</Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
              保存
            </Button>
          </Space>
        }
      >
        {editing && (
          <div className={styles.drawerBody}>
            <Form layout="vertical">
              <Form.Item label="标题">
                <Input
                  value={draftTitle}
                  onChange={(e) => setDraftTitle(e.target.value)}
                  placeholder="模块标题"
                />
              </Form.Item>
              <Form.Item label="状态">
                <Select<DetailedDesignStatus>
                  value={draftStatus}
                  onChange={(value) => setDraftStatus(value)}
                  options={STATUS_OPTIONS}
                />
              </Form.Item>
            </Form>
            <MarkdownEditor
              value={draftContent}
              onChange={setDraftContent}
              readOnly={false}
              height={360}
              placeholder="请输入详细设计内容"
            />
          </div>
        )}
      </Drawer>
    </Card>
  );
};

export default DetailedDesignPanel;
