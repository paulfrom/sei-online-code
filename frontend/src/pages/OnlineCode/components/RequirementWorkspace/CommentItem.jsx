/**
 * CommentItem — renders a single RequirementCommentDto per spec §5.2.
 *
 * A `COMMENT_META` map drives the visual treatment (color / label / icon) per
 * `commentType`. Human authors render with a user avatar + name; agent/system
 * authors render with a robot icon plus the agent type label. URLs inside the
 * `content` text are turned into anchors via a tolerant regex pass.
 *
 * Action hooks (all optional, surfaced by the container):
 *  - `EXECUTION_PLAN`  -> `onJumpPlan?.()` (jump to plan tab)
 *  - `DEV_RESULT`      -> `onHighlightTask?.(taskKey)` when `metadataJson`
 *                        parses to an object carrying a `taskKey` field
 *
 * `metadataJson` is parsed defensively (try/catch); a malformed payload is
 * ignored — this component never throws on bad agent output.
 */
import React, { useMemo } from 'react';
import { Avatar, Tag, Typography } from '@ead/suid';
import {
  BranchesOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileDoneOutlined,
  LinkOutlined,
  ProfileOutlined,
  ReloadOutlined,
  RobotOutlined,
  StopOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ead/suid-icons';
import type {
  CommentItemProps,
  RequirementCommentAuthorType,
  RequirementCommentType,
} from './types';

const { Text, Paragraph } = Typography;

type IconType = React.ComponentType<{ style?: React.CSSProperties }>;

interface CommentMeta {
  color: string;
  label: string;
  icon: IconType;
}

const COMMENT_META: Record<RequirementCommentType, CommentMeta> = {
  HUMAN_FEEDBACK:        { color: 'blue',   label: '用户反馈', icon: UserOutlined },
  EXECUTION_PLAN:        { color: 'geekblue',label: '执行计划', icon: ProfileOutlined },
  DEV_RESULT:            { color: 'cyan',   label: '开发结果', icon: FileDoneOutlined },
  VALIDATION_RESULT:     { color: 'purple', label: '验证结果', icon: CheckCircleOutlined },
  ACCEPTANCE:            { color: 'green',  label: '验收',     icon: CheckCircleOutlined },
  REMEDIATION:           { color: 'orange', label: '修复',     icon: ReloadOutlined },
  INTERRUPTION:          { color: 'volcano',label: '中断',     icon: StopOutlined },
  FAILURE:               { color: 'error',  label: '失败',     icon: WarningOutlined },
  MR_CREATED:            { color: 'magenta',label: 'MR 已创建', icon: BranchesOutlined },
  MR_UPDATED:            { color: 'magenta',label: 'MR 已更新', icon: BranchesOutlined },
  MR_FAILED:             { color: 'error',  label: 'MR 失败',  icon: WarningOutlined },
  MEMORY_UPDATED:        { color: 'lime',   label: '记忆已更新', icon: ClockCircleOutlined },
  MEMORY_UPDATE_FAILED:  { color: 'error',  label: '记忆更新失败', icon: WarningOutlined },
  CONTEXT_SUMMARY_FAILED:{ color: 'error',  label: '上下文摘要失败', icon: WarningOutlined },
};

/** Short label rendered next to the author tag for agent/system authors. */
const AUTHOR_LABEL: Record<RequirementCommentAuthorType, string> = {
  HUMAN: '用户',
  PM_AGENT: 'PM',
  FRONTEND_AGENT: '前端',
  BACKEND_AGENT: '后端',
  TEST_AGENT: '测试',
  SYSTEM: '系统',
};

const URL_RE = /https?:\/\/\S+/g;

/**
 * Render plain text content with bare URLs turned into anchors.
 * Minimal implementation per brief: only URLs become links; file paths are
 * left as-is. Returns a React node array so React can key the fragments.
 */
function renderContent(content: string | null | undefined): React.ReactNode {
  if (!content) return null;
  const parts: React.ReactNode[] = [];
  let last = 0;
  URL_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  let i = 0;
  // eslint-disable-next-line no-cond-assign
  while ((m = URL_RE.exec(content)) !== null) {
    if (m.index > last) parts.push(content.slice(last, m.index));
    const url = m[0];
    parts.push(
      <a key={`url-${i}`} href={url} target="_blank" rel="noreferrer">
        {url}
      </a>,
    );
    last = m.index + url.length;
    i += 1;
  }
  if (last < content.length) parts.push(content.slice(last));
  if (parts.length === 0) return content;
  return parts;
}

/** Tolerant JSON.parse for agent-produced metadataJson; returns {} on failure. */
function parseMetadata(metadataJson?: string | null): Record<string, unknown> {
  if (!metadataJson) return {};
  try {
    const parsed = JSON.parse(metadataJson);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

const CommentItem: React.FC<CommentItemProps> = ({
  comment,
  onJumpPlan,
  onHighlightTask,
}) => {
  const meta = COMMENT_META[comment.commentType];
  const metadata = useMemo(() => parseMetadata(comment.metadataJson), [comment.metadataJson]);
  const isHuman = comment.authorType === 'HUMAN';

  const authorLabel = isHuman
    ? (comment.authorName || AUTHOR_LABEL.HUMAN)
    : (comment.authorName || AUTHOR_LABEL[comment.authorType]);

  const Icon = meta.icon;
  const AvatarIcon = isHuman ? <UserOutlined /> : <RobotOutlined />;

  // DEV_RESULT: surface a "view task" action when metadataJson carries a taskKey.
  const devTaskKey = comment.commentType === 'DEV_RESULT'
    ? (typeof metadata.taskKey === 'string' ? (metadata.taskKey as string) : null)
    : null;

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        padding: '6px 0',
      }}
      data-comment-id={comment.id}
    >
      <Avatar size="small" icon={AvatarIcon} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Text strong style={{ fontSize: 12 }}>{authorLabel}</Text>
          <Tag color={meta.color} style={{ marginInline: 0, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <Icon style={{ fontSize: 12 }} />
            {meta.label}
          </Tag>
          {metadata.planVersion != null && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              计划 v{String(metadata.planVersion)}
            </Text>
          )}
        </div>

        {comment.content && (
          <Paragraph
            style={{ marginTop: 4, marginBottom: 0, fontSize: 13, whiteSpace: 'pre-wrap' }}
          >
            {renderContent(comment.content)}
          </Paragraph>
        )}

        {comment.commentType === 'EXECUTION_PLAN' && onJumpPlan && (
          <a
            role="button"
            style={{ fontSize: 12, marginTop: 4, display: 'inline-block' }}
            onClick={(e) => { e.preventDefault(); onJumpPlan(); }}
          >
            <ProfileOutlined style={{ marginRight: 4 }} />
            查看执行计划
          </a>
        )}

        {devTaskKey && onHighlightTask && (
          <a
            role="button"
            style={{ fontSize: 12, marginTop: 4, display: 'inline-block' }}
            onClick={(e) => { e.preventDefault(); onHighlightTask(devTaskKey); }}
          >
            <FileDoneOutlined style={{ marginRight: 4 }} />
            查看任务 {devTaskKey}
          </a>
        )}

        <div style={{ marginTop: 2 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {comment.createdDate}
          </Text>
        </div>
      </div>
    </div>
  );
};

export default React.memo(CommentItem);
export { COMMENT_META };