/**
 * Run tab: filterable run history list; click a row to open the log drawer.
 */
import React, { useMemo, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { FilterView, Table, Tag } from '@ead/suid';

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

const RUN_TYPE_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'DEVELOPMENT', label: '开发' },
  { value: 'VALIDATION_COMMAND', label: '验证命令' },
  { value: 'TEST_REVIEW', label: '测试评审' },
  { value: 'PM_PLANNING', label: 'PM 规划' },
  { value: 'PM_ACCEPTANCE', label: 'PM 验收' },
  { value: 'DELIVERY', label: '交付' },
];

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

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
 * @param {{ runs: any[], onOpenLog: (run: any) => void }} props
 */
const RunTab = ({ runs, onOpenLog }) => {
  const { styles } = useStyles();
  const [runTypeFilter, setRunTypeFilter] = useState('');

  const filteredRuns = useMemo(() => {
    if (!runTypeFilter) return runs;
    return runs.filter((r) => r.runType === runTypeFilter);
  }, [runs, runTypeFilter]);

  const columns = [
    { title: 'Run 序号', dataIndex: 'runNo', width: 100 },
    {
      title: '类型',
      dataIndex: 'runType',
      width: 130,
      render: (v) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'state',
      width: 110,
      render: (state) => {
        const meta = STATE_META[state] || { color: 'default', label: state };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '触发来源',
      dataIndex: 'triggerSource',
      width: 130,
      render: (v) => v || '-',
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
      render: (v) => v || '-',
    },
  ];

  return (
    <div>
      <div className={styles.toolbar}>
        <FilterView
          data={RUN_TYPE_OPTIONS}
          value={runTypeFilter}
          onChange={(v) => setRunTypeFilter(v || '')}
        />
      </div>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={filteredRuns}
        columns={columns}
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
