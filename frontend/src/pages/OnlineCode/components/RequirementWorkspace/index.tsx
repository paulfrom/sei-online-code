/**
 * Requirement workspace entry component.
 *
 * Displays a single requirement with tabbed panels for PRD, overview design,
 * detailed design, coding tasks and run history.
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { createStyles } from '@ead/antd-style';
import { Button, Tabs, message } from '@ead/suid';
import { ArrowLeftOutlined } from '@ead/suid-icons';
// @ts-ignore JS service module has no declaration file
import { findOneRequirement } from '@/services/requirement';
// @ts-ignore JS service module has no declaration file
import { findOneOverviewDesign } from '@/services/overviewDesign';
// @ts-ignore JS service module has no declaration file
import { findDetailedDesignsByOverview } from '@/services/detailedDesign';
// @ts-ignore JS service module has no declaration file
import { findCodingTasksByPage } from '@/services/codingTask';
// @ts-ignore JS service module has no declaration file
import { findRunsByCodingTask } from '@/services/run';
import type {
  CodingTaskDto,
  DetailedDesignDto,
  OverviewDesignDto,
  RequirementDto,
  RunDto,
} from '@/services/onlineCodeTypes';
import { PageContainer, PageHeader, PageState } from '../PageLayout';
import PrdPanel from './PrdPanel';
import OverviewDesignPanel from './OverviewDesignPanel';
import DetailedDesignPanel from './DetailedDesignPanel';
import CodingTaskPanel from './CodingTaskPanel';
import RunHistoryPanel from './RunHistoryPanel';
import type { ResultData, WorkspaceTab } from './types';

const useStyles = createStyles(() => ({
  tabs: {
    height: '100%',
    '& .ead-tabs-content': { height: '100%' },
    '& .ead-tabs-tabpane': { height: '100%' },
  },
}));

export interface RequirementWorkspaceProps {
  requirementId: string;
  defaultTab?: WorkspaceTab;
}

const RequirementWorkspace: React.FC<RequirementWorkspaceProps> = ({
  requirementId,
  defaultTab = 'prd',
}) => {
  const { styles } = useStyles();
  const [loading, setLoading] = useState(true);
  const [requirement, setRequirement] = useState<RequirementDto | null>(null);
  const [overviewDesign, setOverviewDesign] = useState<OverviewDesignDto | null>(null);
  const [detailedDesigns, setDetailedDesigns] = useState<DetailedDesignDto[]>([]);
  const [codingTasks, setCodingTasks] = useState<CodingTaskDto[]>([]);
  const [runs, setRuns] = useState<RunDto[]>([]);
  const [activeTab, setActiveTab] = useState<WorkspaceTab>(defaultTab);

  const loadAll = useCallback(async () => {
    setLoading(true);
    const reqRes = (await findOneRequirement(requirementId)) as ResultData<RequirementDto>;
    if (!reqRes.success || !reqRes.data) {
      message.error(reqRes.message ?? '加载需求失败');
      setLoading(false);
      return;
    }
    const req = reqRes.data;
    setRequirement(req);

    const ovRes = (await findOneOverviewDesign(requirementId)) as ResultData<OverviewDesignDto>;
    let overview: OverviewDesignDto | null = null;
    if (ovRes.success && ovRes.data) {
      overview = ovRes.data;
      setOverviewDesign(overview);
      const ddRes = (await findDetailedDesignsByOverview(overview.id)) as ResultData<
        DetailedDesignDto[]
      >;
      if (ddRes.success && ddRes.data) {
        setDetailedDesigns(ddRes.data);
      } else {
        setDetailedDesigns([]);
      }
    } else {
      setOverviewDesign(null);
      setDetailedDesigns([]);
    }

    const ctRes = (await findCodingTasksByPage({
      filters: [{ fieldName: 'requirementId', value: requirementId, operator: 'EQ' }],
    })) as ResultData<{ rows: CodingTaskDto[] }>;
    const tasks = ctRes.success && ctRes.data ? ctRes.data.rows || [] : [];
    setCodingTasks(tasks);

    const runLists = await Promise.all(
      tasks.map(async (task) => {
        const res = (await findRunsByCodingTask(task.id)) as ResultData<RunDto[]>;
        return res.success && res.data ? res.data : [];
      }),
    );
    setRuns(runLists.flat().sort((a, b) => b.runNo - a.runNo));

    setLoading(false);
  }, [requirementId]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const handleBack = () => {
    if (requirement?.projectId) {
      history.push(`/online-code/requirements?projectId=${requirement.projectId}`);
    } else {
      history.back();
    }
  };

  const handleViewRuns = useCallback((task: CodingTaskDto) => {
    setActiveTab('runs');
    message.info(`已切换至运行历史：${task.title || task.id}`);
  }, []);

  const tabItems = useMemo(
    () => [
      {
        key: 'prd' as WorkspaceTab,
        label: 'PRD',
        children: requirement ? <PrdPanel requirement={requirement} onRefresh={loadAll} /> : null,
      },
      {
        key: 'overview' as WorkspaceTab,
        label: '概览设计',
        children: requirement ? (
          <OverviewDesignPanel
            requirementId={requirement.id}
            overviewDesign={overviewDesign}
            onRefresh={loadAll}
          />
        ) : null,
      },
      {
        key: 'detailed' as WorkspaceTab,
        label: '详细设计',
        children: <DetailedDesignPanel detailedDesigns={detailedDesigns} onRefresh={loadAll} />,
      },
      {
        key: 'coding' as WorkspaceTab,
        label: '编码任务',
        children: (
          <CodingTaskPanel
            codingTasks={codingTasks}
            onRefresh={loadAll}
            onViewRuns={handleViewRuns}
          />
        ),
      },
      {
        key: 'runs' as WorkspaceTab,
        label: '运行历史',
        children: <RunHistoryPanel runs={runs} onRefresh={loadAll} />,
      },
    ],
    [requirement, overviewDesign, detailedDesigns, codingTasks, runs, loadAll, handleViewRuns],
  );

  if (loading) {
    return (
      <PageContainer>
        <PageHeader title="需求工作区" subTitle="加载中…" />
        <PageState loading />
      </PageContainer>
    );
  }

  if (!requirement) {
    return (
      <PageContainer>
        <PageHeader title="需求工作区" subTitle="错误" />
        <PageState error="需求不存在或加载失败" />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title={requirement.title}
        subTitle="需求工作区"
        actions={
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>
            返回
          </Button>
        }
      />
      <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        <Tabs
          activeKey={activeTab}
          items={tabItems}
          className={styles.tabs}
          onChange={(key) => setActiveTab(key as WorkspaceTab)}
        />
      </div>
    </PageContainer>
  );
};

export default RequirementWorkspace;
