/**
 * Project Detail page with tabs for Plan and Feature Design
 */
import React, { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { createStyles } from '@ead/antd-style';
import { BannerTitle, Button, Spin, Tabs, message } from '@ead/suid';
import { ArrowLeftOutlined } from '@ead/suid-icons';
import { findOneProject } from '@/services/onlineCode';
import type { ProjectDto } from '@/services/onlineCode';
import PlanTab from './PlanTab';

const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    gap: ${token.marginSM}px;
  `,
  header: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
  `,
  content: css`
    flex: 1;
    overflow: auto;
  `,
}));

const ProjectDetail: React.FC = () => {
  const { styles } = useStyles();
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
      <div className={styles.page}>
        <Spin spinning />
      </div>
    );
  }

  if (!project) {
    return (
      <div className={styles.page}>
        <div>项目不存在</div>
      </div>
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
      children: <div>功能设计 (待实现)</div>,
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={() => history.push('/online-code/list')}
        >
          返回列表
        </Button>
        <BannerTitle title={project.name} subTitle="项目详情" />
        <div></div>
      </div>
      <div className={styles.content}>
        <Tabs items={tabItems} />
      </div>
    </div>
  );
};

export default ProjectDetail;
