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
  importGithubSkill,
  importSkillArchive,
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
  subFormCard: css`
    border: 1px solid ${token.colorBorderSecondary};
    padding: 12px;
    margin-bottom: 8px;
    border-radius: ${token.borderRadiusSM}px;
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
  githubUrl: string;
}

const Skills: React.FC = () => {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [form] = Form.useForm<ImportForm>();
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [viewing, setViewing] = useState<SkillDto | null>(null);
  const [importMode, setImportMode] = useState<'github' | 'archive'>('github');
  const [archiveFile, setArchiveFile] = useState<File | null>(null);

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
      const res = importMode === 'github'
        ? await importGithubSkill(values.githubUrl)
        : archiveFile
          ? await importSkillArchive(archiveFile)
          : { success: false, message: '请选择 zip/.skill 文件', data: null };
      if (!res.success || !res.data) {
        message.error(res.message ?? '导入失败');
        return;
      }
      message.success(res.message ?? '技能已导入');
      setImportOpen(false);
      form.resetFields();
      setArchiveFile(null);
      setImportMode('github');
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
        subTitle="支持 GitHub 地址导入或上传 zip/.skill，由后端解析 SKILL.md 并计算 Hash 锁"
        confirmLoading={importing}
        onCancel={() => {
          setImportOpen(false);
          setArchiveFile(null);
          setImportMode('github');
          form.resetFields();
        }}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          onFinish={handleImport}
          layout="vertical"
        >
          <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
            <Button
              type={importMode === 'github' ? 'primary' : 'default'}
              onClick={() => setImportMode('github')}
            >
              GitHub 地址
            </Button>
            <Button
              type={importMode === 'archive' ? 'primary' : 'default'}
              onClick={() => setImportMode('archive')}
            >
              上传 zip/.skill
            </Button>
          </div>

          {importMode === 'github' ? (
            <Form.Item
              name="githubUrl"
              label="GitHub 地址"
              rules={[
                { required: true, message: '请输入 GitHub 地址' },
                { pattern: /^https?:\/\/(www\.)?github\.com\/.+$/, message: '请输入合法的 github.com 地址' },
              ]}
              tooltip="支持仓库根地址、tree 地址，或直接指向 SKILL.md 的 blob 地址"
            >
              <Input placeholder="例如：https://github.com/acme/skills/tree/main/suid" allowClear />
            </Form.Item>
          ) : (
            <div className={styles.subFormCard}>
              <div style={{ marginBottom: 8, color: 'rgba(0,0,0,0.65)' }}>
                请选择包含 SKILL.md 的 zip/.skill 包，后端会自动解压并导入 references 等辅助文件。
              </div>
              <input
                type="file"
                accept=".zip,.skill,application/zip"
                onChange={(event) => setArchiveFile(event.target.files?.[0] ?? null)}
              />
              <div className={styles.hash} style={{ marginTop: 8 }}>
                {archiveFile ? `已选择：${archiveFile.name}` : '未选择文件'}
              </div>
            </div>
          )}
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
