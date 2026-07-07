/**
 * Coding task list tab inside project detail.
 */
import React, { useRef, useState } from 'react';
import {
  Button,
  ExtTable,
  Select,
  message,
} from '@ead/suid';
import { findCodingTasksByPage, runCodingTask, rerunCodingTask } from '@/services/codingTask';
import { findRunsByCodingTask } from '@/services/run';

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'STALE', label: '已过期' },
];

const CodingTaskTab = ({ projectId }) => {
  const tableRef = useRef(null);
  const [statusFilter, setStatusFilter] = useState('');

  const handleRun = async (record) => {
    const res = await runCodingTask(record.id, null);
    if (res.success) {
      message.success('运行已启动');
      tableRef.current?.reloadData();
    } else {
      message.error(res.message ?? '运行失败');
    }
  };

  const handleRerun = async (record) => {
    const prompt = window.prompt('请输入重跑提示词（必填）');
    if (!prompt) return;
    const res = await rerunCodingTask(record.id, prompt);
    if (res.success) {
      message.success('重跑已启动');
      tableRef.current?.reloadData();
    } else {
      message.error(res.message ?? '重跑失败');
    }
  };

  const handleViewRuns = async (record) => {
    const res = await findRunsByCodingTask(record.id);
    if (res.success && res.data) {
      // eslint-disable-next-line no-console
      console.log('runs', res.data);
      message.info(`该任务已有 ${res.data.length} 次运行记录`);
    }
  };

  const columns = [
    {
      title: '操作',
      dataIndex: 'id',
      width: 180,
      render: (_id, record) => (
        <>
          {record.status === 'PENDING' && (
            <Button type="link" onClick={() => handleRun(record)}>运行</Button>
          )}
          {(record.status === 'FAILED' || record.status === 'SUCCEEDED' || record.status === 'CANCELLED') && (
            <Button type="link" onClick={() => handleRerun(record)}>重跑</Button>
          )}
          <Button type="link" onClick={() => handleViewRuns(record)}>运行历史</Button>
        </>
      ),
    },
    { title: '任务标题', dataIndex: 'title', width: 240 },
    { title: '状态', dataIndex: 'status', width: 120 },
    { title: '详细设计版本', dataIndex: 'detailedDesignVersion', width: 120 },
    { title: '失败摘要', dataIndex: 'failureSummary', expandUnusedSpace: true },
    { title: '创建时间', dataIndex: 'createdDate', width: 170, dataType: 'datetime' },
  ];

  const buildSearch = (pageSearch) => ({
    ...pageSearch,
    filters: [
      ...(pageSearch.filters || []),
      { fieldName: 'projectId', value: projectId, operator: 'EQ' },
      ...(statusFilter ? [{ fieldName: 'status', value: statusFilter, operator: 'EQ' }] : []),
    ],
  });

  return (
    <div style={{ padding: 16 }}>
      <div style={{ marginBottom: 16 }}>
        <Select
          placeholder="状态筛选"
          value={statusFilter}
          onChange={setStatusFilter}
          options={STATUS_OPTIONS}
          style={{ width: 160 }}
          allowClear
        />
      </div>
      <ExtTable
        ref={tableRef}
        rowKey="id"
        columns={columns}
        store={{
          url: findCodingTasksByPage,
          type: 'POST',
        }}
        remotePaging
        searchProperties={['title']}
        beforeLoad={buildSearch}
      />
    </div>
  );
};

export default CodingTaskTab;
