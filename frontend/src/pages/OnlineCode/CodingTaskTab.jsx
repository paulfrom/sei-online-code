/**
 * Coding task list tab inside project detail.
 */
import React, { useRef, useState, useMemo } from 'react';
import {
  Button,
  ExtTable,
  Select,
  message,
} from '@ead/suid';
import { CODING_TASK_FIND_BY_PAGE_URL, runCodingTask, rerunCodingTask } from '@/services/codingTask';
import { findRunsByCodingTask } from '@/services/run';
import OverviewDrawer from './components/RequirementWorkspace/OverviewDrawer';
import RunLogDrawer from './components/RequirementWorkspace/RunLogDrawer';

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
  // Unified run-history drawer (reuses the RequirementWorkspace components).
  // open=true shows OverviewDrawer with panelKey='run'; runLogDrawer carries
  // the selected run for the RunLogDrawer.
  const [runHistoryVisible, setRunHistoryVisible] = useState(false);
  const [runHistory, setRunHistory] = useState([]);
  const [runHistoryTaskTitle, setRunHistoryTaskTitle] = useState('');
  // Log sub-drawer.
  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [logDrawerRun, setLogDrawerRun] = useState(null);

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
      setRunHistoryTaskTitle(record.title || record.id);
      setRunHistoryVisible(true);
    } else {
      message.error(res?.message ?? '获取运行历史失败');
    }
  };

  const handleCloseRunHistory = () => {
    setRunHistoryVisible(false);
    setRunHistory([]);
    setRunHistoryTaskTitle('');
  };

  const handleOpenLog = (run) => {
    setLogDrawerRun(run);
    setLogDrawerOpen(true);
  };

  const handleCloseLog = () => {
    setLogDrawerOpen(false);
    setLogDrawerRun(null);
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
    // 状态筛选：选择具体状态时按 EQ 过滤；未选择(全部)时默认隐藏已过期(STALE)任务，
    // 需要查看时可在下拉框中手动选择「已过期」。
    if (statusFilter) {
      filters.push({
        fieldName: 'status',
        operator: 'EQ',
        value: statusFilter,
      });
    } else {
      filters.push({
        fieldName: 'status',
        operator: 'eq',
        value: 'STALE',
      });
    }
    return { filters };
  }, [projectId, statusFilter]);

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
      <OverviewDrawer
        panelKey={runHistoryVisible ? 'run' : null}
        onClose={handleCloseRunHistory}
        titleOverride={runHistoryVisible ? `运行历史 - ${runHistoryTaskTitle}` : ''}
        runs={runHistory}
        onRunLog={handleOpenLog}
      />
      <RunLogDrawer
        open={logDrawerOpen}
        run={logDrawerRun}
        onClose={handleCloseLog}
      />
    </div>
  );
};

export default CodingTaskTab;
