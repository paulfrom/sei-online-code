/**
 * 企业类别
 * - IMPORTANT_SUBSIDIARY: 重要子公司
 * - HOLDING_COMPANY: 控股公司
 */
export type EnterpriseCategory = 'IMPORTANT_SUBSIDIARY' | 'HOLDING_COMPANY';

/** 资产管理人简要信息 */
export interface AssetManagerInfo {
  /** 用户 ID */
  id: string;
  /** 用户姓名 */
  name: string;
}

/** 重要企业台账 */
export interface ImportantEnterprise {
  /** 主键 */
  id: string;
  /** 企业名称 */
  name: string;
  /** 企业类别 */
  category: EnterpriseCategory;
  /** 统一社会信用代码 */
  unifiedSocialCreditCode: string;
  /** 资产管理人 ID */
  assetManagerId: string;
  /** 资产管理人简要信息（详情/列表返回时填充） */
  assetManager?: AssetManagerInfo;
  /** 创建人 */
  createdBy: string;
  /** 更新人 */
  updatedBy: string;
  /** 创建时间 */
  createdAt: string;
  /** 更新时间 */
  updatedAt: string;
  /** 删除时间（逻辑删除标记，列表/详情不展示但后端可能返回） */
  deletedAt?: string;
  /** 是否已删除 */
  isDeleted?: boolean;
}

/** 重要企业列表项（含资产管理人简要信息） */
export type ImportantEnterpriseListItem = ImportantEnterprise;

/** 创建重要企业请求体 */
export interface CreateImportantEnterpriseRequest {
  /** 企业名称 */
  name: string;
  /** 企业类别 */
  category: EnterpriseCategory;
  /** 统一社会信用代码 */
  unifiedSocialCreditCode: string;
  /** 资产管理人 ID */
  assetManagerId: string;
}

/** 更新重要企业请求体 */
export type UpdateImportantEnterpriseRequest = Partial<CreateImportantEnterpriseRequest>;

/** 重要企业分页列表查询参数 */
export interface ImportantEnterpriseListParams {
  /** 页码，默认 1 */
  page?: number;
  /** 每页条数，默认 20，最大 100 */
  pageSize?: number;
  /** 企业名称或统一社会信用代码模糊搜索关键字 */
  keyword?: string;
  /** 按企业类别精确筛选 */
  category?: EnterpriseCategory;
  /** 按资产管理人筛选 */
  assetManagerId?: string;
}

/** 分页响应结构 */
export interface PageResponse<T> {
  /** 数据列表 */
  data: T[];
  /** 总条数 */
  total: number;
  /** 当前页码 */
  page: number;
  /** 每页条数 */
  pageSize: number;
}

/** 企业类别下拉选项 */
export interface EnterpriseCategoryOption {
  /** 显示文案 */
  label: string;
  /** 选项值 */
  value: EnterpriseCategory;
}

/** 重要企业分页列表响应 */
export type ImportantEnterpriseListResponse = PageResponse<ImportantEnterpriseListItem>;
