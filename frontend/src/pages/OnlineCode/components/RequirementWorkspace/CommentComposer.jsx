/** 评论输入：活跃需求增量修订，已完成需求确认创建变更 loop。 */
import React, { useState } from 'react';
import { Alert, Button, Modal } from '@ead/suid';
import {
  ExclamationCircleOutlined,
  SendOutlined,
} from '@ead/suid-icons';
import MarkdownEditor from '../MarkdownEditor';

const COMMENT_WARNING = {
  PLANNING: '评论将在当前 Loop 内增量调整计划；未受影响的任务会继续执行',
  DEVELOPING: '评论将在当前 Loop 内增量调整计划；未受影响的任务会继续执行',
  VALIDATING: '评论将在当前 Loop 内增量调整计划；未受影响的任务会继续执行',
  ACCEPTING: '评论将在当前 Loop 内增量调整计划；未受影响的任务会继续执行',
  COMPLETED: '当前需求已交付，提交评论将基于现有 MR / 分支创建变更请求 loop',
};

const COMPLETED_WARNING = COMMENT_WARNING.COMPLETED;

const ACTIVE_AUTOMATION = [
  'PLANNING',
  'DEVELOPING',
  'VALIDATING',
  'ACCEPTING',
];

function resolveMode(automationStatus) {
  if (automationStatus === 'COMPLETED') {
    return { warning: COMPLETED_WARNING, completed: true, dangerous: true };
  }
  if (automationStatus && ACTIVE_AUTOMATION.includes(automationStatus)) {
    return {
      warning: COMMENT_WARNING[automationStatus] ?? null,
      completed: false,
      dangerous: false,
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
      title: '确认创建变更请求？',
      icon: <ExclamationCircleOutlined />,
      content: warning,
      okText: '创建变更',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => doSend(content),
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {warning && (
        <Alert
          type={completed ? 'warning' : 'info'}
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
