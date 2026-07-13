/**
 * ExecutionPlan tab: shows plan goal, risks, validation and a task table.
 */
import React, { useMemo } from 'react';
import { createStyles } from '@ead/antd-style';
import { Card, Collapse, Descriptions, Space, Table, Tag } from '@ead/suid';
import { parsePlanJson } from './parsePlanJson';

const useStyles = createStyles(({ token, css }) => ({
  card: css`
    margin-bottom: ${token.marginMD}px;
  `,
  goal: css`
    font-weight: ${token.fontWeightStrong};
    margin-bottom: ${token.marginSM}px;
  `,
  risk: css`
    margin: 0;
    padding: ${token.paddingXS}px 0;
  `,
}));

const TASK_STATUS_META = {
  PENDING: { color: 'default', label: '待执行' },
  RUNNING: { color: 'processing', label: '执行中' },
  SUCCEEDED: { color: 'green', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  VALIDATION_FAILED: { color: 'red', label: '验证失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
  STALE: { color: 'default', label: '已过期' },
  BLOCKED: { color: 'orange', label: '阻塞' },
};

/**
 * @param {{
 *   plan?: (ExecutionPlanDto & { planJson?: string | null }) | null,
 *   tasks: any[],
 *   onJumpTask?: (taskKey: string) => void,
 * }} props
 */
const ExecutionPlanTab = ({ plan, tasks, onJumpTask }) => {
  const { styles } = useStyles();
  const parsed = useMemo(() => parsePlanJson(plan && plan.planJson), [plan && plan.planJson]);

  const taskStatusByKey = useMemo(() => {
    const map = new Map();
    tasks.forEach((t) => {
      if (t.planTaskKey) {
        map.set(t.planTaskKey, t.status);
      }
    });
    return map;
  }, [tasks]);

  const planTypeLabel = (plan && plan.planType) || '-';
  const planStatusLabel = (plan && plan.status) || '-';

  const columns = [
    { title: 'Key', dataIndex: 'taskKey', width: 110 },
    { title: '标题', dataIndex: 'title' },
    { title: 'Agent', dataIndex: 'agent', width: 120 },
    { title: 'Area', dataIndex: 'area', width: 100 },
    {
      title: '状态',
      dataIndex: 'taskKey',
      width: 120,
      render: (taskKey) => {
        const status = taskStatusByKey.get(taskKey);
        const meta = status ? TASK_STATUS_META[status] : { color: 'default', label: '未创建' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '操作',
      dataIndex: 'taskKey',
      width: 100,
      render: (taskKey) => (
        <a
          role="button"
          tabIndex={0}
          onClick={() => onJumpTask && onJumpTask(taskKey)}
          onKeyDown={(e) => e.key === 'Enter' && onJumpTask && onJumpTask(taskKey)}
        >
          查看任务
        </a>
      ),
    },
  ];

  const expandedRowRender = (task) => (
    <Descriptions column={1} size="small">
      {task.description && <Descriptions.Item label="描述">{task.description}</Descriptions.Item>}
      {task.dependsOn && task.dependsOn.length > 0 && (
        <Descriptions.Item label="依赖">{task.dependsOn.join(', ')}</Descriptions.Item>
      )}
      {task.fileScope && task.fileScope.length > 0 && (
        <Descriptions.Item label="文件范围">{task.fileScope.join('\n')}</Descriptions.Item>
      )}
      {task.acceptanceCriteria && task.acceptanceCriteria.length > 0 && (
        <Descriptions.Item label="验收标准">{task.acceptanceCriteria.join('\n')}</Descriptions.Item>
      )}
    </Descriptions>
  );

  return (
    <div>
      <Card className={styles.card} size="small">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div className={styles.goal}>目标：{parsed.goal || '-'}</div>
          <Space>
            <Tag>类型：{planTypeLabel}</Tag>
            <Tag color="processing">{planStatusLabel}</Tag>
          </Space>
          {parsed.risks.length > 0 && (
            <div>
              <strong>风险：</strong>
              {parsed.risks.map((risk, i) => (
                <p key={i} className={styles.risk}>• {risk}</p>
              ))}
            </div>
          )}
          {parsed.validation && (
            <Collapse
              ghost
              items={[
                {
                  key: 'validation',
                  label: '验证命令',
                  children: <pre>{parsed.validation}</pre>,
                },
              ]}
            />
          )}
        </Space>
      </Card>

      <Table
        rowKey="taskKey"
        size="small"
        pagination={false}
        dataSource={parsed.tasks}
        columns={columns}
        expandable={{ expandedRowRender }}
        locale={{ emptyText: '暂无计划任务' }}
      />
    </div>
  );
};

export default ExecutionPlanTab;
