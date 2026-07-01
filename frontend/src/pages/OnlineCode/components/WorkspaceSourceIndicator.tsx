/**
 * Track F25 — Workspace-source indicator.
 * Resolves a project's workspace dir via ep #33 and shows the resolved path plus
 * a CLONE/SCAFFOLD source Tag. CLONE = a template GitLab URL is configured (clone
 * -once from it); SCAFFOLD = no URL → the canonical SUID stack is generated.
 * `provisioned=true` marks clone-once reuse (the dir already existed).
 */
import React, { useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Spin, Tag } from '@ead/suid';
import { FolderOpenOutlined } from '@ead/suid-icons';
import { resolveWorkspace } from '@/services/onlineCode';
import type { WorkspaceResolveResult, WorkspaceSource } from '@/services/onlineCode';

const useStyles = createStyles(({ token, css }) => ({
  panel: css`
    display: flex;
    align-items: center;
    gap: ${token.marginXS}px;
    padding: ${token.paddingXS}px ${token.paddingSM}px;
    background: ${token.colorBgContainerDisabled};
    border-radius: ${token.borderRadius}px;
    font-size: ${token.fontSizeSM}px;
  `,
  path: css`
    font-family: ${token.fontFamilyCode};
    color: ${token.colorTextSecondary};
    word-break: break-all;
  `,
  label: css`
    color: ${token.colorTextSecondary};
  `,
}));

/** source → { color, label } — CLONE clones the template, SCAFFOLD generates. */
const SOURCE_META: Record<WorkspaceSource, { color: string; label: string }> = {
  CLONE: { color: 'blue', label: '模板克隆' },
  SCAFFOLD: { color: 'green', label: '脚手架生成' },
};

interface Props {
  projectId: string;
}

const WorkspaceSourceIndicator: React.FC<Props> = ({ projectId }) => {
  const { styles } = useStyles();
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<WorkspaceResolveResult | null>(null);

  useEffect(() => {
    let alive = true;
    if (!projectId) {
      setLoading(false);
      return () => {
        alive = false;
      };
    }
    setLoading(true);
    (async () => {
      const res = await resolveWorkspace(projectId);
      if (!alive) return;
      if (res.success && res.data) setResult(res.data);
      setLoading(false);
    })();
    return () => {
      alive = false;
    };
  }, [projectId]);

  if (loading) {
    return (
      <div className={styles.panel}>
        <Spin size="small" />
        <span className={styles.label}>解析工作区…</span>
      </div>
    );
  }

  if (!result) return null;

  const meta = SOURCE_META[result.source] ?? { color: 'default', label: result.source };
  return (
    <div className={styles.panel}>
      <FolderOpenOutlined />
      <span className={styles.path}>{result.path}</span>
      <Tag color={meta.color}>{meta.label}</Tag>
      <Tag color={result.provisioned ? 'default' : 'processing'}>
        {result.provisioned ? '复用' : '新建'}
      </Tag>
    </div>
  );
};

export default WorkspaceSourceIndicator;
