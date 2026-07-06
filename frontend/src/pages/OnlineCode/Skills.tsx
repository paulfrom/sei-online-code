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
import { DeleteOutlined, EyeOutlined, ImportOutlined, PlusOutlined } from '@ead/suid-icons';
import {
  SKILL_FIND_BY_PAGE_URL,
  deleteSkill,
  importSkill,
} from '@/services/onlineCode';
import type { SkillConfig, SkillDto } from '@/services/onlineCode';
import { PageContainer } from './components/PageLayout';

const useStyles = createStyles(({ token, css }) => ({
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
  files?: Array<{ path: string; content: string }>;
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
        files: (values.files ?? []).filter((f) => f.path && f.content),
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
    <PageContainer>
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
        subTitle="导入后由服务端计算 Hash 锁，同名技能重复导入返回 409"
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
          <Form.Item label="辅助文件（可选，对应 references/**）">
            <Form.List name="files">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name: fieldName, ...restField }) => (
                    <div
                      key={key}
                      style={{
                        border: '1px solid #f0f0f0',
                        padding: 12,
                        marginBottom: 8,
                        borderRadius: 4,
                      }}
                    >
                      <Form.Item
                        {...restField}
                        name={[fieldName, 'path']}
                        label="路径"
                        rules={[
                          { required: true, message: '请输入路径' },
                          {
                            pattern: /^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$)).+$/,
                            message: '禁止绝对路径或 .. 段',
                          },
                        ]}
                      >
                        <Input placeholder="例如：references/dao.md" allowClear />
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[fieldName, 'content']}
                        label="内容"
                        rules={[{ required: true, message: '请输入内容' }]}
                      >
                        <Input.TextArea rows={4} placeholder="辅助文件正文" />
                      </Form.Item>
                      <Button
                        type="link"
                        color="danger"
                        icon={<DeleteOutlined />}
                        onClick={() => remove(fieldName)}
                      >
                        删除该文件
                      </Button>
                    </div>
                  ))}
                  <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />} block>
                    添加辅助文件
                  </Button>
                </>
              )}
            </Form.List>
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
        {viewing?.files?.length ? (
          <div style={{ marginTop: 12 }}>
            <div className={styles.hash}>辅助文件 ({viewing.files.length})</div>
            {viewing.files.map((f) => (
              <div key={f.path} style={{ marginBottom: 8 }}>
                <div className={styles.hash}>{f.path}</div>
                <pre className={styles.content}>{f.content}</pre>
              </div>
            ))}
          </div>
        ) : null}
      </ExtModal>
    </PageContainer>
  );
};

export default Skills;
