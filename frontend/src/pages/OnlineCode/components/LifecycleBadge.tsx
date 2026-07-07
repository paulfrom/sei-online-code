/**
 * Lifecycle state badge — renders a contract §4 state token as a colored Tag.
 * Shared by the project list and current pre-build pages.
 */
import React from 'react';
import { Tag } from '@ead/suid';
import type { LifecycleState } from '@/services/onlineCode';

/** state → { color, label } map. Colors are antd preset tokens. */
const STATE_META: Partial<Record<LifecycleState, { color: string; label: string }>> = {
  DRAFTING: { color: 'default', label: '草稿' },
  SPEC_REFINING: { color: 'processing', label: '概要设计生成中' },
  SPEC_REVIEW: { color: 'gold', label: '详细设计待确认' },
  PLANNING: { color: 'processing', label: '概要设计中' },
  DESIGNING: { color: 'blue', label: '设计中' },
  READY_TO_BUILD: { color: 'green', label: '可编码执行' },
  FAILED: { color: 'error', label: '失败' },
};

interface Props {
  state: LifecycleState;
}

const LifecycleBadge: React.FC<Props> = ({ state }) => {
  const meta = STATE_META[state] ?? { color: 'default', label: state };
  return <Tag color={meta.color}>{meta.label}</Tag>;
};

export default LifecycleBadge;
