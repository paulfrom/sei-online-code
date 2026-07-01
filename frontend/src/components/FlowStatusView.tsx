import React from 'react';
import { Tag, Badge, WorkFlow, Space } from '@ead/suid';
import { constants } from '@/utils';
import {
  getFlowStatusLabel,
  getFlowStatusColor,
  FLOW_STATUS,
} from '@/utils/reportingEnums';

const { HistoryFlowButton } = WorkFlow;

const FlowStatusView = ({ flowStatus, businessId }) => {
  const label = getFlowStatusLabel(flowStatus);
  const color = getFlowStatusColor(flowStatus);
  const isInProcess = flowStatus === FLOW_STATUS.INPROCESS;
  const isClickable = isInProcess || flowStatus === FLOW_STATUS.COMPLETED;

  const tagContent = isInProcess ? (
    <Space>
      <Badge status="processing" />
      {label}
    </Space>
  ) : (
    label
  );

  const tag = color ? (
    <Tag color={color}>{tagContent}</Tag>
  ) : (
    <Tag>{tagContent}</Tag>
  );

  if (isClickable) {
    return (
      <HistoryFlowButton businessId={businessId} store={{ baseUrl: constants.SERVER_PATH }}>
        <div style={{ height: '100%' }}>{tag}</div>
      </HistoryFlowButton>
    );
  }

  return tag;
};

export default FlowStatusView;
