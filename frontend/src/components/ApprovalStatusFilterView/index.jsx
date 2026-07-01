import React, { useState, useCallback, useMemo } from 'react';
import { getIntl } from 'umi';
import { FilterView, Space, Switch } from '@ead/suid';
import { FLOW_STATUS } from '@/utils/constants';

const ALL_KEY = 'ALL';

/**
 * 审批状态筛选（FilterView + 包含他人单据 Switch）
 * @param {{ onChange?: (payload: { includeOthers: boolean, flowStatus: string|null }) => void }} props
 */
const ApprovalStatusFilterView = ({ onChange }) => {
  const { formatMessage } = getIntl();
  const [includeOthers, setIncludeOthers] = useState(false);
  const [statusKey, setStatusKey] = useState(ALL_KEY);

  const dataSource = useMemo(
    () => [
      {
        title: formatMessage({ id: 'reporting.approvalStatus.all', defaultMessage: '全部' }),
        key: ALL_KEY,
      },
      {
        title: formatMessage({ id: 'reporting.approvalStatus.init', defaultMessage: '草稿' }),
        key: FLOW_STATUS.INIT,
      },
      {
        title: formatMessage({ id: 'reporting.approvalStatus.inProcess', defaultMessage: '审批中' }),
        key: FLOW_STATUS.INPROCESS,
      },
      {
        title: formatMessage({ id: 'reporting.approvalStatus.completed', defaultMessage: '审批完成' }),
        key: FLOW_STATUS.COMPLETED,
      },
    ],
    [formatMessage],
  );

  const emitChange = useCallback(
    (nextIncludeOthers, statusKey) => {
      onChange?.({
        includeOthers: nextIncludeOthers,
        flowStatus: statusKey === ALL_KEY ? null : statusKey,
      });
    },
    [onChange],
  );

  const handleIncludeChange = useCallback(
    (checked) => {
      setIncludeOthers(checked);
      emitChange(checked, statusKey);
    },
    [emitChange, statusKey],
  );

  const handleStatusChange = useCallback(
    (items) => {
      const nextKey = items?.[0]?.key ?? ALL_KEY;
      // FilterView 清除按钮会 onChange 当前选中项；将其重置为「全部」
      if (nextKey === statusKey && nextKey !== ALL_KEY) {
        setStatusKey(ALL_KEY);
        emitChange(includeOthers, ALL_KEY);
        return;
      }
      setStatusKey(nextKey);
      emitChange(includeOthers, nextKey);
    },
    [emitChange, includeOthers, statusKey],
  );

  const listBeforeExtra = useMemo(
    () => (
      <Space>
        {formatMessage({ id: 'reporting.approvalStatus.includeOthers', defaultMessage: '包含他人单据' })}
        <Switch size="small" checked={includeOthers} onChange={handleIncludeChange} />
      </Space>
    ),
    [formatMessage, includeOthers, handleIncludeChange],
  );

  const filterViewProps = useMemo(
    () => ({
      allowClear: true,
      rowKey: 'key',
      listBeforeExtra,
      defaultValue: [ALL_KEY],
      selectedKeys: [statusKey],
      dataSource,
      labelTitle: formatMessage({ id: 'reporting.approvalStatus.label', defaultMessage: '审批状态' }),
      onChange: handleStatusChange,
    }),
    [listBeforeExtra, dataSource, formatMessage, handleStatusChange, statusKey],
  );

  return <FilterView {...filterViewProps} />;
};

export default ApprovalStatusFilterView;
