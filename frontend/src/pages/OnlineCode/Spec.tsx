/**
 * Track F5 — Module detailed design page.
 * Renders the structured SpecDto (pages / components / entities / apiContract)
 * as read-only tables; legacy API names remain in the service layer for compatibility.
 */
import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Popconfirm,
  Spin,
  Table,
  Tag,
  message,
} from '@ead/suid';
import { CheckOutlined, ReloadOutlined } from '@ead/suid-icons';
import {
  confirmDetailedDesign,
  findOneDetailedDesign,
  regenerateDetailedDesign,
} from '@/services/onlineCode';
import type { SpecDto } from '@/services/onlineCode';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';
import { ExtModal } from '@ead/suid';

const SPEC_STATE_COLOR: Record<SpecDto['state'], string> = {
  GENERATING: 'processing',
  DRAFT: 'default',
  SPEC_REVIEW: 'gold',
  CONFIRMED: 'green',
  FAILED: 'error',
};

const DETAILED_DESIGN_STATE_TEXT: Record<SpecDto['state'], string> = {
  GENERATING: '生成中',
  DRAFT: '草稿',
  SPEC_REVIEW: '待确认',
  CONFIRMED: '已确认',
  FAILED: '失败',
};

const DetailedDesignReview: React.FC = () => {
  const [searchParams] = useSearchParams();
  const specId = searchParams.get('id') ?? '';
  const [spec, setSpec] = useState<SpecDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [regenerateModalOpen, setRegenerateModalOpen] = useState(false);
  const [regenerateForm] = Form.useForm<{ modifyHint: string }>();
  const [regenerating, setRegenerating] = useState(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      const res = await findOneDetailedDesign(specId);
      if (!alive) return;
      if (res.success && res.data) {
        setSpec(res.data);
      } else {
        message.error(res.message ?? '加载详细设计失败');
      }
      setLoading(false);
    })();
    return () => {
      alive = false;
    };
  }, [specId]);

  // GENERATING 期间轮询，让「生成 / 重新生成」后能自动看到 SPEC_REVIEW/FAILED 结果；
  // 状态离开 GENERATING 即停止（对齐 PlanTab 轮询模式）。
  const specState = spec?.state;
  useEffect(() => {
    if (specState !== 'GENERATING') return;
    const timer = setInterval(async () => {
      const res = await findOneDetailedDesign(specId);
      if (res.success && res.data) {
        setSpec(res.data);
      }
    }, 5000);
    return () => clearInterval(timer);
  }, [specState, specId]);

  const handleConfirm = async () => {
    if (!spec) return;
    setConfirming(true);
    try {
      const res = await confirmDetailedDesign(spec.id);
      if (!res.success || !res.data) {
        message.error(res.message ?? '确认失败');
        return;
      }
      message.success('详细设计已确认，功能设计生成已启动');
      history.push(`/online-code/project?id=${spec.projectId}`);
    } finally {
      setConfirming(false);
    }
  };

  const handleRegenerate = async (values: { modifyHint: string }) => {
    if (!spec) return;
    setRegenerating(true);
    try {
      const res = await regenerateDetailedDesign(spec.projectId, values.modifyHint);
      if (!res.success || !res.data) {
        message.error(res.message ?? '重新生成失败');
        return;
      }
      message.success('已重新生成详细设计');
      setSpec(res.data);
      history.replace(`/online-code/spec?id=${res.data.id}`);
      setRegenerateModalOpen(false);
      regenerateForm.resetFields();
    } finally {
      setRegenerating(false);
    }
  };

  if (loading) {
    return (
      <PageContainer scroll>
        <PageState loading />
      </PageContainer>
    );
  }

  if (!spec) {
    return (
      <PageContainer scroll>
        <PageState error="详细设计不存在" />
      </PageContainer>
    );
  }

  const confirmed = spec.state === 'CONFIRMED';
  const isGenerating = spec.state === 'GENERATING';
  const failed = spec.state === 'FAILED';

  return (
    <PageContainer scroll>
      <PageHeader
        title="详细设计"
        subTitle={`${spec.moduleTitle ? `${spec.moduleTitle} · ` : ''}版本 v${spec.version}`}
        extra={
          <Tag color={SPEC_STATE_COLOR[spec.state]}>
            {DETAILED_DESIGN_STATE_TEXT[spec.state]}
          </Tag>
        }
        actions={
          <>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => setRegenerateModalOpen(true)}
              disabled={confirmed || isGenerating}
            >
              重新生成
            </Button>
            <Popconfirm
              title="确认该详细设计并启动功能设计？"
              onConfirm={handleConfirm}
              disabled={confirmed || isGenerating || failed}
            >
              <Button
                type="primary"
                icon={<CheckOutlined />}
                loading={confirming}
                disabled={confirmed || isGenerating || failed}
              >
                {confirmed ? '已确认' : '确认详细设计并启动功能设计'}
              </Button>
            </Popconfirm>
          </>
        }
      />

      {isGenerating && (
        <Card>
          <Spin spinning tip="正在生成详细设计..." />
        </Card>
      )}

      <Card title="页面">
        <Table
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={spec.pages}
          columns={[
            { title: 'Key', dataIndex: 'key', width: 140 },
            { title: '标题', dataIndex: 'title', width: 180 },
            { title: '路由', dataIndex: 'route', width: 180 },
            { title: '说明', dataIndex: 'description' },
          ]}
        />
      </Card>

      <Card title="组件">
        <Table
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={spec.components}
          columns={[
            { title: 'Key', dataIndex: 'key', width: 160 },
            { title: '类型', dataIndex: 'type', width: 140 },
            { title: '所属页面', dataIndex: 'page', width: 120 },
            { title: '说明', dataIndex: 'description' },
          ]}
        />
      </Card>

      <Card title="实体">
        {/* guard: backend serializes empty entities/fields as null (contract
            mismatch with legacy SpecDto); Tables tolerate null dataSource, .map does not */}
        {(spec.entities ?? []).map((entity) => (
          <Descriptions
            key={entity.key}
            title={entity.key}
            bordered
            size="small"
            column={1}
            style={{ marginBottom: 12 }}
          >
            {(entity.fields ?? []).map((field) => (
              <Descriptions.Item key={field.name} label={`${field.name} (${field.type})`}>
                {field.description}
              </Descriptions.Item>
            ))}
          </Descriptions>
        ))}
      </Card>

      <Card title="接口契约">
        <Table
          rowKey="path"
          size="small"
          pagination={false}
          dataSource={spec.apiContract}
          columns={[
            { title: 'Method', dataIndex: 'method', width: 90 },
            { title: 'Path', dataIndex: 'path', width: 220 },
            { title: '请求', dataIndex: 'requestShape', width: 120 },
            { title: '响应', dataIndex: 'responseShape' },
            { title: '说明', dataIndex: 'description', width: 160 },
          ]}
        />
      </Card>

      <ExtModal
        open={regenerateModalOpen}
        title="重新生成详细设计"
        confirmLoading={regenerating}
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
              placeholder="描述你希望如何修改详细设计"
            />
          </Form.Item>
        </Form>
      </ExtModal>
    </PageContainer>
  );
};

export default DetailedDesignReview;
