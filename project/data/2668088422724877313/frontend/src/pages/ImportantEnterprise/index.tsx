import React, { useCallback, useMemo, useRef, useState } from 'react';
import {
  ActionButton,
  Button,
  ExtModal,
  ExtTable,
  Form,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Tag,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ead/suid-icons';
import { constants } from '@/utils';
import {
  createImportantEnterprise,
  deleteImportantEnterprise,
  ENTERPRISE_CATEGORY_OPTIONS,
  getEnterpriseCategoryLabel,
  getImportantEnterpriseDetail,
  updateImportantEnterprise,
} from '@/services/importantEnterprise';
import type {
  CreateImportantEnterpriseRequest,
  EnterpriseCategory,
  ImportantEnterpriseListItem,
  UpdateImportantEnterpriseRequest,
} from '@/services/importantEnterprise';
import useStyles from './style';

const { SERVER_PATH } = constants;
const SERVICE_CODE = '2668088422724877313';
const BASE_URL = `${SERVER_PATH}/${SERVICE_CODE}/api/v1/important-enterprises`;

/** 简单格式化时间戳为本地可读字符串 */
function formatDateTime(value?: string) {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString('zh-CN');
  } catch {
    return value;
  }
}

interface ListFilter {
  keyword?: string;
  category?: EnterpriseCategory;
}

const ImportantEnterprise: React.FC = () => {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [searchForm] = Form.useForm();
  const [modalForm] = Form.useForm();
  const [filter, setFilter] = useState<ListFilter>({});
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ImportantEnterpriseListItem | null>(null);
  const [saving, setSaving] = useState(false);

  const cascade = useMemo(() => {
    const params: Record<string, string> = {};
    if (filter.keyword) {
      params.keyword = filter.keyword;
    }
    if (filter.category) {
      params.category = filter.category;
    }
    return params;
  }, [filter]);

  const columns: ExtTableProps<ImportantEnterpriseListItem>['columns'] = [
    {
      title: '企业名称',
      dataIndex: 'name',
      width: 240,
      expandUnusedSpace: true,
    },
    {
      title: '类别',
      dataIndex: 'category',
      width: 120,
      render: (category) => <Tag>{getEnterpriseCategoryLabel(category)}</Tag>,
    },
    {
      title: '统一社会信用代码',
      dataIndex: 'unifiedSocialCreditCode',
      width: 180,
    },
    {
      title: '资产管理人',
      dataIndex: 'assetManager',
      width: 140,
      render: (assetManager) => assetManager?.name ?? '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: formatDateTime,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      render: formatDateTime,
    },
    {
      title: '操作',
      dataIndex: 'id',
      width: 120,
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <ActionButton
            title="编辑"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          />
          <Popconfirm
            title="确认删除"
            description={`确定删除“${record.name}”吗？删除后不可恢复。`}
            onConfirm={() => handleDelete(record.id)}
          >
            <ActionButton title="删除" color="danger" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleSearch = useCallback((values: ListFilter) => {
    setFilter(values);
    setTimeout(() => tableRef.current?.reloadData(), 0);
  }, []);

  const handleReset = useCallback(() => {
    searchForm.resetFields();
    setFilter({});
    setTimeout(() => tableRef.current?.reloadData(), 0);
  }, [searchForm]);

  const handleDelete = useCallback(async (id: string) => {
    try {
      await deleteImportantEnterprise(id);
      message.success('删除成功');
      tableRef.current?.reloadData();
    } catch {
      message.error('删除失败，请稍后重试');
    }
  }, []);

  const handleAdd = useCallback(() => {
    setEditingRecord(null);
    modalForm.resetFields();
    setModalVisible(true);
  }, [modalForm]);

  const handleEdit = useCallback(
    async (record: ImportantEnterpriseListItem) => {
      try {
        // 重新拉取最新数据后再编辑，避免列表与详情数据不一致
        const detail = await getImportantEnterpriseDetail(record.id);
        setEditingRecord(detail);
        modalForm.setFieldsValue({
          name: detail.name,
          category: detail.category,
          unifiedSocialCreditCode: detail.unifiedSocialCreditCode,
          assetManagerId: detail.assetManagerId,
        });
        setModalVisible(true);
      } catch {
        message.error('获取详情失败');
      }
    },
    [modalForm],
  );

  const handleSave = useCallback(
    async (values: CreateImportantEnterpriseRequest) => {
      setSaving(true);
      try {
        if (editingRecord) {
          await updateImportantEnterprise(
            editingRecord.id,
            values as UpdateImportantEnterpriseRequest,
          );
        } else {
          await createImportantEnterprise(values);
        }
        message.success(editingRecord ? '更新成功' : '创建成功');
        setModalVisible(false);
        setEditingRecord(null);
        modalForm.resetFields();
        tableRef.current?.reloadData();
      } catch {
        message.error('保存失败，请检查输入后重试');
      } finally {
        setSaving(false);
      }
    },
    [editingRecord, modalForm],
  );

  return (
    <div className={styles.page}>
      <div className={styles.filter}>
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword" label="关键字">
            <Input placeholder="企业名称/统一社会信用代码" allowClear />
          </Form.Item>
          <Form.Item name="category" label="企业类别">
            <Select
              placeholder="请选择"
              allowClear
              options={ENTERPRISE_CATEGORY_OPTIONS}
              style={{ minWidth: 140 }}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                搜索
              </Button>
              <Button onClick={handleReset} icon={<ReloadOutlined />}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </div>

      <ExtTable
        ref={tableRef}
        className={styles.table}
        rowKey="id"
        columns={columns}
        cascade={cascade}
        store={{ url: BASE_URL, type: 'GET' }}
        remotePaging
        showQuickSearch={false}
        toolbar={{
          left: (
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              新增
            </Button>
          ),
        }}
      />

      <ExtModal
        visible={modalVisible}
        title={editingRecord ? '编辑重要企业' : '新增重要企业'}
        confirmLoading={saving}
        destroyOnHidden
        onOk={() => modalForm.submit()}
        onCancel={() => setModalVisible(false)}
        afterClose={() => modalForm.resetFields()}
      >
        <Form
          form={modalForm}
          layout="vertical"
          onFinish={handleSave}
          preserve={false}
        >
          <Form.Item
            name="name"
            label="企业名称"
            rules={[{ required: true, message: '请输入企业名称' }]}
          >
            <Input placeholder="请输入" maxLength={200} showCount />
          </Form.Item>
          <Form.Item
            name="category"
            label="企业类别"
            rules={[{ required: true, message: '请选择企业类别' }]}
          >
            <Select placeholder="请选择" options={ENTERPRISE_CATEGORY_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="unifiedSocialCreditCode"
            label="统一社会信用代码"
            rules={[
              { required: true, message: '请输入统一社会信用代码' },
              { len: 18, message: '统一社会信用代码必须为 18 位' },
            ]}
          >
            <Input placeholder="请输入 18 位统一社会信用代码" maxLength={18} />
          </Form.Item>
          <Form.Item
            name="assetManagerId"
            label="资产管理人"
            rules={[{ required: true, message: '请输入资产管理人 ID' }]}
          >
            <Input placeholder="请输入资产管理人 ID" />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
};

export default ImportantEnterprise;
