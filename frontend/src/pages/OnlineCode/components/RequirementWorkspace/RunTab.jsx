/**
 * Run tab: filterable run history list; click a row to open the log drawer.
 */
import React, { useMemo } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Space, Table, Tag } from '@ead/suid';
import { ArrowLeftOutlined } from '@ead/suid-icons';

const useStyles = createStyles(({ token, css }) => ({
  toolbar: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
    margin-bottom: ${token.marginMD}px;
  `,
}));

const STATE_META = {
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
};

const RUN_TYPE_META = {
  AGENT: { color: 'blue', label: 'Agent' },
  SYSTEM: { color: 'default', label: '系统' },
};

const TERMINAL_REASON_LABELS = {
  SUCCEEDED: '成功',
  FAILED: '失败',
  TIMEOUT: '超时',
  CANCELLED: '取消',
  SUPERSEDED: '被替代',
};

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

const formatTokens = (record) => {
  if (!record || record.totalTokens == null) return '-';
  return `${record.totalTokens} tokens`;
};

const computeDuration = (started, finished) => {
  if (!started) return '-';
  const start = new Date(started).getTime();
  const end = finished ? new Date(finished).getTime() : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return '-';
  const seconds = Math.round((end - start) / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${minutes}m ${remaining}s`;
};

/**
 * @param {{ runs: any[], taskFilterId?: string|null, onClearTaskFilter?: () => void,
 *   onBackToTask?: () => void, onOpenLog: (run: any) => void }} props
 */
const RunTab = ({ runs, taskFilterId, onClearTaskFilter, onBackToTask, onOpenLog }) => {
  const { styles } = useStyles();

  const filteredRuns = useMemo(() => {
    return taskFilterId ? runs.filter((r) => r.codingTaskId === taskFilterId) : runs;
  }, [runs, taskFilterId]);

  const columns = [
    // { title: 'Run 序号', dataIndex: 'runNo', width: 100 },
    {
      title: 'Agent',
      dataIndex: 'agentName',
      width: 130,
      render: (v, record) => v || record.cliTool || '-',
    },
    {
      title: '类型',
      dataIndex: 'runType',
      width: 90,
      render: (type) => {
        const meta = RUN_TYPE_META[type] || { color: 'default', label: type || '-' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    // {
    //   title: '尝试',
    //   dataIndex: 'attemptNo',
    //   width: 80,
    //   render: (v) => v || '-',
    // },
    {
      title: '状态',
      dataIndex: 'state',
      width: 110,
      render: (state) => {
        const meta = STATE_META[state] || { color: 'default', label: state };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    // {
    //   title: '触发来源',
    //   dataIndex: 'triggerSource',
    //   width: 130,
    //   render: (v) => v || '-',
    // },
    {
      title: '终止原因',
      dataIndex: 'terminalReason',
      width: 110,
      render: (v) => TERMINAL_REASON_LABELS[v] || v || '-',
    },
    
    {
      title: 'Token',
      width: 120,
      render: (_v, record) => formatTokens(record),
    },
    {
      title: '持续时间',
      width: 110,
      render: (_v, record) => computeDuration(record.startedDate, record.finishedDate),
    },
    {
      title: '开始时间',
      dataIndex: 'startedDate',
      width: 170,
      render: formatDateTime,
    },
    {
      title: '失败原因',
      dataIndex: 'failureReason',
      render: (v) => (v ? `${v.slice(0, 10)}${v.length > 10 ? '...' : ''}` : '-'),
    },
  ];

  return (
    <div>
      <div className={styles.toolbar}>
        {taskFilterId && (
          <Space>
            <Tag color="blue">已按任务筛选</Tag>
            <Button type="link" onClick={onClearTaskFilter}>清除</Button>
            {onBackToTask && (
              <Button
                type="link"
                icon={<ArrowLeftOutlined />}
                onClick={onBackToTask}
              >
                返回编码任务
              </Button>
            )}
          </Space>
        )}
      </div>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={filteredRuns}
        columns={columns}
        scroll={{ x: 'max-content' }}
        onRow={(record) => ({
          onClick: () => onOpenLog && onOpenLog(record),
          style: { cursor: 'pointer' },
        })}
        locale={{ emptyText: '暂无运行历史' }}
      />
    </div>
  );
};

export default RunTab;
