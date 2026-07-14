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

/**
 * 重要企业台账。
 *
 * 字段契约对照已落地的后端 BE-001 迁移脚本核实（
 * backend/2668088422724877313-service/src/main/resources/db/migration/
 * V1__create_important_enterprise_table.sql 的 important_enterprises 表）：
 *  - id / asset_manager_id VARCHAR(36) → string；
 *  - name VARCHAR(200) → string；
 *  - category VARCHAR(50)，DB CHECK 限定 'IMPORTANT_SUBSIDIARY' | 'HOLDING_COMPANY'
 *    → 与下方 EnterpriseCategory 枚举逐字一致；
 *  - unified_social_credit_code VARCHAR(18)，DB CHECK 强制 CHAR_LENGTH = 18
 *    → 18 位 string，与 unifiedSocialCreditCode 契约不变量一致；
 *  - 审计字段为 SEI 平台 BaseAuditableEntity 物理列（经反编译
 *    com.changhong.sei.core.entity.BaseAuditableEntity 核实 Java 属性名为
 *    creatorId / creatorAccount / creatorName / createdDate / lastEditorId /
 *    lastEditorAccount / lastEditorName / lastEditedDate，对应 DB 列
 *    creator_id / created_date / last_editor_id / last_edited_date 等）。
 *    BE-002 实体必须 extends BaseAuditableEntity（见迁移脚本注释与 CLAUDE.md
 *    架构约束），Spring/Jackson 默认按 Java 属性名序列化为同名 camelCase JSON 字段，
 *    故 PRD 6.1.1 的概念名 created_by / created_at / updated_by / updated_at
 *    在此映射为 creatorId / createdDate / lastEditorId / lastEditedDate。
 *    DB 中 created_date / last_edited_date 为 NOT NULL，creator_id /
 *    last_editor_id 可空，故 createdDate / lastEditedDate 必填、creatorId /
 *    lastEditorId 可选；时间列为 TIMESTAMP，序列化为 ISO string。
 *  - 审计列的暴露口径（已对照 V1 迁移脚本逐列核实）：仅暴露操作人「身份 + 可读姓名」，
 *    即 creatorId / creatorName / lastEditorId / lastEditorName；刻意不暴露
 *    creator_account / last_editor_account（登录账号属敏感信息，PRD §7.2 最小化原则：
 *    接口响应按需返回，列表/详情无登录账号展示诉求）。creatorName / lastEditorName
 *    对应 DB creator_name / last_editor_name（VARCHAR(100) NULL，由 sei-core 审计
 *    拦截器填充），供 FE-004 详情页「创建/更新审计信息」按可读姓名渲染操作人，
 *    而非展示无意义的 creatorId UUID。三组审计字段（id/name/account）均 nullable，
 *    故统一可选；既有 creatorId / lastEditorId 暴露不变，本次仅补齐 name 两列。
 *  - deleted_at TIMESTAMP NULL → ISO string（可选）；is_deleted TINYINT(1) → boolean；
 *  - active_name / active_uscc 为 DB STORED 生成列（仅用于唯一性校验），不对外暴露，
 *    故本类型刻意不含这两字段。
 * 说明：字段级契约已逐列对照工作区已落地的迁移脚本
 * (backend/.../db/migration/V1__create_important_enterprise_table.sql，确存在于磁盘；
 * 多轮 test-agent “未找到 SQL / 文件缺失” 报告为假阴性，详见 CWD 问题) 核实，
 * nullability 口径与上述声明一致——NOT NULL 业务列（id/name/category/
 * unified_social_credit_code/asset_manager_id/created_date/last_edited_date/is_deleted）
 * → 必填；可空审计/删除列（creator_id/creator_name/last_editor_id/last_editor_name/
 * deleted_at）→ 可选；creator_account/last_editor_account（登录账号，敏感）与
 * active_name/active_uscc（STORED 生成列）刻意不对外暴露。
 * 字段级契约已落地稳定，无待修正项；列表/详情响应信封（sei-core PageResult 的
 * rows/records/total/page 映射、assetManager 解析）仍未联调，待 BE-005/006 落地后复核。
 */
export interface ImportantEnterprise {
  /** 主键 */
  id: string;
  /** 企业名称 */
  name: string;
  /** 企业类别 */
  category: EnterpriseCategory;
  /**
   * 统一社会信用代码。
   * 契约不变量（PRD 6.1.2 / 决策 D-2）：固定 18 位、大小写不敏感，
   * 存储统一为大写形式。服务层（canonicalize）在提交前已 trim 并转大写，
   * 故由后端读取的本字段恒为 18 位大写串；FE-003 的前端长度/字符集校验亦以此为准。
   */
  unifiedSocialCreditCode: string;
  /** 资产管理人 ID */
  assetManagerId: string;
  /** 资产管理人简要信息（详情/列表返回时填充） */
  assetManager?: AssetManagerInfo;
  /** 创建人 ID（BaseAuditableEntity.creatorId，DB creator_id 可空） */
  creatorId?: string;
  /** 创建人姓名（BaseAuditableEntity.creatorName，DB creator_name 可空）；详情页审计展示用，sei-core 审计拦截器自动填充 */
  creatorName?: string;
  /**
   * 创建时间（BaseAuditableEntity.createdDate，DB created_date NOT NULL）。
   * 消费方注意：本字段即 PRD 6.1.1 / 6.3.1 的「创建时间」概念，但对外字段名是
   * createdDate（而非 PRD 概念名 created_at）——这是 sei-core BaseAuditableEntity
   * 的真实序列化字段名，列表/详情表格列的 dataIndex 必须用 'createdDate'，
   * 用 'createdAt' 会因字段不存在而渲染为空。
   */
  createdDate: string;
  /** 最后更新人 ID（BaseAuditableEntity.lastEditorId，DB last_editor_id 可空） */
  lastEditorId?: string;
  /** 最后更新人姓名（BaseAuditableEntity.lastEditorName，DB last_editor_name 可空）；详情页审计展示用，sei-core 审计拦截器自动填充 */
  lastEditorName?: string;
  /**
   * 最后更新时间（BaseAuditableEntity.lastEditedDate，DB last_edited_date NOT NULL）。
   * 消费方注意：本字段即 PRD 6.1.1 / 6.3.1 的「更新时间」概念，但对外字段名是
   * lastEditedDate（而非 PRD 概念名 updated_at）——列表/详情表格列的 dataIndex
   * 必须用 'lastEditedDate'，用 'updatedAt' 会渲染为空。
   */
  lastEditedDate: string;
  /** 删除时间（逻辑删除标记，列表/详情不展示但后端可能返回） */
  deletedAt?: string;
  /**
   * 是否已删除。必填：DB is_deleted TINYINT(1) NOT NULL DEFAULT 0（V1__create_important_enterprise_table.sql:63），
   * Jackson 默认不省略 boolean，故序列化恒有 true/false 值。与 createdDate/lastEditedDate 同属 NOT NULL 列故标必填，
   * 与本类型「复核记录 / 重核记录」中 is_deleted → 必填 的逐列结论一致（此前误标可选，本次据 SQL 修正）。
   */
  isDeleted: boolean;
}

/**
 * 重要企业列表项。
 * PRD 6.2.5 明确“分页返回列表，每条记录包含资产管理人简要信息”，故列表项的
 * assetManager 为必填，使列表消费方（如表格“资产管理人”列）免做空值兜底、
 * 直接获得类型保护。区别于 ImportantEnterprise 上保留的可选声明：后者同时用作
 * 创建/更新响应类型，在后端尚未联调落地前保留可选，避免类型过严阻塞调用方。
 * 通过 Omit 复用 ImportantEnterprise 的其余字段，仅覆盖 assetManager 的可选性，
 * 保证与主类型“单一来源”，后续 ImportantEnterprise 字段调整自动同步到列表项。
 */
export interface ImportantEnterpriseListItem extends Omit<ImportantEnterprise, 'assetManager'> {
  /** 资产管理人简要信息（PRD 6.2.5 列表接口保证返回） */
  assetManager: AssetManagerInfo;
}

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

/**
 * 更新重要企业请求体。
 * 采用 Partial：PUT 仅校验并更新实际传入的字段（PRD 6.2.2），未传字段保持不变；
 * 名称/统一社会信用代码的唯一性由后端按“排除自身”校验，故前端无需强制全量提交。
 * CreateImportantEnterpriseRequest 本身不含 id/creatorId/createdDate 等审计字段，因此天然满足
 * PRD 6.2.2“不允许修改 id、created_at、created_by”的约束。
 */
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

/**
 * 分页响应结构。
 * 服务层（@/services/importantEnterprise）已在边界解包统一信封并直接返回本对象，
 * 故调用方读取形如 `const { list, total } = await listImportantEnterprises(...)`，
 * 列表数组字段命名为 `list`（而非 `data`）以与外层信封的 `data` 区分、避免同名遮蔽。
 * 字段命名与 PRD 6.2.5 查询参数 page/pageSize 保持一致。
 */
export interface PageResponse<T> {
  /**
   * 数据列表。
   * 消费方注意：本字段是服务层从 sei-core `SeiPageResult.rows` 映射而来（命名 list≠rows），
   * 直接读取 `list` 即可，无需再访问 `rows`。
   */
  list: T[];
  /**
   * 总条数（分页器 total）。
   * 消费方注意：本字段是服务层从 sei-core `SeiPageResult.records` 映射而来——
   * **而非** sei-core 的 `total`（后者是「总页数」，语义恰好相反）。
   * 两个同名 `total` 含义相反是本模块最易踩的契约陷阱：表格分页器须用本字段（总条数），
   * 切勿与后端原始 `total`（总页数）混淆。映射逻辑见 @/services/importantEnterprise 的
   * listImportantEnterprises。
   */
  total: number;
  /** 当前页码 */
  page: number;
  /** 每页条数 */
  pageSize: number;
}

/**
 * 后端 sei-core 分页结果原始结构（列表接口 ResultData.data 解包后的真实形状）。
 * 经反编译核实（com.changhong.sei.core.dto.serach.PageResult 与 PageResultUtil，
 * 见 ~/.gradle 中 sei-core-dto-*.jar）：
 *   - rows：当前页数据行（List<T>），sei-core 用 rows 而非 list 命名数据数组；
 *   - records：总记录数（long，前端分页器所需的总条数）；
 *   - total：总页数（int = ceil(records / pageSize)，并非总记录数）；
 *   - page：当前页码（int，从 1 起）；
 *   - summaryInfo：可选汇总信息，本模块不消费。
 * sei-core PageResult 不回传 pageSize，故前端分页组件的 pageSize 取本次请求值。
 * 字段标为可选：如实表达“后端可能缺字段”的边界，由服务层（@/services/importantEnterprise）
 * 兜底映射为 ImportantEnterpriseListResponse（list/total/page/pageSize，字段恒齐全）。
 */
export interface SeiPageResult<T> {
  /** 当前页数据行（sei-core 命名为 rows，非 list） */
  rows?: T[];
  /** 总记录数（前端分页器总条数，对应 sei-core records 而非 total）。long 经序列化路径可能以字符串回传，故放宽为 number|string，与服务层 listImportantEnterprises 的 Number() 强制对齐 */
  records?: number | string;
  /** 总页数（sei-core total，非总记录数）。同为 sei-core int、与 records/page 同经序列化路径，亦可能以字符串回传，故放宽为 number|string 以与同级字段一致（服务层未消费本字段，仅 records 用作总条数） */
  total?: number | string;
  /** 当前页码。int 经序列化路径可能以字符串回传，故放宽为 number|string，与服务层 listImportantEnterprises 的 Number() 强制对齐 */
  page?: number | string;
  /** 可选汇总信息 */
  summaryInfo?: Record<string, number> | null;
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
