import React, { useCallback } from 'react';
import { OrganizationTree } from '@ead/suid';
import { SEI_BASIC_SERVER_PATH } from '@/utils/constants';

const FilterOrg = ({ value, onSelect, allowClear = true, disabled = false, placeholder = '组织机构', style = { width: 240 } }) => {
  const handleChange = useCallback((value) => {
    if (value?.organizations?.length) {
      const org = value.organizations[0];
      onSelect?.({ id: org.id, code: org.code, name: org.name });
    } else {
      onSelect?.(null);
    }
  }, [onSelect]);

  return (
    <OrganizationTree.TreeSelect
      baseURL={SEI_BASIC_SERVER_PATH}
      allowClear={allowClear}
      disabled={disabled}
      placeholder={placeholder}
      style={style}cl
      value={value}
      onChange={handleChange}
    />
  );
};

export default FilterOrg;
