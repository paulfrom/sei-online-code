/**
 * F3: PlanTab UI - overview design management tab for a project.
 * Shows the latest overview design, allows editing, regenerating, confirming, and viewing history.
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
  MinusCircleOutlined,
} from '@ead/suid-icons';
import {
  getLatest,
  edit,
  regenerate,
  confirm,
  history as getHistory,
} from '@/services/plan';
import type {
  PlanDto,
  PlanContent,
  PlanFeature,
  PlanModule,
} from '@/services/plan';

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

interface EditablePlanContent {
  summary: string;
  techAssumptions: string;
  modules: PlanModule[];
  nonGoals: string;
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

const splitLines = (value?: string) =>
  (value ?? '')
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);

const joinLines = (value?: string[]) => (value ?? []).join('\n');

const createEmptyFeature = (): PlanFeature => ({
  featureId: '',
  title: '',
  outline: '',
});

const fallbackModules = (content: PlanContent): PlanModule[] => {
  if (content.modules?.length) {
    return content.modules;
  }
  return [
    {
      moduleId: 'default',
      title: '默认模块',
      summary: content.summary ?? '',
      features: content.features?.length ? content.features : [createEmptyFeature()],
    },
  ];
};

const toEditablePlanContent = (content: PlanContent): EditablePlanContent => ({
  summary: content.summary ?? '',
  techAssumptions: joinLines(content.techAssumptions),
  modules: fallbackModules(content),
  nonGoals: joinLines(content.nonGoals),
});

const toPlanContentPayload = (values: EditablePlanContent): PlanContent => {
  const modules = (values.modules ?? []).map((module) => ({
    moduleId: module.moduleId,
    title: module.title,
    summary: module.summary,
    features: (module.features ?? []).map((feature) => ({
      featureId: feature.featureId,
      title: feature.title,
      outline: feature.outline,
    })),
  }));
  return {
    summary: values.summary,
    techAssumptions: splitLines(values.techAssumptions),
    modules,
    features: modules.flatMap((module) => module.features ?? []),
    nonGoals: splitLines(values.nonGoals),
  };
};

const PlanTab: React.FC<PlanTabProps> = ({ projectId }) => {
  const { styles } = useStyles();
  const [plan, setPlan] = useState<PlanDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [form] = Form.useForm<EditablePlanContent>();
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [historyData, setHistoryData] = useState<PlanDto[]>([]);
  const [regenerateModalOpen, setRegenerateModalOpen] = useState(false);
  const [regenerateForm] = Form.useForm<{ modifyHint: string }>();
  const [submitting, setSubmitting] = useState(false);
  const historyTableRef = useRef<ExtTableRef>(null);

  const fetchPlan = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const res = await getLatest(projectId);
      if (res.success && res.data) {
        setPlan(res.data);
        if (editing) {
          form.setFieldsValue(toEditablePlanContent(res.data.content));
        }
      } else if (!silent) {
        message.error(res.message ?? '获取概要设计失败');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [projectId, editing, form]);

  useEffect(() => {
    fetchPlan();
  }, [fetchPlan]);

  // GENERATING 期间轮询，让"重新生成"后能自动看到 DRAFT/FAILED 结果；
  // editing 时不轮询（避免覆盖用户编辑）；状态离开 GENERATING 即停止。
  const planStatus = plan?.status;
  useEffect(() => {
    if (planStatus !== 'GENERATING' || editing) return;
    const timer = setInterval(() => {
      fetchPlan(true);
    }, 5000);
    return () => clearInterval(timer);
  }, [planStatus, editing, fetchPlan]);

  const handleEdit = () => {
    if (!plan) return;
    form.setFieldsValue(toEditablePlanContent(plan.content));
    setEditing(true);
  };

  const handleCancelEdit = () => {
    setEditing(false);
  };

  const handleSaveEdit = async (values: EditablePlanContent) => {
    if (!plan) return;
    setSubmitting(true);
    try {
      const res = await edit(projectId, toPlanContentPayload(values));
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
          暂无概要设计
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
          <span style={{ fontWeight: 600 }}>概要设计 v{plan.version}</span>
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
                  确认概要设计
                </Button>
              )}
            </>
          )}
        </Space>
      </div>

      {isGenerating ? (
        <div className={styles.section}>
          <Spin spinning tip="正在生成概要设计..." />
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
            <Form.Item name="techAssumptions" label="技术假设" rules={[{ required: true, message: '请输入技术假设' }]}>
              <Input.TextArea
                rows={3}
                placeholder="每行一个技术假设，例如：React 18, TypeScript, @ead/suid"
              />
            </Form.Item>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>模块划分</div>
            <Form.List name="modules">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field, index) => (
                    <div
                      key={field.key}
                      style={{
                        marginBottom: 16,
                        padding: 16,
                        border: '1px solid #f0f0f0',
                        borderRadius: 6,
                      }}
                    >
                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'moduleId']}
                          label="模块ID"
                          rules={[{ required: true, message: '请输入模块ID' }]}
                          style={{ width: 180 }}
                        >
                          <Input placeholder="MOD-001" />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'title']}
                          label="模块标题"
                          rules={[{ required: true, message: '请输入模块标题' }]}
                          style={{ flex: 1, minWidth: 220 }}
                        >
                          <Input placeholder="模块标题" />
                        </Form.Item>
                      </div>
                      <Form.Item
                        {...field}
                        name={[field.name, 'summary']}
                        label="模块概要"
                        rules={[{ required: true, message: '请输入模块概要' }]}
                      >
                        <Input.TextArea rows={3} placeholder="描述该模块的职责和边界" />
                      </Form.Item>
                      <div className={styles.sectionTitle} style={{ fontSize: 14, marginBottom: 8 }}>
                        模块功能项
                      </div>
                      <Form.List name={[field.name, 'features']}>
                        {(featureFields, { add: addFeature, remove: removeFeature }) => (
                          <>
                            {featureFields.map((featureField, featureIndex) => (
                              <div
                                key={featureField.key}
                                style={{
                                  display: 'flex',
                                  gap: 8,
                                  marginBottom: 8,
                                  alignItems: 'flex-start',
                                }}
                              >
                                <div style={{ flex: 1, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                  <Form.Item
                                    {...featureField}
                                    name={[featureField.name, 'featureId']}
                                    label="功能ID"
                                    rules={[{ required: true, message: '请输入功能ID' }]}
                                    style={{ width: 150, marginBottom: 0 }}
                                  >
                                    <Input placeholder="FEAT-001" />
                                  </Form.Item>
                                  <Form.Item
                                    {...featureField}
                                    name={[featureField.name, 'title']}
                                    label="功能标题"
                                    rules={[{ required: true, message: '请输入功能标题' }]}
                                    style={{ flex: 1, minWidth: 200, marginBottom: 0 }}
                                  >
                                    <Input placeholder="功能标题" />
                                  </Form.Item>
                                  <Form.Item
                                    {...featureField}
                                    name={[featureField.name, 'outline']}
                                    label="功能概要"
                                    rules={[{ required: true, message: '请输入功能概要' }]}
                                    style={{ flex: 2, minWidth: 300, marginBottom: 0 }}
                                  >
                                    <Input.TextArea rows={1} placeholder="功能概要" />
                                  </Form.Item>
                                </div>
                                {featureIndex > 0 && (
                                  <ActionButton
                                    type="text"
                                    danger
                                    icon={<MinusCircleOutlined />}
                                    onClick={() => removeFeature(featureField.name)}
                                    style={{ marginTop: 24 }}
                                  />
                                )}
                              </div>
                            ))}
                            <Form.Item>
                              <Button type="dashed" onClick={() => addFeature(createEmptyFeature())} block>
                                添加模块功能
                              </Button>
                            </Form.Item>
                          </>
                        )}
                      </Form.List>
                      {index > 0 && (
                        <ActionButton
                          type="text"
                          danger
                          icon={<MinusCircleOutlined />}
                          onClick={() => remove(field.name)}
                        >
                          删除模块
                        </ActionButton>
                      )}
                    </div>
                  ))}
                  <Form.Item>
                    <Button
                      type="dashed"
                      onClick={() =>
                        add({
                          moduleId: '',
                          title: '',
                          summary: '',
                          features: [createEmptyFeature()],
                        })
                      }
                      block
                    >
                      添加模块
                    </Button>
                  </Form.Item>
                </>
              )}
            </Form.List>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>不包含的内容</div>
            <Form.Item name="nonGoals" label="不包含的内容" rules={[{ required: true, message: '请输入不包含的内容' }]}>
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
      ) : plan.content ? (
        <>
          <div className={styles.section}>
            <div className={styles.sectionTitle}>项目概述</div>
            <div className={styles.content}>{plan.content.summary}</div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>技术假设</div>
            <div className={styles.tags}>
              {(plan.content.techAssumptions ?? []).map((tech, i) => (
                <Tag key={i}>{tech}</Tag>
              ))}
            </div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>模块划分</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {(plan.content.modules?.length
                ? plan.content.modules
                : [{
                    moduleId: 'default',
                    title: '默认模块',
                    summary: plan.content.summary,
                    features: plan.content.features ?? [],
                  }]
              ).map((module) => (
                <div key={module.moduleId || module.title}>
                  <div style={{ fontWeight: 600 }}>{module.title}</div>
                  <div className={styles.content}>{module.summary}</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
                    {(module.features ?? []).map((feature) => (
                      <div key={feature.featureId}>
                        <div style={{ fontWeight: 600 }}>
                          {feature.featureId} - {feature.title}
                        </div>
                        <div className={styles.content}>{feature.outline}</div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>不包含的内容</div>
            <div className={styles.tags}>
              {(plan.content.nonGoals ?? []).map((goal, i) => (
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
      ) : (
        <div className={styles.section}>
          概要设计生成失败，内容为空。请点击右上方“重新生成”重试。
        </div>
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
        title="重新生成概要设计"
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
              placeholder="描述你希望如何修改概要设计，例如：添加更多关于用户管理的功能"
            />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
};

export default PlanTab;
