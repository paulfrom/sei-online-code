/**
 * Coding task list tab inside project detail.
 */
import React, { useRef, useState, useMemo } from 'react';
import {
  Button,
  ExtTable,
  Modal,
  Select,
  message,
} from '@ead/suid';
import { CODING_TASK_FIND_BY_PAGE_URL, runCodingTask, rerunCodingTask } from '@/services/codingTask';
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
  const [runHistoryVisible, setRunHistoryVisible] = useState(false);
  const [runHistory, setRunHistory] = useState([]);
  const [runHistoryTitle, setRunHistoryTitle] = useState('运行历史');

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
      setRunHistory(res.data);
      setRunHistoryTitle(`运行历史 - ${record.title || record.id}`);
      setRunHistoryVisible(true);
      message.info(`该任务已有 ${res.data.length} 次运行记录`);
    }
  };

  const handleCloseRunHistory = () => {
    setRunHistoryVisible(false);
    setRunHistory([]);
  };

  const runHistoryColumns = [
    { title: 'Run 序号', dataIndex: 'runNo', width: 100 },
    { title: '状态', dataIndex: 'state', width: 120 },
    { title: '触发来源', dataIndex: 'triggerSource', width: 120 },
    { title: '失败原因', dataIndex: 'failureReason', expandUnusedSpace: true },
    { title: '开始时间', dataIndex: 'startedDate', width: 170, dataType: 'datetime' },
    { title: '结束时间', dataIndex: 'finishedDate', width: 170, dataType: 'datetime' },
  ];

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
    { title: '失败摘要', dataIndex: 'failureSummary', expandUnusedSpace: true },
    { title: '创建时间', dataIndex: 'createdDate', width: 170, dataType: 'datetime' },
  ];

  const cascade = useMemo(() => {
    const filters = [];
    if (projectId) {
      filters.push({
        fieldName: 'projectId',
        operator: 'EQ',
        value: projectId,
      });
    }
    // if (currentUser?.account) {
    //   filters.push({
    //     fieldName: 'creatorAccount',
    //     operator: 'EQ',
    //     value: currentUser.account,
    //   });
    // }
    return { filters };
  }, [projectId]);

  return (
    <div
      style={{
        padding: 16,
        height: '100%',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
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
      <div style={{ flex: 1, minHeight: 0 }}>
        <ExtTable
          ref={tableRef}
          rowKey="id"
          columns={columns}
          cascade={cascade}
          store={{
            url: CODING_TASK_FIND_BY_PAGE_URL,
            type: 'POST',
          }}
          remotePaging
          searchProperties={['title']}
        />
      </div>
      <Modal
        title={runHistoryTitle}
        open={runHistoryVisible}
        onCancel={handleCloseRunHistory}
        footer={<Button onClick={handleCloseRunHistory}>关闭</Button>}
        width={960}
      >
        <ExtTable
          rowKey="id"
          columns={runHistoryColumns}
          dataSource={runHistory}
          remotePaging={false}
          showSearch={false}
        />
      </Modal>
    </div>
  );
};

export default CodingTaskTab;
