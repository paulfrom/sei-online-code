/**
 * Track F14 — Skills page.
 * ExtTable list (ep #17 findByPage) + import ExtModal (ep #16) + delete (ep #19)
 * + a read-only content viewer (ep #18 shape already on the row).
 *
 * `computedHash` is the server-authoritative lock (contract §6); the FE only
 * displays it and never recomputes. Re-importing identical content is
 * idempotent on the server (same hash → existing row returned).
 */
import React, { useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import {
  ActionButton,
  Button,
  ExtModal,
  ExtTable,
  Form,
  Input,
  Popconfirm,
  Select,
  Tag,
  message,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import { DeleteOutlined, EyeOutlined, ImportOutlined } from '@ead/suid-icons';
import {
  SKILL_FIND_BY_PAGE_URL,
  deleteSkill,
  importSkill,
} from '@/services/onlineCode';
import type { SkillDto, SkillSourceType } from '@/services/onlineCode';

const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
  `,
  content: css`
    margin: 0;
    padding: ${token.paddingSM}px;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 320px;
    overflow: auto;
  `,
  hash: css`
    font-family: ${token.fontFamilyCode};
    font-size: ${token.fontSizeSM}px;
    color: ${token.colorTextSecondary};
  `,
}));

const SOURCE_TYPE_META: Record<SkillSourceType, { color: string; label: string }> = {
  GITHUB: { color: 'blue', label: 'GitHub' },
  LOCAL: { color: 'green', label: '本地' },
  INLINE: { color: 'gold', label: '内联' },
};

const SOURCE_TYPE_OPTIONS = (Object.keys(SOURCE_TYPE_META) as SkillSourceType[]).map((k) => ({
  value: k,
  label: SOURCE_TYPE_META[k].label,
}));

interface ImportForm {
  name: string;
  description: string;
  sourceType: SkillSourceType;
  source: string;
  content: string;
}

const Skills: React.FC = () => {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [form] = Form.useForm<ImportForm>();
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [viewing, setViewing] = useState<SkillDto | null>(null);

  const handleDelete = async (id: string) => {
    const res = await deleteSkill(id);
    if (!res.success) {
      message.error(res.message ?? '删除失败');
      return;
    }
    message.success(res.message ?? '删除成功');
    tableRef.current?.reloadData();
  };

  const handleImport = async (values: ImportForm) => {
    setImporting(true);
    try {
      const res = await importSkill({
        name: values.name,
        description: values.description ?? '',
        sourceType: values.sourceType,
        source: values.source ?? `inline:${values.name}`,
        content: values.content,
      });
      if (!res.success || !res.data) {
        message.error(res.message ?? '导入失败');
        return;
      }
      message.success(res.message ?? '技能已导入');
      setImportOpen(false);
      form.resetFields();
      tableRef.current?.reloadData();
    } finally {
      setImporting(false);
    }
  };

  const columns: ExtTableProps<SkillDto>['columns'] = [
    {
      title: '操作',
      dataIndex: 'id',
      width: 120,
      render: (id: string, record: SkillDto) => (
        <>
          <ActionButton title="查看" icon={<EyeOutlined />} onClick={() => setViewing(record)} />
          <Popconfirm title="确认删除该技能？" onConfirm={() => handleDelete(id)}>
            <ActionButton title="删除" color="danger" icon={<DeleteOutlined />} />
          </Popconfirm>
        </>
      ),
    },
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '描述', dataIndex: 'description', expandUnusedSpace: true },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      width: 100,
      render: (t: SkillSourceType) => {
        const meta = SOURCE_TYPE_META[t] ?? { color: 'default', label: t };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '来源', dataIndex: 'source', width: 180 },
    {
      title: 'Hash 锁',
      dataIndex: 'computedHash',
      width: 200,
      render: (h: string) => <span className={styles.hash}>{h}</span>,
    },
    { title: '导入时间', dataIndex: 'createdDate', width: 170, dataType: 'datetime' },
  ];

  return (
    <div className={styles.page}>
      <ExtTable
        ref={tableRef}
        rowKey="id"
        columns={columns}
        store={{ url: SKILL_FIND_BY_PAGE_URL, type: 'POST' }}
        remotePaging
        showQuickSearch
        quickSearchFields={[{ field: 'name', title: '名称' }]}
        quickSearchPlaceHolder="请输入技能名称"
        toolbar={{
          left: (
            <Button type="primary" icon={<ImportOutlined />} onClick={() => setImportOpen(true)}>
              导入技能
            </Button>
          ),
        }}
      />

      <ExtModal
        open={importOpen}
        title="导入技能"
        subTitle="导入后由服务端计算 Hash 锁，相同内容重复导入幂等"
        confirmLoading={importing}
        onCancel={() => setImportOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          onFinish={handleImport}
          layout="vertical"
          initialValues={{ sourceType: 'INLINE' }}
        >
          <Form.Item
            name="name"
            label="技能名称"
            rules={[
              { required: true, message: '请输入技能名称' },
              {
                pattern: /^[a-z0-9][a-z0-9-]{0,63}$/,
                message: '仅小写字母/数字/连字符，需匹配 ^[a-z0-9][a-z0-9-]{0,63}$',
              },
            ]}
          >
            <Input placeholder="例如：suid" allowClear />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="技能用途简述" allowClear />
          </Form.Item>
          <Form.Item
            name="sourceType"
            label="来源类型"
            rules={[{ required: true, message: '请选择来源类型' }]}
          >
            <Select options={SOURCE_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="source"
            label="来源"
            tooltip="github:<owner>/<repo>[/path] | local:<name> | inline"
          >
            <Input placeholder="例如：github:acme/skills/suid" allowClear />
          </Form.Item>
          <Form.Item
            name="content"
            label="SKILL.md 内容"
            rules={[{ required: true, message: '请输入 SKILL.md 内容' }]}
          >
            <Input.TextArea rows={8} placeholder="# 技能标题\n\n技能正文（frontmatter + markdown）" />
          </Form.Item>
        </Form>
      </ExtModal>

      <ExtModal
        open={!!viewing}
        title={viewing?.name}
        subTitle={viewing?.computedHash}
        footer={null}
        onCancel={() => setViewing(null)}
        destroyOnHidden
      >
        <pre className={styles.content}>{viewing?.content}</pre>
      </ExtModal>
    </div>
  );
};

export default Skills;
