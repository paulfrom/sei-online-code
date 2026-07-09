/**
 * Coding task panel: list with status, file scope, and run/rerun/view actions.
 */
import React, { useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Modal, Space, Table, Tag, message } from '@ead/suid';
import { PlayCircleFilled, RedoOutlined, HistoryOutlined, FileTextOutlined } from '@ead/suid-icons';
// @ts-ignore JS service module has no declaration file
import { runCodingTask, rerunCodingTask } from '@/services/codingTask';
import type { CodingTaskDto, CodingTaskStatus } from '@/services/onlineCodeTypes';
import type { CodingTaskPanelProps, ResultData } from './types';

const useStyles = createStyles(({ token, css }) => ({
  fileScope: css`
    margin: 0;
    padding: ${token.paddingSM}px;
    background: ${token.colorFillTertiary};
    border-radius: ${token.borderRadiusSM}px;
    max-height: 240px;
    overflow: auto;
  `,
  fileScopeItem: css`
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    line-height: 1.8;
  `,
}));

const STATUS_META: Record<CodingTaskStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待执行' },
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
  STALE: { color: 'default', label: '已过期' },
};

const CodingTaskPanel: React.FC<CodingTaskPanelProps> = ({ codingTasks, onRefresh, onViewRuns }) => {
  const { styles } = useStyles();
  const [scopeModal, setScopeModal] = useState<{ open: boolean; title: string; files: string[] }>({
    open: false,
    title: '',
    files: [],
  });

  const handleRun = async (task: CodingTaskDto) => {
    const res = (await runCodingTask(task.id, null)) as ResultData<unknown>;
    if (res.success) {
      message.success('运行已启动');
      onRefresh();
    } else {
      message.error(res.message ?? '运行失败');
    }
  };

  const handleRerun = async (task: CodingTaskDto) => {
    const prompt = window.prompt('请输入重跑提示词（必填）');
    if (!prompt) return;
    const res = (await rerunCodingTask(task.id, prompt)) as ResultData<unknown>;
    if (res.success) {
      message.success('重跑已启动');
      onRefresh();
    } else {
      message.error(res.message ?? '重跑失败');
    }
  };

  const openScope = (task: CodingTaskDto) => {
    setScopeModal({
      open: true,
      title: task.title || task.id,
      files: task.fileScope || [],
    });
  };

  const closeScope = () => {
    setScopeModal((prev) => ({ ...prev, open: false }));
  };

  const columns = [
    {
      title: '任务标题',
      dataIndex: 'title',
      render: (value: string | null | undefined, record: CodingTaskDto) => value || record.id,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status: CodingTaskStatus) => {
        const meta = STATUS_META[status] ?? { color: 'default', label: status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '详细设计版本',
      dataIndex: 'detailedDesignVersion',
      width: 120,
    },
    {
      title: '失败摘要',
      dataIndex: 'failureSummary',
      render: (value: string | null | undefined) => value || '-',
    },
    {
      title: '操作',
      dataIndex: 'id',
      width: 240,
      render: (_id: string, record: CodingTaskDto) => (
        <Space>
          {record.status === 'PENDING' && (
            <Button type="link" icon={<PlayCircleFilled />} onClick={() => handleRun(record)}>
              运行
            </Button>
          )}
          {(record.status === 'FAILED' || record.status === 'SUCCEEDED' || record.status === 'CANCELLED') && (
            <Button type="link" icon={<RedoOutlined />} onClick={() => handleRerun(record)}>
              重跑
            </Button>
          )}
          <Button type="link" icon={<HistoryOutlined />} onClick={() => onViewRuns?.(record)}>
            运行历史
          </Button>
          <Button type="link" icon={<FileTextOutlined />} onClick={() => openScope(record)}>
            代码片段
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={codingTasks}
        columns={columns}
        locale={{ emptyText: '暂无编码任务' }}
      />

      <Modal
        title={`代码文件范围 - ${scopeModal.title}`}
        open={scopeModal.open}
        onCancel={closeScope}
        footer={<Button onClick={closeScope}>关闭</Button>}
        width={640}
      >
        {scopeModal.files.length === 0 ? (
          <p>暂无文件范围</p>
        ) : (
          <pre className={styles.fileScope}>
            {scopeModal.files.map((file) => (
              <div key={file} className={styles.fileScopeItem}>
                {file}
              </div>
            ))}
          </pre>
        )}
      </Modal>
    </Card>
  );
};

export default CodingTaskPanel;
