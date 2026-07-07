/**
 * Project detail page with tabs for overview design and feature design.
 */
import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { Tabs, message } from '@ead/suid';
import { findOneProject } from '@/services/onlineCode';
import type { ProjectDto } from '@/services/onlineCode';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';
import PlanTab from './PlanTab';
import DetailedDesignTab from './DetailedDesignTab';
import FeatureDesignTab from './FeatureDesignTab';
import BuildActions from './BuildActions';

const ProjectDetail: React.FC = () => {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('id') ?? '';
  const requestedTab = searchParams.get('tab') ?? 'plan';
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
      label: '概要设计',
      children: <PlanTab projectId={projectId} />,
    },
    {
      key: 'detailedDesign',
      label: '详细设计',
      children: <DetailedDesignTab projectId={projectId} />,
    },
    {
      key: 'featureDesign',
      label: '功能设计',
      children: <FeatureDesignTab projectId={projectId} />,
    },
  ];
  const activeTab = tabItems.some((item) => item.key === requestedTab) ? requestedTab : 'plan';

  return (
    <PageContainer>
      <PageHeader
        title={project.name}
        subTitle="项目详情"
        back={{ to: '/online-code/list', text: '返回列表' }}
      />
      <BuildActions projectId={projectId} />
      <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        <Tabs
          items={tabItems}
          activeKey={activeTab}
          onChange={(tab) => history.replace(`/online-code/project?id=${projectId}&tab=${tab}`)}
        />
      </div>
    </PageContainer>
  );
};

export default ProjectDetail;
