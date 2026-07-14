/**
 * CommentComposer — bottom-of-stream input area (spec §5.3).
 *
 * Markdown editor with an interrupt-aware send button. The composer decides
 * the warning surface and interaction mode from `requirement.automationStatus`
 * (active automation) vs `requirement.status` (delivered):
 *
 *  - active automation (PLANNING/DEVELOPING/VALIDATING/ACCEPTING)
 *      → Alert 「发送评论将中断当前自动化并触发 PM 重规划」
 *      → Button `danger`, Modal.confirm 二次确认 → onSend
 *  - automationStatus === 'COMPLETED'
 *      → Alert 「当前需求已交付，提交评论将基于现有 MR / 分支创建变更请求 loop」
 *      → Button `danger`, Modal.confirm 二次确认 (创建变更) → onSend
 *  - otherwise (IDLE/WAITING_HUMAN/INTERRUPTED/FAILED/PRD_REVIEW/...)
 *      → no Alert, Button `primary`, direct onSend
 *
 * `INTERRUPT_WARNING` maps each automation mode to its user-facing copy. The
 * generic active-warning copy is shared across the four active modes; the
 * delivered branch uses a dedicated copy that is also keyed off `automationStatus`.
 */
import React, { useState } from 'react';
import { Alert, Button, Modal } from '@ead/suid';
import {
  ExclamationCircleOutlined,
  SendOutlined,
} from '@ead/suid-icons';
import MarkdownEditor from '../MarkdownEditor';

/**
 * User-facing warning copy keyed off `requirement.automationStatus` (the four
 * active automation stages plus the delivered `COMPLETED` state). Each entry is
 * the message shown in the Alert strip and quoted inside the Modal.confirm body
 * above the send button.
 *
 * The four active automation stages share the same interruption copy: PM
 * replanning consumes the comment regardless of which sub-stage was running,
 * so the text does not vary across them. The `COMPLETED` slot uses the change-request
 * copy and is gated together with the active states through a single lookup.
 */
const INTERRUPT_WARNING = {
  PLANNING: '发送评论将中断当前自动化并触发 PM 重规划',
  DEVELOPING: '发送评论将中断当前自动化并触发 PM 重规划',
  VALIDATING: '发送评论将中断当前自动化并触发 PM 重规划',
  ACCEPTING: '发送评论将中断当前自动化并触发 PM 重规划',
  COMPLETED: '当前需求已交付，提交评论将基于现有 MR / 分支创建变更请求 loop',
};

/** Warning copy used when `requirement.automationStatus === 'COMPLETED'`. */
const COMPLETED_WARNING = INTERRUPT_WARNING.COMPLETED;

const ACTIVE_AUTOMATION = [
  'PLANNING',
  'DEVELOPING',
  'VALIDATING',
  'ACCEPTING',
];

/**
 * Resolve the composer's interaction mode + warning copy from the requirement.
 * Returns `normal` (no warning, direct send) for every state that is neither
 * an active automation nor a delivered requirement.
 */
function resolveMode(automationStatus) {
  if (automationStatus === 'COMPLETED') {
    return { warning: COMPLETED_WARNING, completed: true, dangerous: true };
  }
  if (automationStatus && ACTIVE_AUTOMATION.includes(automationStatus)) {
    return {
      warning: INTERRUPT_WARNING[automationStatus] ?? null,
      completed: false,
      dangerous: true,
    };
  }
  return { warning: null, completed: false, dangerous: false };
}

const CommentComposer = ({
  requirement,
  onSend,
  sending,
}) => {
  const [content, setContent] = useState('');
  const { warning, completed, dangerous } = resolveMode(requirement.automationStatus);
  const canSend = content.trim().length > 0 && !sending;

  const doSend = async (next) => {
    try {
      await onSend(next);
      setContent('');
    } catch {
      // error surfacing is the container's responsibility via `sending`/messages
    }
  };

  const handleClick = () => {
    if (!canSend) return;
    if (!dangerous) {
      doSend(content);
      return;
    }
    Modal.confirm({
      title: completed ? '确认创建变更请求？' : '确认中断自动化？',
      icon: <ExclamationCircleOutlined />,
      content: warning,
      okText: completed ? '创建变更' : '发送并中断',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => doSend(content),
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {warning && (
        <Alert
          type="warning"
          showIcon
          message={warning}
        />
      )}
      <MarkdownEditor
        value={content}
        onChange={setContent}
        placeholder="输入评论（支持 Markdown）…"
        split={false}
        height="auto"
      />
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button
          type={dangerous ? 'default' : 'primary'}
          danger={dangerous}
          loading={sending}
          disabled={!canSend}
          icon={!sending ? <SendOutlined /> : undefined}
          onClick={handleClick}
        >
          发送
        </Button>
      </div>
    </div>
  );
};

export default CommentComposer;
export { INTERRUPT_WARNING, COMPLETED_WARNING };
