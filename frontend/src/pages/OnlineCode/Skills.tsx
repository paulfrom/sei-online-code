/**
 * Track F14 — Skills page.
 * ExtTable list (ep #17 findByPage) + import ExtModal (ep #16) + delete (ep #19)
 * + a read-only content viewer (ep #18 shape already on the row).
 *
 * `computedHash` is the server-authoritative lock (contract §6); the FE only
 * displays it and never recomputes. Re-importing an existing name is rejected
 * by the server (409, dedup by name).
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
import type { SkillConfig, SkillDto } from '@/services/onlineCode';

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

/** derive a source-type tag from the origin prefix (multica dim d: type encoded in origin) */
function originTypeMeta(origin?: string): { color: string; label: string } {
  if (!origin) return { color: 'default', label: '未知' };
  if (origin.startsWith('github:')) return { color: 'blue', label: 'GitHub' };
  if (origin.startsWith('local:')) return { color: 'green', label: '本地' };
  return { color: 'gold', label: '内联' };
}

interface ImportForm {
  name: string;
  description: string;
  origin: string;
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
        config: { origin: values.origin || `inline:${values.name}` },
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
      dataIndex: 'config',
      width: 100,
      render: (c: SkillConfig | undefined) => {
        const meta = originTypeMeta(c?.origin);
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '来源',
      dataIndex: 'config',
      width: 180,
      render: (c: SkillConfig | undefined) => c?.origin ?? '-',
    },
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
            name="origin"
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
