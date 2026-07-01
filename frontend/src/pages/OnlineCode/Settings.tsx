/**
 * Track F24 — Settings/Config page.
 * Loads the singleton platform config via ep #31 on mount and upserts it via
 * ep #32. Two fields: Workspace Root (per-project workspace dirs live under it)
 * and Template GitLab URL. When the URL is empty the platform generates the
 * canonical SUID scaffold instead of cloning a template — surfaced as helper text.
 */
import React, { useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { BannerTitle, Button, Card, Form, Input, Spin, Tag, message } from '@ead/suid';
import { ReloadOutlined, SaveOutlined } from '@ead/suid-icons';
import { getConfig, saveConfig } from '@/services/onlineCode';
import type { PlatformConfigDto } from '@/services/onlineCode';

const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    padding: ${token.paddingMD}px;
    overflow: auto;
  `,
  header: css`
    margin-bottom: ${token.marginMD}px;
  `,
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

  if (loading) {
    return (
      <div className={styles.page}>
        <Spin spinning />
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <BannerTitle title="平台配置" subTitle="工作区根目录 + 模板仓库地址" />
      </div>
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
    </div>
  );
};

export default Settings;
