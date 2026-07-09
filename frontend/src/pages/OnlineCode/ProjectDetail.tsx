/**
 * Project detail page with tabs for overview design and feature design.
 */
import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Button, Card, Tabs, message } from '@ead/suid';
import { findOneProject } from '@/services/onlineCode';
import type { ProjectDto, PageResult, ResultData } from '@/services/onlineCode';
import type { RequirementDto } from '@/services/onlineCodeTypes';
// @ts-ignore JS service module has no declaration file
import { findRequirementsByPage } from '@/services/requirement';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';
import RequirementList from './RequirementList';
import CodingTaskTab from './CodingTaskTab';
import ProjectSettingsTab from './ProjectSettingsTab';

// ExtTable 的根元素为 height:100%，依赖祖先链传递确定高度。
const useStyles = createStyles(() => ({
  tabs: {
    height: '100%',
    '& .ead-tabs-content': { height: '100%' },
    '& .ead-tabs-tabpane': { height: '100%' },
  },
}));

const ProjectDetail: React.FC = () => {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('id') ?? '';
  const requestedTab = searchParams.get('tab') ?? 'plan';
  const [project, setProject] = useState<ProjectDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [requirementCount, setRequirementCount] = useState<number | null>(null);
  const { styles } = useStyles();

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

  useEffect(() => {
    let alive = true;
    setRequirementCount(null);
    if (!projectId) return;
    (async () => {
      const res = (await findRequirementsByPage({
        pageInfo: { page: 1, rows: 1 },
        filters: [{ fieldName: 'projectId', value: projectId, operator: 'EQ' }],
      })) as ResultData<PageResult<RequirementDto>>;
      if (!alive) return;
      if (res.success && res.data) {
        setRequirementCount(res.data.records);
      } else {
        message.error(res.message ?? '加载需求数量失败');
      }
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
      key: 'requirements',
      label: '需求',
      children: (
        <RequirementList projectId={projectId} />
      ),
    },
    {
      key: 'codingTasks',
      label: '编码任务',
      children: <CodingTaskTab projectId={projectId} />,
    },
    {
      key: 'settings',
      label: '设置',
      children: <ProjectSettingsTab project={project} />,
    },
  ];
  const activeTab = tabItems.some((item) => item.key === requestedTab) ? requestedTab : 'requirements';

  return (
    <PageContainer>
      <PageHeader
        title={project.name}
        subTitle="项目详情"
        back={{ to: '/online-code/list', text: '返回列表' }}
      />
      <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        <Tabs
          items={tabItems}
          activeKey={activeTab}
          className={styles.tabs}
          onChange={(tab) => history.replace(`/online-code/project?id=${projectId}&tab=${tab}`)}
        />
      </div>
    </PageContainer>
  );
};

export default ProjectDetail;
