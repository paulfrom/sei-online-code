/**
 * Task tab: list of coding tasks with run/rerun/view-run/view-snippet actions.
 * Includes the (degraded) stop-automation toolbar button.
 */
import React, { useMemo, useState, useEffect } from 'react';
import { createStyles } from '@ead/antd-style';
import {
  Button,
  Modal,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from '@ead/suid';
import {
  PlayCircleFilled,
  RedoOutlined,
  HistoryOutlined,
  FileTextOutlined,
  StopOutlined,
} from '@ead/suid-icons';
// @ts-ignore JS service module has no declaration file
import { runCodingTask, rerunCodingTask } from '@/services/codingTask';
import type { ResultData } from './types';

const useStyles = createStyles(({ token, css }) => ({
  toolbar: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: ${token.marginSM}px;
    margin-bottom: ${token.marginMD}px;
  `,
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
  highlightedRow: css`
    background: ${token.colorWarningBg};
  `,
}));

const STATUS_META = {
  PENDING: { color: 'default', label: '待执行' },
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  VALIDATION_FAILED: { color: 'red', label: '验证失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
  STALE: { color: 'default', label: '已过期' },
  BLOCKED: { color: 'orange', label: '阻塞' },
};

const isTerminal = (s) =>
  s === 'FAILED' || s === 'SUCCEEDED' || s === 'CANCELLED' || s === 'VALIDATION_FAILED';

/**
 * @param {{
 *   tasks: any[],
 *   onRun?: (t: any) => Promise<void>,
 *   onRerun?: (t: any, p: string) => Promise<void>,
 *   onViewRun?: (t: any) => void,
 *   onStop: () => Promise<void>,
 *   stopEnabled: boolean,
 *   highlightTaskKey?: string | null,
 *   onHighlightTaskConsumed?: () => void,
 * }} props
 */
const TaskTab = ({
  tasks,
  onRun,
  onRerun,
  onViewRun,
  onStop,
  stopEnabled,
  highlightTaskKey,
  onHighlightTaskConsumed,
}) => {
  const { styles } = useStyles();
  const [scopeModal, setScopeModal] = useState({ open: false, title: '', files: [] });
  const [stopping, setStopping] = useState(false);

  const stats = useMemo(() => {
    const map = {};
    tasks.forEach((t) => {
      map[t.status] = (map[t.status] || 0) + 1;
    });
    return map;
  }, [tasks]);

  useEffect(() => {
    if (highlightTaskKey && onHighlightTaskConsumed) {
      onHighlightTaskConsumed();
    }
  }, [highlightTaskKey, onHighlightTaskConsumed]);

  const handleStop = async () => {
    setStopping(true);
    try {
      await onStop();
    } finally {
      setStopping(false);
    }
  };

  const handleRun = async (task) => {
    const res = await runCodingTask(task.id, null);
    if (res.success) {
      message.success('运行已启动');
      await onRun(task);
    } else {
      message.error(res.message ?? '运行失败');
    }
  };

  const handleRerun = async (task) => {
    const prompt = window.prompt('请输入重跑提示词（必填）');
    if (!prompt) return;
    const res = await rerunCodingTask(task.id, prompt);
    if (res.success) {
      message.success('重跑已启动');
      await onRerun(task, prompt);
    } else {
      message.error(res.message ?? '重跑失败');
    }
  };

  const openScope = (task) => {
    setScopeModal({ open: true, title: task.title || task.id, files: task.fileScope || [] });
  };
  const closeScope = () => setScopeModal((prev) => ({ ...prev, open: false }));

  const columns = [
    {
      title: 'Key',
      dataIndex: 'planTaskKey',
      width: 110,
      render: (value) => value || '-',
    },
    { title: '标题', dataIndex: 'title', render: (v, r) => v || r.id },
    {
      title: 'Agent',
      dataIndex: 'assignedAgent',
      width: 130,
      render: (v) => v || '-',
    },
    {
      title: 'Area',
      dataIndex: 'area',
      width: 100,
      render: (v) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status) => {
        const meta = STATUS_META[status] || { color: 'default', label: status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '失败摘要',
      dataIndex: 'failureSummary',
      render: (v) => v || '-',
    },
    {
      title: '操作',
      dataIndex: 'id',
      width: 280,
      render: (_id, record) => (
        <Space>
          {record.status === 'PENDING' && (
            <Button type="link" icon={<PlayCircleFilled />} onClick={() => handleRun(record)}>
              运行
            </Button>
          )}
          {isTerminal(record.status) && (
            <Button type="link" icon={<RedoOutlined />} onClick={() => handleRerun(record)}>
              重跑
            </Button>
          )}
          <Button type="link" icon={<HistoryOutlined />} onClick={() => onViewRun && onViewRun(record)}>
            查看运行
          </Button>
          <Button type="link" icon={<FileTextOutlined />} onClick={() => openScope(record)}>
            代码片段
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className={styles.toolbar}>
        <Space>
          <span>共 {tasks.length} 个任务</span>
          {stats.RUNNING > 0 && <Tag color="processing">执行中 {stats.RUNNING}</Tag>}
          {stats.FAILED > 0 && <Tag color="error">失败 {stats.FAILED}</Tag>}
          {stats.BLOCKED > 0 && <Tag color="orange">阻塞 {stats.BLOCKED}</Tag>}
        </Space>
        {/* TODO: replace with POST /requirement/{id}/stop once backend exposes it.
            Current degraded behavior: batch-cancel RUNNING coding tasks via /coding-task/{id}/cancel. */}
        <Tooltip
          title={
            stopEnabled
              ? '停止当前所有执行中的任务（降级：逐个取消）'
              : '无可停止的运行任务'
          }
        >
          <Button
            danger
            icon={<StopOutlined />}
            disabled={!stopEnabled}
            loading={stopping}
            onClick={handleStop}
          >
            停止自动化
          </Button>
        </Tooltip>
      </div>

      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={tasks}
        columns={columns}
        rowClassName={(record) =>
          highlightTaskKey && record.planTaskKey === highlightTaskKey
            ? styles.highlightedRow
            : ''
        }
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
    </div>
  );
};

export default TaskTab;
