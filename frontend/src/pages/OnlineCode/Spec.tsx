/**
 * Track F5 — Spec review page.
 * Renders the structured SpecDto (pages / components / entities / apiContract)
 * as read-only tables; on confirm calls `/api/spec/confirm` which starts an
 * iteration and moves the project to DISPATCHING, then routes to the preview
 * page to drive the deploy → PREVIEW flow (F6/F7).
 */
import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { createStyles } from '@ead/antd-style';
import {
  BannerTitle,
  Button,
  Card,
  Descriptions,
  Empty,
  Popconfirm,
  Spin,
  Table,
  Tag,
  message,
} from '@ead/suid';
import { CheckOutlined } from '@ead/suid-icons';
import { confirmSpec, findOneSpec } from '@/services/onlineCode';
import type { SpecDto } from '@/services/onlineCode';

const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    overflow: auto;
    padding: ${token.paddingMD}px;
    display: flex;
    flex-direction: column;
    gap: ${token.marginMD}px;
  `,
  header: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
  `,
}));

const SPEC_STATE_COLOR: Record<SpecDto['state'], string> = {
  DRAFT: 'default',
  SPEC_REVIEW: 'gold',
  CONFIRMED: 'green',
};

const SpecReview: React.FC = () => {
  const { styles } = useStyles();
  const [searchParams] = useSearchParams();
  const specId = searchParams.get('id') ?? '';
  const [spec, setSpec] = useState<SpecDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      const res = await findOneSpec(specId);
      if (!alive) return;
      if (res.success && res.data) {
        setSpec(res.data);
      } else {
        message.error(res.message ?? '加载 Spec 失败');
      }
      setLoading(false);
    })();
    return () => {
      alive = false;
    };
  }, [specId]);

  const handleConfirm = async () => {
    if (!spec) return;
    setConfirming(true);
    try {
      const res = await confirmSpec(spec.id);
      if (!res.success || !res.data) {
        message.error(res.message ?? '确认失败');
        return;
      }
      message.success('Spec 已确认，迭代已启动');
      history.push(`/online-code/dispatch?id=${spec.projectId}`);
    } finally {
      setConfirming(false);
    }
  };

  if (loading) {
    return (
      <div className={styles.page}>
        <Spin spinning />
      </div>
    );
  }

  if (!spec) {
    return (
      <div className={styles.page}>
        <Empty description="Spec 不存在" />
      </div>
    );
  }

  const confirmed = spec.state === 'CONFIRMED';

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <BannerTitle title="Spec 评审" subTitle={`版本 v${spec.version}`} />
        <div>
          <Tag color={SPEC_STATE_COLOR[spec.state]}>{spec.state}</Tag>
          <Popconfirm
            title="确认该 Spec 并启动迭代？"
            onConfirm={handleConfirm}
            disabled={confirmed}
          >
            <Button
              type="primary"
              icon={<CheckOutlined />}
              loading={confirming}
              disabled={confirmed}
            >
              {confirmed ? '已确认' : '确认 Spec'}
            </Button>
          </Popconfirm>
        </div>
      </div>

      <Card title="页面 Pages">
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

      <Card title="组件 Components">
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

      <Card title="实体 Entities">
        {spec.entities.map((entity) => (
          <Descriptions
            key={entity.key}
            title={entity.key}
            bordered
            size="small"
            column={1}
            style={{ marginBottom: 12 }}
          >
            {entity.fields.map((field) => (
              <Descriptions.Item key={field.name} label={`${field.name} (${field.type})`}>
                {field.description}
              </Descriptions.Item>
            ))}
          </Descriptions>
        ))}
      </Card>

      <Card title="接口契约 API Contract">
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
    </div>
  );
};

export default SpecReview;
