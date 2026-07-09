/**
 * Run history panel: list of coding task runs with status, duration and timestamps.
 */
import React from 'react';
import { Card, Table, Tag } from '@ead/suid';
import type { RunDto, RunState } from '@/services/onlineCodeTypes';
import type { RunHistoryPanelProps } from './types';

const STATE_META: Record<RunState, { color: string; label: string }> = {
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
};

const formatDateTime = (value?: string | null) => (value ? new Date(value).toLocaleString() : '-');

const computeDuration = (startedDate?: string | null, finishedDate?: string | null) => {
  if (!startedDate) return '-';
  const start = new Date(startedDate).getTime();
  const end = finishedDate ? new Date(finishedDate).getTime() : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return '-';
  const seconds = Math.round((end - start) / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${minutes}m ${remaining}s`;
};

const RunHistoryPanel: React.FC<RunHistoryPanelProps> = ({ runs }) => {
  const columns = [
    { title: 'Run 序号', dataIndex: 'runNo', width: 100 },
    {
      title: '状态',
      dataIndex: 'state',
      width: 120,
      render: (state: RunState) => {
        const meta = STATE_META[state] ?? { color: 'default', label: state };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '触发来源', dataIndex: 'triggerSource', width: 140 },
    {
      title: '持续时间',
      dataIndex: 'id',
      width: 120,
      render: (_id: string, record: RunDto) => computeDuration(record.startedDate, record.finishedDate),
    },
    {
      title: '开始时间',
      dataIndex: 'startedDate',
      width: 180,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '结束时间',
      dataIndex: 'finishedDate',
      width: 180,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '失败原因',
      dataIndex: 'failureReason',
      render: (value: string | null | undefined) => value || '-',
    },
  ];

  return (
    <Card>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={runs}
        columns={columns}
        locale={{ emptyText: '暂无运行历史' }}
      />
    </Card>
  );
};

export default RunHistoryPanel;
