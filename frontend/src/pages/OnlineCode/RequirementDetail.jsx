/**
 * Requirement detail page with step layout.
 */
import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'umi';
import { Button, Card, Form, Input, message, Steps, theme } from '@ead/suid';
import { findOneRequirement, regeneratePrd, editPrd, confirmPrd } from '@/services/requirement';
import { findOneOverviewDesign, confirmOverviewDesign } from '@/services/overviewDesign';
import { findDetailedDesignsByOverview, batchConfirmDetailedDesign } from '@/services/detailedDesign';
import { findCodingTasksByPage, runCodingTask } from '@/services/codingTask';
import { findRunsByCodingTask } from '@/services/run';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';

const RequirementDetail = () => {
  const [searchParams] = useSearchParams();
  const requirementId = searchParams.get('id') ?? '';
  const [requirement, setRequirement] = useState(null);
  const [loading, setLoading] = useState(true);
  const [overview, setOverview] = useState(null);
  const [detailedDesigns, setDetailedDesigns] = useState([]);
  const [codingTasks, setCodingTasks] = useState([]);
  const [runs, setRuns] = useState([]);
  const { token } = theme.useToken();

  const loadAll = async () => {
    setLoading(true);
    const reqRes = await findOneRequirement(requirementId);
    if (!reqRes.success || !reqRes.data) {
      message.error(reqRes.message ?? '加载需求失败');
      setLoading(false);
      return;
    }
    setRequirement(reqRes.data);

    const ovRes = await findOneOverviewDesign(requirementId);
    if (ovRes.success && ovRes.data) {
      setOverview(ovRes.data);
      const ddRes = await findDetailedDesignsByOverview(ovRes.data.id);
      if (ddRes.success && ddRes.data) {
        setDetailedDesigns(ddRes.data);
      }
    } else {
      setOverview(null);
      setDetailedDesigns([]);
    }

    const ctRes = await findCodingTasksByPage({
      filters: [{ fieldName: 'requirementId', value: requirementId, operator: 'EQ' }],
    });
    if (ctRes.success && ctRes.data) {
      setCodingTasks(ctRes.data.rows || []);
    }

    setLoading(false);
  };

  useEffect(() => {
    loadAll();
  }, [requirementId]);

  const loadRuns = async (codingTaskId) => {
    const res = await findRunsByCodingTask(codingTaskId);
    if (res.success && res.data) {
      setRuns(res.data);
    }
  };

  const handleRegeneratePrd = async () => {
    const prompt = window.prompt('请输入重生成提示词');
    if (prompt === null) return;
    const res = await regeneratePrd(requirementId, prompt);
    if (res.success) {
      message.success('PRD 重生成已启动');
      loadAll();
    } else {
      message.error(res.message ?? '重生成失败');
    }
  };

  const handleConfirmPrd = async () => {
    const res = await confirmPrd(requirementId);
    if (res.success) {
      message.success('PRD 已确认');
      loadAll();
    } else {
      message.error(res.message ?? '确认失败');
    }
  };

  const handleEditPrd = async () => {
    const content = window.prompt('请输入 PRD 内容 JSON');
    if (content === null) return;
    const res = await editPrd(requirementId, content);
    if (res.success) {
      message.success('PRD 已更新');
      loadAll();
    } else {
      message.error(res.message ?? '更新失败');
    }
  };

  const handleConfirmOverview = async () => {
    if (!overview) return;
    const res = await confirmOverviewDesign(overview.id);
    if (res.success) {
      message.success('概览设计已确认');
      loadAll();
    } else {
      message.error(res.message ?? '确认失败');
    }
  };

  const handleBatchConfirmDetailedDesigns = async () => {
    const reviewIds = detailedDesigns
      .filter((d) => d.status === 'REVIEW')
      .map((d) => d.id);
    if (reviewIds.length === 0) {
      message.info('没有可确认的详细设计');
      return;
    }
    const res = await batchConfirmDetailedDesign(reviewIds);
    if (res.success) {
      message.success(`已确认 ${reviewIds.length} 条详细设计`);
      loadAll();
    } else {
      message.error(res.message ?? '批量确认失败');
    }
  };

  const handleRunTask = async (task) => {
    const res = await runCodingTask(task.id, null);
    if (res.success) {
      message.success('运行已启动');
      loadAll();
    } else {
      message.error(res.message ?? '运行失败');
    }
  };

  const getCurrentStep = () => {
    if (!requirement) return 0;
    if (requirement.status === 'PRD_GENERATING' || requirement.status === 'PRD_REVIEW') return 0;
    if (!overview || overview.status !== 'CONFIRMED') return 1;
    if (!detailedDesigns.some((d) => d.status === 'CONFIRMED')) return 2;
    return 3;
  };

  if (loading) {
    return (
      <PageContainer>
        <PageState loading />
      </PageContainer>
    );
  }

  if (!requirement) {
    return (
      <PageContainer>
        <PageState error="需求不存在" />
      </PageContainer>
    );
  }

  const steps = [
    { title: 'PRD', key: 'prd' },
    { title: '概览设计', key: 'overview' },
    { title: '详细设计', key: 'detailed' },
    { title: '编码任务', key: 'coding' },
    { title: '运行历史', key: 'runs' },
  ];

  return (
    <PageContainer>
      <PageHeader title={requirement.title} subTitle="需求详情" />
      <Steps current={getCurrentStep()} items={steps} style={{ marginBottom: 24 }} />

      <Card title="PRD" style={{ marginBottom: 16 }}>
        <p>状态：{requirement.status}</p>
        <pre style={{ maxHeight: 300, overflow: 'auto', background: token.colorFillTertiary, padding: 12 }}>
          {requirement.prdContent || '暂无内容'}
        </pre>
        {requirement.status === 'PRD_REVIEW' && (
          <>
            <Button onClick={handleEditPrd}>编辑</Button>
            <Button onClick={handleRegeneratePrd} style={{ marginLeft: 8 }}>重生成</Button>
            <Button type="primary" onClick={handleConfirmPrd} style={{ marginLeft: 8 }}>确认</Button>
          </>
        )}
      </Card>

      <Card title="概览设计" style={{ marginBottom: 16 }}>
        {overview ? (
          <>
            <p>状态：{overview.status}</p>
            <pre style={{ maxHeight: 300, overflow: 'auto', background: token.colorFillTertiary, padding: 12 }}>
              {overview.content || '暂无内容'}
            </pre>
            {overview.status === 'DRAFT' && (
              <Button type="primary" onClick={handleConfirmOverview}>确认</Button>
            )}
          </>
        ) : (
          <p>概览设计尚未生成</p>
        )}
      </Card>

      <Card title="详细设计" style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={handleBatchConfirmDetailedDesigns} style={{ marginBottom: 12 }}>
          批量确认
        </Button>
        {detailedDesigns.map((d) => (
          <div key={d.id} style={{ borderBottom: `1px solid ${token.colorBorderSecondary}`, padding: '8px 0' }}>
            <strong>{d.featureTitle || d.featureId}</strong> - {d.status}
            {d.status === 'REVIEW' && (
              <Button type="link" size="small">确认</Button>
            )}
          </div>
        ))}
      </Card>

      <Card title="编码任务" style={{ marginBottom: 16 }}>
        {codingTasks.map((t) => (
          <div key={t.id} style={{ borderBottom: `1px solid ${token.colorBorderSecondary}`, padding: '8px 0' }}>
            <strong>{t.title || t.id}</strong> - {t.status}
            {t.status === 'PENDING' && (
              <Button type="link" size="small" onClick={() => handleRunTask(t)}>运行</Button>
            )}
            <Button type="link" size="small" onClick={() => loadRuns(t.id)}>查看运行历史</Button>
          </div>
        ))}
      </Card>

      <Card title="运行历史">
        {runs.map((r) => (
          <div key={r.id} style={{ borderBottom: `1px solid ${token.colorBorderSecondary}`, padding: '8px 0' }}>
            Run #{r.runNo} - {r.state}
            {r.failureReason && <pre>{r.failureReason}</pre>}
          </div>
        ))}
      </Card>
    </PageContainer>
  );
};

export default RequirementDetail;
