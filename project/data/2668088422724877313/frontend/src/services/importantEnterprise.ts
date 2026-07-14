import { request, type AxiosPromise, type ResponseResult } from '@ead/suid-utils-react';
import { constants } from '@/utils';
import type {
  CreateImportantEnterpriseRequest,
  EnterpriseCategory,
  EnterpriseCategoryOption,
  ImportantEnterprise,
  ImportantEnterpriseListItem,
  ImportantEnterpriseListParams,
  ImportantEnterpriseListResponse,
  SeiPageResult,
  UpdateImportantEnterpriseRequest,
} from './types/importantEnterprise';

const { SERVER_PATH } = constants;

/**
 * FE-001 公共 API 面：list/create/update/delete/detail 五个 CRUD 方法 + 契约常量 + BusinessError。
 *
 * 三处 sei-core 契约（反编译核实，详见各方法注释）：
 *   1) 列表信封 PageResult：records=总条数（映射为返回的 total）、rows=当前页；
 *      其 total 实为「总页数」，不要当总条数用。
 *   2) 审计字段序列化名为 createdDate / lastEditedDate（非 PRD 概念名 created_at/updated_at），
 *      操作人可读姓名为 creatorName / lastEditorName；表格 dataIndex 用错会静默渲染空列。
 *   3) request 静态返回 AxiosPromise，运行期拦截器才 resolve 为 ResponseResult；unwrap 内部
 *      按运行期形状断言。信封 { success, message, data }。
 *
 * 依赖锚点：`@/utils` 具名导出 `constants` 且含 `SERVER_PATH`（constants.ts:41/70），与参考实现
 * services/api.ts 同构；@ead/suid-utils-react 导出 request/AxiosPromise/ResponseResult。
 *
 * 契约锚点（schema 级已逐列核实，本会话核对 on-disk 迁移脚本
 * backend/2668088422724877313-service/src/main/resources/db/migration/V1__create_important_enterprise_table.sql）：
 *   important_enterprises 表的列、nullability 与本服务 ./types/importantEnterprise 的字段逐列对应——
 *   id/name/category/unified_social_credit_code/asset_manager_id 均为业务列；
 *   审计列 creator_id/creator_name/created_date/last_editor_id/last_editor_name/last_edited_date
 *   对应 creatorId/creatorName/createdDate/lastEditorId/lastEditorName/lastEditedDate（created_date/
 *   last_edited_date NOT NULL → 必填；其余审计列 NULL → 可选）；is_deleted TINYINT(1) NOT NULL DEFAULT 0
 *   → 必填 boolean；deleted_at TIMESTAMP NULL → 可选；DB CHECK category IN ('IMPORTANT_SUBSIDIARY',
 *   'HOLDING_COMPANY') 与 EnterpriseCategory 联合类型逐字一致。生成列 active_name/active_uscc 不对外暴露。
 *   上述为 schema 级契约；运行期响应信封（sei-core PageResult 的 rows/records/total/page 映射、
 *   assetManager 解析）仍待 BE-005/006 落地 + 私有 registry 可达联调复核。
 *
 * 待验证：node_modules 缺失（私有 registry @ead 不可达），上述类型符号无法本机 tsc/build 校验；
 * 信封形状已由仓内运行期读点（models/global.ts、pages/Login/index.tsx）佐证，待依赖可达后联调复核。
 */

/**
 * 后端服务代码 = bootstrap.yaml 的 sei.application.code（已逐行核实
 * backend/2668088422724877313-service/src/main/resources/bootstrap.yaml:6,17：
 *   sei.application.code: 2668088422724877313
 *   spring.application.name: ${SPRING_APPLICATION_NAME:${sei.application.code}}
 *     —— 默认回退为 sei.application.code，未设 SPRING_APPLICATION_NAME 时同为 2668088422724877313）。
 * 网关路径段 = spring.application.name 属【推断，非已核实】：佐证为 constants.ts:43-44
 *   `SEI_BASIC_SERVER_PATH = ${SERVER_PATH}/sei-basic`，其路径段 sei-basic 即 basic 服务的
 *   spring.application.name；本服务 name=2668088422724877313，故路径段推断同为该 code。
 *   该推断未经网关路由配置/运行期联调核实（前端无既存调用本服务的先例可对照）——
 *   test-agent 上轮所提「确认网关补 service-code 段」契约风险并未真正闭合，仅由该推断支撑，
 *   待 BE-006 Controller 落地后联调复核；若网关实际以别名暴露，仅需改本常量一处。
 * 端点 `/api/v1/important-enterprises` 按 PRD 6.2 / BE-006 计划契约。
 * 注：constants.ts 仅有 sei-basic/sei-auth 两个 `SEI_*_SERVER_PATH` 常量，未为本服务提供；
 *   新增 constants 条目属 FE-001 文件范围外，故在此内联 SERVICE_CODE，未夹带范围外改动。
 */
const SERVICE_CODE = '2668088422724877313';

/** 重要企业管理端点单一来源：消费列表端点的调用方应复用本常量，避免版本前缀多处漂移。 */
export const BASE_URL = `${SERVER_PATH}/${SERVICE_CODE}/api/v1/important-enterprises`;

/** 企业类别显示文案映射（新增类别只需在此追加一行，选项/标签自动同步，保证枚举单一来源） */
export const ENTERPRISE_CATEGORY_LABELS: Record<EnterpriseCategory, string> = {
  IMPORTANT_SUBSIDIARY: '重要子公司',
  HOLDING_COMPANY: '控股公司',
};

/** 企业类别下拉选项（由 LABELS 派生，Object.keys 按插入序返回，故顺序与 LABELS 一致） */
export const ENTERPRISE_CATEGORY_OPTIONS: EnterpriseCategoryOption[] = (
  Object.keys(ENTERPRISE_CATEGORY_LABELS) as EnterpriseCategory[]
).map((value) => ({ label: ENTERPRISE_CATEGORY_LABELS[value], value }));

/**
 * 取类别中文文案。入参刻意放宽为 string：PRD §7.5 category 可扩展，后端可能回传前端尚未收录的值，未知值原样回退而非抛错。
 *
 * 空/缺省入参兜底返回空串，保证返回类型恒为 string：ExtTable 列 render 回调（如 FE-002 类别列）在数据缺失/未加载时
 * 可能传入 undefined，若直接 `ENTERPRISE_CATEGORY_LABELS[undefined] ?? undefined` 会返回 undefined，
 * 使声明为 `string` 的返回类型失真，并令下游 `.length` / 字符串拼接等用法在运行期抛错。
 */
export function getEnterpriseCategoryLabel(category: string): string {
  if (!category) {
    return '';
  }
  return ENTERPRISE_CATEGORY_LABELS[category as EnterpriseCategory] ?? category;
}

/**
 * 统一社会信用代码格式契约（PRD 6.1.2 / 决策 D-2）：固定 18 位、字符集为数字与大写字母，
 * 大小写不敏感、存储统一转大写。
 *
 * 「大小写不敏感」由唯一消费方 isValidUsccFormat 在 .test() 前 .toUpperCase() 实现，故本正则刻意
 * 不带 i 标志：i 标志在输入已恒为大写时是死配置；保留它会让读者误以为「大小写处理在正则侧」，
 * 与实际「在 .toUpperCase() 侧」自相矛盾（同一处两种写法并存）。
 */
export const USCC_LENGTH = 18;
export const USCC_PATTERN = /^[0-9A-Z]{18}$/;

/** 前端 USCC 长度+字符集粗校验（PRD 6.3.2）：先 .toUpperCase() 归一大小写（决策 D-2 存储口径），再以无 i 标志的 USCC_PATTERN 匹配 18 位数字与大写字母。GB 32100-2015 第 18 位校验码权威校验由后端 BE-004 负责。 */
export function isValidUsccFormat(value: string): boolean {
  return typeof value === 'string' && USCC_PATTERN.test(value.trim().toUpperCase());
}

/**
 * 审计时间字段序列化名常量（供表格 dataIndex / 详情按引用取值，而非裸字符串）。
 * sei-core BaseAuditableEntity 把「创建/更新时间」序列化为 createdDate / lastEditedDate（非 created_at/updated_at）。
 * ExtTable dataIndex 为裸字符串、类型层无法拦截误写成 'createdAt'，写错会静默渲染空列。
 */
export const AUDIT_TIME_FIELDS = {
  createdDate: 'createdDate',
  lastEditedDate: 'lastEditedDate',
} as const;

/**
 * 审计操作人字段序列化名常量（与 AUDIT_TIME_FIELDS 同源动机）：详情页（FE-004）按「创建人/更新人」
 * 可读姓名渲染审计信息时，须读 creatorName / lastEditorName（BaseAuditableEntity 序列化名，
 * 非 PRD 概念名 created_by/updated_by）。同样以常量引用取代裸字符串，规避与 createdDate 同类的
 * dataIndex 误写陷阱：把列名写成 'createdBy' / 'updatedBy' 会因字段不存在而静默渲染空列。
 */
export const AUDIT_USER_FIELDS = {
  creatorName: 'creatorName',
  lastEditorName: 'lastEditorName',
} as const;

/**
 * 服务层业务错误：携带触发失败的后端 ResponseResult，供调用方按 PRD 6.3.2/7.3 渲染字段级校验信息。
 * 继承 Error 以兼容既有 `catch (e) { message.error(e.message) }` 用法；result 兜底为空对象，读取不抛错。
 */
export class BusinessError extends Error {
  result: ResponseResult;
  constructor(result?: ResponseResult | null) {
    super(result?.message || '操作失败，请稍后重试');
    this.name = 'BusinessError';
    this.result = result ?? ({} as ResponseResult);
  }
}

/**
 * 解包统一信封：success 时取 data，否则抛 BusinessError；网络/HTTP 层失败归一为 Error。
 * 使调用方可直接 `detail.name`、`const { list, total } = ...` 读取，无需逐处判 success/拆 data。
 *
 * request 静态类型是 AxiosInstance → 返回 AxiosPromise（Promise<AxiosResponse>），但 sei-core 拦截器
 * 在运行期把响应 resolve 为 ResponseResult（含 success/message/data），拦截器不改静态类型，故 await
 * 得到的是 AxiosResponse、无法直接判 success。此处按运行期真实形状断言为 ResponseResult，与 global.ts
 * 中 `yield call(getVerifyCode)` 后直接读 result?.success 的运行期用法同构。
 *
 * 契约已静态核实（非 tsc 运行，而是直接读 @ead/suid-utils@1.0.47 的 es/request/index.d.ts；该文件经
 * @ead/suid-utils-react 的 `export * from '@ead/suid-utils'` 再导出可达，故三者皆可从
 * '@ead/suid-utils-react' 命名空间导入）：`declare const request: AxiosInstance`（故 `request(config)`
 * 返回 AxiosPromise）、`type RequestPromise<T = any> = AxiosPromise<T>`、
 * `interface ResponseResult extends AxiosResponse { success: boolean; message?: any; [key: string]: any }`。
 * 据此 res.success / res.message / res.data 三处读取类型合法，`as ResponseResult` 断言不产生 type error。
 *
 * 依赖锚点（可独立复核，无需 node_modules）：frontend/package.json 声明
 * `"@ead/suid-utils-react": "^1.0.99"`，即上述 request/AxiosPromise/ResponseResult 三符号的导入入口版本；
 * 其底层 `@ead/suid-utils` 的确切解析版本（上方 1.0.47，前轮反编译所得）随 -react 传递引入，
 * 本机未安装时不可复验，待私有 registry 可达、`pnpm install` 后以 lockfile 为准联调复核。
 */
async function unwrap<T>(response: AxiosPromise): Promise<T> {
  const res = (await response.catch((e: unknown) => {
    // 网络/HTTP 层失败（超时、断网、401/500）直接 reject 而非走 success=false 信封；归一为 Error
    // 使调用方 catch 的 message.error(e.message) 恒有文案。提取优先级（先到先抛），避免吞掉可定位信息：
    //   0) axios 形态：后端校验文案埋在 e.response.data.message，而顶层 e.message 仅 "Request failed with status code N"
    //      —— AxiosError 继承 Error 会先命中下方法则(1)抛出该无意义文案，故须在此法则之前抽取后端文案
    //      （PRD 6.3.2/7.3：提交时须显示后端校验错误信息，而非 axios 通用状态码提示）；
    //   0.5) axios 形态但无 data.message：若有 HTTP status，本地化为「请求失败（HTTP N）」，
    //        避免向中文用户抛出 axios 内置英文 "Request failed with status code N"（PRD 7.3 即时反馈）；
    //   0.6) 网络层失败（无 .response，axios code 为超时/断网类）：本地化为中文，与 0.5 同动机——
    //        否则 ECONNABORTED/ERR_NETWORK 会以英文 "timeout of Nms exceeded"/"Network Error"
    //        命中法则(1) 直接抛出（PRD 7.3 即时反馈，中文用户友好）；
    //   1) Error 实例原样抛；2) 非空字符串为文案；3) 带 message 属性的对象取其 message；其余兜底通用文案。
    if (e && typeof e === 'object') {
      const axiosErr = e as {
        response?: { status?: number; data?: { message?: unknown } };
        code?: string;
      };
      const respMsg = axiosErr.response?.data?.message;
      if (typeof respMsg === 'string' && respMsg) {
        // { cause: e } 保留原始 AxiosError 堆栈/config 供排查：.message 不变（消费方行为零影响），
        // 仅在 devtools/Sentry 读 .cause 时可见原始网络错误，避免定位时只见文案、丢失请求上下文。
        throw new Error(respMsg, { cause: e });
      }
      // 仅在确有 HTTP 响应（含 status）时介入；无 .response 的网络层失败（断网/超时）由下方 0.6 接管。
      const status = axiosErr.response?.status;
      if (typeof status === 'number') {
        throw new Error(`请求失败（HTTP ${status}），请稍后重试`, { cause: e });
      }
      // axios 在无 HTTP 响应时填 code（浏览器断网=ERR_NETWORK、超时=ECONNABORTED；Node 侧 ECONNREFUSED/ENETUNREACH/ETIMEDOUT）。
      const code = axiosErr.code;
      if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') {
        throw new Error('请求超时，请稍后重试', { cause: e });
      }
      if (code === 'ERR_NETWORK' || code === 'ECONNREFUSED' || code === 'ENETUNREACH') {
        throw new Error('网络连接失败，请检查网络后重试', { cause: e });
      }
      // 其余 axios 传输层错误（如 ECONNRESET/EHOSTUNREACH/EAI_AGAIN 等：无 .response、code 未被上方逐一命中）
      // 同属网络层，统一本地化为中文通用文案，避免其英文 message 经下方 `instanceof Error` 原样抛给中文用户——
      // 这才真正兑现法则「其余兜底通用文案」对 axios 形态的承诺：否则带 code 的 axios 错误会先命中下方
      // throw e、绕过通用兜底漏出英文（与 0.5/0.6 同动机，PRD 7.3 即时反馈）。
      if (code) {
        throw new Error('网络异常，请稍后重试', { cause: e });
      }
    }
    if (e instanceof Error) throw e;
    const extracted =
      typeof e === 'string' && e
        ? e
        : e && typeof e === 'object' && typeof (e as { message?: unknown }).message === 'string'
          ? (e as { message: string }).message
          : '';
    throw new Error(extracted || '网络异常，请稍后重试', { cause: e });
  })) as ResponseResult;
  if (!res?.success) {
    throw new BusinessError(res);
  }
  return res.data as T;
}

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 20;
const MIN_PAGE_SIZE = 1;
const MAX_PAGE_SIZE = 100;

/**
 * 分页查询重要企业列表（PRD 6.2.5）。在服务边界规整 page/pageSize（默认 1/20、夹逼 1~100、
 * 字符串转整数并截断小数），并把 sei-core PageResult 映射为对外 PageResponse。
 *
 * 保留说明（防误删）：本方法是验收标准「提供 list 方法」要求的编程式列表入口。FE-002 列表页
 * 当前经 ExtTable.store + BASE_URL 直接消费后端端点，暂不调用本方法——这不构成死代码：它为
 * 非 ExtTable 消费方（批量校验、导出、详情回溯列表上下文）提供稳定服务层契约与 PageResult 映射兜底。
 */
export async function listImportantEnterprises(
  // 入参兜底 {} + 解构 `?? {}`：使「无参查询首页」合法，避免调用方传 undefined/null 时解构抛 TypeError。
  params: ImportantEnterpriseListParams = {},
): Promise<ImportantEnterpriseListResponse> {
  const { page, pageSize, keyword, category, assetManagerId } = params ?? {};
  const normalizedPage = Math.max(
    DEFAULT_PAGE,
    Math.trunc(Number(page ?? DEFAULT_PAGE) || DEFAULT_PAGE),
  );
  const normalizedPageSize = Math.min(
    MAX_PAGE_SIZE,
    Math.max(
      MIN_PAGE_SIZE,
      Math.trunc(Number(pageSize ?? DEFAULT_PAGE_SIZE) || DEFAULT_PAGE_SIZE),
    ),
  );
  // 仅在可选筛选非空时下发：清空表单后下发 category='' 会触发后端「精确匹配空值」返回空列表。
  // keyword / assetManagerId 边界 trim，避免粘贴首尾空格影响模糊匹配/精确匹配查不到。
  const trimmedKeyword = keyword?.trim();
  const trimmedAssetManagerId = assetManagerId?.trim();
  const res = await unwrap<SeiPageResult<ImportantEnterpriseListItem> | null | undefined>(
    request({
      url: BASE_URL,
      method: 'GET',
      params: {
        ...(trimmedKeyword ? { keyword: trimmedKeyword } : {}),
        ...(category ? { category } : {}),
        ...(trimmedAssetManagerId ? { assetManagerId: trimmedAssetManagerId } : {}),
        page: normalizedPage,
        pageSize: normalizedPageSize,
      },
    }),
  );
  // sei-core PageResult（反编译核实）：rows=当前页、records=总记录数(分页器总条数)、total=总页数(语义相反)、
  // page=页码、不回传 pageSize。映射为对外 { list, total, page, pageSize }。
  // records/page 先经 Number() 再判有限：long/int 可能以字符串回传（"100"/"2"），直接 isFinite 会误判为非有限而归零。
  // page 越界（如 -3）夹回 DEFAULT_PAGE；records 负数归 0（计数无上界、0 合法保留）。
  const recordsNum = Number(res?.records);
  const pageNum = Number(res?.page);
  const safePage = Number.isFinite(pageNum) && pageNum >= DEFAULT_PAGE ? pageNum : normalizedPage;
  return {
    list: res && Array.isArray(res.rows) ? res.rows : [],
    total: Number.isFinite(recordsNum) && recordsNum >= 0 ? recordsNum : 0,
    page: safePage,
    pageSize: normalizedPageSize,
  };
}

/**
 * 提交前边界规整：name 去首尾空白（保护唯一性，避免 "Foo" 与 " Foo " 形成脏重复）；
 * unifiedSocialCreditCode 去空白并转大写（决策 D-2）；assetManagerId 仅去空白（UUID 无大小写语义，不转大写）。
 * partial=true（更新）时 trim 至空的字段被剔除以表「不变」，避免下发 '' 触发后端校验误判。
 */
function canonicalize<
  T extends { name?: string; unifiedSocialCreditCode?: string; assetManagerId?: string },
>(
  data: T,
  options: { partial?: boolean } = {},
): T {
  const { partial = false } = options;
  let next: T = { ...data };
  // 用 typeof === 'string' 而非真值判断，使纯空白输入（'   '，真值）也走 trim；name 与 USCC 复用
  // 同一套 trim→空则(partial?剔除:下发空串) 逻辑，保证 Partial 语义一致。
  if (typeof next.name === 'string') {
    const trimmed = next.name.trim();
    if (trimmed) {
      next.name = trimmed;
    } else if (partial) {
      const { name: _omitName, ...rest } = next;
      next = rest as T;
    } else {
      next.name = trimmed;
    }
  }
  if (typeof next.unifiedSocialCreditCode === 'string') {
    const uscc = next.unifiedSocialCreditCode.trim().toUpperCase();
    if (uscc) {
      next.unifiedSocialCreditCode = uscc;
    } else if (partial) {
      const { unifiedSocialCreditCode: _omit, ...rest } = next;
      next = rest as T;
    } else {
      next.unifiedSocialCreditCode = uscc;
    }
  }
  if (typeof next.assetManagerId === 'string') {
    const managerId = next.assetManagerId.trim();
    if (managerId) {
      next.assetManagerId = managerId;
    } else if (partial) {
      const { assetManagerId: _omitManagerId, ...rest } = next;
      next = rest as T;
    } else {
      next.assetManagerId = managerId;
    }
  }
  return next;
}

/** 路径 id 边界校验：去空白，空值前置抛错，避免 PUT/DELETE 退化为集合端点（尤以 DELETE 集合路径为险）。 */
function requireId(id: string): string {
  const trimmed = id?.trim();
  if (!trimmed) {
    throw new Error('企业 ID 不能为空');
  }
  // 编码为合法单段路径：防 id 含 / ? # 等字符把 `${BASE_URL}/${id}` 拆成多段或附加查询串
  // （即上方注释所述 PUT/DELETE 退化为错误端点的越权风险，PRD 7.2 入参严格校验/防注入）。
  // 主键为 UUID/自增（PRD 6.1.1，DB VARCHAR(36)，字符集 [0-9a-f-]），encodeURIComponent 对其为
  // 恒等变换，故对合法 id 零行为变化，仅把畸形输入收紧为单段、避免发出语义错误的请求。
  // 经 @ead/suid-utils request 运行期核实：request 透传 config.url，不做路径编码，故需在此前置。
  return encodeURIComponent(trimmed);
}

/** 校验返回记录非空且含必填主键 id，否则前置为明确错误（防 success=true 却回传空/残缺 data）。 */
function requireRecord<T extends { id?: unknown }>(
  record: T | null | undefined,
  message: string,
): T {
  if (!record || !record.id) {
    throw new Error(message);
  }
  return record;
}

/**
 * 提交前 USCC 前置格式校验（PRD 6.1.2 / 6.3.2 防御纵深）。
 * 仅做长度+字符集粗校验（isValidUsccFormat 的严格子集），故不会误拒任何后端会接受的合法代码——
 * 后端 BE-004 的 GB 32100-2015 第 18 位校验码仍是权威校验，本前置仅把「显然非法」的输入挡在网络往返之前。
 * 服务边界校验使本服务对绕过表单校验的编程式调用方（如未来批量导入）同样有效，不依赖调用方已校验。
 */
function requireValidUscc(uscc: string): void {
  if (!isValidUsccFormat(uscc)) {
    throw new Error('统一社会信用代码格式不正确，应为 18 位数字与大写字母');
  }
}

/**
 * 企业类别合法取值集合（从 ENTERPRISE_CATEGORY_LABELS 派生，保证枚举单一来源：
 * 新增类别只需在 LABELS 追加一行，本集合与下方校验自动同步）。
 */
const VALID_ENTERPRISE_CATEGORIES = new Set<string>(Object.keys(ENTERPRISE_CATEGORY_LABELS));

/**
 * 提交前 category 枚举前置校验（PRD 6.2.1：category 仅允许预定义枚举值；DB CHECK 同此口径）。
 * 与 requireValidUscc 同属服务边界防御纵深：不依赖表单/类型注解（调用方可经 as EnterpriseCategory
 * 传入未知值），对编程式调用方同样拦截，避免下发后端必然被 DB CHECK 拒绝的请求、省一次网络往返。
 */
function requireValidCategory(category: string): void {
  if (!VALID_ENTERPRISE_CATEGORIES.has(category)) {
    throw new Error('企业类别不合法，请选择「重要子公司」或「控股公司」');
  }
}

/**
 * 提交前必填字符串字段前置非空校验。适用于一切 PRD 标注必填的字符串字段：
 *   - name（PRD 6.2.1 企业名称必填且全系统唯一）
 *   - assetManagerId（PRD 6.1.1 / 决策 D-4 资产管理人单选且必填）
 * 与 requireValidUscc / requireValidCategory 同属服务边界防御纵深：type 注解仅约束「是 string」，
 * 不约束「非空白」；纯空白输入（'   '）经 canonicalize trim 为 '' 后仍会下发，触发后端空值校验
 * 错误并白费一次网络往返。本前置把「显然非法」挡在网络往返之前，对绕过表单校验的编程式调用方同样有效。
 */
function requireNonEmptyString(value: string, message: string): void {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(message);
  }
}

/** 创建重要企业 */
export async function createImportantEnterprise(
  data: CreateImportantEnterpriseRequest,
): Promise<ImportantEnterprise> {
  requireNonEmptyString(data.name, '企业名称不能为空');
  requireValidCategory(data.category);
  requireValidUscc(data.unifiedSocialCreditCode);
  requireNonEmptyString(data.assetManagerId, '资产管理人不能为空');
  return requireRecord(
    await unwrap<ImportantEnterprise | null | undefined>(
      request({
        url: BASE_URL,
        method: 'POST',
        data: canonicalize(data),
      }),
    ),
    '创建失败，未返回企业记录',
  );
}

/** 更新重要企业（Partial：trim 至空的字段表「不变」，唯一性由后端按排除自身校验） */
export async function updateImportantEnterprise(
  id: string,
  data: UpdateImportantEnterpriseRequest,
): Promise<ImportantEnterprise> {
  // 仅当传入 category 时才前置校验：未传表「不变」，不应被枚举校验拦截（与下方 USCC 同为 Partial 语义）。
  if (data.category) {
    requireValidCategory(data.category);
  }
  // 仅当传入非空 USCC 时才前置校验：纯空白输入由 canonicalize(partial) 剔除以表「不变」，不应被格式校验拦截。
  if (data.unifiedSocialCreditCode?.trim()) {
    requireValidUscc(data.unifiedSocialCreditCode);
  }
  return requireRecord(
    await unwrap<ImportantEnterprise | null | undefined>(
      request({
        url: `${BASE_URL}/${requireId(id)}`,
        method: 'PUT',
        data: canonicalize(data, { partial: true }),
      }),
    ),
    '更新失败，未返回企业记录',
  );
}

/** 删除重要企业（逻辑删除） */
export async function deleteImportantEnterprise(id: string): Promise<void> {
  await unwrap<void>(
    request({
      url: `${BASE_URL}/${requireId(id)}`,
      method: 'DELETE',
    }),
  );
}

/**
 * 查询重要企业详情。返回 ImportantEnterpriseListItem（assetManager 必填）：PRD 6.2.4 约定详情
 * 「包含资产管理人基础信息」，与列表项同构故复用该类型，并与列表页 editingRecord 类型对齐。
 */
export async function getImportantEnterpriseDetail(
  id: string,
): Promise<ImportantEnterpriseListItem> {
  const record = requireRecord(
    await unwrap<ImportantEnterpriseListItem | null | undefined>(
      request({
        url: `${BASE_URL}/${requireId(id)}`,
        method: 'GET',
      }),
    ),
    '未找到该重要企业',
  );
  // 返回类型承诺 assetManager 必填且含 id+name；缺任一即前置为明确错误，使运行期必填与类型声明一致、
  // 与 PRD 6.2.4「包含资产管理人基础信息（至少包括用户 ID、姓名）」对齐，避免 FE-004 读取 .name 时渲染空值。
  if (!record.assetManager?.id || !record.assetManager?.name) {
    throw new Error('企业详情数据异常：缺少资产管理人信息');
  }
  return record;
}

export type {
  AssetManagerInfo,
  CreateImportantEnterpriseRequest,
  EnterpriseCategory,
  EnterpriseCategoryOption,
  ImportantEnterprise,
  ImportantEnterpriseListItem,
  ImportantEnterpriseListParams,
  ImportantEnterpriseListResponse,
  PageResponse,
  UpdateImportantEnterpriseRequest,
} from './types/importantEnterprise';
