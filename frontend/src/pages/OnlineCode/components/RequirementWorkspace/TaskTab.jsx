/**
 * Task tab: list of coding tasks with latest development/validation results.
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
} from '@ead/suid';
import {
  RedoOutlined,
  HistoryOutlined,
  FileTextOutlined,
  StopOutlined,
} from '@ead/suid-icons';
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
  VALIDATING: { color: 'processing', label: '验证中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  VALIDATION_FAILED: { color: 'red', label: '验证失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
  STALE: { color: 'default', label: '已过期' },
  BLOCKED: { color: 'orange', label: '阻塞' },
};

const isRerunnable = (s) => s === 'FAILED' || s === 'VALIDATION_FAILED';

const parseMetadata = (raw) => {
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
};

/**
 * @param {{
 *   tasks: any[],
 *   runs?: any[],
 *   comments?: any[],
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
  runs = [],
  comments = [],
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
  const [rerunningTaskId, setRerunningTaskId] = useState(null);

  const resultsByTask = useMemo(() => {
    const result = new Map();
    comments.forEach((comment) => {
      if (comment.commentType !== 'DEV_RESULT' && comment.commentType !== 'VALIDATION_RESULT') return;
      const metadata = parseMetadata(comment.metadataJson);
      const task = tasks.find((item) => item.id === metadata.taskId
        || item.planTaskKey === metadata.taskKey);
      if (!task) return;
      const current = result.get(task.id) || {};
      const field = comment.commentType === 'DEV_RESULT' ? 'development' : 'validation';
      const previous = current[field];
      if (!previous || new Date(comment.createdDate || 0) >= new Date(previous.createdDate || 0)) {
        current[field] = comment;
      }
      result.set(task.id, current);
    });
    return result;
  }, [comments, tasks]);

  const stats = useMemo(() => {
    const map = {};
    tasks.forEach((t) => {
      map[t.status] = (map[t.status] || 0) + 1;
    });
    return map;
  }, [tasks]);

  const activeRunTaskIds = useMemo(() => {
    const ids = new Set();
    runs.forEach((run) => {
      if (run && run.state === 'RUNNING' && run.codingTaskId) {
        ids.add(run.codingTaskId);
      }
    });
    return ids;
  }, [runs]);

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

  const handleRerun = async (task) => {
    if (rerunningTaskId || activeRunTaskIds.has(task.id)) return;
    const prompt = window.prompt('请输入重跑提示词（必填）');
    if (!prompt) return;
    if (!onRerun) return;
    setRerunningTaskId(task.id);
    try {
      await onRerun(task, prompt);
    } finally {
      setRerunningTaskId(null);
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
      title: '最新开发结果',
      width: 180,
      render: (_v, record) => {
        const comment = resultsByTask.get(record.id)?.development;
        return comment ? <Tooltip title={comment.content}>{comment.content}</Tooltip> : '-';
      },
    },
    {
      title: '最新验证报告',
      width: 180,
      render: (_v, record) => {
        const comment = resultsByTask.get(record.id)?.validation;
        return comment ? <Tooltip title={comment.content}>{comment.content}</Tooltip> : '-';
      },
    },
    {
      title: '操作',
      dataIndex: 'id',
      width: 280,
      render: (_id, record) => {
        const hasActiveRun = activeRunTaskIds.has(record.id);
        const rerunDisabled = hasActiveRun || !!rerunningTaskId;
        return (
          <Space>
            {isRerunnable(record.status) && (
              <Tooltip title={hasActiveRun ? '该任务已有运行中的 Run，请等待完成' : '重新执行当前失败任务'}>
                <Button
                  type="link"
                  icon={<RedoOutlined />}
                  loading={rerunningTaskId === record.id}
                  disabled={rerunDisabled}
                  onClick={() => handleRerun(record)}
                >
                  重新执行
                </Button>
              </Tooltip>
            )}
            <Button type="link" icon={<HistoryOutlined />} onClick={() => onViewRun && onViewRun(record)}>
              查看运行
            </Button>
            <Button type="link" icon={<FileTextOutlined />} onClick={() => openScope(record)}>
              代码片段
            </Button>
          </Space>
        );
      },
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
        <Tooltip
          title={
            stopEnabled
              ? '中断当前计划并取消所有活跃 Run'
              : '当前自动化状态不可停止'
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
