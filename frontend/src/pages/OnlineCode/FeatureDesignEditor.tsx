/**
 * F4: FeatureDesignEditor - Modal for viewing/editing FeatureDesign content
 */
import React, { useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import {
  Badge,
  Button,
  ExtModal,
  Form,
  Input,
  message,
  Space,
  Spin,
  Tag,
} from '@ead/suid';
import {
  edit,
  getLatest,
} from '@/services/featureDesign';
import type { FeatureDesignDto, FeatureDesignContent } from '@/services/featureDesign';
import FailureInfoPanel from './components/FailureInfoPanel';

const useStyles = createStyles(({ token, css }) => ({
  section: css`
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorder};
    border-radius: ${token.borderRadius}px;
    padding: ${token.paddingMD}px;
    margin-bottom: ${token.marginSM}px;
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
  statusRow: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
    margin-bottom: ${token.marginMD}px;
  `,
}));

interface FeatureDesignEditorProps {
  open: boolean;
  featureDesignId: string | null;
  onCancel: () => void;
  onSuccess?: (fd: FeatureDesignDto) => void;
}

const statusColorMap: Record<string, string> = {
  PENDING: 'default',
  GENERATING: 'processing',
  DRAFT: 'default',
  CONFIRMED: 'success',
  STALE: 'warning',
  FAILED: 'error',
};

const statusTextMap: Record<string, string> = {
  PENDING: '待生成',
  GENERATING: '生成中',
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  STALE: '已过期',
  FAILED: '失败',
};

const buildStatusColorMap: Record<string, string> = {
  IDLE: 'default',
  BUILDING: 'processing',
  BUILT: 'success',
  BUILD_FAILED: 'error',
  STALE: 'warning',
};

const buildStatusTextMap: Record<string, string> = {
  IDLE: '未执行',
  BUILDING: '编码执行中',
  BUILT: '已执行',
  BUILD_FAILED: '执行失败',
  STALE: '已过期',
};

const FeatureDesignEditor: React.FC<FeatureDesignEditorProps> = ({
  open,
  featureDesignId,
  onCancel,
  onSuccess,
}) => {
  const { styles } = useStyles();
  const [form] = Form.useForm<FeatureDesignContent>();
  const [featureDesign, setFeatureDesign] = useState<FeatureDesignDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [editing, setEditing] = useState(false);

  const fetchFeatureDesign = async (id: string) => {
    setLoading(true);
    try {
      const res = await getLatest(id);
      if (res.success && res.data) {
        setFeatureDesign(res.data);
        if (editing) {
          form.setFieldsValue(res.data.content);
        }
      } else {
        message.error(res.message ?? '获取功能设计失败');
      }
    } catch (e) {
      message.error('获取功能设计失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && featureDesignId) {
      fetchFeatureDesign(featureDesignId);
      setEditing(false);
    } else {
      setFeatureDesign(null);
      setEditing(false);
    }
  }, [open, featureDesignId]);

  const handleEdit = () => {
    if (!featureDesign || !featureDesign.content) return;
    form.setFieldsValue(featureDesign.content);
    setEditing(true);
  };

  const handleCancelEdit = () => {
    setEditing(false);
  };

  const handleSaveEdit = async (values: FeatureDesignContent) => {
    if (!featureDesign) return;
    setSubmitting(true);
    try {
      const res = await edit(featureDesign.id, values);
      if (res.success && res.data) {
        message.success('保存成功');
        setFeatureDesign(res.data);
        setEditing(false);
        onSuccess?.(res.data);
      } else {
        message.error(res.message ?? '保存失败');
      }
    } catch (e: any) {
      if (e.status === 409) {
        message.error('该功能正在编码执行中，无法编辑');
      } else {
        message.error('保存失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const isConfirmed = featureDesign?.status === 'CONFIRMED';
  const isGenerating = featureDesign?.status === 'GENERATING';
  const isBuilding = featureDesign?.buildStatus === 'BUILDING';
  const canEdit = !isConfirmed && !isGenerating && !isBuilding && featureDesign?.content;

  return (
    <ExtModal
      open={open}
      title="功能设计详情"
      width={900}
      onCancel={onCancel}
      footer={null}
      destroyOnHidden
    >
      {loading ? (
        <Spin spinning tip="加载中..." />
      ) : !featureDesign ? (
        <div>暂无数据</div>
      ) : (
        <>
          <div className={styles.statusRow}>
            <span style={{ fontWeight: 600 }}>
              {featureDesign.featureId} v{featureDesign.version}
            </span>
            <Badge
              status={statusColorMap[featureDesign.status] as any}
              text={statusTextMap[featureDesign.status]}
            />
            <Badge
              status={buildStatusColorMap[featureDesign.buildStatus] as any}
              text={buildStatusTextMap[featureDesign.buildStatus]}
            />
            {featureDesign.isLatest && <Tag color="blue">最新</Tag>}
          </div>

          <FailureInfoPanel info={featureDesign} title="功能设计失败信息" />

          {isGenerating ? (
            <div className={styles.section}>
              <Spin spinning tip="正在生成功能设计..." />
            </div>
          ) : editing ? (
            <Form form={form} onFinish={handleSaveEdit} layout="vertical">
              <div className={styles.section}>
                <div className={styles.sectionTitle}>功能目标</div>
                <Form.Item
                  name="goal"
                  label="功能目标"
                  rules={[{ required: true, message: '请输入功能目标' }]}
                >
                  <Input.TextArea rows={4} placeholder="描述该功能的用户故事和目标" />
                </Form.Item>
              </div>

              <div className={styles.section}>
                <div className={styles.sectionTitle}>设计方案</div>
                <Form.Item
                  name="design"
                  label="设计方案"
                  rules={[{ required: true, message: '请输入设计方案' }]}
                >
                  <Input.TextArea
                    rows={6}
                    placeholder="描述页面/组件/交互/数据/接口设计（JSON格式或文本）"
                  />
                </Form.Item>
              </div>

              <div className={styles.section}>
                <div className={styles.sectionTitle}>验收标准</div>
                <Form.List name="acceptance">
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
                          <Form.Item
                            {...field}
                            name={field.name}
                            label={`验收标准 ${index + 1}`}
                            rules={[{ required: true, message: '请输入验收标准' }]}
                            style={{ flex: 1, marginBottom: 0 }}
                          >
                            <Input placeholder="验收标准" />
                          </Form.Item>
                          {index > 0 && (
                            <Button
                              type="text"
                              danger
                              onClick={() => remove(field.name)}
                              style={{ marginTop: 24 }}
                            >
                              删除
                            </Button>
                          )}
                        </div>
                      ))}
                      <Form.Item>
                        <Button type="dashed" onClick={() => add()} block>
                          添加验收标准
                        </Button>
                      </Form.Item>
                    </>
                  )}
                </Form.List>
              </div>

              <div className={styles.section}>
                <div className={styles.sectionTitle}>文件范围</div>
                <Form.List name="fileScope">
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
                          <Form.Item
                            {...field}
                            name={field.name}
                            label={`文件 ${index + 1}`}
                            rules={[{ required: true, message: '请输入文件路径' }]}
                            style={{ flex: 1, marginBottom: 0 }}
                          >
                            <Input placeholder="src/pages/Example/index.tsx" />
                          </Form.Item>
                          {index > 0 && (
                            <Button
                              type="text"
                              danger
                              onClick={() => remove(field.name)}
                              style={{ marginTop: 24 }}
                            >
                              删除
                            </Button>
                          )}
                        </div>
                      ))}
                      <Form.Item>
                        <Button type="dashed" onClick={() => add()} block>
                          添加文件
                        </Button>
                      </Form.Item>
                    </>
                  )}
                </Form.List>
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
              {featureDesign.content && (
                <>
                  <div className={styles.section}>
                    <div className={styles.sectionTitle}>功能目标</div>
                    <div className={styles.content}>{featureDesign.content.goal}</div>
                  </div>

                  <div className={styles.section}>
                    <div className={styles.sectionTitle}>设计方案</div>
                    <div className={styles.content}>
                      {typeof featureDesign.content.design === 'string'
                        ? featureDesign.content.design
                        : JSON.stringify(featureDesign.content.design, null, 2)}
                    </div>
                  </div>

                  <div className={styles.section}>
                    <div className={styles.sectionTitle}>验收标准</div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {featureDesign.content.acceptance.map((item, i) => (
                        <div key={i}>• {item}</div>
                      ))}
                    </div>
                  </div>

                  <div className={styles.section}>
                    <div className={styles.sectionTitle}>文件范围</div>
                    <div className={styles.tags}>
                      {featureDesign.content.fileScope.map((file, i) => (
                        <Tag key={i}>{file}</Tag>
                      ))}
                    </div>
                  </div>
                </>
              )}

              {canEdit && (
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <Button type="primary" onClick={handleEdit}>
                    编辑
                  </Button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </ExtModal>
  );
};

export default FeatureDesignEditor;
