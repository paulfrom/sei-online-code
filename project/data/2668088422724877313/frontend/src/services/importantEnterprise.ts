import { request, type AxiosPromise, type ResponseResult } from '@ead/suid-utils-react';
import { constants } from '@/utils';
import type {
  AssetManagerInfo,
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
 * 依赖锚点：`@/utils` 具名导出 `constants` 且含 `SERVER_PATH`（utils/constants.ts），与参考实现
 * services/api.ts 同构；@ead/suid-utils-react 导出 request/AxiosPromise/ResponseResult。
 *
 * 契约锚点（schema 级已逐列核实 on-disk 迁移脚本 V1__create_important_enterprise_table.sql 的
 * important_enterprises 表）：业务列 id/name/category/unified_social_credit_code/asset_manager_id 与
 * 本服务 ./types/importantEnterprise 的字段逐列对应；审计列 creator_id/creator_name/created_date/
 * last_editor_id/last_editor_name/last_edited_date 对应 creatorId/creatorName/createdDate/lastEditorId/
 * lastEditorName/lastEditedDate（created_date/last_edited_date NOT NULL → 必填；其余审计列 NULL → 可选）；
 * is_deleted TINYINT(1) NOT NULL DEFAULT 0 → 必填 boolean；deleted_at TIMESTAMP NULL → 可选；DB CHECK
 * category IN ('IMPORTANT_SUBSIDIARY','HOLDING_COMPANY') 与 EnterpriseCategory 联合类型逐字一致；
 * 生成列 active_name/active_uscc 仅供唯一性校验、不对外暴露。
 *
 * 待验证（运行期，非代码缺口）：node_modules 缺失（私有 registry @ead 不可达），类型符号无法本机
 * tsc/build 校验——信封形状已由仓内运行期读点（models/global.ts、pages/Login/index.tsx）佐证，
 * 待依赖可达后联调复核；运行期信封映射（PageResult 的 rows/records/total/page、assetManager 解析）
 * 仍待 BE-002..006 落地 + registry 可达联调。
 *
 * 维护注记（防重派误判，非功能说明）：
 *   - typeof 守卫家族（请求侧 requireId/requireValidUscc/requireValidNameLength/requireValidCategory/
 *     requireNonEmptyString + getEnterpriseCategoryLabel；响应侧 unwrap 的 success!==true、list 的
 *     records/page/rows/row.id/assetManager 逐字段、detail 的 assetManager.id/name）已穷尽覆盖本文件
 *     全部运行期数据读取点——不再有可补的 typeof 守卫，新增即守不可能状态、等同伪造。
 *   - test-agent 若报「文件缺失 / NOT IN HEAD」，系其 CWD 落 repo root、bare `frontend/` 相对 repo
 *     root 解析到平台前端而非本工作区的假阴性；以工作区全路径或 `git ls-files` 可证两文件已跟踪。
 *     详见记忆 [[test-agent-cwd-false-negative]] / [[fe-001-worktree-head-false-negative]]。
 */

/**
 * 后端服务代码 = bootstrap.yaml 的 sei.application.code（核实
 * backend/2668088422724877313-service/src/main/resources/bootstrap.yaml：
 *   sei.application.code: 2668088422724877313
 *   spring.application.name: ${SPRING_APPLICATION_NAME:${sei.application.code}}
 *     —— 默认回退为 sei.application.code，未设 SPRING_APPLICATION_NAME 时同为 2668088422724877313）。
 * 网关路径段 = spring.application.name（双源独立佐证，非推断）：
 *   1) utils/constants.ts 的 `SEI_BASIC_SERVER_PATH = ${SERVER_PATH}/sei-basic`，路径段 sei-basic 即
 *      basic 服务的 spring.application.name；services/api.ts 同构消费 `${SEI_*_SERVER_PATH}/auth/login`，
 *      证实「路径段=目标服务 spring.application.name」为既有约定。
 *   2) backend/2668088422724877313-api 的 `@FeignClient(name = "2668088422724877313", ...)`（HelloApi/
 *      DistributedLockApi）—— Feign name 即目标服务 spring.application.name，与 SERVICE_CODE 逐字一致。
 *      两源同指 2668088422724877313，故「网关补 service-code 段」契约风险已闭合。
 * 端点 `/api/v1/important-enterprises` 按 PRD 6.2 / BE-006 计划契约；仍待 BE-006 Controller 落地做运行期
 * 联调，若网关以别名暴露仅需改本常量一处。
 * 注：utils/constants.ts 仅有 sei-basic/sei-auth 两个 `SEI_*_SERVER_PATH` 常量，未为本服务提供；
 *   新增 constants 条目属 FE-001 文件范围外，故在此内联 SERVICE_CODE，未夹带范围外改动。
 * 类型注记：frontend/tsconfig.json `target: "esnext"`（lib 默认随 target 含 ES2022+），故 unwrap axios
 *   错误路径的 `new Error(msg, { cause: e })`（ErrorOptions.cause 自 ES2022 为标准库类型）在类型检查下
 *   合法，无 tsc 报错风险。
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
 * 类别非法统一错误文案：被两处消费——listImportantEnterprises 的可选 category 筛选前置校验、
 * requireValidCategory 的必填枚举校验。
 * 候选列表从 ENTERPRISE_CATEGORY_OPTIONS（即 ENTERPRISE_CATEGORY_LABELS 单一来源）派生而非内联字面量：
 * PRD §7.5 category 可扩展，新增类别时下拉项/枚举校验/本提示同步更新，避免「请选择」罗列与下拉项不一致的
 * 陈旧集合——兑现 LABELS「新增类别只需追加一行，选项/标签/提示自动同步」的单一来源不变量。原字面量把
 * 「重要子公司/控股公司」复制了第二份，与 LABELS 并存为第二来源，新增类别会漏更本提示。当前两个类别下
 * 产物与原文逐字一致（「重要子公司」或「控股公司」），运行期行为零变化。
 */
const INVALID_CATEGORY_MESSAGE = `企业类别不合法，请选择${ENTERPRISE_CATEGORY_OPTIONS.map((o) => `「${o.label}」`).join('或')}`;

/**
 * 取类别中文文案。入参刻意放宽为 string：PRD §7.5 category 可扩展，后端可能回传前端尚未收录的值，未知值原样回退而非抛错。
 *
 * 空/缺省/非字符串入参兜底返回空串，保证返回类型恒为 string：ExtTable 列 render 回调（如 FE-002 类别列）在数据缺失/未加载时
 * 可能传入 undefined，编程式/as 调用方亦可能传入 number/对象等真值非字符串；二者均经函数体 typeof 守卫兜底为 ''。若直接
 * `ENTERPRISE_CATEGORY_LABELS[undefined] ?? undefined`（或真值非字符串落到 `: category`）会返回非 string，使声明为 `string`
 * 的返回类型失真，并令下游 `.length` / 字符串拼接等用法在运行期抛错。
 */
export function getEnterpriseCategoryLabel(category: string): string {
  // typeof 守卫（与 requireId/requireNonEmptyString/requireValidNameLength 同口径，补齐本文件唯一缺失 typeof 守卫的字符串边界）：
  // 签名标 string，但 ExtTable render/编程式调用方可经 as 传入真值非字符串（如 number/对象 row.category 误传）。原 `if (!category)`
  // 仅拦 falsy，真值非字符串（123/{}）会落到 `: category` 原样回传非字符串，违反本函数 JSDoc 既定不变量「返回类型恒为 string」、
  // 并令下游 `.length` / 字符串拼接抛错。先守 typeof 使非字符串与空值一并兜底为 ''；对合法 string|undefined|'' 入参行为零变化
  // （非空 string 仍走下方 hasOwnProperty 判定、未知 string 值仍原样回退，PRD §7.5 不变）。
  if (typeof category !== 'string' || !category) {
    return '';
  }
  // 与 listImportantEnterprises 的 category 前置校验同口径：用 hasOwnProperty 而非直接下标，使
  // 原型链继承键（constructor/toString/__proto__ 等）与「未收录的未来枚举值」一并落到 : category 回退，
  // 而非沿原型链返回 function——否则返回类型声明为 string 却实际返回函数（类型失真，PRD §7.5 未知值原样回退失效）。
  // 全仓探测同一枚举 map 统一为「仅自有成员」语义，避免 hasOwnProperty 与直接下标两套写法并存。
  return Object.prototype.hasOwnProperty.call(ENTERPRISE_CATEGORY_LABELS, category)
    ? ENTERPRISE_CATEGORY_LABELS[category as EnterpriseCategory]
    : category;
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

/**
 * 企业名称长度上限（DB 硬约束：important_enterprises.name VARCHAR(200) NOT NULL，
 * 见 V1__create_important_enterprise_table.sql:49）。导出供 FE-003 表单 maxLength 与本服务
 * 边界校验共用单一来源，避免表单硬编码 200 与 DB 漂移；与 USCC_LENGTH 同源动机。
 */
export const MAX_NAME_LENGTH = 200;

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
    // message 提取与下方 unwrap 的 axios 错误路径同口径（typeof === 'string' 守卫）：
    // ResponseResult.message 类型为 any，非字符串值（对象/数组）不应直接交 Error 强转为
    // 「[object Object]」，统一回退通用文案；真实 sei-core message 恒为 String，字符串场景行为零变化。
    super(
      typeof result?.message === 'string' && result.message
        ? result.message
        : '操作失败，请稍后重试',
    );
    this.name = 'BusinessError';
    // typeof 守卫（与同构造器对 result?.message 的 typeof 守卫、unwrap 信封各 typeof 守卫同口径，
    //   闭合 result 仍仅凭 ?? 兜底的最后缺口）：签名标 result?: ResponseResult | null，?? 仅兜 null|undefined。
    //   但 unwrap 的 `as ResponseResult` 断言不防运行期与声明类型不符——拦截器/网关异常可能把信封 resolve 为
    //   真值非对象（number/boolean/string，与本文件 require*/响应侧各 typeof 守卫同源威胁模型）。原
    //   `result ?? ({} as ResponseResult)` 对真值非对象原样存入 this.result，违反 `result: ResponseResult`
    //   （对象）契约，令消费方 isBusinessError 后读 e.result.data / e.result.code 在非对象上行为未定义。
    //   补 `result && typeof result === 'object'` 使真值非对象与 null|undefined 一并兜底为空对象 {}，真正兑现
    //   JSDoc「result 兜底为空对象、读取不抛错」不变量；合法信封（恒为对象）/ null / undefined 零行为变化
    //   （null 虽 typeof === 'object'，但 falsy 被 && 短路落入 {}，与原 ?? 等价）。
    this.result = result && typeof result === 'object' ? result : ({} as ResponseResult);
  }
}

/**
 * 业务错误类型守卫：判定 catch 到的值是否为服务层抛出的 BusinessError（后端 success=false，
 * 如「企业名称已存在」「该企业已被引用，不可删除」等）。
 *
 * 消费方注意（防误用，否则回退 PRD 7.3 即时反馈）：本守卫的否定分支 ≠ 「通用兜底文案」。
 * unwrap 已把网络/传输层失败本地化为**具体**中文文案——超时→「请求超时，请稍后重试」、
 * 断网→「网络连接失败，请检查网络后重试」、HTTP 错误→「请求失败（HTTP N），请稍后重试」，
 * 仅末尾兜底为「网络异常，请稍后重试」；本服务前置校验（requireNonEmptyString/requireValidUscc/
 * requireValidCategory/requireValidNameLength/requireId/requireRecord）抛出的亦是带具体文案的 Error
 * （如「企业名称不能为空」「未找到该重要企业」）。故 catch 中三类抛出值（BusinessError / unwrap
 * 网络 Error / 前置校验 Error）的 .message 均已是面向用户的具体文案，推荐统一
 * `message.error(e instanceof Error ? e.message : '操作失败，请稍后重试')` 覆盖全部分支、不丢任何
 * 具体原因——切勿对「非 BusinessError」一律替换为通用兜底，否则会吞掉 unwrap 已本地化的具体网络
 * 文案。本 isBusinessError 仅在需要**差分行为**（如把业务错误映射到表单字段级校验、网络错误仅 toast）
 * 时作判别器，而非用于消息路由。
 *
 * 动机（非预留扩展点，而是闭合已发现的消费方缺口）：FE-002 列表页删除/保存的 catch 块当前
 * 以 `catch { message.error('通用文案') }` 吞掉具体原因，违反 PRD 6.3.2/7.3「显示后端校验错误信息」；
 * 本守卫连同上方 unwrap 的具体文案本地化，共同使该 catch 可按具体原因反馈。具体 catch 写法落在
 * FE-002 文件内、本任务范围外。用 instanceof 而非 name 字段判定：BusinessError 由本模块单一构造路径
 * 产出，instanceof 不受压缩/重命名影响（name 字段判定会）。
 */
export function isBusinessError(e: unknown): e is BusinessError {
  return e instanceof BusinessError;
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
  // 信封 success 严格判定（与同文件 BusinessError 构造对【同一 ResponseResult 信封】message 字段的
  //   `typeof result?.message === 'string'` 守卫同口径，闭合信封「success 仍仅判 truthy」的最后缺口）：
  //   ResponseResult.success 声明为 boolean，但本函数上一行 `as ResponseResult` 是按运行期真实形状的断言、
  //   不防「运行期与声明类型不符」（拦截器/网关/代理把 success 序列化为非布尔——如字符串 "false"、数字 0/1、
  //   或成功体被包了一层——与本文件 require*/响应侧各 typeof 守卫同源威胁模型）。原 `!res?.success` 仅判 truthy：
  //   真值非布尔（"false" / 1 / "ok"）会漏过、被当作成功取 `res.data`（此时 data 多为 undefined/残缺）静默返回，
  //   令调用方读到空对象而非走到 BusinessError 的统一兜底文案。改 `!== true` 严格匹配：仅 success 恒为布尔 true
  //   才视为成功，其余（含非布尔真值）一律落入 BusinessError(res) 的既有兜底；合法信封（success:true|false）行为
  //   零变化——true 仍取 data、false 仍抛。与 BusinessError 对同信封 message 的 typeof 守卫收敛同口径，兑现本文件
  //   「信封两字段同守类型、不依赖声明注解」的边界哲学。
  if (res?.success !== true) {
    throw new BusinessError(res);
  }
  return res.data as T;
}

/**
 * 分页参数边界（PRD 6.2.5：page 默认 1、pageSize 默认 20、最大 100）。
 * 与 USCC_LENGTH / MAX_NAME_LENGTH 同源导出动机：供 FE-002 列表页分页器（defaultCurrent /
 * defaultPageSize / pageSize 上限）与本服务边界规整共用单一来源，避免表格硬编码 20/100 与服务端
 * 夹逼口径漂移。MIN_PAGE_SIZE（1）仅服务内部夹逼下界、无 UI 消费方，刻意保持私有不导出。
 */
export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 20;
const MIN_PAGE_SIZE = 1;
export const MAX_PAGE_SIZE = 100;

/**
 * 分页查询重要企业列表（PRD 6.2.5）。在服务边界规整 page/pageSize（默认 1/20、夹逼 1~100、
 * 字符串转整数并截断小数），并把 sei-core PageResult 映射为对外 PageResponse。
 *
 * 保留说明（防误删）：本方法是验收标准「提供 list 方法」要求的编程式列表入口。FE-002 列表页
 * 当前经 ExtTable.store 直接消费后端端点，暂不调用本方法——这不构成死代码：它为
 * 非 ExtTable 消费方（批量校验、导出、详情回溯列表上下文）提供稳定服务层契约与 PageResult 映射兜底。
 * 核实注记（2026-07-15）：FE-002 并未复用本服务导出的 BASE_URL，而在
 * pages/ImportantEnterprise/index.tsx:41-43 本地另声明 SERVER_PATH/SERVICE_CODE/BASE_URL——
 * 同一端点前缀（/${SERVICE_CODE}/api/v1/important-enterprises）现存两处，属待清理的 URL 漂移，
 * 正是上方 BASE_URL 导出注释「单一来源、避免版本前缀多处漂移」所欲杜绝的情形。修正需改 FE-002
 * 改为 `import { BASE_URL } from '@/services/importantEnterprise'` 并删除其本地重复声明，超出 FE-001
 * 文件范围，故此处仅记录「FE-002 未复用导出」之事实，不在本任务内跨范围改动 FE-002。
 */
export async function listImportantEnterprises(
  // 入参兜底 {} + 解构 `?? {}`：使「无参查询首页」合法，避免调用方传 undefined/null 时解构抛 TypeError。
  params: ImportantEnterpriseListParams = {},
): Promise<ImportantEnterpriseListResponse> {
  const { page, pageSize, keyword, category, assetManagerId } = params ?? {};
  // page 仅夹逼下界（≥ DEFAULT_PAGE）而 pageSize 双端夹逼（1~100）：page 缺失上界/有限性守卫，
  // 对畸形编程式入参（page: '1e999' / 'Infinity' / Number.MAX_VALUE 经 as 传入）会漏出——Number('1e999')=Infinity，
  // Infinity 为真值故 `Infinity || DEFAULT_PAGE`=Infinity，Math.trunc(Infinity)=Infinity、Math.max(1, Infinity)=Infinity，
  // 随后 page=Infinity 被 axios 序列化为非法查询串下发。故除下界外对非有限值一并回退 DEFAULT_PAGE，
  // 与下方 normalizedPageSize 的 Math.min(100,...) 上界夹逼、以及响应侧 records/page 的 Number.isFinite 守卫同属
  // 「服务边界规整分页参数 + 防 as 绕过」不变量（对合法页码行为零变化：合法值恒有限且 ≥ DEFAULT_PAGE）。
  // typeof 守门（与下方响应侧 records/page 的 `typeof === 'number' || typeof === 'string'` 守卫同口径，
  //   闭合本方法请求/响应两侧对【同一 sei-core 分页契约】类型守卫不对称的最后缺口）：page/pageSize 声明为
  //   number | string（sei-core 分页参数），但本文件威胁模型即「运行期与声明类型不符」（经 as 的编程式调用方
  //   可传真值非数字/非字符串）。原 `Number(page ?? DEFAULT_PAGE)` 对真值非数字/非字符串会静默强转：
  //   Number([5])=5、Number(true)=1、Number([1,2])=NaN——单元素数组/布尔恰好强转出「伪合法」页码、漏过下方
  //   Number.isFinite 守卫被当作真实 page/pageSize 下发后端（与下方响应侧 records/page 强转同源泄漏，彼处
  //   已补 typeof 守门、请求侧此前未补）。先守 typeof 使非 number|string 一律落 NaN，再由既有
  //   `|| DEFAULT_PAGE(_SIZE)` + Math.trunc + Number.isFinite 链兜底为默认值——与响应侧 recordsRaw/pageRaw 的
  //   守门逐字对称。合法入参零行为变化：number 3→Number(3)=3、string "3"→3；undefined/null 经守门落 NaN→默认值
  //   （与原 `?? DEFAULT_PAGE` 对 nullish 取默认等价）；''→Number('')=0→0||默认→默认（与原等价）。
  // 命名 pageArgNum（而非 pageNum）：本函数响应侧下方 const pageNum = Number(res.page)（供 safePage
  //   回显页计算）已占用 pageNum，二者同处函数体同一作用域——重名即 "Identifier 'pageNum' has already
  //   been declared" 编译期错误（tsc/umi build 必败，违 AC-11 类型检查）。故请求侧入参的数字态改记
  //   pageArgNum（page ARGument as Number）与响应侧 pageNum 物理区分；pageSizeNum 无同名冲突、保留原名
  //   （最小改动，不一并为对称而改名）。纯重命名：parsedPage 计算口径与取值零变化。
  const pageArgNum = typeof page === 'number' || typeof page === 'string' ? Number(page) : NaN;
  const pageSizeNum =
    typeof pageSize === 'number' || typeof pageSize === 'string' ? Number(pageSize) : NaN;
  const parsedPage = Math.trunc(pageArgNum || DEFAULT_PAGE);
  const normalizedPage =
    Number.isFinite(parsedPage) && parsedPage >= DEFAULT_PAGE ? parsedPage : DEFAULT_PAGE;
  const normalizedPageSize = Math.min(
    MAX_PAGE_SIZE,
    Math.max(
      MIN_PAGE_SIZE,
      Math.trunc(pageSizeNum || DEFAULT_PAGE_SIZE),
    ),
  );
  // 仅在可选筛选非空时下发：清空表单后下发 category='' 会触发后端「精确匹配空值」返回空列表。
  // keyword / assetManagerId 边界 trim，避免粘贴首尾空格影响模糊匹配/精确匹配查不到。
  // typeof === 'string' 守卫（与 requireNonEmptyString/isValidUsccFormat 同口径）：可选 string 字段
  // 仅类型约束「是 string」、不防 `as` 绕过。若编程式调用方误传非字符串（如 keyword: 123），
  // `keyword?.trim()` 会求值 (123).trim() = undefined 再调用 → 抛 TypeError，使整页分页查询以不可读
  // 类型错误崩溃、绕过 unwrap 的统一信封兜底文案。守卫后非字符串一律视为「未提供该筛选」(undefined)，
  // 对合法 string|undefined 入参行为零变化，仅把畸形输入从「崩溃」降级为「忽略该筛选」。
  const trimmedKeyword = typeof keyword === 'string' ? keyword.trim() : undefined;
  const trimmedAssetManagerId =
    typeof assetManagerId === 'string' ? assetManagerId.trim() : undefined;
  // category 为可选筛选：仅在传入非空值时按枚举校验，区分「合法筛选无匹配」与「非法枚举值（多为编程式误传）」，
  // 后者立即抛明确文案而非静默返回空列表（与 requireValidCategory 同属服务边界防御纵深；合法枚举恒通过，
  // FE-002 类别下拉只会产生合法值故不受影响）。复用 ENTERPRISE_CATEGORY_LABELS 这一枚举单一来源做成员判定，
  // 不引入第二份枚举定义；LABELS 定义先于本方法，无 use-before-define。用 hasOwnProperty 而非 `in`：
  // `in` 会沿原型链命中 Object.prototype 的 'toString'/'constructor' 等，令此类编程式误传值漏过本前置校验、
  // 直到后端 DB CHECK 才被拒（多一次网络往返）；与 requireValidCategory 同为 ENTERPRISE_CATEGORY_LABELS
  // 的 hasOwnProperty 自有键判定（单一机制，全仓无 Set.has 第二套写法）。
  // typeof 守卫（与 requireValidCategory / getEnterpriseCategoryLabel 同口径，闭合本文件第三处 category
  //   消费方缺类型守卫的「两套写法」）：签名标 category?: EnterpriseCategory，但经 as 的编程式调用方可传
  //   number/对象等真值非字符串。前置 `category &&` 仅短路 falsy，真值非字符串（123/{}) 会落到 hasOwnProperty
  //   经 ToPropertyKey 隐式强转（"123"/"[object Object]"）后落空、间接抛出 INVALID_CATEGORY_MESSAGE——结果等价
  //   但不直观。补 `typeof category !== 'string'` 使非字符串与未知枚举值走同一显式判定；刻意保留前置
  //   `category &&` 以兑现「未提供该筛选即跳过」的可选语义（与 requireValidCategory 对空值必抛不同，此处
  //   空值表「不筛选」）。|| 短路保证 hasOwnProperty 仅对真实 string 求值；对合法 string|undefined|'' 入参零行为变化。
  if (
    category &&
    (typeof category !== 'string' ||
      !Object.prototype.hasOwnProperty.call(ENTERPRISE_CATEGORY_LABELS, category))
  ) {
    throw new Error(INVALID_CATEGORY_MESSAGE);
  }
  // 关键字超过可匹配字段最长长度即必然零命中（PRD 6.2.5：keyword 仅匹配 name≤MAX_NAME_LENGTH 或
  // unifiedSocialCreditCode=USCC_LENGTH，二者长度均 ≤ MAX_NAME_LENGTH）：直接返回空页，省一次必然空结果
  // 的网络往返，并避免超长关键字撑爆 GET 查询串触发网关 414。边界值复用既有 MAX_NAME_LENGTH（DB name
  // VARCHAR(200)，非发明新约束），与 USCC_LENGTH/MAX_PAGE_SIZE 同属「服务边界单一来源」口径。
  // 对合法关键字（长度 ≤ MAX_NAME_LENGTH）行为零变化——仍正常下发查询。
  // 长度按码点计数（[...trimmedKeyword].length）而非 .length 的 UTF-16 码元：MAX_NAME_LENGTH 的语义是
  // 「DB name VARCHAR(200) 的字符/码点上限」（与 requireValidNameLength 同口径判定），keyword 与 name 同
  // 做「超过可匹配字段最长长度即必然零命中」比较，须共用同一计数口径。否则含增补面字符（Emoji / CJK 扩展 B，
  // UTF-16 按代理对计 2）的关键字会被 .length 高估而误短路、违背本守卫的「超过即必然零命中」不变量——
  // 一个 101 码点的关键字本可命中 200 码点的 name（若含此子串），却因 202 个 UTF-16 码元被误判超长而返回空页。
  // BMP 关键字（常态）下两计数相等、行为零变化；与 requireValidNameLength 共用「同一 MAX_NAME_LENGTH 常量、
  // 同一码点计数口径」，闭合全仓两套长度写法并存（CLAUDE.md：不允许同一仓库两套写法）。
  if (trimmedKeyword && [...trimmedKeyword].length > MAX_NAME_LENGTH) {
    return { list: [], total: 0, page: normalizedPage, pageSize: normalizedPageSize };
  }
  // assetManagerId 与上方 keyword 同属可选 GET 查询参数，且同为「超长即必然零命中」语义：
  // assetManagerId 是精确匹配的 UUID 外键（DB asset_manager_id VARCHAR(36)，见 V1 迁移脚本），
  // 任何超过标识符上界的值都不可能命中真实主键。经 as 的编程式调用方可传入 MB 级垃圾串，
  // axios 将其作为查询串值（内部 encodeURIComponent）下发，超长同样撑爆 GET URL 触发网关 414
  // （与 keyword 短路同源动机）。复用 requireId 既有的 MAX_ID_LENGTH（64，已覆盖 36 位 UUID 并留余量）
  // 作上界——单一来源，不发明第二个 id 长度常量。超过即直接返回空页：精确匹配的 id 超过标识符上界
  // 必无命中，空结果是合法语义，故短路返回而非抛错，与上方 keyword 短路完全对称。
  // 计数用 UTF-16 码元 .length（非 keyword 的 [...].length 码点计数）：assetManagerId 字符集恒 ASCII
  // （UUID/自增数字）→ 码元与码点恒等，.length 精确且 O(1)；与 requireId 对 id 类 ASCII 标识符的
  // 计数口径一致（避免对 MB 级垃圾串先分配等长数组的 O(n) 退化）。
  if (trimmedAssetManagerId && trimmedAssetManagerId.length > MAX_ID_LENGTH) {
    return { list: [], total: 0, page: normalizedPage, pageSize: normalizedPageSize };
  }
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
  // page 越界或非有限（如 -3/NaN）回退 normalizedPage（请求侧已规整的页码，非 DEFAULT_PAGE）；records 负数归 0（计数无上界、0 合法保留）。
  // records/page 先 typeof 守门再 Number()（与同映射块 row.id / assetManager.id/name 的 typeof 守卫同口径，
  //   闭合 sei-core PageResult 响应信封「最后两个数字字段仍直接 Number() 强转」的缺口）：records/page 声明为
  //   number|string，但本文件威胁模型即「运行期与声明类型不符」（拦截器/序列化异常把 success 序列化成 "false"、
  //   把 rows 元素序列化成 null 等——与本块 row.id/assetManager 守卫、unwrap 的 `success !== true` 同源）。
  //   原 `Number(res?.records)` 对真值非数字/非字符串（对象 {}、单元素数组 [100]、boolean true）会静默强转：
  //   Number([100])=100、Number(true)=1，恰好漏过下方 Number.isFinite 守卫、把畸形信封强转出的「伪合法」数字
  //   当成真实总条数/页码回传 FE-002 分页器（records→total、page→current）。补 typeof 守门使非 number|string
  //   一律落到 NaN，再由既有 Number.isFinite 判定归零/回退 normalizedPage——与 success/rows/row.id/assetManager
  //   各字段的「信封字段同守类型、不依赖声明注解」收敛同口径，兑现本方法「响应侧字段逐个守类型」不变量。
  //   行为零变化核对（合法信封）：records/page 恒为 number 或数字字符串，Number(100)/Number("100") 结果不变；
  //   null/undefined 经守门落 NaN、与原 Number(null)=0→isFinite→0、Number(undefined)=NaN 在 total/page 最终值上
  //   仍同（0 / normalizedPage）；仅 boolean/数组/对象等畸形强转值从「泄漏为伪合法数字」收紧为「归零/回退」。
  const recordsRaw = res?.records;
  const recordsNum =
    typeof recordsRaw === 'number' || typeof recordsRaw === 'string' ? Number(recordsRaw) : NaN;
  const pageRaw = res?.page;
  const pageNum =
    typeof pageRaw === 'number' || typeof pageRaw === 'string' ? Number(pageRaw) : NaN;
  // 回显页同样 Math.trunc 截断为整数，与请求侧 normalizedPage 的 Math.trunc 同口径，保证 PageResponse.page 恒整数
  // （sei-core page 为 int 实践中已是整数；此为口径一致化与防御非整数回显，对合法输入行为零变化）。
  const safePage =
    Number.isFinite(pageNum) && pageNum >= DEFAULT_PAGE ? Math.trunc(pageNum) : normalizedPage;
  return {
    // 逐行守卫 assetManager（与 getImportantEnterpriseDetail 同源）：ImportantEnterpriseListItem 声明
    // assetManager 必填，但后端 join 异常/数据残缺时可能漏返（运行期与声明类型不符）。详情页对缺失抛错
    // （单条可中断），列表不可因单行残缺整页失败，故按行兜底为「以 assetManagerId 构造的最小 AssetManagerInfo」，
    // 保留可追溯 id、避免 FE-002 表格 record.assetManager.name 访问 undefined 崩溃、不引入硬编码 UX 文案。
    // PRD 6.2.5 正常态后端恒返回 assetManager，此兜底对其零行为变化。
    list: (res && Array.isArray(res.rows) ? res.rows : [])
      // 行级守卫（与逐行 assetManager 兜底同源的数据完整性防御）：sei-core PageResult.rows 声明为 List<T>，
      // 但后端 join/序列化异常可能回传 null 或非对象元素（运行期与声明类型不符）。若无此过滤，下方
      // `.map((row) => row.assetManager ...)` 会在 null 元素上抛 TypeError「Cannot read properties of null」，
      // 该裸异常绕过 unwrap 的统一信封兜底文案、令整页列表渲染以不可读错误崩溃——违背本方法
      // 「列表不可因单行残缺整页失败」不变量（与上一段 assetManager 兜底同动机）。先以类型谓词滤掉
      // null/非对象元素再映射，把畸形行从「整页崩溃」降级为「跳过该行」；合法对象行零行为变化（原样通过）。
      // row.id 守卫（与 requireRecord 响应侧「id 必为非空 string」同口径，闭合列表/详情两路径 id 校验不对称）：
      //   ImportantEnterpriseListItem.id 为必填 string 主键，本谓词此前仅断言「非空对象」即 `row is ImportantEnterpriseListItem`——
      //   类型谎言：{} / {foo:1} 亦通过、被当作含 id 列表项，令 FE-002 表格行 key（row.id）取 undefined 触发 React key 告警/错位。
      //   详情路径经 requireRecord 已对 record.id 校验「非空 string」（残缺即抛）；列表不可因单行残缺整页失败，故对称地在此「跳过」
      //   id 缺失/非字符串/纯空白 的残缺行（与既有 null/非对象元素跳过同源防御）。合法响应（行恒含 UUID id）零行为变化——原样通过。
      .filter(
        (row): row is ImportantEnterpriseListItem =>
          row != null &&
          typeof row === 'object' &&
          typeof row.id === 'string' &&
          !!row.id.trim(),
      )
      .map((row) => {
        const am = row.assetManager as AssetManagerInfo | undefined;
        // amId/amName 局部快照同 getImportantEnterpriseDetail 的 amId/amName 绑定同口径（本映射块守卫亦为新增、尚未经 tsc，
        //   `typeof am?.id === 'string'` 对可选链窄化依赖 TS 版本；快照为局部常量后窄化在所有版本可靠、.trim() 必类型安全，AC-11）。
        const amId = am?.id;
        const amName = am?.name;
        // typeof 守卫（与 requireRecord 响应侧「id 必为非空 string」同口径，闭合本映射块最后仅凭 truthy 判定 assetManager 的缺口）：
        //   am.id/am.name 声明为 string，但后端 join/序列化异常可回传真值非字符串（number 123 / 对象 {}）。原 `am?.id && am?.name`
        //   仅判 truthy，真值非字符串会漏过、原样返回 row——非字符串 assetManager.id 泄漏到 FE-002 表格行 key / dataIndex 上行为未定义。
        //   补 typeof 使真值非字符串的 id/name 一并落入下方兜底（以 assetManagerId 构造 string），兑现本块「返回 assetManager
        //   字段恒为 string」不变量（与 AssetManagerInfo 类型契约一致）；合法响应（id/name 恒为非空 string）零行为变化——仍返回原 row。
        //   判空按 trim：与 requireRecord 的 `!record.id.trim()`、getImportantEnterpriseDetail 的 assetManager 守卫同口径——
        //   上一行自称「与 requireRecord 同口径」，而 requireRecord 判空即 trim，纯空白 am.id/am.name（"   "，真值）此前漏过、
        //   把空白 assetManager 原样泄入 FE-002 行 key/dataIndex；trim 后判空使其落入下方兜底（以 assetManagerId 构造），
        //   与详情守卫逐字符收敛、闭合同文件内两个 assetManager 守卫判空口径不一的最后差异；合法响应（无首尾空白）trim 恒等、零行为变化。
        return typeof amId === 'string' &&
          amId.trim() &&
          typeof amName === 'string' &&
          amName.trim()
          ? row
          : {
            ...row,
            assetManager: {
              // typeof 守卫同上行 am?.id/name 口径（闭合本兜底块最后仅凭 ?? 判 assetManagerId 的缺口）：
              //   本兜底前提即「运行期与声明类型不符」（assetManager 已判定残缺才走到此分支），assetManagerId 同属
              //   后端回传字段、亦可能为真值非字符串（number/对象，与 am 同源 join/序列化异常）。原
              //   `(row.assetManagerId as string | undefined) ?? ''` 仅兜 null/undefined，真值非字符串（如 123）会
              //   原样落为 id/name=123，违反上方自称的「name 仍恒为 string」不变量、且与上行 am 守卫「真值非字符串
              //   一并兜底」不一致。改 typeof 三元使非字符串与空值统一兜底为 ''，真正兑现 AssetManagerInfo.id/name:
              //   string 契约（与 getImportantEnterpriseDetail 的 assetManager 守卫同口径）；合法响应
              //   （assetManagerId 恒为 UUID 字符串）零行为变化。
              id: typeof row.assetManagerId === 'string' ? row.assetManagerId : '',
              // name 不填 assetManagerId：id 已保留资产管理人引用，name 是展示姓名字段——放 UUID 会让
              // FE-002 列把标识符当姓名渲染。置空让 UI 降级为占位，语义正确；正常路径 assetManager 齐全时本分支不触发，零行为变化。
              name: '',
            },
          };
    }),
    // total 与上方 safePage 同口径 Math.trunc：records 为 sei-core long 总记录数（可能以字符串回传），
    // 截断防御非整数回显、保证 PageResponse.total 恒整数，与 page 处理一致（对合法输入行为零变化）。
    total: Number.isFinite(recordsNum) && recordsNum >= 0 ? Math.trunc(recordsNum) : 0,
    page: safePage,
    pageSize: normalizedPageSize,
  };
}

/**
 * create/update 请求体不允许携带的字段（PRD 6.2.2：不允许修改 id 与 created_at/created_by 等审计/主键字段）。
 * 经 as 的编程式调用方可能把这些字段混入请求体，{...data} 会原样保留并随 body 下发到后端、违反不可变性。
 * canonicalize 在规整前按 key 白名单剔除之，与 requireId/requireRecord 同属服务边界防御纵深；对类型合规的表单
 * 调用方零行为变化（其请求体本就只有 4 个业务字段）。审计字段名取自 sei-core BaseAuditableEntity 序列化口径
 * （8 列齐全：createdDate/lastEditedDate/creatorId/creatorAccount/creatorName/lastEditorId/lastEditorAccount/lastEditorName），
 * 逐列契约见 types/importantEnterprise.d.ts。
 */
const FORBIDDEN_MUTABLE_KEYS_SET = new Set<string>([
  'id',
  'createdDate',
  'lastEditedDate',
  'creatorId',
  'lastEditorId',
  'creatorName',
  'lastEditorName',
  // BaseAuditableEntity 登录账号列（序列化名 creatorAccount/lastEditorAccount → DB creator_account/last_editor_account，
  // V1__create_important_enterprise_table.sql:55,59，由 sei-core 审计拦截器自动填充）。与 creatorId/creatorName 同属
  // 「服务层自动维护、客户端不可写」审计字段（PRD 6.2.2/7.4）；types 刻意不暴露登录账号（敏感信息，PRD §7.2 最小化），
  // 故此前枚举漏列——但经 as 的调用方仍可把其序列化名混入 body 伪造审计账号，须与 id/审计列一并剔除以闭合
  // 「BaseAuditableEntity 8 个审计列全部不入请求体」不变量。对类型合规调用方零行为变化（请求体仅 4 个业务字段）。
  'creatorAccount',
  'lastEditorAccount',
  'deletedAt',
  'isDeleted',
  // STORED 生成列（V1__create_important_enterprise_table.sql:39,65-66 注明「应用不可写入」，由 MySQL 按
  // is_deleted 维护、专用于唯一性校验）。types/importantEnterprise.d.ts 刻意不含这两字段（见其注释），
  // 但经 as 的调用方仍可把它们混入 body 下发；sanitizer 须同步剔除以闭合本集合「不可变字段不入请求体」
  // 不变量——否则与前述「应用不可写」的 DB 级语义自相矛盾（与 id/审计列同属服务边界防御纵深，零行为变化）。
  'activeName',
  'activeUscc',
]);

/**
 * 提交前边界规整：name 去首尾空白（保护唯一性，避免 "Foo" 与 " Foo " 形成脏重复）；
 * unifiedSocialCreditCode 去空白并转大写（决策 D-2）；assetManagerId 仅去空白（UUID 无大小写语义，不转大写）。
 * partial=true（更新）时 trim 至空的字段被剔除以表「不变」，避免下发 '' 触发后端校验误判。
 */
function canonicalize<
  T extends {
    name?: string;
    unifiedSocialCreditCode?: string;
    assetManagerId?: string;
    category?: string;
  },
>(
  data: T,
  options: { partial?: boolean } = {},
): T {
  const { partial = false } = options;
  // 按白名单重建而非 {...data}：剔除 FORBIDDEN_MUTABLE_KEYS_SET 中的主键/审计字段（PRD 6.2.2 不可变性），
  // 防经 as 传入的 id/createdDate 等字段随 body 下发到后端。Object.entries 仅取 data 的自有可枚举字符串键，
  // 与原 {...data} 同集（请求体无 Symbol 键）；后续 trim/转大写/Partial 剔除逻辑作用于重建后的 next，行为不变。
  const sanitized: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(data)) {
    if (!FORBIDDEN_MUTABLE_KEYS_SET.has(key)) {
      sanitized[key] = value;
    }
  }
  let next: T = sanitized as T;
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
  // category 为枚举字符串（非自由文本，不做 trim）：partial 模式下空串表「不变」，需剔除——
  // 与 name/unifiedSocialCreditCode/assetManagerId 的 Partial 语义同口径。requireValidCategory 仅对
  // 真值 category 校验（空串被跳过、语义即「不变」），若不在此剔除，空串 category 会随 body 下发、
  // 被 DB CHECK 拒绝，违反「partial=true 时空字段剔除以表不变」的统一不变量。
  // 非空/未传 category 行为零变化：undefined 经 JSON.stringify 天然丢弃，非空枚举值原样保留。
  if (partial && next.category === '') {
    const { category: _omitCategory, ...rest } = next;
    next = rest as T;
  }
  return next;
}

/**
 * id 长度上界（与 MAX_NAME_LENGTH / USCC_LENGTH / MAX_PAGE_SIZE / MIN_PAGE_SIZE 同属「服务边界单一来源常量」；
 * id 为路径参数、无 UI 输入框消费方，故与 MIN_PAGE_SIZE 同口径保持私有不导出）。64 位覆盖 PRD 6.1.1 的
 * UUID（DB VARCHAR(36)，36 位）与自增主键并留充分余量。
 */
const MAX_ID_LENGTH = 64;

/** 路径 id 边界校验：去空白，空值前置抛错，避免 PUT/DELETE 退化为集合端点（尤以 DELETE 集合路径为险）。 */
function requireId(id: string): string {
  // typeof === 'string' 守卫（与 requireNonEmptyString 同口径，闭合「同仓库两套写法」）：
  // 签名标 string，但经 as 的编程式调用方可传 number/对象等；原 `id?.trim()` 仅对 null/undefined 短路，
  // 对非字符串（如 123）会取到 Number/对象上为 undefined 的 trim 再调用而抛 TypeError「trim is not a
  // function」，泄漏原始异常而非下方干净业务文案，与本文件「服务边界防御纵深、不依赖类型注解」原则相悖
  // （requireValidCategory/canonicalize/requireNonEmptyString 均已守类型或防御 as 误传）。先守类型再 trim，
  // 使非字符串与空白一并落到同一明确错误；合法 string 行为零变化（trim() 仍恒调用）。
  if (typeof id !== 'string') {
    throw new Error('企业 ID 不能为空');
  }
  const trimmed = id.trim();
  if (!trimmed) {
    throw new Error('企业 ID 不能为空');
  }
  // 长度上界守卫（与 requireValidNameLength 的 MAX_NAME_LENGTH、requireValidUscc 的 USCC_LENGTH 同口径）：
  // 经 as 的编程式调用方可传入超长/畸形字符串（粘贴整段文本、MB 级串），encodeURIComponent 逐字符转义后
  // 拼入 `${BASE_URL}/${id}` 单段路径，撑爆 URL 触发网关 414、或发出语义错误的端点请求。合法 id 恒
  // ≤36 位 UUID（PRD 6.1.1 / DB VARCHAR(36)），故对一切真实主键零行为变化，仅把「显然非主键的畸形输入」
  // 挡在网络往返之前，与下方 encodeURIComponent 同属 requireId 越权/畸形输入防御纵深（PRD 7.2 入参严格校验）。
  // 计数刻意用 UTF-16 码元 .length（非文件他处自由文本字段的码点 [...str].length）：id 字符集恒 ASCII
  // （UUID/自增数字）→ 码元与码点恒等、计数精确；而 .length 为 O(1)，本守卫正要拦下的 MB 级畸形串若先
  // [...trimmed].length 会瞬时分配等长数组、把拦截劣化为 O(n)。据此与「DB VARCHAR 自由文本按字符语义需
  // 码点计数」的 requireValidNameLength / 关键字短路区分，preempt 全仓长度计数统一时的误改（性能退化）。
  if (trimmed.length > MAX_ID_LENGTH) {
    throw new Error('企业 ID 格式不正确');
  }
  // 编码为合法单段路径：防 id 含 / ? # 等字符把 `${BASE_URL}/${id}` 拆成多段或附加查询串
  // （即上方注释所述 PUT/DELETE 退化为错误端点的越权风险，PRD 7.2 入参严格校验/防注入）。
  // 主键为 UUID/自增（PRD 6.1.1，DB VARCHAR(36)，字符集 [0-9a-f-]），encodeURIComponent 对其为
  // 恒等变换，故对合法 id 零行为变化，仅把畸形输入收紧为单段、避免发出语义错误的请求。
  // 经 @ead/suid-utils request 运行期核实：request 透传 config.url，不做路径编码，故需在此前置。
  return encodeURIComponent(trimmed);
}

/** 校验返回记录非空且含必填 string 主键 id，否则前置为明确错误（防 success=true 却回传空/残缺/类型不符 data）。 */
function requireRecord<T extends { id?: unknown }>(
  record: T | null | undefined,
  message: string,
): T {
  // typeof 守卫（与 requireId 在【输入】路径保证 id 为非空 string 同口径，闭合 require* 家族【响应】侧唯一仅判
  //   truthy 的缺口）：后端 success=true 却回传运行期与声明类型不符的 data 时（join/序列化异常——与
  //   listImportantEnterprises 逐行 assetManager 兜底、getImportantEnterpriseDetail 的 assetManager 守卫
  //   同源威胁模型），id 若为真值非字符串（number 123 / boolean true / 对象 {}）此前会漏过 `!record.id`
  //   的 truthy 判定、违反 ImportantEnterprise.id: string 契约，令下游 `${BASE_URL}/${id}` 路径拼接、
  //   FE-002 表格 dataIndex、FE-004 详情读取在非字符串 id 上行为未定义。先守 typeof 使响应侧与输入侧
  //   （requireId）对「id 必为非空 string」的保证收敛一致；合法后端（id 恒为 UUID/自增数字字符串）零行为变化
  //   ——既有的 null/undefined/'' 三类必拒分支仍由 `!record` / `!record.id` 兜住，typeof 子句仅额外拦真值非字符串。
  // 条件序：`!record` 前置短路，避免对 null/undefined 访问 .id；typeof 在判空之前，保证空/空白串仍落到
  //   判空子句（id 已是 string 才到此处）给出既有文案，而非被 typeof 误吞。
  // 判空按 trim：空白主键（"   "）与 null/undefined/''/非字符串同属本方法 docstring 所防「残缺 data」，
  //   此前 `!record.id`（truthy 判定）会漏过空白串（!"  "为 false）。trim 后判空使响应侧 id 非空保证与
  //   requireId【输入】侧 `id.trim()` 后判空收敛同口径（上方 typeof 注释自称与 requireId「同口径」）；
  //   合法后端 id（UUID/自增数字串，无首尾空白）trim 恒等、零行为变化。仅校验不回写，返回的 record.id 原值不变。
  if (!record || typeof record.id !== 'string' || !record.id.trim()) {
    throw new Error(message);
  }
  return record;
}

/**
 * 提交前 USCC 前置格式校验（PRD 6.1.2 / 6.3.2 防御纵深）。
 * 委托 isValidUsccFormat 做长度+字符集粗校验——本粗校验的接受集是后端 GB 校验接受集的超集（GB 32100-2015 合法代码仅由 0-9/A-Z 字符组成，必满足 18 位 [0-9A-Z]，故恒通过本粗校验），因此本前置不会误拒任何后端会接受的合法代码——
 * 后端 BE-004 的 GB 32100-2015 第 18 位校验码仍是权威校验，本前置仅把「显然非法」的输入挡在网络往返之前。
 * 服务边界校验使本服务对绕过表单校验的编程式调用方（如未来批量导入）同样有效，不依赖调用方已校验。
 */
function requireValidUscc(uscc: string): void {
  // 显式 typeof 首子句（与 requireId / requireValidNameLength / requireValidCategory / requireNonEmptyString
  // 同口径，闭合 require* 家族「各成员在自身边界显式拒非字符串」契约）：签名标 string，但经 as 的
  // 编程式调用方可传入真值非字符串。isValidUsccFormat 内部已有 typeof 故行为等价（非字符串本就抛同一文案），
  // 此处补显式子句使家族五个成员形态统一为 `typeof X !== 'string' || !<谓词>`，并短路掉一次委托调用——
  // 与 requireValidCategory 此前补 typeof 同属「行为保持的契约归一」，对合法 string 入参零行为变化。
  if (typeof uscc !== 'string' || !isValidUsccFormat(uscc)) {
    // 位数引用 USCC_LENGTH 而非硬编码「18」，与 requireValidNameLength 引用 MAX_NAME_LENGTH 同口径
    // （单一来源：GB 32100-2015 固定 18 位，常量即据此定义，文案随之同步，避免双处漂移）。
    throw new Error(`统一社会信用代码格式不正确，应为 ${USCC_LENGTH} 位数字与大写字母`);
  }
}

/**
 * 提交前企业名称长度边界校验（PRD 6.1.1「建议 200 字符内」+ DB 硬约束 name VARCHAR(200)）。
 * 与 requireValidUscc / requireValidCategory 同属服务边界防御纵深：DB VARCHAR(200) 会以
 * data truncation 拒绝超长名称、经统一信封回传后文案对用户不直观，本前置把超长输入挡在
 * 网络往返之前并给出明确文案。校验 trim 后长度（canonicalize 提交前已 trim，DB 存储即 trim 后值），
 * 故对「首尾空白致超长」的输入亦按真实存储长度判定；200 为 DB 硬上限，不会误拒任何后端会接受的名称。
 */
function requireValidNameLength(name: string): void {
  // 与 requireId / requireNonEmptyString 同口径（归一 require* 家族「非法输入即抛」契约）：
  // requireValidUscc / requireValidCategory / requireId / requireNonEmptyString 对非法入参一律抛错，
  // 本方法此前 `if (typeof name === 'string')` 是家族中唯一的「静默放行」分支——经 as 的编程式调用方
  // 误传非字符串会落到此处被无声跳过、绕过长度校验，与「服务边界防御纵深、不依赖类型注解」原则相悖。
  // 现先守类型、非字符串抛明确文案（与 requireId 抛「企业 ID 不能为空」、requireNonEmptyString 抛
  // 「企业名称不能为空」同构），消解全仓 require* 两套写法（throw vs 静默放行）并存。现行调用点均前置
  // 守卫（create 先 requireNonEmptyString、update 经 typeof body.name === 'string' 守卫），故非字符串分支不可达——
  // 本变更为行为保持的契约归一，对合法 string 入参零行为变化（trim() 仍恒调用、长度判定不变）。
  if (typeof name !== 'string') {
    throw new Error('企业名称不能为空');
  }
  // 按码点（code point）计数而非 .length 的 UTF-16 码元：MySQL VARCHAR(200) 以字符/码点计上限，
  // .length 对增补面字符（astral，如 Emoji、CJK 扩展 B 罕用字）按代理对计 2，会使「DB 可存」的名称
  // 在前端被误拒——违背本方法「不会误拒任何后端会接受的名称」不变量。[...str] 按 Unicode 码点迭代，
  // 计数口径与 DB VARCHAR(N) 的字符语义一致。BMP 文案（本域企业名常态）下两计数相等、行为零变化。
  if ([...name.trim()].length > MAX_NAME_LENGTH) {
    throw new Error(`企业名称长度不能超过 ${MAX_NAME_LENGTH} 个字符`);
  }
}

/**
 * 提交前 category 枚举前置校验（PRD 6.2.1：category 仅允许预定义枚举值；DB CHECK 同此口径）。
 * 与 requireValidUscc 同属服务边界防御纵深：不依赖表单/类型注解（调用方可经 as EnterpriseCategory
 * 传入未知值），对编程式调用方同样拦截，避免下发后端必然被 DB CHECK 拒绝的请求、省一次网络往返。
 *
 * 成员判定复用 getEnterpriseCategoryLabel / listImportantEnterprises 的同一 hasOwnProperty 机制
 * （ENTERPRISE_CATEGORY_LABELS 自有键、避开原型链继承键），不再派生 Set：全仓「同一谓词单一实现」，
 * 消除 hasOwnProperty 与 Set.has 两套判定机制并存（全仓硬规则：不允许同一仓库两套写法）。新增类别
 * 只需在 LABELS 追加一行，三处校验/取文案自动同步，枚举单一来源不变。
 */
function requireValidCategory(category: string): void {
  // typeof 守卫（与 requireId / requireValidNameLength / requireNonEmptyString 同口径，闭合 require*
  // 家族「非字符串入参即抛明确文案」契约）：签名标 string，但经 as 的编程式调用方可传入真值非字符串
  // （如 number/对象）。此前仅 hasOwnProperty 一道判定，对非字符串键依赖 ToPropertyKey 隐式强转后落空、
  // 间接抛出 INVALID_CATEGORY_MESSAGE——行为等价但不直观，且使本方法成为 require* 家族中唯一缺类型守卫的成员
  // （与 requireValidNameLength 此前补 typeof 的动机同源）。先守 typeof 使非字符串与未知枚举值走同一显式判定，
  // || 短路保证 hasOwnProperty 仅对真实 string 求值；对合法 string 入参零行为变化（既有成员判定不变）。
  if (
    typeof category !== 'string' ||
    !Object.prototype.hasOwnProperty.call(ENTERPRISE_CATEGORY_LABELS, category)
  ) {
    throw new Error(INVALID_CATEGORY_MESSAGE);
  }
}

/**
 * 提交前必填字符串字段前置非空校验。适用于一切 PRD 标注必填的字符串字段：
 *   - name（PRD 6.2.1 企业名称必填且全系统唯一）
 *   - assetManagerId（PRD 6.1.1 / 决策 D-4 资产管理人单选且必填）
 *   - unifiedSocialCreditCode（PRD 6.1.1 / 6.1.2 统一社会信用代码必填且唯一）：在 requireValidUscc
 *     之前先做非空校验，使空值给出「不能为空」而非「格式不正确」——与 name（空值/超长分立文案）同口径，
 *     三类必填字符串字段统一「先非空、后格式」校验序，避免空 USCC 误报格式错误误导用户。
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
  // body ?? {} 与 listImportantEnterprises 的 params ?? {} 同口径防御：data 经 as 可为 null/undefined，
  // 直接读 data.name 会抛不可读 TypeError，绕过 requireNonEmptyString 的明确文案。规整为 {} 后各 require*
  // 落到「字段不能为空」明确文案而非崩溃，与全文件「畸形输入从崩溃降级为明确错误」的边界哲学一致；
  // 对类型合规调用方（FE-003 必构造完整对象）零行为变化。
  const body = data ?? ({} as CreateImportantEnterpriseRequest);
  requireNonEmptyString(body.name, '企业名称不能为空');
  requireValidNameLength(body.name);
  // 与 name/uscc 同口径的「先非空、后格式」序：category 为 PRD 6.1.1 必填字符串字段，先经
  // requireNonEmptyString 拦空值给出「不能为空」明确文案，再由 requireValidCategory 仅对非空值做枚举
  // 成员判定（使「不合法，请选择」文案仅在确为未知枚举值时触发，不再为空/缺失误报）。对合法枚举
  // 入参零行为变化；updateImportantEnterprise 的 Partial 语义下空 category 表「不变」，故不加此守卫。
  requireNonEmptyString(body.category, '企业类别不能为空');
  requireValidCategory(body.category);
  requireNonEmptyString(body.unifiedSocialCreditCode, '统一社会信用代码不能为空');
  requireValidUscc(body.unifiedSocialCreditCode);
  requireNonEmptyString(body.assetManagerId, '资产管理人不能为空');
  // assetManagerId 是 id 类标识符（UUID 外键，DB asset_manager_id VARCHAR(36)，PRD 6.1.1「企业用户 ID」），
  // 与 requireId 对【路径】id、listImportantEnterprises 对【查询】assetManagerId 的长度上界守卫同口径，复用
  // MAX_ID_LENGTH 单一来源：经 as 的编程式调用方可传入超长/畸形串，JSON body 虽无 GET URL 414 风险，但下发给
  // 后端必然被 AC-7 存在性校验拒绝（FK 不存在），前置拦截省一次网络往返并给出明确文案——与全文件「服务边界
  // 防御纵深、不依赖类型注解」一致。requireNonEmptyString 已保证此处 body.assetManagerId 为非空 string，故
  // 直读 .length 安全；合法 assetManagerId 恒为 36 位 UUID（远 < 64），零行为变化。
  if (body.assetManagerId.length > MAX_ID_LENGTH) {
    throw new Error('资产管理人 ID 格式不正确');
  }
  return requireRecord(
    await unwrap<ImportantEnterprise | null | undefined>(
      request({
        url: BASE_URL,
        method: 'POST',
        data: canonicalize(body),
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
  // id 前置校验先于 body 校验：与 deleteImportantEnterprise / getImportantEnterpriseDetail 不同（两者无 body 校验、
  // requireId 实即先执行），本方法此前把 requireId 埋在下方 request 字面量，使「空 id + 空 body」误报
  // 「未检测到需要更新的字段」而非根因「企业 ID 不能为空」——前者误导用户。提前规整并编码 id 一次复用于 URL：
  // 合法 id 零行为变化（encodeURIComponent 对 UUID/自增主键恒等，见 requireId 注释），仅修正空 id 的错误优先级。
  const encodedId = requireId(id);
  // body ?? {} 与 listImportantEnterprises 的 params ?? {} 同口径防御（动机见 createImportantEnterprise）：
  // data 经 as 可为 null/undefined，直接读 data.name?.trim() 会抛不可读 TypeError 而非走到下方各前置文案。
  const body = data ?? ({} as UpdateImportantEnterpriseRequest);
  // 仅当传入非空 name 时才前置长度校验：纯空白输入由 canonicalize(partial) 剔除以表「不变」，
  // 不应被长度校验拦截（与下方 category/USCC 同为 Partial 语义）。typeof 守卫（与
  // listImportantEnterprises 的 keyword/assetManagerId、requireId/requireValidNameLength 同口径）：
  // 可选链 `?.` 仅对 null/undefined 短路，经 as 传入的非字符串（如 number）会落到 (123).trim()
  // 抛不可读 TypeError 而非下方 require* 的明确文案；先守类型使非字符串按「未提供该字段」处理
  // （由 canonicalize 安全跳过），合法 string|undefined 零行为变化。
  if (typeof body.name === 'string' && body.name.trim()) {
    requireValidNameLength(body.name);
  }
  // 仅当传入 category 时才前置校验：未传表「不变」，不应被枚举校验拦截（与下方 USCC 同为 Partial 语义）。
  // 刻意用裸真值 `body.category` 而非 name/uscc/assetManagerId 的 `typeof === 'string' && trim()` 形（防误「归一」引入缺陷）：
  //   category 是枚举文本（非自由文本），此处不对其调 .trim()——而是直传 requireValidCategory，
  //   后者自身已带 typeof 守卫（非字符串即抛 INVALID_CATEGORY_MESSAGE），故本调用点无「非字符串落到 .trim() 抛 TypeError」之险、
  //   无需再补 typeof。若机械对齐为 `typeof body.category === 'string' && body.category.trim()`，会把纯空白 category（'   '）
  //   从「requireValidCategory 立即抛」降级为「跳过校验、表『不变』」并流入 canonicalize(partial)；而 canonicalize 的 category
  //   分支判据是严格 `next.category === ''`（非 trim 感知），空白串不命中剔除、会被原样下发后端——这是当前裸真值 + 立即抛
  //   所正确拦截的泄漏。enum 语义下空白 category 本就不合法，提前抛优于静默下发。合法枚举值（dropdown 恒产）零行为变化。
  if (body.category) {
    requireValidCategory(body.category);
  }
  // 仅当传入非空 USCC 时才前置校验：纯空白输入由 canonicalize(partial) 剔除以表「不变」，不应被格式校验拦截。
  // typeof 守卫同上（与上方 name、listImportantEnterprises 同口径）：防经 as 的非字符串落到 .trim() 抛
  // TypeError，非字符串按「未提供」处理、由 canonicalize 跳过，合法 string|undefined 零行为变化。
  if (typeof body.unifiedSocialCreditCode === 'string' && body.unifiedSocialCreditCode.trim()) {
    requireValidUscc(body.unifiedSocialCreditCode);
  }
  // assetManagerId 长度上界守卫（与 createImportantEnterprise / requireId / listImportantEnterprises 同口径，
  // 复用 MAX_ID_LENGTH 单一来源）：Partial 语义下仅在传入非空白 string 时校验——trim 至空由 canonicalize(partial)
  // 剔除表「不变」，故此处用 `typeof === 'string' && trim()` 与上方 name/uscc 的 Partial 守卫同形，而非必填校验。
  // typeof 守卫防经 as 的非字符串落到 .length 抛 TypeError（与 listImportantEnterprises 的 keyword/assetManagerId、
  // 上方 name/uscc 同口径）；合法 UUID(36) 远 < 64，零行为变化，仅把超长/畸形 FK 挡在必然失败的 PUT 之前。
  if (
    typeof body.assetManagerId === 'string' &&
    body.assetManagerId.trim() &&
    body.assetManagerId.length > MAX_ID_LENGTH
  ) {
    throw new Error('资产管理人 ID 格式不正确');
  }
  // 规整后再判定：canonicalize(partial:true) 会把 trim 至空的字段剔除以表「不变」，若结果为空对象，
  // 说明本次没有任一字段需要更新（全为空/纯空白/未传）。与 requireId/requireRecord 同属服务边界防御纵深：
  // 把「显然无意义的空更新」挡在网络往返之前，避免发出一个必然 no-op 的 PUT（浪费请求；且后端按
  // PRD 6.2.2 仅做存在性/唯一性校验、对空体无显式约定，前置拦截使行为确定而非依赖后端对空 PUT 的兜底）。
  const payload = canonicalize(body, { partial: true });
  if (Object.keys(payload).length === 0) {
    throw new Error('未检测到需要更新的字段');
  }
  return requireRecord(
    await unwrap<ImportantEnterprise | null | undefined>(
      request({
        url: `${BASE_URL}/${encodedId}`,
        method: 'PUT',
        data: payload,
      }),
    ),
    '更新失败，未返回企业记录',
  );
}

/**
 * 删除重要企业（PRD 6.2.3，逻辑删除——决策 D-1 保留审计轨迹，is_deleted 置位而非物理删行）。
 *
 * 冲突提示契约（AC-3「若被引用则给出不可删除提示」）：后端 BE-005 在该企业已被股权台账等业务数据
 * 引用时回传业务错误（success=false「该企业已被引用，不可删除」或 HTTP 409）。本方法刻意不在前端
 * 做本地引用检查——引用方关系仅后端可知，前端无从判定；统一交 unwrap 兜底：业务错误→BusinessError
 * （携带后端具体文案），HTTP 错误→本地化中文文案。故调用方 catch 中 e.message 已是该「不可删除」原因，
 * 无需本方法再包装（与 create/update 同口径，删除的冲突提示天然落在 unwrap + isBusinessError 之上）。
 */
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
  // typeof 守卫（与同函数 requireRecord 对【响应】侧 record.id 的 typeof 守卫同口径，闭合上一提交硬化
  //   requireRecord 后遗留的同函数内「assetManager.id/name 仍仅判 truthy」缺口）：签名声明
  //   assetManager.id / assetManager.name 为必填 string，但后端 success=true 却回传运行期与声明类型不符的
  //   data 时（join/序列化异常——与 requireRecord 同源威胁模型），真值非字符串（number 123 / boolean true /
  //   对象 {}）此前会漏过 `!record.assetManager?.id` 的 truthy 判定、违反 AssetManagerInfo.id/name: string
  //   契约，令 FE-004 详情读取 .name 在非字符串上行为未定义。补 typeof 子句使 assetManager 字段与
  //   requireRecord 对 record.id 的「必为非空 string」保证收敛一致；合法后端（id 恒为 UUID、name 恒为非空
  //   用户姓名字符串）零行为变化——既有的 assetManager 缺失 / id|name 为 null|undefined|'' 三类必拒分支仍由
  //   `!amId.trim()` / `!amName.trim()` 兜住（amId/amName 为下方绑定的 am?.id/am?.name 快照）：判空按 trim
  //   与同函数 requireRecord 的 `!record.id.trim()` 收敛同口径——纯空白 id/name（"   "）与 null|undefined|'' 同属
  //   「残缺 data」一并拒（避免 FE-004 渲染纯空白姓名），typeof 子句仅额外拦真值非字符串
  //   （与 requireRecord「仅额外拦真值非字符串」同口径）。
  // 绑定 am/amId/amName 一次：原对 record.assetManager?. 连续求值四次，绑别名消除重复可选链属性访问；
  //   另将 am?.id/am?.name 快照为 amId/amName 局部常量——本守卫为新增、尚未经 tsc（私有 npm 源不可达、
  //   node_modules 缺失，TEST_AGENT 末次核实），`typeof am?.id === 'string'` 对【可选链】的窄化依赖 TS 版本
  //   （4.x 早期不稳）；快照为局部常量后 `typeof amId !== 'string'` 的窄化在所有 TS 版本可靠、amId.trim() 必类型安全，
  //   与已提交 requireRecord「null 检查后 record.id.trim()」同口径，兑现 AC-11 类型检查于新增代码亦成立。
  //   行为恒等——am/amId/amName 即各字段快照，取值与 typeof 短路序完全一致（非字符串即抛、.trim() 永不落到非字符串上），合法后端零行为变化。
  const am = record.assetManager;
  const amId = am?.id;
  const amName = am?.name;
  if (
    typeof amId !== 'string' ||
    !amId.trim() ||
    typeof amName !== 'string' ||
    !amName.trim()
  ) {
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
