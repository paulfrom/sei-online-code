/**
 * Track F3 + F4 — Projects list with create-project flow.
 * F3: ExtTable remotePaging against `/api/project/findByPage`.
 * F4: create form (name, design) → `/api/project/save`.
 * Lifecycle-aware row action drives the walking skeleton: DRAFTING → refine,
 * SPEC_REVIEW → review, DISPATCHING/DEPLOYING/PREVIEW → preview (F7).
 */
import React, { useRef, useState } from 'react';
import { history } from 'umi';
import { createStyles } from '@ead/antd-style';
import {
  ActionButton,
  Button,
  ExtModal,
  ExtTable,
  Form,
  Input,
  message,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import { PlusOutlined } from '@ead/suid-icons';
import {
  PROJECT_FIND_BY_PAGE_URL,
  refineSpec,
  saveProject,
} from '@/services/onlineCode';
import type { LifecycleState, ProjectDto } from '@/services/onlineCode';
import LifecycleBadge from './components/LifecycleBadge';

const useStyles = createStyles(({ css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
  `,
}));

/** states routed to the dispatch (concurrency) view */
const DISPATCH_STATES: LifecycleState[] = ['DISPATCHING', 'DEVELOPING', 'MERGING'];

/** states from which the user should go straight to the preview page */
const PREVIEW_STATES: LifecycleState[] = ['DEPLOYING', 'PREVIEW', 'ACCEPTED'];

const ProjectList: React.FC = () => {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [form] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const goSpec = async (record: ProjectDto) => {
    // DRAFTING: run the Requirement Agent first, then open the Spec review.
    if (record.state === 'DRAFTING') {
      const res = await refineSpec(record.id);
      if (!res.success || !res.data) {
        message.error(res.message ?? '需求解析失败');
        return;
      }
      history.push(`/online-code/spec?id=${res.data.id}`);
      return;
    }
    if (record.currentSpecId) {
      history.push(`/online-code/spec?id=${record.currentSpecId}`);
    }
  };

  const handleRowAction = (record: ProjectDto) => {
    if (DISPATCH_STATES.includes(record.state) && record.currentIterationId) {
      history.push(`/online-code/dispatch?id=${record.id}`);
      return;
    }
    if (PREVIEW_STATES.includes(record.state) && record.currentIterationId) {
      history.push(`/online-code/preview?id=${record.id}`);
      return;
    }
    goSpec(record);
  };

  const rowActionLabel = (record: ProjectDto): string => {
    if (record.state === 'DRAFTING') return '解析需求';
    if (record.state === 'SPEC_REVIEW') return '评审 Spec';
    if (DISPATCH_STATES.includes(record.state)) return '查看派发';
    if (PREVIEW_STATES.includes(record.state)) return '查看预览';
    return '查看';
  };

  const columns: ExtTableProps<ProjectDto>['columns'] = [
    {
      title: '操作',
      dataIndex: 'id',
      width: 110,
      render: (_id: string, record: ProjectDto) => (
        <ActionButton title={rowActionLabel(record)} onClick={() => handleRowAction(record)} />
      ),
    },
    { title: '项目名称', dataIndex: 'name', width: 200 },
    { title: '需求描述', dataIndex: 'design', expandUnusedSpace: true },
    {
      title: '状态',
      dataIndex: 'state',
      width: 120,
      render: (state: LifecycleState) => <LifecycleBadge state={state} />,
    },
    { title: '创建时间', dataIndex: 'createdDate', width: 170, dataType: 'datetime' },
  ];

  const handleSave = async (values: { name: string; design: string }) => {
    setSaving(true);
    try {
      const res = await saveProject(values);
      if (!res.success || !res.data) {
        message.error(res.message ?? '创建失败');
        return;
      }
      message.success('创建成功');
      setModalOpen(false);
      form.resetFields();
      tableRef.current?.reloadData();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={styles.page}>
      <ExtTable
        ref={tableRef}
        rowKey="id"
        columns={columns}
        store={{ url: PROJECT_FIND_BY_PAGE_URL, type: 'POST' }}
        remotePaging
        showQuickSearch
        quickSearchFields={[{ field: 'name', title: '项目名称' }]}
        quickSearchPlaceHolder="请输入项目名称"
        toolbar={{
          left: (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setModalOpen(true)}
            >
              新建项目
            </Button>
          ),
        }}
      />

      <ExtModal
        open={modalOpen}
        title="新建项目"
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} onFinish={handleSave} layout="vertical">
          <Form.Item
            name="name"
            label="项目名称"
            rules={[{ required: true, message: '请输入项目名称' }]}
          >
            <Input placeholder="例如：库存管理台" allowClear />
          </Form.Item>
          <Form.Item
            name="design"
            label="Project Design（需求描述）"
            rules={[{ required: true, message: '请输入需求描述' }]}
          >
            <Input.TextArea
              rows={6}
              placeholder="用自然语言描述你想要的产品：页面、数据、交互…"
              allowClear
            />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
};

export default ProjectList;
