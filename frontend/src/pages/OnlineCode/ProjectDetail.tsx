/**
 * Project Detail page with tabs for Plan and Feature Design
 */
import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'umi';
import { Tabs, message } from '@ead/suid';
import { findOneProject } from '@/services/onlineCode';
import type { ProjectDto } from '@/services/onlineCode';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';
import PlanTab from './PlanTab';
import FeatureDesignTab from './FeatureDesignTab';
import BuildActions from './BuildActions';

const ProjectDetail: React.FC = () => {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('id') ?? '';
  const [project, setProject] = useState<ProjectDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      const res = await findOneProject(projectId);
      if (!alive) return;
      if (res.success && res.data) {
        setProject(res.data);
      } else {
        message.error(res.message ?? '加载项目失败');
      }
      setLoading(false);
    })();
    return () => {
      alive = false;
    };
  }, [projectId]);

  if (loading) {
    return (
      <PageContainer>
        <PageState loading />
      </PageContainer>
    );
  }

  if (!project) {
    return (
      <PageContainer>
        <PageState error="项目不存在" />
      </PageContainer>
    );
  }

  const tabItems = [
    {
      key: 'plan',
      label: '计划',
      children: <PlanTab projectId={projectId} />,
    },
    {
      key: 'featureDesign',
      label: '功能设计',
      children: <FeatureDesignTab projectId={projectId} />,
    },
  ];

  return (
    <PageContainer>
      <PageHeader
        title={project.name}
        subTitle="项目详情"
        back={{ to: '/online-code/list', text: '返回列表' }}
      />
      <BuildActions projectId={projectId} />
      <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        <Tabs items={tabItems} />
      </div>
    </PageContainer>
  );
};

export default ProjectDetail;
