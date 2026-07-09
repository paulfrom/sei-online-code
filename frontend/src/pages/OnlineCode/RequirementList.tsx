/**
 * Standalone requirement list page.
 *
 * URL: /online-code/requirements?projectId=...
 */
import React, { useRef, useState } from 'react';
import { history } from 'umi';
import { createStyles } from '@ead/antd-style';
// @ts-ignore JS service module has no declaration file
import { saveRequirement, REQUIREMENT_FIND_BY_PAGE_URL } from '@/services/requirement';
import type { RequirementDto } from '@/services/onlineCodeTypes';
import type { ResultData } from '@/services/onlineCode';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';
import { Button, ExtModal, ExtTable, Form, Input, message } from '@ead/suid';

const useStyles = createStyles(() => ({
  tableWrap: {
    flex: 1,
    minHeight: 0,
    overflow: 'auto',
  },
}));

const RequirementList = ({ projectId }) => {
  const tableRef = useRef<any>(null);
  const [form] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const { styles } = useStyles();

  const handleRowClick = (record: RequirementDto) => {
    history.push(`/online-code/requirement?id=${record.id}`);
  };

  const handleSave = async (values: { title: string; description?: string }) => {
    setSaving(true);
    try {
      const res = (await saveRequirement({ ...values, projectId })) as ResultData<RequirementDto>;
      if (!res.success || !res.data) {
        message.error(res.message ?? '创建需求失败');
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

  const buildSearch = (pageSearch: any) => ({
    ...pageSearch,
    filters: [
      ...(pageSearch.filters || []),
      { fieldName: 'projectId', value: projectId, operator: 'EQ' },
    ],
  });

  const formatDate = (value?: string | null) =>
    value ? new Date(value).toLocaleString() : '-';

  const columns = [
    {
      title: '需求名称',
      dataIndex: 'title',
      width: 280,
      render: (text: string, record: RequirementDto) => (
        <Button type="link" onClick={() => handleRowClick(record)}>
          {text}
        </Button>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 140 },
    {
      title: '创建时间',
      dataIndex: 'createdDate',
      width: 170,
      render: (value: string) => formatDate(value),
    },
    {
      title: '更新时间',
      dataIndex: 'lastEditedDate',
      width: 170,
      render: (value: string) => formatDate(value),
    },
  ] as any[];

  if (!projectId) {
    return (
      <PageContainer>
        <PageHeader title="需求列表" subTitle="缺少项目 ID" />
        <PageState error="未找到 projectId 参数" />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className={styles.tableWrap}>
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
          onRow={(record: RequirementDto) => ({
            onClick: () => handleRowClick(record),
          })}
        />
      </div>
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
    </PageContainer>
  );
};

export default RequirementList;
