# SUID 工具函数与 Hooks 完整参考

> 导入路径：`import { X } from '@ead/suid-utils-react'`（包含所有 Hooks + 重导出
> `@ead/suid-utils`）纯 JS 工具也可从
> `@ead/suid-utils` 单独导入

## 目录

- [SUID 工具函数与 Hooks 完整参考](#suid-工具函数与-hooks-完整参考)
  - [目录](#目录)
  - [React Hooks](#react-hooks)
    - [useStore — 远程数据请求](#usestore--远程数据请求)
    - [useMergedState — 受控/非受控状态](#usemergedstate--受控非受控状态)
    - [useStorageState — 持久化状态](#usestoragestate--持久化状态)
    - [useCopyToClipboard — 剪贴板](#usecopytoclipboard--剪贴板)
    - [useDeepCompareMemo / useDeepCompareMemoize — 深比较缓存](#usedeepcomparememo--usedeepcomparememoize--深比较缓存)
    - [useDocumentTitle — 文档标题](#usedocumenttitle--文档标题)
    - [useLockScroll — 移动端滚动锁定](#uselockscroll--移动端滚动锁定)
    - [useMobile — 设备检测](#usemobile--设备检测)
    - [usePagedInfiniteScroll — 分页无限滚动](#usepagedinfinitescroll--分页无限滚动)
    - [useTouchState — 触摸手势](#usetouchstate--触摸手势)
    - [useUserContext — 用户上下文](#useusercontext--用户上下文)
  - [工具函数](#工具函数)
    - [authAction — 权限过滤组件](#authaction--权限过滤组件)
    - [hightLight — 文本高亮](#hightlight--文本高亮)
    - [scrollToElement — 平滑滚动](#scrolltoelement--平滑滚动)
    - [isReactNode — ReactNode 类型守卫](#isreactnode--reactnode-类型守卫)
    - [getContextUser — 获取当前用户](#getcontextuser--获取当前用户)
    - [dvaModel — DVA 模型扩展](#dvamodel--dva-模型扩展)
    - [hotKeys — 快捷键](#hotkeys--快捷键)
  - [纯 JS 工具（@ead/suid-utils）](#纯-js-工具eadsuid-utils)
    - [storage — 多后端存储](#storage--多后端存储)
    - [createAppStore — Zustand 状态管理](#createappstore--zustand-状态管理)
    - [request — 预配置 Axios](#request--预配置-axios)
    - [Decimal / toDecimal / util — 精确小数运算](#decimal--todecimal--util--精确小数运算)
    - [eventBus — 事件总线](#eventbus--事件总线)
    - [格式化函数](#格式化函数)
    - [树操作函数](#树操作函数)
    - [Excel 导出](#excel-导出)
    - [hasPermission — 权限校验](#haspermission--权限校验)
    - [compressImage / compressImageFile — 图片压缩](#compressimage--compressimagefile--图片压缩)
    - [validator / checkStrongPassword — 密码校验](#validator--checkstrongpassword--密码校验)
    - [其他工具函数](#其他工具函数)

---

## React Hooks

### useStore — 远程数据请求

数据获取 Hook，基于预配置 Axios 实例，管理 loading/error 状态和请求取消。

```tsx
import {
  useStore,
  type StoreOption,
} from "@ead/suid-utils-react";
```

- **参数**: `StoreOption`

| 属性          | 说明                  | 类型                  | 默认值  |
| ------------- | --------------------- | --------------------- | ------- |
| `url`         | 请求地址              | `string`              | -       |
| `type`        | 请求方法              | `'GET' \| 'POST'`     | `'GET'` |
| `params`      | 附加参数              | `Record<string, any>` | `{}`    |
| `autoLoad`    | 挂载时自动请求        | `boolean`             | `false` |
| `neverCancel` | 允许并发请求          | `boolean`             | `false` |
| `needMerge`   | setStore 时合并参数   | `boolean`             | `false` |
| `manual`      | 手动控制 loading 完成 | `boolean`             | `false` |

- **返回值**: `StoreResult`

| 属性               | 说明                   |
| ------------------ | ---------------------- |
| `data`             | 响应数据               |
| `dataLoading`      | 加载状态               |
| `errorResult`      | 错误信息               |
| `getData()`        | 手动触发请求           |
| `cancelRequest()`  | 取消进行中的请求       |
| `setStore(option)` | 更新请求配置并重新请求 |
| `getStore()`       | 获取当前配置           |
| `loadingComplete`  | 手动模式下标记加载完成 |

```tsx
function UserList() {
  const {
    data,
    dataLoading,
    getData,
    setStore,
  } = useStore({
    url: "/api/users",
    type: "POST",
    autoLoad: true,
  });

  // 更新筛选条件并重新请求
  const handleSearch = (
    keyword: string,
  ) => {
    setStore({ params: { keyword } });
  };

  if (dataLoading) return <Spin />;
  return (
    <Table dataSource={data?.list} />
  );
}

// ExtTable 的 store 属性也使用 StoreOption 类型
<ExtTable
  store={{
    url: "/api/list",
    type: "POST",
  }}
  remotePaging
/>;
```

### useMergedState — 受控/非受控状态

优雅处理受控/非受控组件状态，来自 rc-util。

```tsx
import { useMergedState } from "@ead/suid-utils-react";
```

| 参数                | 说明             | 类型                 |
| ------------------- | ---------------- | -------------------- |
| `defaultValue`      | 非受控模式默认值 | `T`                  |
| `options.value`     | 受控模式值       | `T`                  |
| `options.onChange`  | 值变更回调       | `(value: T) => void` |
| `options.postState` | 值后处理         | `(value: T) => T`    |

```tsx
function MyPicker({ value, onChange }) {
  const [mergedValue, setValue] =
    useMergedState(undefined, {
      value,
      onChange,
    });
  return (
    <div
      onClick={() => setValue("new")}
    >
      {mergedValue}
    </div>
  );
}
```

### useStorageState — 持久化状态

将 React 状态自动持久化到浏览器存储，API 与
`useState` 一致。

```tsx
import { useStorageState } from "@ead/suid-utils-react";
```

| 参数           | 说明             | 类型                                 | 默认值             |
| -------------- | ---------------- | ------------------------------------ | ------------------ |
| `key`          | 存储键名         | `string`                             | -                  |
| `type`         | 存储类型         | `'localStorage' \| 'sessionStorage'` | `'sessionStorage'` |
| `defaultValue` | 默认值           | `T`                                  | -                  |
| `encode`       | 是否 base64 编码 | `boolean`                            | `true`             |

- **返回值**: `[state, setState]`（设置
  `undefined` 清除存储值）

```tsx
function Settings() {
  const [theme, setTheme] =
    useStorageState("app-theme", {
      type: "localStorage",
      defaultValue: "light",
    });

  return (
    <Select
      value={theme}
      onChange={setTheme}
    >
      <Select.Option value="light">
        浅色
      </Select.Option>
      <Select.Option value="dark">
        深色
      </Select.Option>
    </Select>
  );
}
```

### useCopyToClipboard — 剪贴板

复制文本到剪贴板，自动降级兼容旧浏览器。

```tsx
import { useCopyToClipboard } from "@ead/suid-utils-react";
```

- **返回值**:
  `[copiedText: string, copy: (text: string) => Promise<boolean>]`

```tsx
function CopyButton({ text }) {
  const [copied, copy] =
    useCopyToClipboard();

  return (
    <Button onClick={() => copy(text)}>
      {copied ? "已复制" : "复制"}
    </Button>
  );
}
```

### useDeepCompareMemo / useDeepCompareMemoize — 深比较缓存

- `useDeepCompareMemo(factory, deps)`
  — 类似
  `useMemo`，但使用深比较判断依赖是否变化
- `useDeepCompareMemoize(value, options?)`
  — 返回相同引用，直到值深度变化

```tsx
import {
  useDeepCompareMemo,
  useDeepCompareMemoize,
} from "@ead/suid-utils-react";

function TableView({
  columns,
  dataSource,
}) {
  // columns 对象引用变化但内容不变时不会重新计算
  const processed = useDeepCompareMemo(
    () => heavyProcess(columns),
    [columns],
  );
}

function Parent({ config }) {
  // config 内容不变时返回相同引用，避免子组件重渲染
  const stableConfig =
    useDeepCompareMemoize(config, {
      ignoreKeys: ["timestamp"],
    });
  return (
    <Child config={stableConfig} />
  );
}
```

### useDocumentTitle — 文档标题

设置
`document.title`，卸载时可恢复原标题。

```tsx
import { useDocumentTitle } from "@ead/suid-utils-react";
```

```tsx
useDocumentTitle("用户管理");
useDocumentTitle("编辑 - 用户管理", {
  preserveTitleOnUnmount: false,
});
```

### useLockScroll — 移动端滚动锁定

防止滚动穿透（移植自 Vant
UI），支持多层锁定。

```tsx
import { useLockScroll } from "@ead/suid-utils-react";
```

```tsx
function Popup({ visible, rootRef }) {
  useLockScroll(rootRef, visible); // boolean
  // useLockScroll(rootRef, 'strict'); // 同时锁定 body
  return visible ? (
    <div ref={rootRef}>弹层内容</div>
  ) : null;
}
```

### useMobile — 设备检测

响应式设备检测 Hook。

```tsx
import { useMobile } from "@ead/suid-utils-react";
```

- **返回值**:
  `{ isMobile: boolean, isAndroid: boolean, isIos: boolean }`

```tsx
function ResponsivePage() {
  const { isMobile } = useMobile();
  return isMobile ? (
    <MobileLayout />
  ) : (
    <DesktopLayout />
  );
}
```

### usePagedInfiniteScroll — 分页无限滚动

基于 ahooks 的 `useInfiniteScroll`
封装，内置分页逻辑和 `noMore` 检测。

```tsx
import { usePagedInfiniteScroll } from "@ead/suid-utils-react";
```

- **参数**:
  `service: (page: number, pageSize: number, currentData?: any[]) => Promise<{ list: any[] }>`
- **选项**: 标准 `useInfiniteScroll`
  选项 + `pageSize`

```tsx
function InfiniteList() {
  const {
    data,
    loading,
    loadMore,
    noMore,
  } = usePagedInfiniteScroll(
    async (page, pageSize) => {
      const res = await request.get(
        "/api/items",
        { params: { page, pageSize } },
      );
      return { list: res.data.list };
    },
    { pageSize: 20 },
  );

  return (
    <div>
      {data?.map((item) => (
        <div key={item.id}>
          {item.name}
        </div>
      ))}
      {!noMore && (
        <Button
          loading={loading}
          onClick={loadMore}
        >
          加载更多
        </Button>
      )}
    </div>
  );
}
```

### useTouchState — 触摸手势

追踪触摸状态和滑动方向。

```tsx
import { useTouchState } from "@ead/suid-utils-react";
```

- **返回值**: `[state, handlers]`
  - `state`:
    `{ startX, startY, deltaX, deltaY, offsetX, offsetY, direction, isVertical(), isHorizontal() }`
  - `handlers`: `{ start, move, reset }`

```tsx
function SwipeCard() {
  const [
    state,
    { start, move, reset },
  ] = useTouchState();

  return (
    <div
      onTouchStart={start}
      onTouchMove={move}
      onTouchEnd={reset}
      style={{
        transform: `translateX(${state.offsetX}px)`,
      }}
    >
      {state.isHorizontal() &&
        state.direction ===
          "horizontal" &&
        "左右滑动中"}
    </div>
  );
}
```

### useUserContext — 用户上下文

读取当前用户、语言、会话等上下文信息。

```tsx
import { useUserContext } from "@ead/suid-utils-react";
```

- **返回值**:

| 属性                                                                                    | 说明         |
| --------------------------------------------------------------------------------------- | ------------ |
| `currentUser`                                                                           | 当前用户信息 |
| `currentLocale`                                                                         | 当前语言     |
| `currentSessionId`                                                                      | 会话 ID      |
| `currentAuth`                                                                           | 当前权限信息 |
| `currentPolicy`                                                                         | 当前策略     |
| `setCurrentUser` / `setLocale` / `setSessionId` / `setCurrentAuth` / `setCurrentPolicy` | 设置方法     |

```tsx
function UserProfile() {
  const { currentUser, currentLocale } =
    useUserContext();
  return (
    <div>
      {currentUser?.name} (
      {currentLocale})
    </div>
  );
}
```

---

## 工具函数

### authAction — 权限过滤组件

根据权限码过滤组件，管理员自动放行。

```tsx
import { authAction } from "@ead/suid-utils-react";
```

- **参数**: `comp`
  — 单个组件或组件数组（需有 `authCode`
  属性）
- **选项**: `force`
  — 强制校验；`endMatch`
  — 后缀匹配；`ignore` — 忽略

```tsx
const actions = [
  {
    authCode: "user:create",
    component: <Button>新增</Button>,
  },
  {
    authCode: "user:edit",
    component: <Button>编辑</Button>,
  },
  {
    authCode: "user:delete",
    component: <Button>删除</Button>,
  },
];

// 仅返回当前用户有权限的组件
const authorizedActions =
  authAction(actions);
```

### hightLight — 文本高亮

在文本中高亮匹配关键词（不区分大小写），返回 React 节点。

```tsx
import { hightLight } from "@ead/suid-utils-react";
```

```tsx
const text = "Hello World你好";
const highlighted = hightLight(
  text,
  "hello",
); // <span>**Hello**</span> World你好
const highlighted2 = hightLight(
  text,
  "你好",
  "#f50",
); // 指定高亮颜色
```

### scrollToElement — 平滑滚动

平滑滚动到目标元素。

```tsx
import { scrollToElement } from "@ead/suid-utils-react";
```

- **参数**: `target` —
  ID、类名、标签名或 Element；`options.container`
  — 滚动容器

```tsx
scrollToElement("#section-3");
scrollToElement(".detail-card", {
  container: ".page-container",
  horizontal: true,
});
```

### isReactNode — ReactNode 类型守卫

判断值是否为有效 ReactNode。

```tsx
import { isReactNode } from "@ead/suid-utils-react";
```

```tsx
if (isReactNode(children)) {
  return <span>{children}</span>;
}
```

### getContextUser — 获取当前用户

从 sessionStorage 读取当前用户信息。

```tsx
import { getContextUser } from "@ead/suid-utils-react";
```

```tsx
const user = getContextUser(); // 返回 {} 如果未登录
```

### dvaModel — DVA 模型扩展

提供 `baseModel` 和
`modelExtend`，用于 dva 状态管理。

```tsx
import { dvaModel } from "@ead/suid-utils-react";
const { modelExtend, baseModel } =
  dvaModel;
```

```tsx
const model = modelExtend(baseModel, {
  namespace: "user",
  state: { name: "" },
  effects: {
    *fetch(_, { call, put }) {
      const data =
        yield call(fetchUser);
      yield put({
        type: "updateState",
        payload: data,
      });
    },
  },
});
```

### hotKeys — 快捷键

重导出
`react-hotkeys-hook`，提供键盘快捷键支持。

```tsx
import {
  useHotkeys,
  useRecordHotkeys,
  isHotkeyPressed,
  HotkeysProvider,
} from "@ead/suid-utils-react";
```

```tsx
useHotkeys("ctrl+s", (e) => {
  e.preventDefault();
  handleSave();
});
useHotkeys("esc", () => closeModal());

const pressed =
  isHotkeyPressed("shift"); // boolean
```

---

## 纯 JS 工具（@ead/suid-utils）

> 以下工具从 `@ead/suid-utils`
> 导入，也可从 `@ead/suid-utils-react`
> 导入（自动重导出）

### storage — 多后端存储

三种存储后端的统一 API，支持 base64 编码和自定义序列化。

```tsx
import {
  storage,
  type StorageOption,
} from "@ead/suid-utils";
```

| 后端                     | 说明                         | 特点               |
| ------------------------ | ---------------------------- | ------------------ |
| `storage.localStorage`   | 浏览器 localStorage          | 同步，5MB 限制     |
| `storage.sessionStorage` | 浏览器 sessionStorage        | 同步，会话级       |
| `storage.webStorage`     | IndexedDB（via localforage） | 异步，适合大数据量 |

- **公共 API**:
  `set(key, data, options?)`、`get(key, options?)`、`clear(key?, options?)`
- **额外**: `storage.encode(data)` /
  `storage.decode(encodedData)` —
  base64 编解码

```tsx
// localStorage — 同步
storage.localStorage.set(
  "token",
  "abc123",
  { prefix: "myapp_" },
);
const token = storage.localStorage.get(
  "token",
  { prefix: "myapp_" },
);
storage.localStorage.clear("token");

// sessionStorage — 同步
storage.sessionStorage.set(
  "tab",
  "active",
);

// webStorage (IndexedDB) — 异步，适合大数据
await storage.webStorage.set(
  "largeData",
  bigArray,
);
const data =
  await storage.webStorage.get(
    "largeData",
  );
```

### createAppStore — Zustand 状态管理

创建基于 Zustand 的全局状态管理 Store，支持异步 effects 和持久化。

```tsx
import {
  createAppStore,
  type StoreType,
} from "@ead/suid-utils";
```

| 参数            | 说明                                           |
| --------------- | ---------------------------------------------- |
| `initialState`  | 初始状态对象                                   |
| `actions`       | 同步操作方法                                   |
| `effects`       | 异步操作方法                                   |
| `persistConfig` | 持久化配置（storageKey, whitelist, blacklist） |

- **返回值**:
  `{ store: StoreApi, useStore: selector hook }`

```tsx
const { store, useStore } =
  createAppStore(
    { count: 0, user: null },
    {
      increment(state) {
        state.count += 1;
      },
      setUser(state, user) {
        state.user = user;
      },
    },
    {
      async fetchUser(state) {
        const res = await request.get(
          "/api/user",
        );
        state.setUser(res.data);
      },
    },
    {
      storageKey: "my-store",
      whitelist: ["count"],
    },
  );

// 在组件中使用
function Counter() {
  const count = useStore(
    (s) => s.count,
  );
  const increment = useStore(
    (s) => s.increment,
  );
  return (
    <Button onClick={increment}>
      {count}
    </Button>
  );
}
```

### request — 预配置 Axios

SEI 平台预配置 Axios 实例，内置认证、语言、重复请求取消、401 处理。

```tsx
import {
  request,
  axios,
  type ResponseResult,
  type AxiosRequestConfig,
} from "@ead/suid-utils";
```

**内置特性**：

- 请求拦截：自动添加 `sei: 6`
  header、语言 header、认证 token、缓存控制
- 响应拦截：统一 `ResponseResult` 格式
  `{ success, message, statusCode, data, detail, error }`
- 重复请求取消（MD5 去重，可设置
  `neverCancel` 跳过）
- 401 自动触发 `timeoutLogin` 事件

```tsx
// 基础用法
const res = await request.get(
  "/api/users",
);
const res2 = await request.post(
  "/api/users",
  { name: "test" },
);

// 自定义配置
const res3 = await request.get(
  "/api/data",
  {
    params: { page: 1 },
    neverCancel: true, // 允许并发
  },
);

// 文件下载
const blob = await request.get(
  "/api/export",
  { responseType: "blob" },
);
```

### Decimal / toDecimal / util — 精确小数运算

基于 `decimal.js`
的精确小数运算，避免浮点数精度问题。

```tsx
import {
  Decimal,
  toDecimal,
  util,
} from "@ead/suid-utils";
```

**util 方法**：

| 方法                                                                 | 说明                 |
| -------------------------------------------------------------------- | -------------------- |
| `util.sum(...values)`                                                | 求和，返回 number    |
| `util.add(a, b)`                                                     | 加法                 |
| `util.sub(a, b)`                                                     | 减法                 |
| `util.mul(a, b)`                                                     | 乘法                 |
| `util.div(a, b)`                                                     | 除法                 |
| `util.abs(val)`                                                      | 绝对值               |
| `util.sumBy(list, iteratee)`                                         | 按 key/函数求和      |
| `util.calcForeignAmount({ amount, fromUnit, rate, toUnit })`         | 外币换算（四舍五入） |
| `util.calcAmountRoundDown({ targetAmount, fromUnit, rate, toUnit })` | 外币换算（向下取整） |

```tsx
// 基础运算
util.add(0.1, 0.2); // 0.3（而非 0.30000000000000004）
util.mul(0.1, 3); // 0.3

// 列表求和
const total = util.sumBy(
  items,
  "amount",
);
const total2 = util.sumBy(
  items,
  (item) => item.price * item.quantity,
);

// 外币换算
const cny = util.calcForeignAmount({
  amount: 100,
  fromUnit: "USD",
  rate: 7.25,
  toUnit: "CNY",
}); // 725.00（四舍五入到2位小数）

// Decimal 实例操作
const d = toDecimal("123.45");
d.plus("67.55").toNumber(); // 191
```

### eventBus — 事件总线

基于 Node.js
EventEmitter 的事件系统，支持全局通信和页面级私有事件。

```tsx
import {
  eventBus,
  PORTAL_EVENTS,
} from "@ead/suid-utils";
```

**全局事件**（跨页面通信，需平台主应用支持）：

| 方法                           | 说明     |
| ------------------------------ | -------- |
| `eventBus.on(event, handler)`  | 监听事件 |
| `eventBus.off(event, handler)` | 取消监听 |
| `eventBus.emit(event, data)`   | 触发事件 |

**私有事件**（当前页面路由范围内）：

| 方法                             | 说明         |
| -------------------------------- | ------------ |
| `eventBus.myOn(event, handler)`  | 监听私有事件 |
| `eventBus.myOff(event, handler)` | 取消私有事件 |
| `eventBus.myEmit(event, data)`   | 触发私有事件 |

**PORTAL_EVENTS 枚举**:
`OPEN_TAB`、`CLOSE_TAB`、`REFRESH_TAB`

```tsx
// 全局事件 — 打开新标签页
eventBus.emit(PORTAL_EVENTS.OPEN_TAB, {
  url: "/detail/123",
  title: "详情",
});

// 私有事件 — 页面内组件通信
eventBus.myOn("form:saved", (data) => {
  refreshTable();
});
// 在表单保存成功后
eventBus.myEmit("form:saved", formData);

// 初始化带 scope 的私有事件
eventBus.initPagePrivateEvent(
  "my-scope",
);
```

### 格式化函数

```tsx
import {
  formaterNumber,
  toChineseAmount,
  toFileSize,
  toThousandsAmount,
} from "@ead/suid-utils";
```

| 函数                                                   | 说明                         | 示例                                                                 |
| ------------------------------------------------------ | ---------------------------- | -------------------------------------------------------------------- |
| `formaterNumber(value, precision, thousand?, suffix?)` | 格式化数字，nil 值返回 `'-'` | `formaterNumber(1234.5, 2, true)` → `'1,234.50'`                     |
| `toChineseAmount(amount)`                              | 转中文大写金额               | `toChineseAmount(12345)` → `'壹万贰仟叁佰肆拾伍元整'`                |
| `toFileSize(size)`                                     | 字节转可读文件大小           | `toFileSize(1024)` → `'1.00KB'`                                      |
| `toThousandsAmount(amount, options?)`                  | 千分位格式化                 | `toThousandsAmount(1234567, { currency: true })` → `'¥1,234,567.00'` |

```tsx
formaterNumber(1234.5, 2, true, "元"); // '1,234.50元'
formaterNumber(null, 2); // '-'
toChineseAmount(-123.45); // '负壹佰贰拾叁元肆角伍分'
toFileSize(1073741824); // '1.00GB'
toThousandsAmount(12345.67, {
  currency: "USD",
  locales: "en-US",
}); // '$12,345.67'
```

### 树操作函数

```tsx
import {
  forEachTree,
  findTree,
  filterTree,
  buildTree,
  mapTree,
  someTree,
  flattenTree,
  updateLazyNodeChildren,
  getTreeShortPath,
  getAllParentNodeKeys,
  getAllParentByNodeValue,
  getAllChildByNodeValue,
  getAllNodeKeys,
  getRootParentIdByNode,
  buildTreeData,
  flattenTreeData,
} from "@ead/suid-utils";
```

| 函数                                                          | 说明                               |
| ------------------------------------------------------------- | ---------------------------------- |
| `forEachTree(tree, callback, options?)`                       | 遍历树，支持前序/后序/广度优先     |
| `findTree(tree, callback, options?)`                          | 查找第一个匹配节点                 |
| `filterTree(tree, callback, options?)`                        | 过滤节点，`includeParent` 保留祖先 |
| `buildTree(array, options?)`                                  | 扁平数组转树（id/pid 关系）        |
| `buildTreeData(data, options?)`                               | 同上，使用 lodash-es 实现          |
| `mapTree(tree, callback, options?)`                           | 映射每个节点                       |
| `someTree(tree, callback, options?)`                          | 判断是否存在匹配节点               |
| `flattenTree(tree, options?)`                                 | 树扁平化为数组                     |
| `flattenTreeData(data, options?)`                             | 同上，移除 children 属性           |
| `updateLazyNodeChildren(tree, nodeValue, children, options?)` | 更新懒加载节点的子节点             |
| `getTreeShortPath(fullPath, options?)`                        | 获取树的短路径                     |
| `getAllParentNodeKeys(tree, options?)`                        | 获取所有非叶节点 key               |
| `getAllParentByNodeValue(tree, nodeValue, options?)`          | 获取某节点的所有祖先               |
| `getAllChildByNodeValue(tree, nodeValue, options?)`           | 获取某节点的所有后代               |
| `getAllNodeKeys(tree, options?)`                              | 获取所有节点 key                   |
| `getRootParentIdByNode(tree, targetId, options?)`             | 追溯某节点的根父节点               |

**通用 options**:

| 选项            | 说明                | 默认值       |
| --------------- | ------------------- | ------------ |
| `childrenKey`   | 子节点字段名        | `'children'` |
| `key`           | 节点值字段名        | `'value'`    |
| `strategy`      | 遍历策略            | `'pre'`      |
| `getParentKeys` | 是否返回父级 key 链 | `false`      |

```tsx
// 扁平数据转树
const tree = buildTree(flatList, {
  id: "id",
  pid: "parentId",
});

// 查找节点
const node = findTree(
  tree,
  (n) => n.value === "target",
);

// 过滤树（保留匹配节点的祖先）
const filtered = filterTree(
  tree,
  (n) => n.status === "active",
  { includeParent: true },
);

// 获取某节点的所有后代
const { keys, items } =
  getAllChildByNodeValue(
    tree,
    "parent-1",
  );
// keys: ['child-1', 'child-2', 'grandchild-1']
// items: [{ value: 'child-1', ... }, ...]

// 更新懒加载子节点
updateLazyNodeChildren(
  treeData,
  "node-1",
  newChildren,
);
```

### Excel 导出

```tsx
import {
  exportJsonToXlsx,
  exportAoaToXlsx,
  getXlsxArrayData,
  type IColumnConfig,
} from "@ead/suid-utils";
```

**IColumnConfig**:
`{ title: string, dataIndex?: string, formatter?: (value, record) => any }`

| 函数                       | 说明                                   |
| -------------------------- | -------------------------------------- |
| `exportJsonToXlsx(params)` | JSON 数据导出为 xlsx，自动按列配置转换 |
| `exportAoaToXlsx(params)`  | AOA（数组的数组）导出为 xlsx           |
| `getXlsxArrayData(params)` | 获取原始二进制数据（自行处理下载）     |

```tsx
// JSON 数据导出
exportJsonToXlsx({
  data: userList,
  columns: [
    {
      title: "姓名",
      dataIndex: "name",
    },
    {
      title: "金额",
      dataIndex: "amount",
      formatter: (v) =>
        toThousandsAmount(v, {
          currency: true,
        }),
    },
  ],
  fileName: "用户列表",
  sheetName: "用户",
});

// 获取原始数据（用于自定义下载）
const buffer = await getXlsxArrayData({
  data,
  columns,
  fileName: "export",
});
```

### hasPermission — 权限校验

```tsx
import { hasPermission } from "@ead/suid-utils";
```

| 参数                 | 说明                                         |
| -------------------- | -------------------------------------------- |
| `authcode`           | 权限码                                       |
| `option.authorities` | 自定义权限列表（默认从 sessionStorage 读取） |
| `option.matchMode`   | `'once'` 任一匹配 / `'all'` 全部匹配         |

```tsx
if (hasPermission("user:create")) {
  /* 显示创建按钮 */
}
hasPermission(
  ["user:edit", "user:delete"],
  { matchMode: "all" },
);
```

### compressImage / compressImageFile — 图片压缩

```tsx
import {
  compressImage,
  compressImageFile,
} from "@ead/suid-utils";
```

```tsx
// 压缩图片 URL → base64
const base64 = await compressImage(
  path,
  {
    responseType: "base64",
    quality: 0.7,
  },
);

// 压缩图片 URL → Blob
const blob = await compressImage(path, {
  responseType: "blob",
  width: 800,
});

// 压缩 File 对象
const compressedFile =
  await compressImageFile(file, {
    maxWidth: 1200,
    maxHeight: 800,
    quality: 0.8,
  });
```

### validator / checkStrongPassword — 密码校验

```tsx
import {
  checkStrongPassword,
  analyzePassword,
  PASSWORD_RULE,
} from "@ead/suid-utils";
```

- `checkStrongPassword(password)`
  — 校验密码强度（8-32位，至少含大写/小写/数字/符号中的3种）
- `analyzePassword(password)`
  — 分析密码组成（各类字符数量）
- `PASSWORD_RULE` — 密码规则描述文本

```tsx
const result =
  checkStrongPassword("Abc123!@");
// { passed: true, analysisResult: { uppercase: 1, lowercase: 2, digits: 3, symbols: 2, length: 8, uniqueChars: 8 } }

const result2 =
  checkStrongPassword("weak");
// { passed: false, error: '密码长度不能少于8个字符', analysisResult: {...} }
```

### 其他工具函数

```tsx
import {
  canUseDom,
  md5,
  getUUID,
  qs,
  tplMessage,
  formartUrl,
  blobToFile,
  pathMatchRegexp,
  isDeepEqual,
  isMobile,
  isAndroid,
  isIos,
  SEI_CONSTANTS,
  CONST_GLOBAL,
  AUTH_POLICY,
  API_GATEWAY,
} from "@ead/suid-utils";
```

| 函数/常量                             | 说明                           | 示例                                                       |
| ------------------------------------- | ------------------------------ | ---------------------------------------------------------- |
| `canUseDom`                           | 是否浏览器环境（SSR 安全判断） | `canUseDom // true \| false`                               |
| `md5(data)`                           | MD5 哈希                       | `md5('hello')`                                             |
| `getUUID()`                           | 生成 UUID v4                   | `getUUID() // '550e8400-...'`                              |
| `qs.stringify(obj)` / `qs.parse(str)` | 查询字符串序列化/解析          | `qs.stringify({ a: 1, b: 2 })` → `'a=1&b=2'`               |
| `tplMessage(msg, values)`             | 模板字符串插值                 | `tplMessage('{name}是{age}岁', { name: '张三', age: 25 })` |
| `formartUrl(base, path)`              | URL 拼接（处理斜杠）           | `formartUrl('https://a.com', '/b')` → `'https://a.com/b'`  |
| `blobToFile(res, fileName?)`          | Axios 响应转文件下载           | `blobToFile(response)`                                     |
| `pathMatchRegexp(pattern, pathname)`  | 路径匹配（path-to-regexp）     | `pathMatchRegexp('/user/:id', '/user/123')`                |
| `isDeepEqual(a, b, ignoreKeys?)`      | 深比较                         | `isDeepEqual(obj1, obj2, ['timestamp'])`                   |
| `isMobile()`                          | 移动设备检测                   | `isMobile() // boolean`                                    |
| `CONST_GLOBAL.TOKEN_KEY`              | Token 存储 key 常量            | —                                                          |
| `CONST_GLOBAL.CURRENT_USER`           | 当前用户存储 key               | —                                                          |
| `CONST_GLOBAL.CURRENT_LOCALE`         | 当前语言存储 key               | —                                                          |
| `AUTH_POLICY.ADMIN`                   | 管理员策略常量                 | —                                                          |
| `API_GATEWAY`                         | API 网关路径常量               | —                                                          |
| `API_CONTEXTS_V6` / `API_CONTEXTS_V7` | V6/V7 服务上下文               | —                                                          |
