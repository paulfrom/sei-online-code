/**
 * Track F24 — Settings/Config page.
 * Loads the singleton platform config via ep #31 on mount and upserts it via
 * ep #32. Two fields: Workspace Root (per-project workspace dirs live under it)
 * and Template GitLab URL. When the URL is empty the platform generates the
 * canonical SUID scaffold instead of cloning a template — surfaced as helper text.
 */
import React, { useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Form, Input, Select, Space, Table, Tag, message } from '@ead/suid';
import { ReloadOutlined, SaveOutlined } from '@ead/suid-icons';
import { getConfig, saveConfig } from '@/services/onlineCode';
import * as memorySeedTemplate from '@/services/memorySeedTemplate';
import type { PlatformConfigDto } from '@/services/onlineCode';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';

const useStyles = createStyles(({ token, css }) => ({
  card: css`
    max-width: 720px;
  `,
  hint: css`
    color: ${token.colorTextSecondary};
    font-size: ${token.fontSizeSM}px;
  `,
  actions: css`
    display: flex;
    gap: ${token.marginSM}px;
    margin-top: ${token.marginMD}px;
  `,
}));

interface ConfigForm {
  workspaceRoot: string;
  templateGitlabUrl: string;
}

const Settings: React.FC = () => {
  const { styles } = useStyles();
  const [form] = Form.useForm<ConfigForm>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [config, setConfig] = useState<PlatformConfigDto | null>(null);

  /** Seed template config state. */
  const [seedForm] = Form.useForm();
  const [seedTemplates, setSeedTemplates] = useState<Array<{
    id: string;
    code: string;
    name: string;
    description?: string;
    version: number;
    status: string;
    isDefault: boolean;
    projectMemoryTemplate?: string;
    memoryRulesTemplate?: string;
    decisionsTemplate?: string;
    modulesTemplate?: string;
  }>>([]);
  const [seedLoading, setSeedLoading] = useState(false);
  const [seedSaving, setSeedSaving] = useState(false);

  /** template URL drives the clone-vs-scaffold path; watched for live helper text */
  const templateGitlabUrl = Form.useWatch('templateGitlabUrl', form);
  const willScaffold = !templateGitlabUrl || !templateGitlabUrl.trim();

  const load = async () => {
    setLoading(true);
    try {
      const res = await getConfig();
      if (res.success && res.data) {
        setConfig(res.data);
        form.setFieldsValue({
          workspaceRoot: res.data.workspaceRoot,
          templateGitlabUrl: res.data.templateGitlabUrl,
        });
      } else {
        message.error(res.message ?? '加载配置失败');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSave = async (values: ConfigForm) => {
    setSaving(true);
    try {
      const res = await saveConfig({
        workspaceRoot: values.workspaceRoot,
        templateGitlabUrl: values.templateGitlabUrl ?? '',
      });
      if (!res.success || !res.data) {
        message.error(res.message ?? '保存失败');
        return;
      }
      setConfig(res.data);
      form.setFieldsValue({
        workspaceRoot: res.data.workspaceRoot,
        templateGitlabUrl: res.data.templateGitlabUrl,
      });
      message.success(res.message ?? '配置已保存');
    } finally {
      setSaving(false);
    }
  };

  const loadSeedTemplates = async () => {
    setSeedLoading(true);
    try {
      const res = await memorySeedTemplate.list();
      if (res.success && res.data) {
        setSeedTemplates(res.data);
      } else {
        message.error(res.message ?? '加载 seed 模板失败');
      }
    } finally {
      setSeedLoading(false);
    }
  };

  const selectTemplate = (id: string) => {
    const template = seedTemplates.find((t) => t.id === id);
    if (!template) return;
    seedForm.setFieldsValue({
      selectedTemplateId: template.id,
      code: template.code,
      name: template.name,
      description: template.description,
      projectMemoryTemplate: template.projectMemoryTemplate,
      memoryRulesTemplate: template.memoryRulesTemplate,
      decisionsTemplate: template.decisionsTemplate,
      modulesTemplate: template.modulesTemplate,
    });
  };

  const handleSaveSeedDraft = async () => {
    const values = await seedForm.validateFields();
    setSeedSaving(true);
    try {
      const dto = {
        id: values.selectedTemplateId,
        code: values.code,
        name: values.name,
        description: values.description,
        projectMemoryTemplate: values.projectMemoryTemplate,
        memoryRulesTemplate: values.memoryRulesTemplate,
        decisionsTemplate: values.decisionsTemplate,
        modulesTemplate: values.modulesTemplate,
      };
      const res = await memorySeedTemplate.saveDraft(dto);
      if (res.success) {
        message.success('草稿已保存');
        await loadSeedTemplates();
      } else {
        message.error(res.message ?? '保存草稿失败');
      }
    } finally {
      setSeedSaving(false);
    }
  };

  const handlePublish = async () => {
    const id = seedForm.getFieldValue('selectedTemplateId');
    if (!id) return;
    setSeedSaving(true);
    try {
      const res = await memorySeedTemplate.publish(id);
      if (res.success) {
        message.success('已发布新版本');
        await loadSeedTemplates();
      } else {
        message.error(res.message ?? '发布失败');
      }
    } finally {
      setSeedSaving(false);
    }
  };

  const handleSetDefault = async () => {
    const id = seedForm.getFieldValue('selectedTemplateId');
    if (!id) return;
    setSeedSaving(true);
    try {
      const res = await memorySeedTemplate.setDefault(id);
      if (res.success) {
        message.success('已设为全局默认模板');
        await loadSeedTemplates();
      } else {
        message.error(res.message ?? '设为默认失败');
      }
    } finally {
      setSeedSaving(false);
    }
  };

  const handleArchive = async () => {
    const id = seedForm.getFieldValue('selectedTemplateId');
    if (!id) return;
    setSeedSaving(true);
    try {
      const res = await memorySeedTemplate.archive(id);
      if (res.success) {
        message.success('已归档');
        seedForm.resetFields();
        await loadSeedTemplates();
      } else {
        message.error(res.message ?? '归档失败');
      }
    } finally {
      setSeedSaving(false);
    }
  };

  const handleBootstrapDefault = async () => {
    setSeedSaving(true);
    try {
      const res = await memorySeedTemplate.bootstrapDefault();
      if (res.success) {
        message.success('已重建默认模板');
        await loadSeedTemplates();
      } else {
        message.error(res.message ?? '重建默认模板失败');
      }
    } finally {
      setSeedSaving(false);
    }
  };

  useEffect(() => {
    loadSeedTemplates();
  }, []);

  if (loading) {
    return (
      <PageContainer scroll>
        <PageState loading />
      </PageContainer>
    );
  }

  return (
    <PageContainer scroll>
      <PageHeader title="平台配置" subTitle="工作区根目录 + 模板仓库地址" />
      <Card className={styles.card}>
        <Form form={form} onFinish={handleSave} layout="vertical">
          <Form.Item
            name="workspaceRoot"
            label="工作区根目录"
            tooltip="各项目工作区目录位于该根目录下：<workspaceRoot>/<projectId>"
            rules={[{ required: true, message: '请输入工作区根目录' }]}
            extra={
              <span className={styles.hint}>
                留空将回退到默认值 /tmp/sei-online-code；可用环境变量 oc.workspace.root 覆盖。
              </span>
            }
          >
            <Input placeholder="/tmp/sei-online-code" allowClear />
          </Form.Item>
          <Form.Item
            name="templateGitlabUrl"
            label="模板 GitLab 地址"
            extra={
              <span className={styles.hint}>
                {willScaffold ? (
                  <>
                    未配置模板地址 → 首次预置将走
                    <Tag color="green" style={{ marginInlineStart: 4 }}>
                      脚手架生成
                    </Tag>
                    路径（生成规范 SUID 技术栈）。
                  </>
                ) : (
                  <>
                    已配置模板地址 → 首次预置将走
                    <Tag color="blue" style={{ marginInlineStart: 4 }}>
                      模板克隆
                    </Tag>
                    路径（clone-once 后复用）。
                  </>
                )}
              </span>
            }
          >
            <Input placeholder="留空则使用脚手架生成；例如 https://gitlab.example.com/tpl/suid-app.git" allowClear />
          </Form.Item>
          <div className={styles.actions}>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              保存
            </Button>
            <Button icon={<ReloadOutlined />} onClick={load} disabled={saving}>
              重置
            </Button>
          </div>
        </Form>
        {config ? (
          <div className={styles.hint} style={{ marginTop: 12 }}>
            配置 ID：{config.id}　创建时间：{config.createdDate}
          </div>
        ) : null}
      </Card>
      <Card className={styles.card} style={{ marginTop: 24 }} loading={seedLoading}>
        <PageHeader title="记忆 Seed 模板配置" subTitle="平台默认项目记忆模板；修改不影响已创建项目" />
        <Table
          size="small"
          rowKey="id"
          dataSource={seedTemplates}
          pagination={false}
          columns={[
            { title: 'Code', dataIndex: 'code', width: 120 },
            { title: '名称', dataIndex: 'name' },
            { title: '版本', dataIndex: 'version', width: 80 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (status: string) => <Tag>{status}</Tag>,
            },
            {
              title: '默认',
              dataIndex: 'isDefault',
              width: 80,
              render: (isDefault: boolean) => (isDefault ? <Tag color="blue">是</Tag> : '—'),
            },
          ]}
        />
        <Form form={seedForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="selectedTemplateId" label="选择模板编辑">
            <Select
              placeholder="请选择要编辑的模板"
              allowClear
              onChange={(value: string) => selectTemplate(value)}
              options={seedTemplates.map((t) => ({
                value: t.id,
                label: `${t.name} (v${t.version})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="code" label="模板 Code" rules={[{ required: true, message: '请输入 code' }]}>
            <Input placeholder="例如：default" allowClear />
          </Form.Item>
          <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="例如：平台默认 seed 模板" allowClear />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="模板用途说明" allowClear />
          </Form.Item>
          <Form.Item name="projectMemoryTemplate" label="project-memory.md">
            <Input.TextArea rows={6} placeholder="Markdown 内容" />
          </Form.Item>
          <Form.Item name="memoryRulesTemplate" label="memory-rules.md">
            <Input.TextArea rows={6} placeholder="Markdown 内容" />
          </Form.Item>
          <Form.Item name="decisionsTemplate" label="decisions.md">
            <Input.TextArea rows={6} placeholder="Markdown 内容" />
          </Form.Item>
          <Form.Item name="modulesTemplate" label="modules.md">
            <Input.TextArea rows={6} placeholder="Markdown 内容" />
          </Form.Item>
          <Space wrap>
            <Button type="primary" loading={seedSaving} onClick={handleSaveSeedDraft}>
              保存草稿
            </Button>
            <Button loading={seedSaving} onClick={handlePublish}>发布新版本</Button>
            <Button loading={seedSaving} onClick={handleSetDefault}>设为默认</Button>
            <Button loading={seedSaving} onClick={handleArchive}>归档</Button>
            <Button loading={seedSaving} onClick={handleBootstrapDefault}>从内置 default bootstrap</Button>
          </Space>
        </Form>
      </Card>
    </PageContainer>
  );
};

export default Settings;
