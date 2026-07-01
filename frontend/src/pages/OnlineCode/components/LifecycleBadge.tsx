/**
 * Lifecycle state badge — renders a contract §4 state token as a colored Tag.
 * Shared by the project list and preview pages (Track F7).
 */
import React from 'react';
import { Tag } from '@ead/suid';
import type { LifecycleState } from '@/services/onlineCode';

/** state → { color, label } map. Colors are antd preset tokens. */
const STATE_META: Record<LifecycleState, { color: string; label: string }> = {
  DRAFTING: { color: 'default', label: '草稿' },
  SPEC_REFINING: { color: 'processing', label: '需求解析中' },
  SPEC_REVIEW: { color: 'gold', label: '待评审' },
  DISPATCHING: { color: 'processing', label: '派发中' },
  DEVELOPING: { color: 'processing', label: '开发中' },
  MERGING: { color: 'processing', label: '合并中' },
  DEPLOYING: { color: 'blue', label: '部署中' },
  PREVIEW: { color: 'success', label: '可预览' },
  ACCEPTED: { color: 'green', label: '已验收' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'default', label: '已取消' },
};

interface Props {
  state: LifecycleState;
}

const LifecycleBadge: React.FC<Props> = ({ state }) => {
  const meta = STATE_META[state] ?? { color: 'default', label: state };
  return <Tag color={meta.color}>{meta.label}</Tag>;
};

export default LifecycleBadge;
