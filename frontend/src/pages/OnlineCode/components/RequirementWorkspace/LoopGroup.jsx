/**
 * LoopGroup — collapsible wrapper for all comments belonging to one PRD loop
 * (spec §5.1).
 *
 * The group is a single-panel `Collapse` that defaults to expanded when
 * `active`, collapsed otherwise. Its header shows:
 *   - `loopId` short code (first 8 chars)
 *   - earliest `createdDate` within the loop
 *   - `planVersion` (when provided via props)
 *   - inferred final status, scanned from this loop's `commentType`s:
 *       ACCEPTANCE                      -> 已验收
 *       FAILURE / MR_FAILED             -> 失败
 *       INTERRUPTION                   -> 已中断
 *       otherwise (active=true)        -> 进行中
 *       otherwise (active=false)       -> 历史
 *
 * Comments are sorted ascending by `createdDate` then rendered with
 * `CommentItem`. `onJumpPlan` / `onHighlightTask` are forwarded transparently.
 */
import React, { useMemo } from 'react';
import { Collapse, Tag } from '@ead/suid';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  HistoryOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ead/suid-icons';
import type { LoopGroupProps, RequirementCommentDto } from './types';
import CommentItem from './CommentItem';

interface LoopStatusMeta {
  color: string;
  label: string;
  icon: React.ComponentType<{ style?: React.CSSProperties }>;
}

/**
 * Infer the terminal status of a loop from its comment types.
 * Precedence: 验收 > 失败 > 中断 > (active ? 进行中 : 历史). The first
 * matching signal wins so a loop that both failed and was later accepted
 * reports as 验收.
 */
function inferStatus(comments: RequirementCommentDto[], active: boolean): LoopStatusMeta {
  const has = (t: RequirementCommentDto['commentType']) =>
    comments.some((c) => c.commentType === t);
  if (has('ACCEPTANCE')) {
    return { color: 'green', label: '已验收', icon: CheckCircleOutlined };
  }
  if (has('FAILURE') || has('MR_FAILED')) {
    return { color: 'error', label: '失败', icon: WarningOutlined };
  }
  if (has('INTERRUPTION')) {
    return { color: 'volcano', label: '已中断', icon: StopOutlined };
  }
  if (active) {
    return { color: 'processing', label: '进行中', icon: ClockCircleOutlined };
  }
  return { color: 'default', label: '历史', icon: HistoryOutlined };
}

const PANEL_KEY = 'loop';

const LoopGroup: React.FC<LoopGroupProps> = ({
  loopId,
  comments,
  active,
  planVersion,
  onJumpPlan,
  onHighlightTask,
}) => {
  const sorted = useMemo(
    () => [...comments].sort((a, b) => a.createdDate.localeCompare(b.createdDate)),
    [comments],
  );

  const status = useMemo(() => inferStatus(sorted, active), [sorted, active]);

  const earliest = sorted.length ? sorted[0].createdDate : null;
  const shortLoopId = loopId ? loopId.slice(0, 8) : '—';

  const StatusIcon = status.icon;

  const header = (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
      <span style={{ fontFamily: 'monospace', fontSize: 12 }}>#{shortLoopId}</span>
      {earliest && (
        <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>{earliest}</span>
      )}
      {planVersion != null && (
        <Tag color="geekblue" style={{ marginInline: 0 }}>计划 v{planVersion}</Tag>
      )}
      <Tag color={status.color} style={{ marginInline: 0, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
        <StatusIcon style={{ fontSize: 12 }} />
        {status.label}
      </Tag>
    </div>
  );

  return (
    <Collapse
      defaultActiveKey={active ? [PANEL_KEY] : []}
      items={[{ key: PANEL_KEY, label: header, children: (
        <div>
          {sorted.length === 0 && (
            <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12, padding: '4px 0' }}>
              暂无评论
            </div>
          )}
          {sorted.map((c) => (
            <CommentItem
              key={c.id}
              comment={c}
              onJumpPlan={onJumpPlan}
              onHighlightTask={onHighlightTask}
            />
          ))}
        </div>
      ) }]}
    />
  );
};

export default React.memo(LoopGroup);