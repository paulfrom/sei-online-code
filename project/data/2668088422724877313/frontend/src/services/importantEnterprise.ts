import { request } from '@ead/suid-utils-react';
import { constants } from '@/utils';
import type {
  CreateImportantEnterpriseRequest,
  EnterpriseCategory,
  EnterpriseCategoryOption,
  ImportantEnterprise,
  ImportantEnterpriseListParams,
  ImportantEnterpriseListResponse,
  PageResponse,
  UpdateImportantEnterpriseRequest,
} from './types/importantEnterprise';

const { SERVER_PATH } = constants;

/** 当前后端服务在网关中的服务代码 */
const SERVICE_CODE = '2668088422724877313';

/** 重要企业管理接口基础路径 */
const BASE_URL = `${SERVER_PATH}/${SERVICE_CODE}/api/v1/important-enterprises`;

/** 企业类别显示文案映射 */
export const ENTERPRISE_CATEGORY_LABELS: Record<EnterpriseCategory, string> = {
  IMPORTANT_SUBSIDIARY: '重要子公司',
  HOLDING_COMPANY: '控股公司',
};

/** 企业类别下拉选项 */
export const ENTERPRISE_CATEGORY_OPTIONS: EnterpriseCategoryOption[] = [
  { label: ENTERPRISE_CATEGORY_LABELS.IMPORTANT_SUBSIDIARY, value: 'IMPORTANT_SUBSIDIARY' as EnterpriseCategory },
  { label: ENTERPRISE_CATEGORY_LABELS.HOLDING_COMPANY, value: 'HOLDING_COMPANY' as EnterpriseCategory },
];

/** 获取企业类别显示文案 */
export function getEnterpriseCategoryLabel(category: EnterpriseCategory): string {
  return ENTERPRISE_CATEGORY_LABELS[category] ?? category;
}

/**
 * 分页查询重要企业列表
 * @param params 查询参数
 */
export async function listImportantEnterprises(
  params: ImportantEnterpriseListParams,
): Promise<ImportantEnterpriseListResponse> {
  return request({
    url: BASE_URL,
    method: 'GET',
    params,
  });
}

/**
 * 创建重要企业
 * @param data 创建请求体
 */
export async function createImportantEnterprise(
  data: CreateImportantEnterpriseRequest,
): Promise<ImportantEnterprise> {
  return request({
    url: BASE_URL,
    method: 'POST',
    data,
  });
}

/**
 * 更新重要企业
 * @param id 企业 ID
 * @param data 更新请求体
 */
export async function updateImportantEnterprise(
  id: string,
  data: UpdateImportantEnterpriseRequest,
): Promise<ImportantEnterprise> {
  return request({
    url: `${BASE_URL}/${id}`,
    method: 'PUT',
    data,
  });
}

/**
 * 删除重要企业
 * @param id 企业 ID
 */
export async function deleteImportantEnterprise(id: string): Promise<void> {
  return request({
    url: `${BASE_URL}/${id}`,
    method: 'DELETE',
  });
}

/**
 * 查询重要企业详情
 * @param id 企业 ID
 */
export async function getImportantEnterpriseDetail(id: string): Promise<ImportantEnterprise> {
  return request({
    url: `${BASE_URL}/${id}`,
    method: 'GET',
  });
}

export type {
  CreateImportantEnterpriseRequest,
  EnterpriseCategory,
  EnterpriseCategoryOption,
  ImportantEnterprise,
  ImportantEnterpriseListParams,
  ImportantEnterpriseListResponse,
  PageResponse,
  UpdateImportantEnterpriseRequest,
} from './types/importantEnterprise';
