/**
 * F3: PlanTab UI - Plan management tab for a project
 * Shows the latest plan, allows editing, regenerating, confirming, and viewing history
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import {
  ActionButton,
  Badge,
  Button,
  Drawer,
  ExtModal,
  ExtTable,
  Form,
  Input,
  message,
  Space,
  Spin,
  Tag,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import {
  EditOutlined,
  HistoryOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
} from '@ead/suid-icons';
import {
  getLatest,
  edit,
  regenerate,
  confirm,
  history as getHistory,
} from '@/services/plan';
import type { PlanDto, PlanContent, PlanFeature } from '@/services/plan';

const useStyles = createStyles(({ token, css }) => ({
  container: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    gap: ${token.marginSM}px;
    overflow: auto;
  `,
  header: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
  `,
  statusRow: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
  `,
  section: css`
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorder};
    border-radius: ${token.borderRadius}px;
    padding: ${token.paddingMD}px;
  `,
  sectionTitle: css`
    font-size: ${token.fontSizeLG}px;
    font-weight: ${token.fontWeightStrong};
    margin-bottom: ${token.marginSM}px;
  `,
  content: css`
    white-space: pre-wrap;
    word-break: break-word;
  `,
  tags: css`
    display: flex;
    flex-wrap: wrap;
    gap: ${token.marginXS}px;
  `,
}));

interface PlanTabProps {
  projectId: string;
}

const statusColorMap: Record<string, string> = {
  GENERATING: 'processing',
  DRAFT: 'default',
  CONFIRMED: 'success',
  FAILED: 'error',
};

const statusTextMap: Record<string, string> = {
  GENERATING: '生成中',
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  FAILED: '失败',
};

const PlanTab: React.FC<PlanTabProps> = ({ projectId }) => {
  const { styles } = useStyles();
  const [plan, setPlan] = useState<PlanDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [form] = Form.useForm<PlanContent>();
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [historyData, setHistoryData] = useState<PlanDto[]>([]);
  const [regenerateModalOpen, setRegenerateModalOpen] = useState(false);
  const [regenerateForm] = Form.useForm<{ modifyHint: string }>();
  const [submitting, setSubmitting] = useState(false);
  const historyTableRef = useRef<ExtTableRef>(null);

  const fetchPlan = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getLatest(projectId);
      if (res.success && res.data) {
        setPlan(res.data);
        if (editing) {
          form.setFieldsValue(res.data.content);
        }
      } else {
        message.error(res.message ?? '获取计划失败');
      }
    } finally {
      setLoading(false);
    }
  }, [projectId, editing, form]);

  useEffect(() => {
    fetchPlan();
  }, [fetchPlan]);

  const handleEdit = () => {
    if (!plan) return;
    form.setFieldsValue(plan.content);
    setEditing(true);
  };

  const handleCancelEdit = () => {
    setEditing(false);
  };

  const handleSaveEdit = async (values: PlanContent) => {
    if (!plan) return;
    setSubmitting(true);
    try {
      const res = await edit(projectId, values);
      if (res.success && res.data) {
        message.success('保存成功');
        setPlan(res.data);
        setEditing(false);
      } else {
        message.error(res.message ?? '保存失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleRegenerate = async (values: { modifyHint: string }) => {
    setSubmitting(true);
    try {
      const res = await regenerate(projectId, values.modifyHint);
      if (res.success && res.data) {
        message.success('重新生成已开始');
        setPlan(res.data);
        setRegenerateModalOpen(false);
        regenerateForm.resetFields();
      } else {
        message.error(res.message ?? '重新生成失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirm = async () => {
    if (!plan) return;
    setSubmitting(true);
    try {
      const res = await confirm(projectId);
      if (res.success && res.data) {
        message.success('确认成功');
        setPlan(res.data);
      } else {
        message.error(res.message ?? '确认失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openHistory = async () => {
    try {
      const res = await getHistory(projectId);
      if (res.success && res.data) {
        setHistoryData(res.data);
        setHistoryDrawerOpen(true);
      } else {
        message.error(res.message ?? '获取历史失败');
      }
    } catch (e) {
      message.error('获取历史失败');
    }
  };

  const historyColumns: ExtTableProps<PlanDto>['columns'] = [
    { title: '版本', dataIndex: 'version', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status: string) => (
        <Badge status={statusColorMap[status] as any} text={statusTextMap[status]} />
      ),
    },
    { title: '创建时间', dataIndex: 'createdDate', width: 180, dataType: 'datetime' },
    { title: '修改时间', dataIndex: 'lastEditedDate', width: 180, dataType: 'datetime' },
    {
      title: '修改提示',
      dataIndex: 'modifyHint',
      render: (hint: string) => hint || '-',
    },
  ];

  if (loading) {
    return (
      <div className={styles.container}>
        <Spin spinning />
      </div>
    );
  }

  if (!plan) {
    return (
      <div className={styles.container}>
        <div className={styles.section}>
          暂无计划
        </div>
      </div>
    );
  }

  const isConfirmed = plan.status === 'CONFIRMED';
  const isGenerating = plan.status === 'GENERATING';

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div className={styles.statusRow}>
          <span style={{ fontWeight: 600 }}>计划 v{plan.version}</span>
          <Badge
            status={statusColorMap[plan.status] as any}
            text={statusTextMap[plan.status]}
          />
          {plan.isLatest && <Tag color="blue">最新</Tag>}
        </div>
        <Space>
          <Button icon={<HistoryOutlined />} onClick={openHistory}>
            历史版本
          </Button>
          {!isConfirmed && !isGenerating && (
            <>
              <Button icon={<ReloadOutlined />} onClick={() => setRegenerateModalOpen(true)}>
                重新生成
              </Button>
              {plan.status === 'DRAFT' && (
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  onClick={handleConfirm}
                  loading={submitting}
                >
                  确认计划
                </Button>
              )}
            </>
          )}
        </Space>
      </div>

      {isGenerating ? (
        <div className={styles.section}>
          <Spin spinning tip="正在生成计划..." />
        </div>
      ) : editing ? (
        <Form form={form} onFinish={handleSaveEdit} layout="vertical">
          <div className={styles.section}>
            <div className={styles.sectionTitle}>项目概述</div>
            <Form.Item
              name="summary"
              label="项目概述"
              rules={[{ required: true, message: '请输入项目概述' }]}
            >
              <Input.TextArea rows={4} placeholder="描述项目的目标和范围" />
            </Form.Item>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>技术假设</div>
            <Form.Item
              name="techAssumptions"
              label="技术假设"
              rules={[{ required: true, message: '请输入技术假设' }]}
            >
              <Input.TextArea
                rows={3}
                placeholder="每行一个技术假设，例如：React 18, TypeScript, @ead/suid"
              />
            </Form.Item>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>功能列表</div>
            <Form.List name="features">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field, index) => (
                    <div
                      key={field.key}
                      style={{
                        display: 'flex',
                        gap: 8,
                        marginBottom: 8,
                        alignItems: 'flex-start',
                      }}
                    >
                      <div style={{ flex: 1, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'featureId']}
                          label="功能ID"
                          rules={[{ required: true, message: '请输入功能ID' }]}
                          style={{ width: 150, marginBottom: 0 }}
                        >
                          <Input placeholder="FEAT-001" />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'title']}
                          label="功能标题"
                          rules={[{ required: true, message: '请输入功能标题' }]}
                          style={{ flex: 1, minWidth: 200, marginBottom: 0 }}
                        >
                          <Input placeholder="功能标题" />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'outline']}
                          label="功能概要"
                          rules={[{ required: true, message: '请输入功能概要' }]}
                          style={{ flex: 2, minWidth: 300, marginBottom: 0 }}
                        >
                          <Input.TextArea rows={1} placeholder="功能概要" />
                        </Form.Item>
                      </div>
                      {index > 0 && (
                        <ActionButton
                          type="text"
                          danger
                          icon={<EditOutlined />}
                          onClick={() => remove(field.name)}
                          style={{ marginTop: 24 }}
                        />
                      )}
                    </div>
                  ))}
                  <Form.Item>
                    <Button type="dashed" onClick={() => add()} block>
                      添加功能
                    </Button>
                  </Form.Item>
                </>
              )}
            </Form.List>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>不包含的内容</div>
            <Form.Item
              name="nonGoals"
              label="不包含的内容"
              rules={[{ required: true, message: '请输入不包含的内容' }]}
            >
              <Input.TextArea
                rows={3}
                placeholder="每行一个不包含的内容"
              />
            </Form.Item>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={handleCancelEdit} disabled={submitting}>
              取消
            </Button>
            <Button type="primary" htmlType="submit" loading={submitting}>
              保存
            </Button>
          </div>
        </Form>
      ) : (
        <>
          <div className={styles.section}>
            <div className={styles.sectionTitle}>项目概述</div>
            <div className={styles.content}>{plan.content.summary}</div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>技术假设</div>
            <div className={styles.tags}>
              {plan.content.techAssumptions.map((tech, i) => (
                <Tag key={i}>{tech}</Tag>
              ))}
            </div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>功能列表</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {plan.content.features.map((feature) => (
                <div key={feature.featureId}>
                  <div style={{ fontWeight: 600 }}>
                    {feature.featureId} - {feature.title}
                  </div>
                  <div className={styles.content}>{feature.outline}</div>
                </div>
              ))}
            </div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>不包含的内容</div>
            <div className={styles.tags}>
              {plan.content.nonGoals.map((goal, i) => (
                <Tag key={i} color="default">{goal}</Tag>
              ))}
            </div>
          </div>

          {!isConfirmed && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={handleEdit}
                loading={submitting}
              >
                编辑
              </Button>
            </div>
          )}
        </>
      )}

      <Drawer
        title="历史版本"
        width={800}
        open={historyDrawerOpen}
        onClose={() => setHistoryDrawerOpen(false)}
      >
        <ExtTable
          ref={historyTableRef}
          rowKey="version"
          columns={historyColumns}
          dataSource={historyData}
          pagination={false}
        />
      </Drawer>

      <ExtModal
        open={regenerateModalOpen}
        title="重新生成计划"
        confirmLoading={submitting}
        onCancel={() => {
          setRegenerateModalOpen(false);
          regenerateForm.resetFields();
        }}
        onOk={() => regenerateForm.submit()}
        destroyOnHidden
      >
        <Form form={regenerateForm} onFinish={handleRegenerate} layout="vertical">
          <Form.Item name="modifyHint" label="修改提示（可选）">
            <Input.TextArea
              rows={4}
              placeholder="描述你希望如何修改计划，例如：添加更多关于用户管理的功能"
            />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
};

export default PlanTab;
