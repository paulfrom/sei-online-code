/**
 * Track F19–F22 — Iteration timeline + build-loop actions.
 * F19: the project's iteration rounds rendered as a Timeline (round, specVersion,
 *      state, previewUrl, feedback), sourced from `/api/iteration/findByPage` (#27).
 * F20: PREVIEW actions on the active iteration — Accept (#25) + Optimize (#26,
 *      feedback prose → new Spec version → re-confirm via the #6 Spec review flow).
 * F21: Cancel (#28, non-terminal only) / Retry (#29, FAILED only), gated by state.
 * F22: Spec version history viewer (#30) in a read-only ExtModal.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { createStyles } from '@ead/antd-style';
import {
  BannerTitle,
  Button,
  Empty,
  ExtModal,
  Form,
  Input,
  Popconfirm,
  Spin,
  Table,
  Tag,
  Timeline,
  message,
} from '@ead/suid';
import {
  BranchesOutlined,
  CheckOutlined,
  CloseOutlined,
  HistoryOutlined,
  RedoOutlined,
  ThunderboltOutlined,
} from '@ead/suid-icons';
import {
  acceptIteration,
  cancelIteration,
  findIterationsByPage,
  findOneProject,
  findSpecsByProject,
  optimizeProject,
  retryIteration,
} from '@/services/onlineCode';
import type {
  IterationDto,
  LifecycleState,
  ProjectDto,
  SpecDto,
} from '@/services/onlineCode';
import LifecycleBadge from './components/LifecycleBadge';

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
  actions: css`
    display: flex;
    gap: ${token.marginXS}px;
    align-items: center;
  `,
  itemHead: css`
    display: flex;
    align-items: center;
    gap: ${token.marginXS}px;
    font-weight: ${token.fontWeightStrong};
  `,
  itemMeta: css`
    margin-top: ${token.marginXXS}px;
    color: ${token.colorTextSecondary};
    font-size: ${token.fontSizeSM}px;
    line-height: 1.8;
  `,
  feedback: css`
    margin-top: ${token.marginXXS}px;
    padding: ${token.paddingXS}px ${token.paddingSM}px;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    color: ${token.colorText};
    font-size: ${token.fontSizeSM}px;
  `,
  timelineWrap: css`
    padding: ${token.paddingMD}px;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
  `,
}));

/** Timeline dot color per lifecycle state (Timeline accepts antd color tokens). */
const STATE_DOT_COLOR: Partial<Record<LifecycleState, string>> = {
  ACCEPTED: 'green',
  PREVIEW: 'green',
  FAILED: 'red',
  CANCELLED: 'gray',
};

/** Terminal states carry a finishedDate and block cancel. */
const TERMINAL_STATES: LifecycleState[] = ['ACCEPTED', 'FAILED', 'CANCELLED'];

const SPEC_STATE_COLOR: Record<SpecDto['state'], string> = {
  DRAFT: 'default',
  SPEC_REVIEW: 'gold',
  CONFIRMED: 'green',
};

const IterationTimeline: React.FC = () => {
  const { styles } = useStyles();
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('id') ?? '';

  const [project, setProject] = useState<ProjectDto | null>(null);
  const [iterations, setIterations] = useState<IterationDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);

  // optimize modal (F20)
  const [optimizeOpen, setOptimizeOpen] = useState(false);
  const [optimizing, setOptimizing] = useState(false);
  const [form] = Form.useForm<{ feedback: string }>();

  // spec history viewer (F22)
  const [historyOpen, setHistoryOpen] = useState(false);
  const [specs, setSpecs] = useState<SpecDto[]>([]);
  const [specsLoading, setSpecsLoading] = useState(false);

  const load = useCallback(async () => {
    if (!projectId) return;
    const [prjRes, iterRes] = await Promise.all([
      findOneProject(projectId),
      findIterationsByPage({
        filters: [{ fieldName: 'projectId', value: projectId, operator: 'EQ' }],
        pageInfo: { page: 1, rows: 100 },
      }),
    ]);
    if (prjRes.success && prjRes.data) setProject(prjRes.data);
    if (iterRes.success && iterRes.data) setIterations(iterRes.data.rows);
  }, [projectId]);

  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      await load();
      if (alive) setLoading(false);
    })();
    return () => {
      alive = false;
    };
  }, [load]);

  /** the active iteration is the project's current one (drives action gating). */
  const active =
    iterations.find((it) => it.id === project?.currentIterationId) ??
    iterations[iterations.length - 1] ??
    null;

  const handleAccept = async () => {
    if (!active) return;
    setActing(true);
    try {
      const res = await acceptIteration(active.id);
      if (!res.success) {
        message.error(res.message ?? '验收失败');
        return;
      }
      message.success(res.message ?? '已验收');
      await load();
    } finally {
      setActing(false);
    }
  };

  const handleCancel = async () => {
    if (!active) return;
    setActing(true);
    try {
      const res = await cancelIteration(active.id);
      if (!res.success) {
        message.error(res.message ?? '取消失败');
        return;
      }
      message.success(res.message ?? '迭代已取消');
      await load();
    } finally {
      setActing(false);
    }
  };

  const handleRetry = async () => {
    if (!active) return;
    setActing(true);
    try {
      const res = await retryIteration(active.id);
      if (!res.success || !res.data) {
        message.error(res.message ?? '重试失败');
        return;
      }
      message.success(res.message ?? '迭代已重新派发');
      // re-dispatch resumes on the concurrency view
      history.push(`/online-code/dispatch?id=${projectId}`);
    } finally {
      setActing(false);
    }
  };

  const handleOptimize = async (values: { feedback: string }) => {
    setOptimizing(true);
    try {
      const res = await optimizeProject({ projectId, feedback: values.feedback });
      if (!res.success || !res.data) {
        message.error(res.message ?? '优化失败');
        return;
      }
      message.success(res.message ?? '已生成新一轮 Spec');
      setOptimizeOpen(false);
      form.resetFields();
      // re-confirm the new Spec version via the existing #6 Spec review flow (F20)
      history.push(`/online-code/spec?id=${res.data.specId}`);
    } finally {
      setOptimizing(false);
    }
  };

  const openHistory = async () => {
    setHistoryOpen(true);
    setSpecsLoading(true);
    try {
      const res = await findSpecsByProject(projectId);
      if (res.success && res.data) setSpecs(res.data);
    } finally {
      setSpecsLoading(false);
    }
  };

  const timelineItems = iterations.map((it) => ({
    key: it.id,
    color: STATE_DOT_COLOR[it.state] ?? 'blue',
    children: (
      <div>
        <div className={styles.itemHead}>
          <span>
            第 {it.round} 轮 · Spec v{it.specVersion}
          </span>
          <LifecycleBadge state={it.state} />
        </div>
        <div className={styles.itemMeta}>
          <div>迭代：{it.id}</div>
          <div>创建：{it.createdDate}</div>
          {it.finishedDate ? <div>结束：{it.finishedDate}</div> : null}
          {it.previewUrl ? (
            <div>
              预览：
              <a href={it.previewUrl} target="_blank" rel="noreferrer">
                {it.previewUrl}
              </a>
            </div>
          ) : null}
        </div>
        {it.feedback ? <div className={styles.feedback}>优化反馈：{it.feedback}</div> : null}
      </div>
    ),
  }));

  if (loading) {
    return (
      <div className={styles.page}>
        <Spin spinning />
      </div>
    );
  }

  if (!project) {
    return (
      <div className={styles.page}>
        <Empty description="项目不存在" />
      </div>
    );
  }

  const canAccept = active?.state === 'PREVIEW';
  const canOptimize = active?.state === 'PREVIEW';
  const canCancel = !!active && !TERMINAL_STATES.includes(active.state);
  const canRetry = active?.state === 'FAILED';

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <BannerTitle title={project.name} subTitle="迭代时间线" />
        <div className={styles.actions}>
          <LifecycleBadge state={project.state} />
          <Popconfirm title="确认验收当前迭代？验收后不可再优化" onConfirm={handleAccept} disabled={!canAccept}>
            <Button type="primary" icon={<CheckOutlined />} loading={acting} disabled={!canAccept}>
              验收
            </Button>
          </Popconfirm>
          <Button
            icon={<ThunderboltOutlined />}
            disabled={!canOptimize}
            onClick={() => setOptimizeOpen(true)}
          >
            优化
          </Button>
          <Popconfirm title="确认取消当前迭代？运行中的任务将级联取消" onConfirm={handleCancel} disabled={!canCancel}>
            <Button danger icon={<CloseOutlined />} loading={acting} disabled={!canCancel}>
              取消
            </Button>
          </Popconfirm>
          <Button icon={<RedoOutlined />} loading={acting} disabled={!canRetry} onClick={handleRetry}>
            重试
          </Button>
          <Button icon={<HistoryOutlined />} onClick={openHistory}>
            Spec 版本
          </Button>
        </div>
      </div>

      <div className={styles.timelineWrap}>
        {timelineItems.length ? (
          <Timeline items={timelineItems} />
        ) : (
          <Empty description="尚无迭代记录" image={<BranchesOutlined />} />
        )}
      </div>

      <ExtModal
        open={optimizeOpen}
        title="优化迭代"
        subTitle="反馈将驱动需求 Agent 增量更新 Spec，生成新一轮版本供评审确认"
        confirmLoading={optimizing}
        onCancel={() => setOptimizeOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} onFinish={handleOptimize} layout="vertical">
          <Form.Item
            name="feedback"
            label="优化反馈"
            rules={[{ required: true, message: '请输入优化反馈' }]}
          >
            <Input.TextArea
              rows={6}
              placeholder="用自然语言描述本轮想要改动的内容，例如：把库存列表加上导出按钮"
              allowClear
            />
          </Form.Item>
        </Form>
      </ExtModal>

      <ExtModal
        open={historyOpen}
        title="Spec 版本历史"
        subTitle={`项目 ${project.name} 的历次 Spec 版本（不可变）`}
        footer={null}
        width={720}
        onCancel={() => setHistoryOpen(false)}
        destroyOnHidden
      >
        <Spin spinning={specsLoading}>
          <Table
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={specs}
            columns={[
              { title: '版本', dataIndex: 'version', width: 90, render: (v: number) => `v${v}` },
              { title: 'Spec ID', dataIndex: 'id', width: 140 },
              {
                title: '状态',
                dataIndex: 'state',
                width: 120,
                render: (state: SpecDto['state']) => (
                  <Tag color={SPEC_STATE_COLOR[state]}>{state}</Tag>
                ),
              },
              { title: '页面数', dataIndex: 'pages', width: 90, render: (p: SpecDto['pages']) => p?.length ?? 0 },
              { title: '创建时间', dataIndex: 'createdDate' },
            ]}
          />
        </Spin>
      </ExtModal>
    </div>
  );
};

export default IterationTimeline;
