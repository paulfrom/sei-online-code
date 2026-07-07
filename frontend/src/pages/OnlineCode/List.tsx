/**
 * Track F3 + F4 — Projects list with create-project flow.
 * F3: ExtTable remotePaging against `/api/project/findByPage`.
 * F4: create form (name, design) → `/api/project/save`.
 * Row action enters the current Project Description -> Overview Design -> Module Detailed Design
 * -> Feature Design -> Coding Execution flow.
 */
import React, { useRef, useState } from 'react';
import { history } from 'umi';
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
  generateOverviewDesign,
  saveProject,
} from '@/services/onlineCode';
import type { LifecycleState, ProjectDto } from '@/services/onlineCode';
import LifecycleBadge from './components/LifecycleBadge';
import { PageContainer } from './components/PageLayout';

const ProjectList: React.FC = () => {
  const tableRef = useRef<ExtTableRef>(null);
  const [form] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const goOverviewDesign = async (record: ProjectDto) => {
    // DRAFTING: start overview design generation, then open the project workspace.
    if (record.state === 'DRAFTING' || (!record.currentSpecId && record.state === 'FAILED')) {
      const res = await generateOverviewDesign(record.id);
      if (!res.success || !res.data) {
        message.error(res.message ?? '概要设计生成失败');
        return;
      }
      history.push(`/online-code/project?id=${record.id}`);
      return;
    }
    if (record.state === 'SPEC_REVIEW' && record.currentSpecId) {
      history.push(`/online-code/spec?id=${record.currentSpecId}`);
      return;
    }
    history.push(`/online-code/project?id=${record.id}`);
  };

  const handleRowAction = (record: ProjectDto) => {
    goOverviewDesign(record);
  };

  const rowActionLabel = (record: ProjectDto): string => {
    if (record.state === 'DRAFTING') return '生成概要设计';
    if (record.state === 'FAILED' && !record.currentSpecId) return '重新生成概要设计';
    if (record.state === 'SPEC_REVIEW') return '查看详细设计';
    return '查看项目';
  };

  const columns: ExtTableProps<ProjectDto>['columns'] = [
    {
      title: '操作',
      dataIndex: 'id',
      width: 110,
      render: (_id: string, record: ProjectDto) => (
        // actionType="title" so the dynamic label renders as visible button text;
        // ActionButton defaults to actionType="icon" which suppresses `title` to a
        // tooltip — without an icon the button would render empty (invisible).
        <ActionButton
          actionType="title"
          title={rowActionLabel(record)}
          onClick={() => handleRowAction(record)}
        />
      ),
    },
    { title: '项目名称', dataIndex: 'name', width: 200 },
    { title: '项目描述', dataIndex: 'design', expandUnusedSpace: true },
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
    <PageContainer>
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
            label="项目描述"
            rules={[{ required: true, message: '请输入项目描述' }]}
          >
            <Input.TextArea
              rows={6}
              placeholder="用自然语言描述你想要的产品：页面、数据、交互…"
              allowClear
            />
          </Form.Item>
        </Form>
      </ExtModal>
    </PageContainer>
  );
};

export default ProjectList;
