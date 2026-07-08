/**
 * Requirement list tab inside project detail.
 */
import React, { useRef, useState } from 'react';
import { history } from 'umi';
import {
  Button,
  ExtModal,
  ExtTable,
  Form,
  Input,
  message,
} from '@ead/suid';
import { PlusOutlined } from '@ead/suid-icons';
import { REQUIREMENT_FIND_BY_PAGE_URL, saveRequirement } from '@/services/requirement';

const RequirementListTab = ({ projectId }) => {
  const tableRef = useRef(null);
  const [form] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const columns = [
    {
      title: '操作',
      dataIndex: 'id',
      width: 110,
      render: (id) => (
        <Button type="link" onClick={() => history.push(`/online-code/requirement?id=${id}`)}>
          查看
        </Button>
      ),
    },
    { title: '需求标题', dataIndex: 'title', width: 240 },
    { title: '需求描述', dataIndex: 'description', expandUnusedSpace: true },
    { title: '状态', dataIndex: 'status', width: 140 },
    { title: '创建时间', dataIndex: 'createdDate', width: 170, dataType: 'datetime' },
  ];

  const handleSave = async (values) => {
    setSaving(true);
    try {
      const res = await saveRequirement({ ...values, projectId });
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

  // 与 CodingTaskTab 一致：按 projectId 过滤，避免查出其它项目的需求
  const buildSearch = (pageSearch) => ({
    ...pageSearch,
    filters: [
      ...(pageSearch.filters || []),
      { fieldName: 'projectId', value: projectId, operator: 'EQ' },
    ],
  });

  return (
    <div style={{ padding: 16, height: '100%', boxSizing: 'border-box' }}>
      <ExtTable
        ref={tableRef}
        rowKey="id"
        columns={columns}
        store={{
          url: REQUIREMENT_FIND_BY_PAGE_URL,
          type: 'POST',
        }}
        remotePaging
        beforeLoad={buildSearch}
        toolbar={{
          left: (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setModalOpen(true)}
            >
              新建需求
            </Button>
          ),
        }}
      />
      <ExtModal
        open={modalOpen}
        title="新建需求"
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} onFinish={handleSave} layout="vertical">
          <Form.Item
            name="title"
            label="需求标题"
            rules={[{ required: true, message: '请输入需求标题' }]}
          >
            <Input placeholder="例如：用户登录流程" allowClear />
          </Form.Item>
          <Form.Item name="description" label="需求描述">
            <Input.TextArea rows={4} placeholder="描述需求背景与目标…" allowClear />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
};

export default RequirementListTab;
