/**
 * Project memory tab: shows current WorkspaceMemory state, a short summary, and MemoryJob list.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Button, Card, List, Tag, message } from '@ead/suid';
import * as memoryWorkspace from '@/services/memoryWorkspace';
import * as memoryJob from '@/services/memoryJob';

const useStyles = createStyles(() => ({
  container: {
    padding: 16,
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
  },
  actions: {
    display: 'flex',
    gap: 8,
  },
  empty: {
    color: 'rgba(0, 0, 0, 0.45)',
  },
}));

/** @param {{ projectId: string }} props */
const parseJson = (json) => {
  if (!json) return null;
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
};

const ProjectMemoryTab = ({ projectId }) => {
  const { styles } = useStyles();

  const renderClaimList = (items) => {
    if (!items || items.length === 0) return <span className={styles.empty}>无</span>;
    return (
      <ul style={{ paddingLeft: 16, margin: 0 }}>
        {items.slice(0, 5).map((item, idx) => (
          <li key={idx}>
            {item.priority && <Tag>{item.priority}</Tag>}
            {item.content ? item.content.substring(0, 120) : item}
          </li>
        ))}
        {items.length > 5 && <li className={styles.empty}>…等 {items.length} 条</li>}
      </ul>
    );
  };

  const [loading, setLoading] = useState(false);
  const [memory, setMemory] = useState(null);
  const [jobList, setJobList] = useState([]);
  const [actionLoading, setActionLoading] = useState(false);

  const isMock = process.env.MOCK === 'yes';

  const load = useCallback(async () => {
    if (isMock) {
      return;
    }
    setLoading(true);
    try {
      const [currentRes, jobsRes] = await Promise.all([
        memoryWorkspace.current(projectId),
        memoryJob.findByProject(projectId),
      ]);
      if (currentRes.success) {
        setMemory(currentRes.data);
      } else {
        message.error(currentRes.message ?? '加载工作区记忆失败');
      }
      if (jobsRes.success) {
        setJobList(jobsRes.data || []);
      }
    } finally {
      setLoading(false);
    }
  }, [projectId, isMock]);

  useEffect(() => {
    load();
  }, [load]);

  const handleInitialize = async () => {
    setActionLoading(true);
    try {
      const res = await memoryWorkspace.initialize(projectId);
      if (res.success) {
        message.success('已投递初始化任务');
        await load();
      } else {
        message.error(res.message ?? '初始化失败');
      }
    } finally {
      setActionLoading(false);
    }
  };

  const handleRebuild = async () => {
    setActionLoading(true);
    try {
      const res = await memoryWorkspace.rebuild(projectId);
      if (res.success) {
        message.success('已投递重建任务');
        await load();
      } else {
        message.error(res.message ?? '重建失败');
      }
    } finally {
      setActionLoading(false);
    }
  };

  const handleRetry = async (jobId) => {
    setActionLoading(true);
    try {
      const res = await memoryJob.retry(jobId);
      if (res.success) {
        message.success('已重试');
        await load();
      } else {
        message.error(res.message ?? '重试失败');
      }
    } finally {
      setActionLoading(false);
    }
  };

  if (isMock) {
    return (
      <div className={styles.container}>
        <Card title="项目记忆">
          当前处于 MOCK 模式，项目记忆能力依赖后端服务，请在真实后台开发/联调模式下查看。
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Card
        title="当前工作区记忆"
        extra={
          <div className={styles.actions}>
            <Button loading={actionLoading} onClick={handleInitialize}>初始化</Button>
            <Button loading={actionLoading} onClick={handleRebuild}>刷新</Button>
            <Button loading={loading} onClick={load}>刷新状态</Button>
          </div>
        }
        loading={loading}
      >
        {memory ? (
          <>
            <p>版本：{memory.version} <Tag>{memory.status}</Tag> <Tag>{memory.freshness}</Tag></p>
            <p>规范版本：{memory.memorySpecVersion}</p>
            <p>生成时间：{memory.generatedAt}</p>
            <p>agent-memory 指纹：{memory.agentMemoryFingerprint || '-'}</p>
            <p>项目规则指纹：{memory.projectRuleFingerprint || '-'}</p>

            {(() => {
              const norms = parseJson(memory.workspaceNormsJson);
              const snapshot = parseJson(memory.workspaceSnapshotJson);
              const conflicts = parseJson(memory.conflictFindingsJson);
              const normClaims = parseJson(memory.normClaimsJson) || [];
              const generatedSuggestions = normClaims.filter(
                (claim) => claim.type === 'generated_suggestion',
              );
              const codingTaskUpdate = snapshot?.scanLimits;
              return (
                <>
                  <h4>规范摘要</h4>
                  <div>
                    <strong>Hard Rules：</strong>
                    {renderClaimList(norms?.hardRules)}
                  </div>
                  <div>
                    <strong>Forbidden Choices：</strong>
                    {renderClaimList(norms?.forbiddenChoices)}
                  </div>

                  <h4>快照摘要</h4>
                  <div>
                    <strong>模块：</strong>
                    {snapshot?.modules?.length ? snapshot.modules.slice(0, 10).join('、') : '-'}
                  </div>
                  <div>
                    <strong>扫描范围：</strong>
                    {snapshot?.scanLimits
                      ? `文件 ${snapshot.scanLimits.scannedFiles || 0} / ${snapshot.scanLimits.maxFiles || 200}`
                      : '-'}
                  </div>

                  <h4>冲突摘要</h4>
                  <div>
                    {conflicts?.length ? (
                      <>
                        <Tag color="red">{conflicts.length} 个冲突</Tag>
                        <ul style={{ paddingLeft: 16, margin: 0 }}>
                          {conflicts.slice(0, 5).map((c, idx) => (
                            <li key={idx}>
                              [{c.severity}] {c.summary?.substring(0, 120)}
                            </li>
                          ))}
                        </ul>
                      </>
                    ) : (
                      <span className={styles.empty}>无冲突</span>
                    )}
                  </div>

                  <h4>Generated Suggestions</h4>
                  {renderClaimList(generatedSuggestions)}

                  <h4>CodingTask Updates</h4>
                  {codingTaskUpdate?.codingTaskHeadCommit ? (
                    <div>
                      <div>变更文件数：{codingTaskUpdate.codingTaskChangedFiles || 0}</div>
                      <div>Base Commit：{codingTaskUpdate.codingTaskBaseCommit || '-'}</div>
                      <div>Head Commit：{codingTaskUpdate.codingTaskHeadCommit}</div>
                    </div>
                  ) : (
                    <span className={styles.empty}>暂无 CodingTask 回写记录</span>
                  )}
                </>
              );
            })()}
          </>
        ) : (
          <p className={styles.empty}>暂无工作区记忆，点击“初始化”开始扫描。</p>
        )}
      </Card>

      <Card title="记忆任务" loading={loading}>
        {(() => {
          const latestUpdate = jobList.find(
            (job) => job.jobType === 'MEMORY_UPDATE_AFTER_CODING_TASK',
          );
          return latestUpdate ? (
            <p>
              最近回写：<Tag>{latestUpdate.status}</Tag>
              {latestUpdate.finishedAt || latestUpdate.createdDate || ''}
            </p>
          ) : null;
        })()}
        <List
          dataSource={jobList}
          renderItem={(job) => (
            <List.Item
              actions={
                job.status === 'FAILED'
                  ? [
                      <Button key="retry" size="small" onClick={() => handleRetry(job.id)}>
                        重试
                      </Button>,
                    ]
                  : []
              }
            >
              <List.Item.Meta
                title={`${job.jobType} · ${job.status}`}
                description={
                  <>
                    <div>触发来源：{job.triggerSource}</div>
                    {job.failureSummary && <div style={{ color: '#cf1322' }}>{job.failureSummary}</div>}
                  </>
                }
              />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
};

export default ProjectMemoryTab;
