/**
 * Module detailed design list for a project.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { history } from 'umi';
import { Button, Empty, message, Space, Spin, Table, Tag } from '@ead/suid';
import { findDetailedDesignsByProject } from '@/services/onlineCode';
import type { SpecDto } from '@/services/onlineCode';

interface DetailedDesignTabProps {
  projectId: string;
}

const STATE_META: Record<SpecDto['state'], { color: string; label: string }> = {
  GENERATING: { color: 'processing', label: '生成中' },
  DRAFT: { color: 'default', label: '草稿' },
  SPEC_REVIEW: { color: 'gold', label: '待确认' },
  CONFIRMED: { color: 'green', label: '已确认' },
  FAILED: { color: 'error', label: '失败' },
};

const DetailedDesignTab: React.FC<DetailedDesignTabProps> = ({ projectId }) => {
  const [loading, setLoading] = useState(true);
  const [designs, setDesigns] = useState<SpecDto[]>([]);

  const fetchDesigns = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const res = await findDetailedDesignsByProject(projectId);
      if (res.success && res.data) {
        setDesigns(res.data);
      } else if (!silent) {
        message.error(res.message ?? '加载详细设计失败');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchDesigns();
  }, [fetchDesigns]);

  const hasGenerating = designs.some((item) => item.state === 'GENERATING');
  useEffect(() => {
    if (!hasGenerating) return;
    const timer = setInterval(() => fetchDesigns(true), 5000);
    return () => clearInterval(timer);
  }, [fetchDesigns, hasGenerating]);

  if (loading) {
    return <Spin spinning />;
  }

  if (!designs.length) {
    return <Empty description="暂无模块详细设计，请先确认概要设计" />;
  }

  return (
    <Table
      rowKey="id"
      size="small"
      pagination={false}
      dataSource={designs}
      columns={[
        {
          title: '模块',
          dataIndex: 'moduleTitle',
          render: (value: string | null, record: SpecDto) => value || record.moduleId || '默认模块',
        },
        { title: '概要', dataIndex: 'moduleSummary' },
        { title: '版本', dataIndex: 'version', width: 90 },
        {
          title: '状态',
          dataIndex: 'state',
          width: 110,
          render: (state: SpecDto['state']) => {
            const meta = STATE_META[state] ?? { color: 'default', label: state };
            return <Tag color={meta.color}>{meta.label}</Tag>;
          },
        },
        {
          title: '失败摘要',
          dataIndex: 'failureSummary',
          width: 260,
          render: (value: string | null | undefined, record: SpecDto) =>
            value || (record.retryCount ? `已重试 ${record.retryCount} 次` : '-'),
        },
        {
          title: '操作',
          dataIndex: 'id',
          width: 120,
          render: (id: string) => (
            <Space>
              <Button type="link" onClick={() => history.push(`/online-code/spec?id=${id}`)}>
                查看
              </Button>
            </Space>
          ),
        },
      ]}
    />
  );
};

export default DetailedDesignTab;
