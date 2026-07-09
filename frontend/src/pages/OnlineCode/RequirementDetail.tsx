/**
 * Requirement workspace page.
 *
 * URL: /online-code/requirement?id=...
 *
 * Thin wrapper that routes from the query string into the tabbed workspace.
 */
import React from 'react';
import { useSearchParams } from 'umi';
import { PageContainer, PageHeader, PageState } from './components/PageLayout';
import RequirementWorkspace from './components/RequirementWorkspace';

const RequirementDetail: React.FC = () => {
  const [searchParams] = useSearchParams();
  const id = searchParams.get('id') ?? '';

  if (!id) {
    return (
      <PageContainer>
        <PageHeader title="需求详情" subTitle="缺少需求 ID" />
        <PageState error="未找到 id 参数" />
      </PageContainer>
    );
  }

  return <RequirementWorkspace requirementId={id} />;
};

export default RequirementDetail;
