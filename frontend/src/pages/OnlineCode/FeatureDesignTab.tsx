/**
 * F4: FeatureDesignTab - Tab for managing FeatureDesigns with ExtTable remotePaging
 */
import React, { useCallback, useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import {
  ActionButton,
  Badge,
  Button,
  ExtModal,
  ExtTable,
  Form,
  Input,
  message,
  Space,
  Tooltip,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import {
  EditOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  PlayCircleFilled,
  EyeOutlined,
} from '@ead/suid-icons';
import {
  FEATURE_DESIGN_FIND_BY_PAGE_URL,
  confirm,
  confirmOne,
  regenerate,
  build,
} from '@/services/featureDesign';
import type { FeatureDesignDto } from '@/services/featureDesign';
import FeatureDesignEditor from './FeatureDesignEditor';
import FailureInfoPanel from './components/FailureInfoPanel';

const useStyles = createStyles(({ token, css }) => ({
  container: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    gap: ${token.marginSM}px;
  `,
  toolbar: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
  `,
}));

interface FeatureDesignTabProps {
  projectId: string;
}

const statusColorMap: Record<string, string> = {
  PENDING: 'default',
  GENERATING: 'processing',
  DRAFT: 'default',
  CONFIRMED: 'success',
  STALE: 'warning',
  FAILED: 'error',
};

const statusTextMap: Record<string, string> = {
  PENDING: '待生成',
  GENERATING: '生成中',
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  STALE: '已过期',
  FAILED: '失败',
};

const buildStatusColorMap: Record<string, string> = {
  IDLE: 'default',
  BUILDING: 'processing',
  BUILT: 'success',
  BUILD_FAILED: 'error',
  STALE: 'warning',
};

const buildStatusTextMap: Record<string, string> = {
  IDLE: '未执行',
  BUILDING: '编码执行中',
  BUILT: '已执行',
  BUILD_FAILED: '执行失败',
  STALE: '已过期',
};

const FeatureDesignTab: React.FC<FeatureDesignTabProps> = ({ projectId }) => {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [selectedRows, setSelectedRows] = useState<FeatureDesignDto[]>([]);
  const [editorOpen, setEditorOpen] = useState(false);
  const [currentFeatureDesignId, setCurrentFeatureDesignId] = useState<string | null>(null);
  const [regenerateModalOpen, setRegenerateModalOpen] = useState(false);
  const [regenerateFeatureDesignId, setRegenerateFeatureDesignId] = useState<string | null>(null);
  const [regenerateForm] = Form.useForm<{ modifyHint: string }>();
  const [buildModalOpen, setBuildModalOpen] = useState(false);
  const [currentBuildRunId, setCurrentBuildRunId] = useState<string | null>(null);
  const [currentFailureRecord, setCurrentFailureRecord] = useState<FeatureDesignDto | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const refreshTable = useCallback(() => {
    tableRef.current?.refresh?.();
  }, []);

  const handleView = (record: FeatureDesignDto) => {
    setCurrentFeatureDesignId(record.id);
    setEditorOpen(true);
  };

  const handleEdit = (record: FeatureDesignDto) => {
    setCurrentFeatureDesignId(record.id);
    setEditorOpen(true);
  };

  const handleEditorSuccess = (fd: FeatureDesignDto) => {
    refreshTable();
  };

  const handleRegenerate = (record: FeatureDesignDto) => {
    setRegenerateFeatureDesignId(record.id);
    setRegenerateModalOpen(true);
  };

  const handleConfirmRegenerate = async (values: { modifyHint: string }) => {
    if (!regenerateFeatureDesignId) return;
    setSubmitting(true);
    try {
      const res = await regenerate(regenerateFeatureDesignId, values.modifyHint);
      if (res.success && res.data) {
        message.success('重新生成已开始');
        setRegenerateModalOpen(false);
        regenerateForm.resetFields();
        refreshTable();
      } else {
        message.error(res.message ?? '重新生成失败');
      }
    } catch (e: any) {
      if (e.status === 409) {
        message.error('该功能正在编码执行中，无法重新生成');
      } else {
        message.error('重新生成失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirm = async (record: FeatureDesignDto) => {
    setSubmitting(true);
    try {
      const res = await confirmOne(record.id);
      if (res.success && res.data) {
        message.success('确认成功');
        refreshTable();
      } else {
        message.error(res.message ?? '确认失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleBatchConfirm = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择要确认的功能设计');
      return;
    }
    setSubmitting(true);
    try {
      const res = await confirm(selectedRowKeys as string[]);
      if (res.success && res.data) {
        message.success('批量确认成功');
        setSelectedRowKeys([]);
        setSelectedRows([]);
        refreshTable();
      } else {
        message.error(res.message ?? '批量确认失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleBuild = async (record: FeatureDesignDto) => {
    setSubmitting(true);
    try {
      const res = await build(record.id);
      if (res.success && res.data) {
        message.success('编码执行已开始');
        setCurrentBuildRunId(res.data.runId);
        setBuildModalOpen(true);
        refreshTable();
      } else {
        message.error(res.message ?? '编码执行失败');
      }
    } catch (e: any) {
      if (e.status === 409) {
        message.error('该功能正在编码执行中');
      } else {
        message.error('编码执行失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const isStale = (record: FeatureDesignDto) => record.status === 'STALE';
  const isConfirmed = (record: FeatureDesignDto) => record.status === 'CONFIRMED';
  const isGenerating = (record: FeatureDesignDto) => record.status === 'GENERATING';
  const isBuilding = (record: FeatureDesignDto) => record.buildStatus === 'BUILDING';

  const hasNonStaleSelected = selectedRows.some(row => !isStale(row));

  const columns: ExtTableProps<FeatureDesignDto>['columns'] = [
    {
      title: '功能ID',
      dataIndex: 'featureId',
      width: 150,
    },
    {
      title: '标题',
      dataIndex: ['content', 'goal'],
      width: 300,
      render: (goal: string) => goal?.substring?.(0, 50) || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status: string) => (
        <Badge
          status={statusColorMap[status] as any}
          text={statusTextMap[status]}
        />
      ),
    },
    {
      title: '编码执行状态',
      dataIndex: 'buildStatus',
      width: 120,
      render: (buildStatus: string) => (
        <Badge
          status={buildStatusColorMap[buildStatus] as any}
          text={buildStatusTextMap[buildStatus]}
        />
      ),
    },
    {
      title: '失败摘要',
      dataIndex: 'failureSummary',
      width: 240,
      render: (value: string | null | undefined, record) =>
        value || (record.retryCount ? `已重试 ${record.retryCount} 次` : '-'),
    },
    {
      title: '版本',
      dataIndex: 'version',
      width: 80,
    },
    {
      title: '操作',
      dataIndex: 'id',
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <ActionButton
            type="text"
            icon={<EyeOutlined />}
            onClick={() => handleView(record)}
          >
            查看
          </ActionButton>
          {(record.failureSummary || record.failureCode) && (
            <ActionButton type="text" onClick={() => setCurrentFailureRecord(record)}>
              失败信息
            </ActionButton>
          )}
          {!isConfirmed(record) && !isGenerating(record) && !isBuilding(record) && (
            <ActionButton
              type="text"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            >
              编辑
            </ActionButton>
          )}
          {!isConfirmed(record) && !isGenerating(record) && !isBuilding(record) && (
            <ActionButton
              type="text"
              icon={<ReloadOutlined />}
              onClick={() => handleRegenerate(record)}
            >
              重新生成
            </ActionButton>
          )}
          {!isConfirmed(record) && !isGenerating(record) && (
            <Tooltip title={isStale(record) ? '需重新生成' : ''}>
              <ActionButton
                type="text"
                icon={<CheckCircleOutlined />}
                onClick={() => handleConfirm(record)}
                disabled={isStale(record)}
              >
                确认
              </ActionButton>
            </Tooltip>
          )}
          {isConfirmed(record) && !isBuilding(record) && (
            <ActionButton
              type="text"
              icon={<PlayCircleFilled />}
              onClick={() => handleBuild(record)}
            >
              执行编码
            </ActionButton>
          )}
        </Space>
      ),
    },
  ];

  const rowSelection: ExtTableProps<FeatureDesignDto>['rowSelection'] = {
    selectedRowKeys,
    onChange: (keys, rows) => {
      setSelectedRowKeys(keys);
      setSelectedRows(rows as FeatureDesignDto[]);
    },
  };

  const tableSearch = {
    quickSearchProperties: ['featureId', 'content.goal'],
    filters: [
      {
        fieldName: 'projectId',
        operator: 'EQ',
        value: projectId,
      },
    ],
  };

  return (
    <div className={styles.container}>
      <div className={styles.toolbar}>
        <Space>
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            onClick={handleBatchConfirm}
            loading={submitting}
            disabled={!hasNonStaleSelected}
          >
            批量确认
          </Button>
        </Space>
      </div>

      <ExtTable
        ref={tableRef}
        rowKey="id"
        columns={columns}
        store={{ url: FEATURE_DESIGN_FIND_BY_PAGE_URL, type: 'POST' }}
        remotePaging
        search={tableSearch}
        rowSelection={rowSelection}
        enableSetting
      />

      <FeatureDesignEditor
        open={editorOpen}
        featureDesignId={currentFeatureDesignId}
        onCancel={() => setEditorOpen(false)}
        onSuccess={handleEditorSuccess}
      />

      <ExtModal
        open={Boolean(currentFailureRecord)}
        title="功能设计失败信息"
        footer={null}
        onCancel={() => setCurrentFailureRecord(null)}
        destroyOnHidden
      >
        <FailureInfoPanel info={currentFailureRecord} />
      </ExtModal>

      <ExtModal
        open={regenerateModalOpen}
        title="重新生成功能设计"
        confirmLoading={submitting}
        onCancel={() => {
          setRegenerateModalOpen(false);
          regenerateForm.resetFields();
        }}
        onOk={() => regenerateForm.submit()}
        destroyOnHidden
      >
        <Form form={regenerateForm} onFinish={handleConfirmRegenerate} layout="vertical">
          <Form.Item name="modifyHint" label="修改提示（可选）">
            <Input.TextArea
              rows={4}
              placeholder="描述你希望如何修改功能设计"
            />
          </Form.Item>
        </Form>
      </ExtModal>

      <ExtModal
        open={buildModalOpen}
        title="编码执行已开始"
        onCancel={() => setBuildModalOpen(false)}
        onOk={() => setBuildModalOpen(false)}
        destroyOnHidden
      >
        <div>
          <p>编码执行任务已启动</p>
          {currentBuildRunId && (
            <p>
              运行ID: <strong>{currentBuildRunId}</strong>
            </p>
          )}
        </div>
      </ExtModal>
    </div>
  );
};

export default FeatureDesignTab;
