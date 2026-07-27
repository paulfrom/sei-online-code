-- ============================================================
-- V13: 内置技能改为数据库关联（去掉 classpath resources/skills/）
--
-- 原内置 4 个技能（suid/eadp-backend/project-planning/feature-design）
-- 之前 vendor 在 classpath skills/<name>/，经 BuiltInSkillRegistry 以
-- builtin:<name> synthetic id 加载。本迁移把它们改为普通 oc_skill 行
-- （+ oc_skill_file 辅助文件行），agent 经 oc_agent_skill 绑定真实 DB id。
--
-- 幂等：name 列 uk_skill_name 唯一约束，重跑会因唯一键冲突失败 → 由部署
-- 保证 V13 仅执行一次（与现有 V9-V12 手工 DBA 迁移脚本同一惯例）。
-- ============================================================

-- ============================ oc_skill 种子行 ============================
INSERT INTO oc_skill (id, name, description, config, content, created_date) VALUES
('SKILBLTNSUID000000000000000000000000', 'suid', '@ead/suid 组件库开发技能', '{"origin": "local:suid"}', '---
name: suid
description:
  使用 @ead/suid 组件库开发 React UI(suid 2.0)
  的企业级组件库，包含基础组件和业务扩展组件。当需要：(1)
  选择合适的 SUID 组件，(2) 查询组件 API
  和用法，(3) 编写 SUID 组件代码，(4)
  确定 import 路径，(5)
  组合多个组件构建页面，(6) 使用
  CSS-in-JS 样式，(7) 使用工具函数时触发
---

# SUID 组件库开发指南

> `@ead/suid` v2.1

## 包路径速查

| 包名                    | 用途                                                                               | 示例                                                                |
| ----------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `@ead/suid`             | **所有组件**（基础 + 业务扩展），统一从此包导入                                    | `import { ExtTable, Button } from ''@ead/suid''`                      |
| `@ead/suid-icons`       | **所有图标**（Outlined 369 + Filled 60），禁止从 `@ead/suid` 导入图标              | `import { SearchOutlined } from ''@ead/suid-icons''`                  |
| `@ead/antd-style`       | **CSS-in-JS 样式**，使用 `createStyles`                                            | `import { createStyles } from ''@ead/antd-style''`                    |
| `@ead/suid-utils-react` | **React Hooks + 工具函数**（useStore、storage 等），自动重导出 `@ead/suid-utils`   | `import { useStore, storage, util } from ''@ead/suid-utils-react''`   |
| `@ead/suid-utils`       | **纯 JS 工具函数**（request、Decimal、tree 等），被 `@ead/suid-utils-react` 重导出 | `import { request, util, exportJsonToXlsx } from ''@ead/suid-utils''` |

## 适用范围

- 配套工具：使用 `@ead/suid-cli`
  完成组件离线元数据、示例、更新日志、代码迁移、代码检查、环境检测以及使用情况分析等工作。

## 默认约定

- 开发语言：根据当前工程配置是否使用 TypeScript。
- 样式方案：优先使用设计令牌，其次采用类名/内联样式，禁止全局样式覆盖。
- 全局配置：若无严格隔离需求，仅设置一个根级
  `ConfigProvider` 配置组件。

## 强制规范

1. 使用 suid 组件代码前，必须先执行
   `suid info <组件名> --format json`
   查询组件接口。可借助命令行工具离线查询时，严禁凭记忆开发。
2. 执行所有 suid 命令行指令时，统一添加
   `--format json` 参数。
3. 若项目版本有明确要求，需通过
   `--version <主版本.次版本.修订版本>`
   指定对应版本；也可由命令行工具从本地
   `node_modules` 自动识别版本。
4. 若 suid 命令行指令出现崩溃、返回数据错误或与文档描述行为不符的情况，需提交
   `suid bug-cli`
   预览问题供相关人员确认，不得私下绕过问题处理。
5. 查阅组件相关问题时，先将组件名称转换为官方路由短标识（小写短横线命名格式，例如
   `TreeSelect` 对应
   `tree-select`、`Button` 对应
   `button`），再按以下顺序查阅文档（优先中文，无中文则查看英文）：基础组件：`https://sei.changhong.com/suid-react-v2/components/{组件标识}-cn`
   →
   `https://sei.changhong.com/suid-react-v2/components/{组件标识}`
   扩展组件：`https://sei.changhong.com/suid-react-v2/components/ext-suid/{组件标识}-cn`
   →
   `https://sei.changhong.com/suid-react-v2/components/ext-suid/{组件标识}`
   示例：`tree-select-cn` 对应
   `tree-select`，`button-cn` 对应
   `button`。
6. 仅使用 suid 系列中已对外公开的接口。
7. 禁止自行新增属性、事件及组件名称。
8. 禁止依赖组件内部 DOM 结构与 `.ead-*`
   选择器。
9. 主题优先级：全局设计令牌 > 组件专属令牌 > 别名令牌。

## 组件选择原则

1. **业务组件优先**: 业务扩展组件专为业务场景设计，优先使用（如
   `ExtTable`、`ComboList`、`ExtModal`
   等）
2. **基础组件次之**: `@ead/suid`
   基础组件覆盖所有通用场景
3. **禁止使用 antd 原生**: 所有组件均来自
   `@ead/suid`，禁止直接使用 `antd`

## CSS-in-JS 样式（createStyles）

使用 `@ead/antd-style` 的
`createStyles`，基于 antd-style（https://github.com/ant-design/antd-style）实现
CSS-in-JS：

```tsx
import { createStyles } from "@ead/antd-style";

const useStyles = createStyles(
  ({ token, css }) => ({
    container: css`
      padding: ${token.paddingMD}px;
      background: ${token.colorBgContainer};
      border-radius: ${token.borderRadius}px;
    `,
    title: {
      color: token.colorTextHeading,
      fontSize: token.fontSizeLG,
    },
  }),
);

function MyComponent() {
  const { styles } = useStyles();
  return (
    <div className={styles.container}>
      ...
    </div>
  );
}
```

**注意事项**：

- `token` 包含所有 antd Design
  Token（`colorPrimary`、`paddingMD`、`borderRadius`
  等）
- `css` 是 tagged template
  literal，支持完整 CSS 语法
- 对象写法等价于 `css` 写法，可混用
- 样式自动支持暗色模式（通过 token 响应主题变化）

## 工具函数与 Hooks（@ead/suid-utils-react）

`@ead/suid-utils-react` 提供
**12 个 React Hooks** +
**8 个 React 工具函数**，并自动重导出
`@ead/suid-utils` 全部
**20+ 纯 JS 工具函数**。完整 API 参见
[references/utils-hooks.md](references/utils-hooks.md)。

### 数据请求

```tsx
import { useStore, type StoreOption } from ''@ead/suid-utils-react'';

// StoreOption 同时用于 useStore Hook 和 ExtTable/ComboList/ListCard 的 store 属性
const store: StoreOption = {
  url: ''/api/list'',
  type: ''POST'',  // ''GET'' | ''POST''
  params: {},    // 附加参数
  autoLoad: true, // 自动请求
};

// Hook 用法：管理 loading/error/request 状态
const { data, dataLoading, getData, setStore } = useStore(store);

// 组件 props 用法
<ExtTable store={store} remotePaging />
<ComboList store={store} reader={{ textField: ''label'', valueField: ''value'' }} />
```

### 状态管理

```tsx
import { useStorageState } from "@ead/suid-utils-react";
import { createAppStore } from "@ead/suid-utils-react"; // Zustand 全局状态

// 持久化状态（API 与 useState 一致）
const [theme, setTheme] =
  useStorageState("app-theme", {
    type: "localStorage",
    defaultValue: "light",
  });

// Zustand Store（支持 actions + effects + 持久化）
const {
  store,
  useStore: useGlobalStore,
} = createAppStore(
  { count: 0, user: null },
  {
    increment(s) {
      s.count += 1;
    },
  },
  {
    async fetchUser(s) {
      s.setUser(
        await request.get("/api/user"),
      );
    },
  },
);
```

### 精确计算

```tsx
import {
  Decimal,
  toDecimal,
  util,
} from "@ead/suid-utils-react";

util.add(0.1, 0.2); // 0.3（精确）
util.mul(0.1, 3); // 0.3
util.sumBy(items, "amount"); // 按字段求和
util.calcForeignAmount({
  amount: 100,
  fromUnit: "USD",
  rate: 7.25,
  toUnit: "CNY",
}); // 外币换算
```

### 格式化

```tsx
import {
  formaterNumber,
  toChineseAmount,
  toFileSize,
  toThousandsAmount,
} from "@ead/suid-utils-react";

formaterNumber(1234.5, 2, true, "元"); // ''1,234.50元''
toChineseAmount(12345); // ''壹万贰仟叁佰肆拾伍元整''
toFileSize(1073741824); // ''1.00GB''
toThousandsAmount(1234567, {
  currency: true,
}); // ''¥1,234,567.00''
```

### 树操作

```tsx
import {
  buildTree,
  filterTree,
  getAllChildByNodeValue,
  updateLazyNodeChildren,
} from "@ead/suid-utils-react";

const tree = buildTree(flatList, {
  id: "id",
  pid: "parentId",
});
const filtered = filterTree(
  tree,
  (n) => n.status === "active",
  { includeParent: true },
);
const { keys } = getAllChildByNodeValue(
  tree,
  "parent-1",
); // 获取所有后代
```

### 存储与事件

```tsx
import {
  storage,
  eventBus,
  PORTAL_EVENTS,
} from "@ead/suid-utils-react";

// 三种存储后端（localStorage / sessionStorage / IndexedDB）
storage.localStorage.set(
  "token",
  value,
);
const data =
  await storage.webStorage.get(
    "largeData",
  ); // 异步 IndexedDB

// 事件总线
eventBus.emit(PORTAL_EVENTS.OPEN_TAB, {
  url: "/detail",
  title: "详情",
});
eventBus.myOn("form:saved", handler); // 页面级私有事件
```

### 其他常用 Hooks

```tsx
import {
  useCopyToClipboard,
  useMobile,
  useDocumentTitle,
  useLockScroll,
} from "@ead/suid-utils-react";

const [copied, copy] =
  useCopyToClipboard();
const { isMobile } = useMobile();
useDocumentTitle("用户管理");
useLockScroll(rootRef, visible); // 移动端防滚动穿透
```

> 完整 API（12 Hooks + 8 工具函数 +
> 20+ 纯 JS 工具）见
> [references/utils-hooks.md](references/utils-hooks.md)

## 场景决策表

### 表单场景

| 场景                  | 首选组件                                | 备选                      |
| --------------------- | --------------------------------------- | ------------------------- |
| 金额输入              | `MoneyInput`                            | `InputNumber` + formatter |
| 金额展示              | `Money`                                 | `Statistic`               |
| 中文大写金额          | `ChineseAmount`                         | 自行转换                  |
| 下拉选择(远程数据)    | `ComboList`                             | `Select` + 自行请求       |
| 树形下拉选择          | `ComboTree`                             | `TreeSelect`              |
| 组织架构选择          | `OrganizationTree`                      | `TreeSelect`              |
| 条码输入/展示         | `BarCode`                               | `Input`                   |
| 日期筛选              | `FilterDate`                            | `DatePicker`              |
| 枚举/状态条件筛选     | `FilterView`                            | `Select`                  |
| Cron 表达式构建       | `Cronbuilder`                           | `Input` + 自行解析        |
| Cron 表达式输入       | `CronInput`                             | `Input`                   |
| 图标选择              | `IconPicker`                            | `Input`                   |
| 富文本编辑            | `TextEditor`                            | `Input.TextArea`          |
| 附件管理              | `Attachment`                            | `Upload`                  |
| 文本/数字/选择/开关等 | `Input`/`InputNumber`/`Select`/`Switch` | —                         |

### 展示场景

| 场景                  | 首选组件                           | 备选                       |
| --------------------- | ---------------------------------- | -------------------------- |
| 数据表格(增强)        | `ExtTable`                         | `Table`                    |
| 数据表格(基础)        | `Table`                            | —                          |
| 详情/查看             | `BillView`                         | `Descriptions`             |
| 卡片/列表分页         | `ListCard`                         | `Card` + `List`            |
| 文本溢出省略          | `EllipsisText`                     | `Typography.Text` ellipsis |
| 区域标题              | `BannerTitle`                      | `Typography.Title`         |
| 统计/标签/徽标/头像等 | `Statistic`/`Tag`/`Badge`/`Avatar` | —                          |

### 交互反馈场景

| 场景               | 首选组件                   | 备选         |
| ------------------ | -------------------------- | ------------ |
| 弹窗(增强)         | `ExtModal`                 | `Modal`      |
| 弹窗(基础)         | `Modal`                    | —            |
| 侧边抽屉           | `Drawer`                   | —            |
| 全局消息           | `Message`                  | —            |
| 确认气泡           | `Popconfirm`               | —            |
| 加载/骨架屏/结果页 | `Spin`/`Skeleton`/`Result` | —            |
| 自定义滚动条       | `Scrollbar`                | CSS overflow |

### 业务专用场景

| 场景     | 首选组件       | 备选                |
| -------- | -------------- | ------------------- |
| 权限按钮 | `AuthAction`   | `Button` + 权限判断 |
| 操作按钮 | `ActionButton` | `Button`            |
| 数据审计 | `DataAudit`    | 自行实现            |
| 数据导出 | `DataExport`   | 自行实现            |
| AI 对话  | `Chat`         | 自行实现            |
| 工作流   | `WorkFlow`     | 自行实现            |
| 分享链接 | `ShareLink`    | 自行实现            |

### 工具函数与 Hooks 场景

> 所有工具函数均从
> `@ead/suid-utils-react`
> 导入（包含 Hooks 和重导出的
> `@ead/suid-utils`）

| 场景                                      | 首选方案                                                     | 备选                           |
| ----------------------------------------- | ------------------------------------------------------------ | ------------------------------ |
| 远程数据请求（组件内）                    | `useStore({ url, type, autoLoad })`                          | `request.get/post`             |
| 远程数据请求（表格）                      | `store` prop on `ExtTable/ComboList`                         | `useStore` + `Table`           |
| 持久化状态（localStorage/sessionStorage） | `useStorageState(key, { type, defaultValue })`               | `storage.localStorage.set/get` |
| 精确小数/金额运算                         | `util.add/sub/mul/div/sumBy`                                 | `Decimal` 实例                 |
| 外币换算                                  | `util.calcForeignAmount({ amount, fromUnit, rate, toUnit })` | `Decimal` 手动计算             |
| 中文大写金额                              | `toChineseAmount(amount)`                                    | `ChineseAmount` 组件           |
| 千分位格式化                              | `toThousandsAmount(amount, { currency })`                    | `formaterNumber`               |
| 文件大小格式化                            | `toFileSize(bytes)`                                          | 自行转换                       |
| 树形数据转树                              | `buildTree(flatList)`                                        | `buildTreeData`                |
| 树节点过滤/搜索                           | `filterTree(tree, predicate, { includeParent })`             | `findTree`                     |
| 树节点遍历/查找                           | `forEachTree` / `findTree`                                   | `someTree`                     |
| 获取树节点后代/祖先                       | `getAllChildByNodeValue` / `getAllParentByNodeValue`         | 手动递归                       |
| 更新懒加载树子节点                        | `updateLazyNodeChildren(tree, nodeValue, children)`          | 手动更新                       |
| Excel 导出（JSON → xlsx）                 | `exportJsonToXlsx({ data, columns, fileName })`              | `DataExport` 组件              |
| Excel 导出（自定义下载）                  | `getXlsxArrayData({ data, columns })`                        | `dataToXlsx`                   |
| HTTP 请求                                 | `request.get/post`                                           | 原生 `fetch`                   |
| localStorage 存储                         | `storage.localStorage.set/get/clear`                         | `useStorageState` Hook         |
| IndexedDB 大数据存储                      | `storage.webStorage.set/get`（异步）                         | `localStorage`                 |
| Zustand 全局状态                          | `createAppStore(initialState, actions, effects)`             | `useContext` / Redux           |
| 事件总线（跨页面）                        | `eventBus.emit/on/off`                                       | `PORTAL_EVENTS.OPEN_TAB`       |
| 事件总线（页面内）                        | `eventBus.myEmit/myOn/myOff`                                 | `useSyncExternalStore`         |
| 权限过滤组件                              | `authAction(components)`                                     | `hasPermission`                |
| 权限校验                                  | `hasPermission(authcode)`                                    | `authAction`                   |
| 剪贴板复制                                | `useCopyToClipboard()`                                       | `navigator.clipboard`          |
| 文本搜索高亮                              | `hightLight(text, keyword, color)`                           | 自行正则替换                   |
| 平滑滚动到元素                            | `scrollToElement(''#id'')`                                     | `element.scrollIntoView`       |
| 密码强度校验                              | `checkStrongPassword(password)`                              | 自行正则                       |
| 图片压缩                                  | `compressImageFile(file, { maxWidth, quality })`             | Canvas API                     |
| UUID 生成                                 | `getUUID()`                                                  | `crypto.randomUUID()`          |
| MD5 哈希                                  | `md5(data)`                                                  | Web Crypto API                 |
| 移动端设备检测                            | `useMobile()` Hook                                           | `isMobile()` 函数              |
| 移动端滚动锁定                            | `useLockScroll(ref, shouldLock)`                             | CSS `overflow: hidden`         |
| 分页无限滚动                              | `usePagedInfiniteScroll(service, { pageSize })`              | ahooks `useInfiniteScroll`     |
| 触摸手势追踪                              | `useTouchState()`                                            | Touch Events API               |
| 获取当前用户信息                          | `useUserContext()` / `getContextUser()`                      | `CONST_GLOBAL.CURRENT_USER`    |
| 快捷键绑定                                | `useHotkeys(''ctrl+s'', handler)`                              | `addEventListener(''keydown'')`  |
| 模板字符串插值                            | `tplMessage(''{name}是{age}岁'', values)`                      | `String.replace`               |
| URL 路径匹配                              | `pathMatchRegexp(''/user/:id'', pathname)`                     | `path-to-regexp`               |
| 深比较对象                                | `isDeepEqual(a, b, ignoreKeys)`                              | `JSON.stringify` 对比          |
| 深比较 useMemo                            | `useDeepCompareMemo(factory, deps)`                          | `useMemo` + JSON.stringify     |
| DVA 模型扩展                              | `dvaModel.modelExtend(baseModel, model)`                     | 原生 dva model                 |

## 组合模式

### 搜索／筛选区域（必读）

> **`ExtTableRef`** 仅有 `setStore`、`getStore`、`reloadData` 三个方法，**没有 `search()` 方法**。
> **`FilterView`** 是下拉枚举选择器，不支持 `onSearch`/`children`/`Form.Item` 包裹，**不支持在 Form 中使用**。

ExtTable 搜索有**两种模式**：

#### 模式一：内置快速搜索（推荐，简单关键词搜索）

```tsx
// ✅ ExtTable 内置 quickSearchFields — 无需外部 Form
<ExtTable
  showQuickSearch
  quickSearchFields={[
    { field: ''code'', title: ''公司代码'' },
    { field: ''name'', title: ''公司名称'' },
  ]}
  quickSearchPlaceHolder="请输入代码或名称"
  searchRealTime  // 输入实时搜索（默认 true）
  store={{ url: ''/api/list'', type: ''POST'' }}
  remotePaging
/>
```

#### 模式二：外部筛选 Form + cascade 传参（多条件复杂筛选）

```tsx
import React, { useState, useCallback, useMemo, useRef } from ''react'';
import { ExtTable, Form, Input, ComboList, Button } from ''@ead/suid'';
import type { ExtTableRef } from ''@ead/suid'';
import { SearchOutlined, ReloadOutlined } from ''@ead/suid-icons'';

const [filter, setFilter] = useState<Record<string, any>>({});
const tableRef = useRef<ExtTableRef>(null);

// 构建 cascade 参数（传给 store 请求）
const cascade = useMemo(() => {
  const params: Record<string, any> = {};
  if (filter.keyword) params.keyword = filter.keyword;
  if (filter.status) params.status = filter.status;
  return params;
}, [filter]);

// Form 提交时更新 filter 并触发数据刷新
const handleSearch = useCallback((values: any) => {
  setFilter(values);
  setTimeout(() => tableRef.current?.reloadData(), 0);
}, []);

// 重置时清空 filter 并刷新
const handleReset = useCallback(() => {
  setFilter({});
  setTimeout(() => tableRef.current?.reloadData(), 0);
}, []);

<Form layout="inline" onFinish={handleSearch}>
  <Form.Item name="keyword" label="关键词">
    <Input placeholder="请输入" allowClear />
  </Form.Item>
  <Form.Item name="status" label="状态">
    <ComboList store={{ url: ''/api/enums/status'' }} reader={{ textField: ''label'', valueField: ''value'' }} allowClear />
  </Form.Item>
  <Form.Item>
    <Button htmlType="submit" type="primary" icon={<SearchOutlined />}>搜索</Button>
    <Button onClick={handleReset} icon={<ReloadOutlined />}>重置</Button>
  </Form.Item>
</Form>

<ExtTable
  ref={tableRef}
  cascade={cascade}
  store={{ url: ''/api/list'', type: ''POST'' }}
  remotePaging
/>
```

> **`FilterView` 的正确用法**：独立的枚举/状态下拉选择器，用于表格/列表工具栏中的单条件快速筛选。

```tsx
// ✅ 正确：FilterView 作为工具栏独立筛选器
<FilterView
  dataSource={[{ title: ''全部'', key: ''ALL'' }, { title: ''草稿'', key: ''INIT'' }, { title: ''已发布'', key: ''PUBLISHED'' }]}
  labelTitle="状态"
  defaultValue={[''ALL'']}
  onChange={(val) => {
    setFilter((f) => ({ ...f, status: val }));
    tableRef.current?.reloadData();
  }}
/>

// 或使用远程数据
<FilterView
  store={{ url: ''/api/enums/status'' }}
  reader={{ title: ''title'', value: ''key'' }}
  labelTitle="状态"
  onChange={(val) => {
    setFilter((f) => ({ ...f, status: val }));
    tableRef.current?.reloadData();
  }}
/>
```

### 标准增删改查页面

```tsx
import React, { useState, useRef } from ''react'';

import {
  ExtTable, ExtModal, Form, Input, Button, ActionButton, Popconfirm, message,
} from ''@ead/suid'';
import type { ExtTableProps, ExtTableRef } from ''@ead/suid'';

import { PlusOutlined, EditOutlined, DeleteOutlined } from ''@ead/suid-icons'';

import { createStyles } from ''@ead/antd-style'';

const useStyles = createStyles(({ css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    position: relative;
    display: flex;
    flex-direction: column;
  `,
}));

function CrudPage() {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [form] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);

  const columns: ExtTableProps<DataType>[''columns''] = [
    {
      title: ''操作'',
      dataIndex: ''id'',
      width: 120,
      render: (id, record) => (
        <>
          <ActionButton title="编辑" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(id)}>
            <ActionButton title="删除" color="danger" icon={<DeleteOutlined />} />
          </Popconfirm>
        </>
      ),
    },
    { title: ''名称'', dataIndex: ''name'', expandUnusedSpace: true },
  ];

  const handleDelete = async (id: string) => {
    // ... call delete API ...
    message.success(''删除成功'');
    tableRef.current?.reloadData();  // ✅ 刷新表格数据
  };

  const handleSave = async (values: any) => {
    // ... call save API ...
    message.success(''保存成功'');
    setModalOpen(false);
    setEditingRecord(null);
    tableRef.current?.reloadData();  // ✅ 刷新表格数据
  };

  return (
    <div className={styles.page}>
      <ExtTable
        ref={tableRef}
        columns={columns}
        store={{ url: ''/api/list'', type: ''POST'' }}
        remotePaging
        showQuickSearch
        quickSearchFields={[{ field: ''name'', title: ''名称'' }]}
        quickSearchPlaceHolder="请输入关键词"
        toolbar={{
          left: (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => { setEditingRecord(null); setModalOpen(true); }}
            >
              新增
            </Button>
          ),
        }}
      />

      <ExtModal
        open={modalOpen}
        title={editingRecord ? ''编辑'' : ''新增''}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} onFinish={handleSave} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
}
```

### 金额相关表单

```tsx
import {
  Form,
  MoneyInput,
  ChineseAmount,
  Money,
} from "@ead/suid";

function AmountForm() {
  const [form] = Form.useForm();
  const amount = Form.useWatch(
    "amount",
    form,
  );

  return (
    <Form
      form={form}
      onFinish={handleSubmit}
    >
      <Form.Item
        name="amount"
        label="金额"
        rules={[{ required: true }]}
      >
        <MoneyInput
          precision={2}
          thousand
          textAlign="right"
        />
      </Form.Item>
      <Form.Item label="大写金额">
        <ChineseAmount
          amount={amount}
        />
      </Form.Item>
      <Form.Item label="格式化显示">
        <Money
          value={amount}
          suffix="元"
        />
      </Form.Item>
    </Form>
  );
}
```

### 使用 createStyles 的完整示例

```tsx
import {
  Button,
  Card,
  Flex,
} from "@ead/suid";
import { SearchOutlined } from "@ead/suid-icons";
import { createStyles } from "@ead/antd-style";

const useStyles = createStyles(
  ({ token, css, prefixCls }) => ({
    wrapper: css`
      padding: ${token.paddingLG}px;
      background: ${token.colorBgLayout};
    `,
    card: {
      borderRadius:
        token.borderRadiusLG,
      boxShadow:
        token.boxShadowTertiary,
    },
    primaryText: css`
      color: ${token.colorPrimary};
      font-weight: ${token.fontWeightStrong};
    `,
  }),
);

function StyledPage() {
  const { styles, cx } = useStyles();
  return (
    <div className={styles.wrapper}>
      <Card
        className={styles.card}
        title={
          <span
            className={
              styles.primaryText
            }
          >
            标题
          </span>
        }
      >
        <Flex gap="middle">
          <Button
            type="primary"
            icon={<SearchOutlined />}
          >
            搜索
          </Button>
        </Flex>
      </Card>
    </div>
  );
}
```

## 降级策略

**只在 `@ead/suid`
内部降级**：业务组件 → 基础组件（均来自
`@ead/suid`），禁止降级到 `antd` 原生。

```tsx
// 业务组件优先（首选）
import {
  ExtTable,
  ComboList,
  ExtModal,
} from "@ead/suid";
// 降级到基础组件（备选，仍来自 @ead/suid）
import {
  Table,
  Select,
  Modal,
} from "@ead/suid";
// ❌ 禁止：直接使用 antd
// import { Table, Select } from ''antd'';
```

## 图标使用

图标**必须**从 `@ead/suid-icons`
导入，禁止从 `@ead/suid` 或
`@ant-design/icons` 导入。

共
**429 个图标**：Outlined（线条，369 个）+
Filled（实心，60 个）。完整参考见
[references/icons.md](references/icons.md)。

> ⚠️
> **严禁臆想图标名**：图标组件名必须严格来自
> `@ead/suid-icons`
> 实际导出的列表，**不得自行推断或拼造**不存在的名称（例如
> `UploadFileOutlined`、`TableOutlined`、`MoreVertOutlined`
> 等未收录名称）。使用任何图标前，必须先核对
> [references/icons.md](references/icons.md)
> 中的分类列表或通过
> `iconsByTheme.Outlined` /
> `iconsByTheme.Filled`
> 验证该名称确实存在。

### 基础用法

```tsx
import { SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from ''@ead/suid-icons'';

<SearchOutlined style={{ fontSize: 16, color: token.colorPrimary }} />
<SyncOutlined spin />
<SyncOutlined rotate={180} />
<LoadingOutlined />  {/* Loading 图标自动 spin */}
```

### 双色图标（Filled 图标）

```tsx
import { CheckCircleFilled, setTwoToneColor } from ''@ead/suid-icons'';

setTwoToneColor(''#1890ff'');                     // 全局设置主色
<CheckCircleFilled twoToneColor="#eb2f96" />    // 单个覆盖
<CheckCircleFilled twoToneColor={[''#eb2f96'', ''#f5222d'']} /> // 主色+辅色
```

### iconfont 集成

```tsx
import { createFromIconfontCN } from "@ead/suid-icons";

const IconFont = createFromIconfontCN({
  scriptUrl:
    "//at.alicdn.com/t/font_xxx.js",
});
<IconFont type="icon-tuichu" />;
```

### 自定义 SVG 图标

```tsx
import Icon from "@ead/suid-icons";

const MyIcon = (props) => (
  <Icon
    component={() => (
      <svg
        width="1em"
        height="1em"
        fill="currentColor"
        viewBox="0 0 1024 1024"
      >
        <path d="..." />
      </svg>
    )}
    {...props}
  />
);

<MyIcon
  style={{ color: "hotpink" }}
  spin
/>;
```

### 动态加载

```tsx
import * as SuidIcons from "@ead/suid-icons";
import { iconsByTheme } from "@ead/suid-icons";

const IconComp =
  SuidIcons["HomeOutlined"]; // 按名称动态访问
iconsByTheme.Outlined; // 369 个 Outlined 图标名列表
iconsByTheme.Filled; // 60 个 Filled 图标名列表
```

## 详细 API 参考

按需读取以下参考文件，不要一次性全部加载：

- **业务扩展组件** (ExtTable, ComboList,
  MoneyInput, Chat, WorkFlow 等 28 个):
  [references/ext-business.md](references/ext-business.md)
- **表单输入组件** (Input, Select,
  DatePicker, Form 等 19 个):
  [references/form-input.md](references/form-input.md)
- **数据展示组件** (Table, Descriptions,
  Tag, Tree 等 16 个):
  [references/data-display.md](references/data-display.md)
- **反馈交互组件** (Modal, Drawer,
  Message, Spin 等 11 个):
  [references/feedback.md](references/feedback.md)
- **导航布局组件** (Layout, Menu, Tabs,
  Splitter 等 18 个):
  [references/navigation-layout.md](references/navigation-layout.md)
- **通用基础组件** (Button,
  ConfigProvider, App 等 5 个):
  [references/general.md](references/general.md)
- **工具函数与 Hooks** (useStore,
  storage, Decimal, tree,
  eventBus 等 40+):
  [references/utils-hooks.md](references/utils-hooks.md)
- **图标** (Outlined 369 + Filled
  60, 双色, iconfont, 自定义 SVG):
  [references/icons.md](references/icons.md)
', CURRENT_TIMESTAMP),
('SKILBLTNEADP000000000000000000000000', 'eadp-backend', 'sei-core 分层架构后端开发技能', '{"origin": "local:eadp-backend"}', '---
name: eadp-backend
description: >
  EADP/SEI Java Spring Boot backend development for business modules following the sei-core platform''s
  layered architecture, JPA persistence, and framework conventions. Use when implementing or modifying
  an EADP/SEI backend module with Entity, DTO, API, Controller, Service, DAO, or DAOImpl code.
  Typical triggers include creating a new backend module, adding save/query/delete APIs, building DTO
  and entity mappings, adding attachment binding fields, integrating workflow callbacks, writing JPA
  data access, or following EADP naming, transaction, validation, and response conventions. Also use
  for Chinese requests about layered business-module implementation, attachment binding, workflow
  callbacks, ResultData-based responses, or EADP backend coding standards.
---

# EADP Backend Development

Implement EADP backend code with the sei-core platform''s standard layering and framework idioms. Prefer these patterns over generic Spring Boot advice when the task is inside an EADP or SEI codebase.

## Architecture

```
Entity → DAO (interface) → DAOImpl → Service → Controller → API (Feign interface)
                                                              ↕
                                                            DTO
```

Key modules: `sei-core-entity`, `sei-core-dto`, `sei-core-api`, `sei-core-service`, `sei-core-webmvc`.

**Choose the variant** based on entity type:
- **Plain entity** → BaseEntityApi / BaseEntityController / BaseEntityService / BaseEntityDao
- **Tree entity** → BaseTreeApi / BaseTreeController / BaseTreeService / BaseTreeDao
- **Relation entity** → BaseRelationApi / BaseRelationController / BaseRelationService / BaseRelationDao

## Core Rules

- Keep layers strict: `Entity → DAO → DAOImpl → Service → Controller → API`, with `DTO` alongside the API layer.
- Use DTOs for API input and output. Do not expose entities directly through controllers.
- Controllers implement the API interface directly; services do NOT implement the API interface.
- Extend framework base types consistently (see layer expectations below).
- Return `ResultData` from controllers/API. Services return `OperateResult`/`OperateResultWithData` for writes.
- Put write logic in service methods and add `@Transactional` to data-changing methods.
- Use `convertToDto`/`convertToEntity` (ModelMapper-based, from BaseController) for DTO/entity conversion in controllers.
- If the entity participates in EDM or BPM, add the required fields and callbacks.

## Decide What To Read

Read only the reference files needed for the task.

| Request type | Read |
| --- | --- |
| Entity fields, feature interfaces, auto-filtering, EDM/BPM fields | `references/entity.md` |
| DTO design, request/response objects, validation, paging | `references/dto.md` |
| Public API interface definitions, `ResultData`, Feign contracts | `references/api.md` |
| Request mapping, DTO conversion, controller patterns | `references/controller.md` |
| Business logic, transactions, EDM/BPM, lifecycle hooks, Search/SearchFilter queries | `references/service.md` |
| DAO interface, ExtDao patterns, derived queries | `references/dao.md` |
| DAOImpl, dynamic JPQL, EntityManager patterns | `references/dao-impl.md` |
| ContextUtil, session user, i18n, token, async context, testing | `references/context-util.md` |

## Default Implementation Flow

Follow this order unless the user asks for only one layer.

1. Define or update the entity (extend BaseAuditableEntity, add feature interfaces).
2. Define DTOs (extend BaseEntityDto) and validation annotations.
3. Define the DAO interface (extend BaseEntityDao, compose with ExtDao if needed).
4. Implement DAO extension logic (only if custom queries are needed).
5. Define the API interface (compose BaseEntityApi + FindAllApi + FindByPageApi).
6. Implement service logic (extend BaseEntityService, override getDao() and lifecycle hooks).
7. Implement the controller (extend BaseEntityController, implement API interface).

## Layer Expectations

### Entity

- Extend `BaseAuditableEntity` (which extends `BaseEntity` → `AbstractEntity<ID>`).
- ID is `String` (36-char UUID), generated by `IdGenerator.nextIdStr()` — do NOT use `@GeneratedValue`.
- Required annotations: `@Entity`, `@Table(name = "...")`, `@Access(AccessType.FIELD)`.
- Override `@Transient getDisplay()` to return a display-friendly value.
- Use Java `camelCase` field names; DB column names use `snake_case`.
- Add feature interfaces as needed: `ITenant`, `IProjectEntity`, `ISoftDelete`, `IFrozen`, `ICodeUnique`, `IParentEntity`.
- Add one binding ID field per attachment type for EDM.
- Add `flowStatus`, `organizationId`, `organizationName` for BPM-enabled entities.
- Use `BigDecimal` for money; `Long deleted = 0L` for soft delete.

### DTO

- Extend `BaseEntityDto` (has `id` field) for standard business DTOs.
- Add JSR-303 validation annotations where input validation is required.
- Keep request DTOs, response DTOs, and query DTOs explicit.
- Use descriptive names such as `ContractDto`, `ContractSaveRequest`, `ContractQuickQueryParam`.

### API

- API interfaces are Feign client contracts with `@FeignClient`.
- Compose base interfaces: `BaseEntityApi<Dto>`, `FindAllApi<Dto>`, `FindByPageApi<Dto>`, `BaseTreeApi<Dto>`.
- Add `@Operation` metadata on custom methods.
- Return `ResultData` consistently.
- Keep the interface free of business logic, persistence, and transaction concerns.

### Controller

- Implement the API interface directly.
- Extend `BaseEntityController<Entity, Dto>` or `BaseTreeController<Entity, Dto>` for standard modules.
- Use `convertToDto`/`convertToEntity`/`convertToDtoPageResult` from base controller for Entity↔DTO conversion.
- Override `customConvertToDtoMapper()` for custom field mapping.
- Implement `getService()` to return the backing service.
- Keep controllers thin. Delegate business logic to the service layer.

### Service

- Extend `BaseEntityService<YourEntity>` (or `BaseTreeService` for tree entities).
- Override `getDao()` to connect the service to the framework.
- Services do NOT implement the API interface.
- Return `OperateResult`/`OperateResultWithData` for write operations; Controller adapts to `ResultData`.
- Wrap write operations in `@Transactional(rollbackFor = Exception.class)`.
- Use lifecycle hooks (`preInsert`, `preUpdate`, `preDelete`) for validation and enrichment.
- Clean up related EDM bindings during delete flows.
- Update BPM status during workflow callbacks.

### DAO and DAOImpl

- Extend `BaseEntityDao<YourEntity>` (or `BaseTreeDao` for tree entities).
- Compose with `XxxExtDao` for non-trivial custom queries.
- Use Spring Data JPA method naming and `@Query` for simple queries in the main DAO.
- Only write `DAOImpl` when custom JPQL/EntityManager logic is needed.
- `DAOImpl` extends `BaseEntityDaoImpl<YourEntity>` and implements `XxxExtDao`.
- This codebase is JPA-only. Do not reference MyBatis patterns.

## EDM Rules

Apply these when the module has attachments.

- Store binding IDs on the entity, not only on the DTO.
- Use a different binding ID field for each attachment category.
- Bind incoming `docIds` through `DocumentManager`.
- Unbind documents when deleting or otherwise removing the business object.
- Read `references/service.md` for concrete upload, bind, append, download, and cleanup patterns.

## BPM Rules

Apply these when the module enters workflow.

- Implement `BpmDefaultBaseApi` on the API contract.
- Ensure the entity carries the BPM-required organization and status fields.
- Validate business data in `beforeStartFlow`.
- Update status fields in callbacks such as `afterStartFlow` and `afterEndFlow`.
- Use `BpmOperationManager` for start, auto-task, and terminate operations.
- Use `BpmQueryManager` for task and history queries.

## Working Style

- Prefer the module''s existing naming and package structure over inventing new abstractions.
- If only one layer is requested, still preserve EADP conventions at that layer boundary.
- If the request is ambiguous, inspect nearby modules and mirror the closest existing pattern.
- When generating code, bias toward pragmatic consistency with the current codebase over textbook purity.

## Detailed Reference

- `references/entity.md`
- `references/dto.md`
- `references/api.md`
- `references/controller.md`
- `references/service.md`
- `references/dao.md`
- `references/dao-impl.md`
- `references/context-util.md`
', CURRENT_TIMESTAMP),
('SKILBLTNPLAN000000000000000000000000', 'project-planning', '概要设计生成 skill', '{"origin": "local:project-planning"}', '---
name: project-planning
description: 规划书生成 skill。将项目描述精炼为 Plan JSON（summary/techAssumptions/features[featureId,title,outline]/nonGoals）。
---

# project-planning Skill (stub)

> TODO: replace with real skill content. 完整技能内容尚未沉淀；当前为占位 stub，
> 由 `BuiltInSkillRegistry` 从 classpath 加载（multica 维度 g）。

强制输出 Plan JSON 骨架：

- `summary`: 项目一句话摘要
- `techAssumptions`: 技术假设（如 Vite+React+TS+@ead/suid+MSW）
- `features[]`: `{ featureId, title, outline }`
- `nonGoals`: 明确不做的事项

禁止预估 fileScope（留给 feature-design 阶段）。
', CURRENT_TIMESTAMP),
('SKILBLTNFEAT000000000000000000000000', 'feature-design', '功能设计生成 skill', '{"origin": "local:feature-design"}', '---
name: feature-design
description: 功能设计生成 skill。从规划书 feature outline 展开为 FeatureDesign JSON（goal/design/acceptance[]/fileScope）。
---

# feature-design Skill (stub)

> TODO: replace with real skill content. 完整技能内容尚未沉淀；当前为占位 stub，
> 由 `BuiltInSkillRegistry` 从 classpath 加载（multica 维度 g）。

从 outline 展开：

- `goal`: 该功能要达成的目标
- `design`: 实现设计（粒度自定）
- `acceptance[]`: 验收条件
- `fileScope`: 涉及文件范围（须遵循模板文件边界约定）

骨架固定，粒度自定。
', CURRENT_TIMESTAMP);

-- ============================ oc_skill_file 辅助文件 ============================
INSERT INTO oc_skill_file (id, skill_id, path, content, created_date) VALUES
('SKILF_SUID_0100000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/data-display.md', '# SUID 数据展示组件

> 导入: `import { X } from ''@ead/suid''`
> 完整 API 参考: `components/<组件名>/index.en-US.md`

## 表格

### Table
基础数据表格，支持排序、搜索、分页、筛选
- **何时选用**: 需要展示结构化数据（不需要远程分页/列存储等增强功能时）
- **关键 Props**: `dataSource`, `columns`, `rowKey`, `rowSelection`, `pagination`, `onChange`
- **关键 Events**: `onChange(pagination, filters, sorter, extra)`, `onRow`
- **关键 Slots**: `title`, `footer`, `summary`, `expandedRowRender`
- **示例**:
```tsx
<Table
  dataSource={data}
  columns={columns}
  rowKey="id"
  pagination={{ pageSize: 10 }}
/>
```

## 描述/详情

### Descriptions
键值对描述列表
- **何时选用**: 详情页展示只读字段
- **关键 Props**: `title`, `items`, `column`(默认3), `bordered`, `layout`
- **示例**:
```tsx
<Descriptions title="用户信息" bordered column={2}>
  <Descriptions.Item label="姓名">张三</Descriptions.Item>
</Descriptions>
```

## 列表/卡片

### List
列表展示（已废弃，将在下个大版本移除）
- **何时选用**: 需要列表展示数据（建议使用 ListCard 或自定义）
- **关键 Props**: `dataSource`, `renderItem`, `grid`, `pagination`, `loading`
- **子组件**: `List.Item`(`actions`,`extra`), `List.Item.Meta`(`avatar`,`title`,`description`)

### Card
信息卡片容器
- **何时选用**: 需要展示与单个主题相关的信息
- **关键 Props**: `title`, `actions`, `cover`, `extra`, `variant`(''outlined''|''borderless'')
- **子组件**: `Card.Grid`, `Card.Meta`(`avatar`,`title`,`description`)

### Carousel
轮播/走马灯
- **何时选用**: 需要循环展示内容
- **关键 Props**: `autoplay`, `effect`(''scrollx''|''fade''), `dots`, `arrows`

## 标签/徽标

### Tag
标签标记
- **何时选用**: 需要标记和分类
- **关键 Props**: `color`, `closable`/`closeIcon`, `icon`, `variant`(''filled''|''solid''|''outlined''), `disabled`
- **子组件**: `Tag.CheckableTag`, `Tag.CheckableTagGroup`

### Badge
徽标数字/状态点
- **何时选用**: 需要在元素旁显示数量或状态
- **关键 Props**: `count`, `dot`, `overflowCount`, `status`, `color`
- **子组件**: `Badge.Ribbon`

### Avatar
头像
- **何时选用**: 需要展示用户/实体头像
- **关键 Props**: `src`, `size`, `shape`(''circle''|''square''), `icon`, `onError`
- **子组件**: `Avatar.Group`(`max`,`size`)

## 数据可视化

### Statistic
统计数值展示
- **何时选用**: 需要展示统计数字
- **关键 Props**: `value`, `title`, `prefix`, `suffix`, `precision`
- **子组件**: `Statistic.Timer`(替代已废弃的 Countdown)

### Progress
进度条
- **何时选用**: 需要展示操作进度
- **关键 Props**: `percent`, `type`(''line''|''circle''|''dashboard''), `status`, `showInfo`, `strokeColor`

## 树形

### Tree
树形控件
- **何时选用**: 需要展示/操作层级结构数据
- **关键 Props**: `treeData`, `checkedKeys`, `expandedKeys`, `selectedKeys`, `checkable`
- **关键 Events**: `onCheck`, `onSelect`, `onExpand`, `onDrop`
- **关键 Slots**: `titleRender`
- **子组件**: `Tree.DirectoryTree`(`expandAction`)

### Timeline
时间线
- **何时选用**: 需要按时间顺序展示事件
- **关键 Props**: `items`, `mode`(''start''|''alternate''|''end''), `orientation`(''vertical''|''horizontal''), `variant`

## 图片/二维码

### Image
图片预览
- **何时选用**: 需要可预览的图片展示
- **关键 Props**: `src`, `preview`, `fallback`, `placeholder`, `width`/`height`
- **子组件**: `Image.PreviewGroup`

### QRCode
二维码
- **何时选用**: 需要生成二维码
- **关键 Props**: `value`, `type`(''canvas''|''svg''), `size`, `icon`, `errorLevel`

## 其他

### Empty
空状态
- **何时选用**: 无数据时的占位展示
- **关键 Props**: `description`, `image`, `imageStyle`

### Typography
排版文字，支持复制、编辑、省略
- **何时选用**: 文字展示，需要复制/编辑/省略等功能
- **关键 Props**: `copyable`, `editable`, `ellipsis`, `type`(''secondary''|''success''|''warning''|''danger'')
- **子组件**: `Typography.Text`, `Typography.Title`(`level` 1-5), `Typography.Paragraph`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0200000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/ext-business.md', '# SUID 业务扩展组件 (ext-suid)

> 所有组件统一从 `@ead/suid` 导入，图标从 `@ead/suid-icons` 导入，工具类型从 `@ead/suid-utils-react` 导入
> 完整 API: `https://sei.changhong.com/suid-react-v2/components/ext-suid/<组件名>`

## 数据表格

### ExtTable
增强数据表格，内置远程分页、快捷搜索、个性化存储、工具栏
- **何时选用**: 需要分页表格展示数据，尤其是远程分页、列个性化存储场景
- **降级**: `Table`（需自行实现分页、远程请求、列存储）
- **关键 Props**:

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `columns` | 列配置，支持多表头 | `ExtColumnProps<T>[]` | `[]` |
| `dataSource` | 本地数据源 | `any[]` | `[]` |
| `store` | 远程数据接口配置，设置后覆盖 dataSource | `StoreOption` | - |
| `remotePaging` | 是否远程分页 | `boolean` | - |
| `rowKey` | 数据唯一键 | `string` | `''id''` |
| `toolbar` | 工具栏配置 `{ left, right }` | `Toolbar` | - |
| `header` | 表格容器头部（BannerTitle配置） | `ExtTableHead` | - |
| `showQuickSearch` | 是否显示快速搜索框 | `boolean` | `true` |
| `quickSearchFields` | 搜索字段，`[''*'']` 全字段 | `string[] \| SearchProperty[]` | - |
| `quickSearchWidth` | 搜索框宽度（toolbar的left定义后才生效） | `number` | - |
| `storageCfg` | 个性化存储配置 | `StorageCfg` | - |
| `sort` | 排序配置 `{ fields: [{name, sortOrder}] }` | `Sort` | - |
| `cascade` | 级联参数（附加请求参数） | `Record<string, any>` | - |
| `pagination` | 分页配置 | `boolean \| ExtTablePagination` | - |
| `size` | 尺寸 | `''middle'' \| ''small'' \| ''large''` | `''middle''` |

- **ExtColumnProps 扩展字段**:

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `width` | 列宽 | `80` |
| `expandUnusedSpace` | 占满表格剩余空间（设为true后不可调整列宽） | - |
| `hidden` | 是否隐藏 | - |
| `notExport` | 导出时不包含此列 | - |
| `dataType` | 数据类型：`text/date/datetime/number/year/month/boolean` | `''text''` |
| `titleAlias` | 列标题别名（title非string时需设置） | - |

- **storageCfg 配置**:

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `storageType` | `''local'' \| ''remote''` | `''remote''` |
| `dataView` | 启用数据视图存储 | - |
| `rowColumn` | 启用表格行列存储 | - |
| `storageId` | 全局唯一ID（不设则自动hash） | - |

- **示例**:
```tsx
import { ExtTable } from ''@ead/suid'';
import type { ExtTableProps } from ''@ead/suid'';
import { StoreOption } from ''@ead/suid-utils-react'';
import { ReloadOutlined } from ''@ead/suid-icons'';

const columns: ExtTableProps<DataType>[''columns''] = [
  { title: ''名称'', dataIndex: ''name'', expandUnusedSpace: true },
  { title: ''状态'', dataIndex: ''status'', width: 100 },
];

<ExtTable
  columns={columns}
  store={{ url: ''/api/list'', type: ''POST'' }}
  remotePaging
  quickSearchFields={[''name'', ''code'']}
  toolbar={{
    left: <Button>新增</Button>,
    right: <Button icon={<ReloadOutlined />} />,
  }}
  storageCfg={{ storageType: ''local'', rowColumn: true }}
  sort={{ fields: [{ name: ''createTime'', sortOrder: ''desc'' }] }}
/>
```

### ListCard
列表/卡片式分页数据展示，支持选择、搜索、工具栏
- **何时选用**: 需要以卡片或列表形式展示分页数据（非表格场景）
- **降级**: `List` + `Card`
- **关键 Props**: `dataSource`, `store`, `header`, `multiple`(多选), `renderItem`
- **关键 Events**: `onSelectChange`, `onLoadingChange`
- **关键 Slots**: `toolbar`(`{left, right}`), `itemField`(avatar/title/desc/extra), `itemActions`
- `quickSearchWidth`: 搜索框宽度（toolbar.left 定义后才生效）

## 表单增强

### ComboList
下拉列表选择，支持本地/远程数据、分页、快速搜索、级联
- **何时选用**: 需要从下拉列表选择，数据来源为远程接口或本地枚举
- **降级**: `Select`（需自行处理远程数据加载）
- **关键 Props**:

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `dataSource` | 本地数据源 | `T[]` | `[]` |
| `store` | 远程数据接口配置 | `StoreOption` | - |
| `checkedStore` | 回填选中数据接口（用于回显） | `StoreOption` | - |
| `checkedData` | 静态回填选中数据 | `T[]` | - |
| `reader` | 数据读取器 | `ComboListReader` | - |
| `rowKey` | 数据唯一键 | `string` | `''id''` |
| `multiple` | 是否多选 | `boolean` | - |
| `remotePaging` | 是否远程分页 | `boolean` | - |
| `extFields` | 扩展字段（从 onChange ext 中获取） | `string[]` | - |
| `cascade` | 级联条件配置 | `Cascade` | - |
| `quickSearchFields` | 搜索字段 | `string[]` | `[''code'',''name'']` |
| `allowClear` | 允许清除 | `boolean` | `false` |
| `maxCount` | 最多展示个数 | `number` | - |

- **ComboListReader**:

| 属性 | 说明 | 类型 |
|------|------|------|
| `textField` | 显示文本字段名，支持函数 | `string \| (item, index) => string` |
| `descField` | 次要信息字段名 | `string \| (item, index) => string \| ReactNode` |
| `avatar` | 图标字段名 | `string \| (item, index) => ReactNode` |
| `extFields` | 扩展字段映射 | `string[]` |
| `data` | 截取的数据节点 | `string` |

- **示例**:
```tsx
import { ComboList, Form } from ''@ead/suid'';
import { StoreOption } from ''@ead/suid-utils-react'';

// 本地数据
<ComboList
  rowKey="code"
  dataSource={[{ name: ''北京'', code: ''001'' }]}
  quickSearchFields={[''name'']}
  reader={{ textField: ''name'', descField: ''code'' }}
  onChange={(value, ext) => {}}
/>

// 远程接口 + 扩展字段 + 级联
<ComboList
  rowKey="code"
  store={{ url: ''/api/users'', type: ''POST'' }}
  extFields={[''userCode'']}
  multiple
  remotePaging
  cascade={{
    fields: [{ formItemName: ''deptId'', valueField: ''deptCode'' }],
    params: { corpCode: ''Q000'' },
  }}
  reader={{ textField: (u) => `${u.userName}-${u.code}`, extFields: [''code''] }}
/>
```

### ComboTree
下拉树形选择，支持本地/远程数据、异步加载
- **何时选用**: 需要选择树形层级结构数据（如分类、区域）
- **降级**: `TreeSelect`（需自行处理数据加载和映射）
- **关键 Props**: `dataSource`, `store`, `reader`(`textField`, `childrenField`), `multiple`, `loadData`(异步加载节点)
- **关键 Events**: `onChange(value)`

### MoneyInput
金额输入框，支持精度、千分位、对齐方式
- **何时选用**: 需要输入金额，要求千分位、精度控制
- **降级**: `InputNumber` + formatter/parser
- **关键 Props**: `precision`(默认2), `precisionType`(''round''|''floor''|''ceil''), `thousand`(千分位), `textAlign`(''left''|''center''|''right''), `selectMode`

```tsx
<MoneyInput precision={2} thousand textAlign="right" />
```

### FilterView
枚举/状态下拉筛选选择器 — **下拉面板类选择器，非搜索表单容器**

> ⚠️ **常见错误**：不要给 `FilterView` 传 `onSearch`/`children`/`Form.Item`。
> `FilterView` 是自包含的下拉选择器，**不支持 `onSearch` 回调**，**不支持在 Form 中使用**，**不支持包裹子组件**。
> 如需多条件搜索区域，使用 `Form` + `Form.Item`（见 SKILL.md "搜索区域" 章节）。

- **何时选用**: 表格/列表工具栏中按枚举值/状态快速筛选
- **降级**: `Select`（需自行处理数据和 UI）
- **关键 Props**:

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `dataSource` | 数据源 | `any[]` | `[]` |
| `store` | 远程数据接口 | `StoreOption` | - |
| `reader` | 读取器 `{ title: ''title'', value: ''key'' }` | `FilterViewReader` | - |
| `labelTitle` | 内置标题 | `string` | - |
| `rowKey` | 唯一键属性 | `string` | `''key''` |
| `defaultValue` | 默认选中值（number为索引，string为rowKey值） | `number[] \| string[]` | - |
| `selectedKeys` | 受控选中值 | `string[]` | - |
| `multiple` | 是否多选 | `boolean` | - |
| `allowClear` | 允许清除 | `boolean` | `false` |
| `listBeforeExtra` | 列表顶部额外内容 | `ReactNode` | - |
| `listAfterExtra` | 列表底部额外内容 | `ReactNode` | - |
| `onChange` | 值变化回调 | `(value?: any) => void` | - |

```tsx
<FilterView
  dataSource={[{ title: ''全部'', key: ''ALL'' }, { title: ''草稿'', key: ''INIT'' }]}
  labelTitle="审批状态"
  defaultValue={[''ALL'']}
  onChange={(val) => {}}
/>
```

### FilterDate
日期范围筛选
- **何时选用**: 表格/列表的日期范围筛选条件
- **降级**: `DatePicker.RangePicker`
- **关键 Props**: `presets`(快捷选项), `value`, `format`, `defaultValue`, `onChange`

### Cronbuilder
Cron 表达式可视化编辑面板
- **何时选用**: 需要用户通过界面构建 Cron 表达式
- **注意**: 导入名为 `Cronbuilder`（小写 b），非 `CronBuilder`
- **关键 Props**: `value`, `onChange(value, description)`, `showExpression`, `showSeconds`, `showDescription`

### CronInput
Cron 表达式输入框（带下拉面板）
- **关键 Props**: `value`, `onChange`, `disabled`, `status`, `showSeconds`

### IconPicker
图标选择器
- **关键 Props**: `value`, `onChange`, `placeholder`, `status`, `allowClear`

### TextEditor
富文本编辑器（基于 Quill）
- **降级**: `Input.TextArea`（降级为纯文本）
- **关键 Props**: `value`, `onChange`, `height`, `toolbar`, `onImageUpload`

### Attachment
附件上传、下载、预览管理，支持拖拽和粘贴上传
- **降级**: `Upload`（需自行实现预览和业务关联）
- **关键 Props**: `entityId`(业务实体ID), `docIds`, `uploadUrl`, `multiple`, `maxCount`
- **关键 Events**: `onChange`, `onSelectChange`

## 数据展示

### Money
金额展示，支持千分位、精度、前后缀、动画
- **何时选用**: 需要格式化展示金额
- **降级**: `Statistic`
- **关键 Props**:

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `value` | 金额值 | `number` | - |
| `roundType` | 舍入类型 | `''round'' \| ''ceil''` | `''round''` |
| `precision` | 精度（继承自 Statistic） | `number` | `2` |
| `prefix` | 前缀 | `string \| ReactNode` | - |
| `suffix` | 后缀 | `string \| ReactNode` | - |
| `animation` | 是否开启动效 | `boolean` | - |

```tsx
<Money value={127512.12} suffix="元" animation />
```

### ChineseAmount
数值金额转大写中文
- **何时选用**: 需要展示中文大写金额
- **关键 Props**: `amount`(数字金额，**注意是 `amount` 而非 `value`**), `className`, `style`

```tsx
<ChineseAmount amount={512.12} />
```

### EllipsisText
多行文本溢出省略
- **降级**: `Typography.Text` ellipsis
- **关键 Props**: `text`, `lineClamp`(行数,默认1), `tooltip`(是否显示提示), `clickExpand`

### BarCode
条码生成和展示（基于 JsBarcode）
- **关键 Props**: `encodeText`, `format`(默认CODE128), `height`, `width`, `displayValue`

### BillView
发票/票据详情预览
- **降级**: `Descriptions`
- **关键 Props**: `invoiceType`(''G''普通|''E''电子|''FE''全电), `items`, `totalMoney`, `buyerName`, `sellerName`

### BannerTitle
横幅标题，支持主标题 + 副标题
- **降级**: `Typography.Title`
- **关键 Props**: `title`, `subTitle`, `className`, `style`

```tsx
<BannerTitle title="系统日志" subTitle="近7天数据" />
```

### OrganizationTree
组织架构树，支持多维组织
- **降级**: `Tree` / `TreeSelect`
- **关键 Props**: `enableMultiDimensional`(多维), `selectable`, `multiple`, `checkable`, `checkMode`, `onlySelectLeaf`(仅叶子节点可选)
- **关键 Events**: `onSelect`, `onCheck`, `onDimensionChange`

## 弹窗增强

### ExtModal
扩展弹窗，支持拖拽、全屏、副标题
- **何时选用**: 需要弹窗支持拖拽移动或全屏切换
- **降级**: `Modal`
- **关键 Props（在 Modal 基础上新增）**:

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `fullScreen` | 是否全屏 | `boolean` | `false` |
| `subTitle` | 副标题 | `string \| ReactNode` | - |
| `showTriggerBack` | 是否显示返回按钮 | `boolean` | `false` |
| `onTriggerBack` | 返回按钮点击 | `(e: MouseEvent) => void` | - |

- **注意**: 全屏时会忽略 `getContainer` 配置，自动使用默认配置

```tsx
<ExtModal
  open={open}
  title="主标题"
  subTitle="副标题"
  fullScreen
  destroyOnHidden
  onOk={() => form.submit()}
  onCancel={() => setOpen(false)}
>
  <Form form={form}>...</Form>
</ExtModal>
```

## 操作组件

### ActionButton
表格/列表行内操作按钮，支持仅图标/仅文本/图标+文本模式
- **降级**: `Button`
- **关键 Props**:

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `title` | 按钮标题 | `string` | - |
| `icon` | 图标 | `ReactNode` | - |
| `actionType` | 显示类型 | `''icon'' \| ''title'' \| ''both''` | `''icon''` |
| `color` | 颜色 | `''default'' \| ''primary'' \| ''danger'' \| PresetColors` | `''default''` |
| `variant` | 样式 | `''filled'' \| ''text'' \| ''link''` | `''text''` |
| `disabled` | 禁用 | `boolean` | - |
| `ignoreGlobal` | 忽略全局 ConfigProvider 的 actionButton 配置 | `boolean` | - |

- **全局控制**: 通过 `ConfigProvider` 的 `actionButton={{ actionType }}` 统一控制显示类型

```tsx
import { ActionButton } from ''@ead/suid'';
import { EditOutlined, DeleteOutlined } from ''@ead/suid-icons'';

// 操作列中的按钮组
<>
  <ActionButton title="编辑" icon={<EditOutlined />} onClick={handleEdit} />
  <ActionButton title="删除" color="danger" icon={<DeleteOutlined />} onClick={handleDelete} />
</>
```

### AuthAction
功能权限校验包裹组件（**不支持 SSR**）
- **降级**: `Button` + 手动权限判断
- **关键 Props**: `items`(IAuthActionItem[]), `children`
- **AuthActionItem Props**: `authCode`, `ignore`(忽略检查), `endMatch`(末项匹配)

```tsx
import { AuthAction, Button } from ''@ead/suid'';
import type { IAuthActionItem } from ''@ead/suid'';

// 推荐：items 写法
const items: IAuthActionItem[] = [
  { authCode: ''CREATE'', children: <Button>创建</Button> },
  { authCode: ''DELETE'', children: <Button danger>删除</Button> },
];
<AuthAction items={items} />

// 子组件写法
<AuthAction>
  <AuthAction.AuthActionItem authCode="CREATE">
    <Button>创建</Button>
  </AuthAction.AuthActionItem>
</AuthAction>
```

## 业务功能

### DataAudit
数据审计，查看数据变更历史
- **关键 Props**: `entityId`, `baseURL`, `entityDataAuditUrl`, `logId`, `sceneCode`

### DataExport
通用数据导出为 Excel，支持远程数据导出
- **关键 Props**: `baseURL`, `downloadUrl`, `exportConfig`, `className`, `style`

### Chat
AI 对话组件，支持流式响应、气泡渲染、消息管理、思维链展示
- **关键 Props**: `appId`, `streamUrl`, `bubble`, `sender`, `conversation`, `globalParams`, `components`
- **关键 Events**: `onBubbleCreated`, `onBubbleUpdate`
- **关键 Slots**: `renderNavbar`, `renderWelcome`
- **实例方法**: `addAIMessageItem`（通过 ref 调用）

### WorkFlow
工作流引擎集成（发起、审批、终止、历史、预测），支持审批和流程历史局部内容配置
- **关键 Props**: `businessId`, `businessModelCode`, `beforeStart`, `startComplete`, `store`

### ShareLink
分享链接给其他用户（支持虹云通分享并选择分享人）
- **关键 Props**: `baseUrl`, `shareOptions`, `channels`, `buttonProps`

### Scrollbar
自定义样式滚动条
- **降级**: CSS overflow
- **关键 Props**: `maxHeight`, `onScroll`, `onScrollFrame`, `onScrollStart`, `onScrollStop`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0300000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/feedback.md', '# SUID 反馈交互组件

> 导入: `import { X } from ''@ead/suid''`
> 完整 API 参考: `components/<组件名>/index.en-US.md`

## 弹窗/抽屉

### Modal
模态对话框
- **何时选用**: 需要用户在不离开当前页面的情况下交互
- **关键 Props**: `open`, `title`, `onOk`, `onCancel`, `footer`(设null隐藏), `width`, `confirmLoading`
- **静态方法**: `Modal.info/success/error/warning/confirm`
- **Hooks**: `Modal.useModal()` → `[modal, contextHolder]`
- **示例**:
```tsx
<Modal open={open} title="确认" onOk={handleOk} onCancel={() => setOpen(false)}>
  <p>确定要执行此操作吗？</p>
</Modal>
```

### Drawer
侧边抽屉
- **何时选用**: 侧边面板式交互（表单、详情、子任务）
- **关键 Props**: `open`, `placement`(''top''|''right''|''bottom''|''left''), `title`, `onClose`, `size`
- **关键 Slots**: `footer`, `extra`

## 全局提示

### Message
全局消息提示（轻量级，自动消失）
- **何时选用**: 操作反馈提示，不中断用户
- **方法**: `message.success/info/warning/error/loading`
- **Hooks**: `message.useMessage()` → `[messageApi, contextHolder]`
- **关键配置**: `content`, `duration`(秒,默认3), `key`, `icon`
- **示例**:
```tsx
const [messageApi, contextHolder] = message.useMessage();
messageApi.success(''操作成功'');
```

### Notification
通知提醒框（带标题和描述）
- **何时选用**: 需要展示更多信息的全局提示
- **方法**: `notification.success/info/warning/error/open`
- **Hooks**: `notification.useNotification()` → `[api, contextHolder]`
- **关键配置**: `title`, `description`, `duration`(默认4.5), `placement`, `icon`

## 确认/气泡

### Popconfirm
气泡确认框
- **何时选用**: 轻量级操作确认（替代 Modal.confirm）
- **关键 Props**: `title`, `description`, `icon`, `okText`, `cancelText`, `onConfirm`, `onCancel`
- **示例**:
```tsx
<Popconfirm title="确定删除？" onConfirm={handleDelete}>
  <Button danger>删除</Button>
</Popconfirm>
```

### Popover
气泡卡片
- **何时选用**: 需要在元素旁展示额外信息或操作
- **关键 Props**: `content`, `title`, `trigger`(''hover''|''click''), `placement`, `onOpenChange`

### Tooltip
文字提示
- **何时选用**: 鼠标悬停时展示简短提示
- **关键 Props**: `title`, `open`, `placement`, `trigger`, `color`

## 加载/状态

### Spin
加载指示器
- **何时选用**: 内容加载中
- **关键 Props**: `spinning`, `indicator`, `size`, `delay`, `fullscreen`
- **示例**:
```tsx
<Spin spinning={loading}>
  <Content />
</Spin>
```

### Skeleton
骨架屏
- **何时选用**: 内容加载前的占位展示
- **关键 Props**: `loading`, `active`, `avatar`, `paragraph`, `title`
- **子组件**: `Skeleton.Avatar`, `Skeleton.Button`, `Skeleton.Input`

### Alert
警告提示
- **何时选用**: 需要用户注意的提示信息
- **关键 Props**: `type`(''success''|''info''|''warning''|''error''), `title`, `description`, `closable`, `showIcon`
- **子组件**: `Alert.ErrorBoundary`

### Result
结果页
- **何时选用**: 操作完成后的结果反馈
- **关键 Props**: `status`(''success''|''error''|''info''|''warning''|''404''|''403''|''500''), `title`, `subTitle`, `extra`, `icon`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0400000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/form-input.md', '# SUID 表单输入组件

> 导入: `import { X } from ''@ead/suid''`
> 完整 API 参考: `components/<组件名>/index.en-US.md`

## 文本输入

### Input
基础文本输入框，支持前后缀、清除、密码、搜索、OTP 等变体
- **何时选用**: 文本输入场景
- **关键 Props**: `value`, `onChange`, `allowClear`, `disabled`, `variant`(''outlined''|''borderless''|''filled''|''underlined'')
- **子组件**: `Input.TextArea`(`autoSize`), `Input.Search`(`onSearch`,`enterButton`), `Input.Password`(`visibilityToggle`), `Input.OTP`(`length`,`mask`)
- **示例**:
```tsx
<Input allowClear placeholder="请输入" />
<Input.Search enterButton onSearch={handleSearch} />
<Input.TextArea autoSize={{ minRows: 2, maxRows: 6 }} />
```

### InputNumber
数字输入框，支持范围、步长、格式化
- **何时选用**: 数值输入场景
- **关键 Props**: `value`, `onChange`, `min`, `max`, `step`, `formatter`, `parser`
- **子组件**: `prefix`, `suffix`

## 选择类

### Select
下拉选择，支持搜索、多选、标签模式
- **何时选用**: 选项数量 >= 5 时的选择场景
- **关键 Props**: `value`, `options`, `onChange`, `mode`(''multiple''|''tags''), `showSearch`
- **子组件**: `Select.Option`, `Select.OptGroup`
- **示例**:
```tsx
<Select
  options={[{ label: ''A'', value: ''a'' }, { label: ''B'', value: ''b'' }]}
  showSearch
  onChange={handleChange}
/>
```

### Radio
单选框，支持按钮样式
- **何时选用**: 选项数量 < 5 的单选场景
- **关键 Props**: `value`, `options`, `onChange`, `optionType`(''default''|''button'')
- **子组件**: `Radio.Group`, `Radio.Button`

### Checkbox
多选框，支持半选状态
- **何时选用**: 多选场景
- **关键 Props**: `checked`, `onChange`, `disabled`, `indeterminate`
- **子组件**: `Checkbox.Group`(`value`, `options`, `onChange`)

### Cascader
级联选择，用于省市区等层级数据
- **何时选用**: 需要级联选择关联数据
- **关键 Props**: `options`, `value`, `onChange`, `multiple`, `fieldNames`

### AutoComplete
自动补全输入
- **何时选用**: 需要输入文字时自动补全建议
- **关键 Props**: `options`, `value`, `onChange`, `variant`

### TreeSelect
树形下拉选择
- **何时选用**: 需要在下拉中选择树形结构数据
- **关键 Props**: `treeData`, `value`, `treeCheckable`, `multiple`, `showCheckedStrategy`

### Transfer
穿梭框
- **何时选用**: 需要在两栏之间移动选项
- **关键 Props**: `dataSource`, `targetKeys`, `selectedKeys`, `render`, `oneWay`

## 开关/滑块/评分

### Switch
开关切换
- **何时选用**: 布尔值切换
- **关键 Props**: `checked`, `onChange`, `disabled`, `loading`
- **Form 集成**: 需设置 `valuePropName="checked"`
- **示例**:
```tsx
<Form.Item name="enabled" valuePropName="checked">
  <Switch />
</Form.Item>
```

### Slider
滑块输入
- **何时选用**: 需要在范围内选择数值
- **关键 Props**: `value`, `min`, `max`, `range`, `step`

### Rate
评分
- **何时选用**: 需要星级评分
- **关键 Props**: `value`, `count`, `allowHalf`, `allowClear`

## 日期时间

### DatePicker
日期选择器
- **何时选用**: 需要选择日期
- **关键 Props**: `value`, `picker`(''date''|''week''|''month''|''quarter''|''year''), `format`, `onChange`, `disabled`
- **子组件**: `DatePicker.RangePicker`
- **示例**:
```tsx
<DatePicker format="YYYY-MM-DD" onChange={handleChange} />
<DatePicker.RangePicker />
```

### TimePicker
时间选择器
- **何时选用**: 需要选择时间
- **关键 Props**: `value`, `format`, `disabled`, `use12Hours`, `variant`
- **子组件**: `TimePicker.RangePicker`

## 其他

### Mentions
@提及输入
- **何时选用**: 需要在文本中 @提及某人/某事
- **关键 Props**: `options`, `prefix`(@), `value`, `autoSize`, `variant`

### Upload
文件上传
- **何时选用**: 需要上传文件
- **关键 Props**: `action`, `fileList`, `beforeUpload`, `listType`(''text''|''picture''|''picture-card''|''picture-circle''), `customRequest`
- **示例**:
```tsx
<Upload action="/api/upload" listType="picture-card">
  <Button icon={<UploadOutlined />}>上传</Button>
</Upload>
```

### ColorPicker
颜色选择器
- **何时选用**: 需要选择颜色
- **关键 Props**: `value`, `mode`(''single''|''gradient''), `format`, `presets`, `onChange`

### Form
表单容器，数据域管理、校验
- **何时选用**: 需要收集和校验用户输入
- **关键 Props**: `form`(Form.useForm()), `layout`, `initialValues`, `onFinish`, `onFinishFailed`
- **子组件**: `Form.Item`(`name`,`label`,`rules`,`dependencies`), `Form.List`, `Form.ErrorList`, `Form.Provider`
- **Hooks**: `Form.useForm()`, `Form.useWatch()`, `Form.useFormInstance()`, `Form.Item.useStatus()`
- **示例**:
```tsx
const [form] = Form.useForm();
<Form form={form} onFinish={handleSubmit} layout="vertical">
  <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
    <Input />
  </Form.Item>
</Form>
```

### Segmented
分段控制器
- **何时选用**: 在少量选项间切换（类似 Tabs 但更紧凑）
- **关键 Props**: `value`, `options`, `onChange`, `disabled`, `block`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0500000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/general.md', '# SUID 通用基础组件

> 导入: `import { X } from ''@ead/suid''`
> 完整 API 参考: `https://sei.changhong.com/suid-react-v2/components/<组件名>`

## 图标使用
图标**必须**从 `@ead/suid-icons` 导入，禁止从 `@ead/suid` 或 `@ant-design/icons` 导入：
```tsx
import { SearchOutlined, PlusOutlined } from ''@ead/suid-icons'';
<SearchOutlined spin style={{ fontSize: 16, color: ''#1890ff'' }} />
```
- **关键 Props**: `spin`, `rotate`, `style`(`fontSize`,`color`), `component`(自定义SVG)

## CSS-in-JS（createStyles）
样式使用 `@ead/antd-style` 的 `createStyles`，基于 antd-style：
```tsx
import { createStyles } from ''@ead/antd-style'';

const useStyles = createStyles(({ token, css }) => ({
  container: css`
    padding: ${token.paddingMD}px;
    background: ${token.colorBgContainer};
  `,
  title: {
    color: token.colorTextHeading,
    fontSize: token.fontSizeLG,
  },
}));

function MyComponent() {
  const { styles, cx } = useStyles();
  return <div className={styles.container}>...</div>;
}
```

## Button
操作按钮
- **何时选用**: 触发操作
- **关键 Props**: `type`(''primary''|''dashed''|''link''|''text''|''default''), `variant`, `color`, `size`(''large''|''middle''|''small''), `loading`, `icon`, `disabled`, `danger`, `onClick`
- **示例**:
```tsx
<Button type="primary" loading={submitting} onClick={handleSubmit}>提交</Button>
<Button danger type="primary" icon={<DeleteOutlined />}>删除</Button>
```

## ConfigProvider
全局配置，支持主题、语言、方向、尺寸、actionButton 统一配置
- **关键 Props**: `theme`, `locale`, `direction`(''ltr''|''rtl''), `componentSize`, `variant`(''outlined''|''filled''|''borderless''), `actionButton`(`{ actionType: ''icon''|''title''|''both'' }`)
- **示例**:
```tsx
<ConfigProvider
  theme={{ token: { colorPrimary: ''#1890ff'' } }}
  componentSize="middle"
  actionButton={{ actionType: ''icon'' }}
>
  <App />
</ConfigProvider>
```

## App
应用级包裹，提供全局 message/notification/modal 上下文（推荐替代静态方法）
- **关键 Props**: `component`, `message`(MessageConfig), `notification`(NotificationConfig)
- **Hooks**: `App.useApp()` → `{ message, notification, modal }`
- **示例**:
```tsx
import { App } from ''@ead/suid'';

// 根组件
<ConfigProvider><App><MainContent /></App></ConfigProvider>

// 子组件中使用
function MainContent() {
  const { message, modal } = App.useApp();
  const handleClick = () => message.success(''操作成功'');
}
```

## Calendar
日历
- **关键 Props**: `value`, `mode`(''month''|''year''), `cellRender`, `fullscreen`, `disabledDate`
- **关键 Events**: `onChange`, `onPanelChange`, `onSelect`

## Collapse
折叠面板
- **关键 Props**: `activeKey`, `accordion`, `items`, `collapsible`(''header''|''icon''|''disabled''), `ghost`
- **关键 Events**: `onChange`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0600000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/icons.md', '# SUID 图标完整参考

> 包名: `@ead/suid-icons`（基于 `@ead/suid-icons-svg`）
> 导入: `import { X } from ''@ead/suid-icons''`
> 禁止从 `@ead/suid` 或 `@ant-design/icons` 导入图标

> ⚠️ **严禁臆想图标名**：所有图标组件名必须来自本文件列举的实际导出列表（Outlined/Filled 分类），不得自行推断或拼造不存在的名称（如 `UploadFileOutlined`、`TableOutlined` 等未列出的名称）。使用前必须核对下方分类列表或通过 `iconsByTheme` 验证。

## 主题系统

图标分为两种主题：

| 主题 | 数量 | 命名规则 | 说明 |
|------|------|----------|------|
| **Outlined** | 369 个 | `{Name}Outlined` | 线条/描边风格，覆盖所有分类 |
| **Filled** | 60 个 | `{Name}Filled` | 实心/填充风格，常见于状态、品牌、方向图标 |

```tsx
import { HomeOutlined, HomeFilled, CheckCircleOutlined, CheckCircleFilled } from ''@ead/suid-icons'';

<HomeOutlined />    // 线条风格
<HomeFilled />      // 实心风格
```

> 填充图标集中在：文件类型（FileExcel/Word/Pdf/Image/Zip）、状态圆圈（Check/Close/Info/Warning Circle）、表情（Smile/Meh/Frown/Heart）、品牌（Apple/Android/Windows）、方向箭头等

## 关键 Props

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| `spin` | 旋转动画 | `boolean` | `false` |
| `rotate` | 旋转角度（度） | `number` | - |
| `twoToneColor` | 双色图标颜色 | `string \| [string, string]` | — |
| `style` | 行内样式 | `CSSProperties` | — |
| `className` | CSS 类名 | `string` | — |
| `onClick` | 点击事件 | `function` | — |
| `aria-label` | 无障碍标签 | `string` | — |

> 所有图标组件均通过 `React.forwardRef` 转发 ref 到 `<span>` 元素

## 基础用法

```tsx
import {
  HomeOutlined,
  SearchOutlined,
  SettingOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SyncOutlined,
  LoadingOutlined,
} from ''@ead/suid-icons'';

// 基本使用
<HomeOutlined />
<SearchOutlined />

// 旋转动画
<SyncOutlined spin />
<LoadingOutlined />  {/* Loading 图标自动启用 spin */}

// 旋转指定角度
<SyncOutlined rotate={180} />

// 样式控制
<SearchOutlined style={{ fontSize: 24, color: ''#1890ff'' }} />

// 事件处理（自动添加 tabIndex）
<DeleteOutlined onClick={() => handleDelete(id)} />
```

## 双色图标

Filled 图标支持双色模式，可分别设置主色和辅色。

```tsx
import { CheckCircleFilled, setTwoToneColor } from ''@ead/suid-icons'';

// 方式1：全局设置
setTwoToneColor(''#1890ff'');                    // 单色（自动计算辅色）
setTwoToneColor([''#1890ff'', ''#f5222d'']);       // 显式主色 + 辅色

// 方式2：单个图标 prop 覆盖
<CheckCircleFilled twoToneColor="#eb2f96" />
<CheckCircleFilled twoToneColor={[''#eb2f96'', ''#f5222d'']} />

// 获取当前设置
import { getTwoToneColor } from ''@ead/suid-icons'';
getTwoToneColor(); // string | [string, string]
```

## iconfont 集成

使用 `createFromIconfontCN` 加载 iconfont.cn 上的 SVG 符号图标。

```tsx
import { createFromIconfontCN } from ''@ead/suid-icons'';

const IconFont = createFromIconfontCN({
  scriptUrl: ''//at.alicdn.com/t/font_8d5l8fzk5b87iudi.js'',
  // 也支持多 URL（按反序加载，首个 URL 的图标优先）
  // scriptUrl: [''url1.js'', ''url2.js''],
  extraCommonProps: { style: { fontSize: 16 } }, // 传递给所有图标的额外 props
});

// 使用
<IconFont type="icon-tuichu" />
<IconFont type="icon-facebook" />
<IconFont type="icon-tuichu" spin />
```

**工作原理**：动态注入 `<script>` 标签加载 SVG symbol 定义，通过 `<use xlinkHref="#icon-name">` 引用。

## 自定义 SVG 图标

使用默认导出的 `Icon` 组件包裹自定义 SVG。

```tsx
import Icon from ''@ead/suid-icons'';

// 定义 SVG 组件
const HeartSvg = () => (
  <svg width="1em" height="1em" fill="currentColor" viewBox="0 0 1024 1024">
    <path d="M923 283.6a260.4 260.4 0 0 0-56.9-82.8 264.4 264.4 0 0 0-84-55.5A265.3 265.3 0 0 0 679.7 125c-49.3 0-97.4 13.5-139.2 39-10 6.1-19.5 12.8-28.5 20.1-9-7.3-18.5-14-28.5-20.1a265.3 265.3 0 0 0-334.4 62.1c-51 68-64.3 156-35.4 237.3 13.2 36 32.9 68.8 58.1 97.8 13.4 15.6 28.1 30 43.8 43.2l.2.2L512 864l345.1-352.4c15.7-13.2 30.4-27.6 43.8-43.2 25.2-29 44.9-61.8 58.1-97.8 28.9-81.3 15.6-169.3-35.4-237.3z" />
  </svg>
);

// 包装为图标组件
const HeartIcon = (props) => <Icon component={HeartSvg} {...props} />;

// 使用（自动获得 spin、rotate、事件等能力）
<HeartIcon style={{ color: ''hotpink'', fontSize: 32 }} />
<HeartIcon spin />
```

## IconProvider 上下文

全局配置所有子图标的 CSS 前缀、类名、CSP 等选项。

```tsx
import { IconProvider, createFromIconfontCN } from ''@ead/suid-icons'';

const IconFont = createFromIconfontCN({ scriptUrl: ''//...'' });

<IconProvider value={{
  prefixCls: ''myicon'',        // 自定义 CSS 前缀（默认 ''suidicon''）
  rootClassName: ''custom-cls'', // 额外根类名
  csp: { nonce: ''abc123'' },    // CSP nonce
  layer: ''icons'',              // CSS @layer 名称
}}>
  <HomeOutlined />
  <IconFont type="icon-tuichu" />
</IconProvider>
```

**IconContextProps**:

| 属性 | 说明 |
|------|------|
| `prefixCls` | CSS 类名前缀，默认 `''suidicon''` |
| `rootClassName` | 图标根元素额外类名 |
| `csp` | Content Security Policy nonce |
| `layer` | CSS @layer 名称 |

## 动态加载

通过通配符导入获取所有图标，实现动态访问。

```tsx
import * as SuidIcons from ''@ead/suid-icons'';

// 动态按名称访问
const iconName = ''HomeOutlined'';
const IconComponent = SuidIcons[iconName];
if (IconComponent) {
  return <IconComponent />;
}

// 按主题过滤
const outlinedIcons = Object.keys(SuidIcons).filter(name => name.endsWith(''Outlined''));
const filledIcons = Object.keys(SuidIcons).filter(name => name.endsWith(''Filled''));

// 使用 iconsByTheme 获取分组
import { iconsByTheme } from ''@ead/suid-icons'';
// iconsByTheme.Outlined → string[] (369 个)
// iconsByTheme.Filled → string[] (60 个)
```

## 导出总览

```tsx
// 具体图标组件
import { HomeOutlined, CheckCircleFilled } from ''@ead/suid-icons'';

// 通用 Icon 组件（默认导出，用于自定义 SVG）
import Icon from ''@ead/suid-icons'';

// iconfont 工厂
import { createFromIconfontCN } from ''@ead/suid-icons'';

// 双色全局设置
import { setTwoToneColor, getTwoToneColor } from ''@ead/suid-icons'';

// 上下文
import { IconProvider } from ''@ead/suid-icons'';

// 主题分组
import { iconsByTheme } from ''@ead/suid-icons'';

// 通配符导入
import * as SuidIcons from ''@ead/suid-icons'';
```

## 无障碍

所有图标内置无障碍支持：
- `<span role="img">` 包裹
- 自动设置 `aria-hidden="true"`（除非指定了 `aria-label`）
- 设置 `onClick` 时自动添加 `tabIndex={-1}`
- 通过 `aria-label` prop 支持屏幕阅读器

```tsx
<SearchOutlined aria-label="搜索" />
<CloseOutlined onClick={handleClose} />  {/* 自动 tabIndex */}
```

## 完整图标名称列表

> ⚠️ 使用图标前必须从此列表确认名称存在，**禁止使用未列出的名称**。

### Outlined（线条风格，369 个）

`AccountBookOutlined` `AimOutlined` `AlertOutlined` `AlignCenterOutlined` `AlignLeftOutlined`
`AlignRightOutlined` `AlipayCircleOutlined` `AlipayOutlined` `AndroidOutlined` `ApartmentOutlined`
`ApiOutlined` `AppOutlined` `AppleOutlined` `AppstoreAddOutlined` `AppstoreOutlined`
`AreaChartOutlined` `ArrowDownOutlined` `ArrowLeftOutlined` `ArrowRightOutlined` `ArrowUpOutlined`
`ArrowsAltOutlined` `AudioMutedOutlined` `AudioOutlined` `AuditOutlined` `BankOutlined`
`BarChartOutlined` `BarcodeOutlined` `BarsOutlined` `BellOutlined` `BgColorsOutlined`
`BlockOutlined` `BoldOutlined` `BookOutlined` `BorderBottomOutlined` `BorderLeftOutlined`
`BorderOutlined` `BorderRightOutlined` `BorderTopOutlined` `BorderlessTableOutlined` `BoxPlotOutlined`
`BranchesOutlined` `BugOutlined` `BuildOutlined` `BulbOutlined` `CalculatorOutlined`
`CalendarOutlined` `CameraOutlined` `CarOutlined` `CaretDownOutlined` `CaretLeftOutlined`
`CaretRightOutlined` `CaretUpOutlined` `CarryOutOutlined` `ChangeOutlined` `CheckCircleOutlined`
`CheckOutlined` `CheckSquareOutlined` `CiCircleOutlined` `ClearOutlined` `ClockCircleOutlined`
`CloseCircleOutlined` `CloseOutlined` `CloseSquareOutlined` `CloudDownloadOutlined` `CloudOutlined`
`CloudServerOutlined` `CloudSyncOutlined` `CloudUploadOutlined` `ClusterOutlined` `CodeOutlined`
`CoffeeOutlined` `ColumnHeightOutlined` `ColumnWidthOutlined` `CommentOutlined` `CompassOutlined`
`CompressOutlined` `ConsoleSqlOutlined` `ContactsOutlined` `ContainerOutlined` `ControlOutlined`
`CopyOutlined` `CopyrightOutlined` `CreditCardOutlined` `CrownOutlined` `CustomerServiceOutlined`
`DashOutlined` `DashboardOutlined` `DatabaseOutlined` `DeleteColumnOutlined` `DeleteOutlined`
`DeleteRowOutlined` `DeliveredProcedureOutlined` `DeploymentUnitOutlined` `DesktopOutlined` `DiffOutlined`
`DingdingOutlined` `DisconnectOutlined` `DislikeOutlined` `DollarCircleOutlined` `DotChartOutlined`
`DoubleLeftOutlined` `DoubleRightOutlined` `DownCircleOutlined` `DownOutlined` `DownloadOutlined`
`DragHandlerOutlined` `DragLineHandlerOutlined` `DragOutlined` `DribbbleOutlined` `EditOutlined`
`EllipsisOutlined` `EnterOutlined` `EnvironmentOutlined` `EuroCircleOutlined` `ExceptionOutlined`
`ExclamationCircleOutlined` `ExclamationOutlined` `ExpandOutlined` `ExperimentOutlined` `ExportOutlined`
`EyeInvisibleOutlined` `EyeOutlined` `FallOutlined` `FastBackwardOutlined` `FastForwardOutlined`
`FieldBinaryOutlined` `FieldNumberOutlined` `FieldStringOutlined` `FieldTimeOutlined` `FileAddOutlined`
`FileDoneOutlined` `FileExcelOutlined` `FileExclamationOutlined` `FileGifOutlined` `FileImageOutlined`
`FileJpgOutlined` `FileMarkdownOutlined` `FileOutlined` `FilePdfOutlined` `FilePptOutlined`
`FileProtectOutlined` `FileSearchOutlined` `FileSyncOutlined` `FileTextOutlined` `FileUnknownOutlined`
`FileWordOutlined` `FileZipOutlined` `FilterOutlined` `FireOutlined` `FixedLeftOutlined`
`FixedRightOutlined` `FlagOutlined` `FolderAddOutlined` `FolderOpenOutlined` `FolderOutlined`
`FolderViewOutlined` `FontColorsOutlined` `FontSizeOutlined` `ForkOutlined` `FormOutlined`
`FormatPainterOutlined` `FrownOutlined` `FullscreenExitOutlined` `FullscreenOutlined` `FunctionOutlined`
`FundOutlined` `FundProjectionScreenOutlined` `FundViewOutlined` `FunnelPlotOutlined` `GatewayOutlined`
`GifOutlined` `GiftOutlined` `GlobalOutlined` `GoldOutlined` `GroupOutlined`
`HddOutlined` `HeartOutlined` `HeatMapOutlined` `HighlightOutlined` `HistoryOutlined`
`HomeOutlined` `HourglassOutlined` `IdcardOutlined` `ImportOutlined` `InboxOutlined`
`InfoCircleOutlined` `InfoOutlined` `InsertRowAboveOutlined` `InsertRowBelowOutlined` `InsertRowLeftOutlined`
`InsertRowRightOutlined` `InsuranceOutlined` `InteractionOutlined` `IssuesCloseOutlined` `ItalicOutlined`
`KeyOutlined` `LaptopOutlined` `LayoutOutlined` `LeftCircleOutlined` `LeftOutlined`
`LikeOutlined` `LineChartOutlined` `LineHeightOutlined` `LinkOutlined` `Loading3QuartersOutlined`
`LoadingOutlined` `LockOutlined` `MacCommandOutlined` `MailOutlined` `ManOutlined`
`MaximizeOutlined` `MedicineBoxOutlined` `MehOutlined` `MenuFoldOutlined` `MenuOutlined`
`MenuUnfoldOutlined` `MergeCellsOutlined` `MessageOutlined` `MinimizeOutlined` `MinusCircleOutlined`
`MinusOutlined` `MinusSquareOutlined` `MobileOutlined` `MoneyCollectOutlined` `MonitorOutlined`
`MoonOutlined` `MoreOutlined` `NodeCollapseOutlined` `NodeExpandOutlined` `NodeIndexOutlined`
`NonFixedOutlined` `NotificationOutlined` `NumberOutlined` `OneToOneOutlined` `OrderedListOutlined`
`PaperClipOutlined` `PartitionOutlined` `PauseCircleOutlined` `PauseOutlined` `PayCircleOutlined`
`PercentageOutlined` `PhoneOutlined` `PicCenterOutlined` `PicLeftOutlined` `PicRightOutlined`
`PictureOutlined` `PieChartOutlined` `PlaySquareOutlined` `PlusCircleOutlined` `PlusOutlined`
`PlusSquareOutlined` `PoundOutlined` `PoweroffOutlined` `PrinterOutlined` `ProfileOutlined`
`ProjectOutlined` `PropertySafetyOutlined` `PullRequestOutlined` `PushpinOutlined` `QqOutlined`
`QrcodeOutlined` `QuestionCircleOutlined` `QuestionOutlined` `RadarChartOutlined` `RadiusBottomleftOutlined`
`RadiusBottomrightOutlined` `RadiusSettingOutlined` `RadiusUpleftOutlined` `RadiusUprightOutlined` `ReadOutlined`
`ReconciliationOutlined` `RedEnvelopeOutlined` `RedoOutlined` `ReloadOutlined` `RestOutlined`
`RightCircleOutlined` `RightOutlined` `RiseOutlined` `RobotOutlined` `RocketOutlined`
`RotateLeftOutlined` `RotateRightOutlined` `SafetyCertificateOutlined` `SafetyOutlined` `SaveOutlined`
`ScanOutlined` `ScheduleOutlined` `ScissorOutlined` `SearchOutlined` `SecurityScanOutlined`
`SelectOutlined` `SendOutlined` `SettingOutlined` `ShakeOutlined` `ShareAltOutlined`
`ShopOutlined` `ShoppingCartOutlined` `ShoppingOutlined` `ShrinkOutlined` `SisternodeOutlined`
`SkinOutlined` `SlidersOutlined` `SmileOutlined` `SnippetsOutlined` `SolutionOutlined`
`SortAscendingOutlined` `SortDescendingOutlined` `SortingDownOutlined` `SortingOutlined` `SortingUpOutlined`
`SoundOutlined` `SplitCellsOutlined` `StarOutlined` `StepBackwardOutlined` `StepForwardOutlined`
`StockOutlined` `StopOutlined` `StrikethroughOutlined` `SubnodeOutlined` `SunOutlined`
`SwapLeftOutlined` `SwapOutlined` `SwapRightOutlined` `SwitcherOutlined` `SyncOutlined`
`TableOutlined` `TabletOutlined` `TagOutlined` `TagsOutlined` `TeamOutlined`
`ThunderboltOutlined` `ToTopOutlined` `ToolOutlined` `TrademarkCircleOutlined` `TransactionOutlined`
`TranslationOutlined` `TrophyOutlined` `UnderlineOutlined` `UndoOutlined` `UngroupOutlined`
`UnlockOutlined` `UnorderedListOutlined` `UpCircleOutlined` `UpOutlined` `UploadOutlined`
`UsbOutlined` `UserAddOutlined` `UserDeleteOutlined` `UserOutlined` `UserSwitchOutlined`
`UsergroupAddOutlined` `UsergroupDeleteOutlined` `VerifiedOutlined` `VerticalAlignBottomOutlined` `VerticalAlignMiddleOutlined`
`VerticalAlignTopOutlined` `VerticalLeftOutlined` `VerticalRightOutlined` `VideoCameraAddOutlined` `VideoCameraOutlined`
`WalletOutlined` `WarningOutlined` `WechatOutlined` `WhatsAppOutlined` `WifiOutlined`
`WindowsOutlined` `WomanOutlined` `ZoomInOutlined` `ZoomOutOutlined`

### Filled（实心风格，60 个）

`AlipaySquareFilled` `AndroidFilled` `AppFilled` `AppleFilled` `CheckCircleFilled`
`CheckSquareFilled` `ClockCircleFilled` `CloseCircleFilled` `CloseSquareFilled` `DingtalkCircleFilled`
`DingtalkSquareFilled` `DislikeFilled` `DownCircleFilled` `DownSquareFilled` `DribbbleCircleFilled`
`DribbbleSquareFilled` `ExclamationCircleFilled` `FastBackwardFilled` `FastForwardFilled` `FileExcelFilled`
`FileFolderFilled` `FileImageFilled` `FileMp3Filled` `FileMp4Filled` `FileNnknownFilled`
`FilePdfFilled` `FilePptFilled` `FileTxtFilled` `FileWordFilled` `FileZipFilled`
`FilterFilled` `FolderFilled` `FrownFilled` `HeartFilled` `InfoCircleFilled`
`LeftCircleFilled` `LeftSquareFilled` `LikeFilled` `MehFilled` `MinusCircleFilled`
`MinusSquareFilled` `PauseCircleFilled` `PlayCircleFilled` `PlusCircleFilled` `PlusSquareFilled`
`QqCircleFilled` `QqSquareFilled` `QuestionCircleFilled` `RightCircleFilled` `RightSquareFilled`
`SendFilled` `SmileFilled` `StarFilled` `StepBackwardFilled` `StepForwardFilled`
`StopFilled` `UpCircleFilled` `UpSquareFilled` `WarningFilled` `WindowsFilled`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0700000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/navigation-layout.md', '# SUID 导航与布局组件

> 导入: `import { X } from ''@ead/suid''`
> 完整 API 参考: `components/<组件名>/index.en-US.md`

## 页面布局

### Layout
页面整体布局
- **何时选用**: 构建页面整体结构
- **关键 Props**: `hasSider`; Sider: `collapsed`, `collapsible`, `onCollapse`, `onBreakpoint`
- **子组件**: `Layout.Header`, `Layout.Sider`, `Layout.Content`, `Layout.Footer`
- **示例**:
```tsx
<Layout>
  <Layout.Sider collapsible>侧边栏</Layout.Sider>
  <Layout>
    <Layout.Header>顶部</Layout.Header>
    <Layout.Content>内容</Layout.Content>
  </Layout>
</Layout>
```

### Grid
24栅格布局（独立导出 `Row` 和 `Col`）
- **何时选用**: 需要灵活的行列表格布局
- **关键 Props(Row)**: `gutter`, `justify`, `align`, `wrap`; **关键 Props(Col)**: `span`, `offset`, `xs/sm/md/lg/xl/xxl`
- **导入**: `import { Row, Col } from ''@ead/suid''` 或 `import { Grid } from ''@ead/suid''`
- **示例**:
```tsx
<Row gutter={16}>
  <Col span={8}><Input /></Col>
  <Col span={8}><Select /></Col>
  <Col span={8}><Button>查询</Button></Col>
</Row>
```

### Flex
弹性布局容器
- **何时选用**: 需要快速实现 flex 对齐和间距
- **关键 Props**: `vertical`, `justify`, `align`, `gap`, `wrap`
- **示例**:
```tsx
<Flex gap="middle" align="center">
  <Button type="primary">保存</Button>
  <Button>取消</Button>
</Flex>
```

### Splitter
分隔面板，支持拖拽调整
- **何时选用**: 需要水平/垂直分割区域并拖拽调整大小
- **关键 Props**: `orientation`(''horizontal''|''vertical''), `onResize`, `onResizeEnd`, `lazy`
- **子组件**: `Splitter.Panel`(`defaultSize`,`min`,`max`,`collapsible`)
- **示例**:
```tsx
<Splitter>
  <Splitter.Panel defaultSize="30%" min="20%">
    <LeftPanel />
  </Splitter.Panel>
  <Splitter.Panel>
    <RightPanel />
  </Splitter.Panel>
</Splitter>
```

### Masonry
瀑布流布局
- **何时选用**: 需要展示不等高内容（图片墙等）
- **降级**: CSS columns
- **关键 Props**: `columns`(固定值或响应式), `items`, `itemRender`, `gutter`, `fresh`
- **示例**:
```tsx
<Masonry
  columns={{ xs: 1, sm: 2, md: 3 }}
  gutter={16}
  items={items}
  itemRender={(item) => <Card>{item.content}</Card>}
/>
```

### Space
间距组件
- **何时选用**: 需要统一设置行内元素间距
- **关键 Props**: `size`, `orientation`(''vertical''|''horizontal''), `align`, `wrap`, `separator`
- **子组件**: `Space.Compact`

## 导航

### Menu
菜单导航
- **何时选用**: 侧边栏/顶栏导航菜单
- **关键 Props**: `items`, `mode`(''vertical''|''horizontal''|''inline''), `selectedKeys`, `openKeys`, `theme`
- **关键 Events**: `onClick`, `onSelect`, `onOpenChange`

### Tabs
标签页
- **何时选用**: 内容分区切换
- **关键 Props**: `activeKey`, `items`, `type`(''line''|''card''|''editable-card''), `tabPlacement`, `onChange`
- **关键 Slots**: `tabBarExtraContent`(left/right)

### Breadcrumb
面包屑导航
- **何时选用**: 需要展示当前页面在导航层级中的位置
- **关键 Props**: `items`, `separator`, `itemRender`

### Anchor
锚点导航
- **何时选用**: 页面内段落快速跳转
- **关键 Props**: `items`, `affix`, `offsetTop`, `direction`(''vertical''|''horizontal'')

### Steps
步骤条
- **何时选用**: 引导用户完成分步任务
- **关键 Props**: `current`, `items`, `status`, `type`(''default''|''dot''|''inline''|''navigation''|''panel''), `orientation`

### Pagination
分页
- **何时选用**: 数据分页
- **关键 Props**: `total`, `current`, `pageSize`, `showSizeChanger`, `showQuickJumper`, `onChange`

### Dropdown
下拉菜单
- **何时选用**: 触发式操作菜单
- **关键 Props**: `menu`, `trigger`, `open`, `placement`, `onOpenChange`
- **关键 Slots**: `popupRender`

## 特殊

### FloatButton
浮动操作按钮
- **何时选用**: 全局操作按钮（回到顶部、帮助等）
- **关键 Props**: `icon`, `type`, `shape`, `tooltip`, `onClick`
- **子组件**: `FloatButton.Group`(`trigger`,`open`,`placement`), `FloatButton.BackTop`

### Tour
用户引导
- **何时选用**: 需要引导用户了解产品功能
- **关键 Props**: `open`, `current`, `steps`, `mask`, `type`, `onChange`, `onClose`

### Watermark
水印
- **何时选用**: 需要在页面上添加水印
- **关键 Props**: `content`, `image`, `gap`, `rotate`, `font`

### Divider
分隔线
- **何时选用**: 区分不同内容区块
- **关键 Props**: `orientation`(''horizontal''|''vertical''), `titlePlacement`, `variant`(''dashed''|''dotted''|''solid'')`

### Affix
固钉
- **何时选用**: 需要元素在滚动时固定
- **关键 Props**: `offsetTop`, `offsetBottom`, `target`, `onChange`
', CURRENT_TIMESTAMP),
('SKILF_SUID_0800000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/suid-cli.md', '# Sui Design CLI

当任务涉及 Sui
Design 组件应用程序接口、示例、文档、版本迁移、项目分析或问题排查，且本地
`@ead/suid-cli`
可离线解答相关问题时，请参考以下内容。

## 规则

- 优先检查安装状态：
  `which suid || npm install -g @ead/suid-cli`
- 若任意命令弹出更新提示，需先执行下方命令再继续操作：
  `npm install -g @ead/suid-cli`
- 执行命令时统一使用
  `--format json`参数。
- 必要时通过 `--version <x.y.z>`
  参数匹配项目对应的版本号。
- 编写 Sui
  Design 代码前先查询相关信息，切勿凭记忆猜测组件属性。
- 修改 Sui
  Design 相关代码后，在改动文件路径下执行
  `suid lint` 代码检查。

## 核心使用流程

### 编写组件代码

1. `suid info Button --format json`
2. `suid demo Button basic --format json`
3. 可选：查看样式相关钩子
   - `suid semantic Button --format json`
   - `suid token Button --format json`

### 查看完整文档

- `suid doc Table --format json`
- `suid doc Table --lang zh --format json`

### 问题排查

1. `suid doctor --format json`
2. `suid info Select --version 5.12.0 --format json`
3. `suid lint ./src/components/MyForm.tsx --format json`

### 版本迁移

1. `suid migrate 4 5 --format json`
2. `suid migrate 4 5 --component Select --format json`
3. `suid changelog 4.24.0 5.0.0 --format json`
4. `suid changelog 4.24.0 5.0.0 Select --format json`

### 项目分析

- `suid usage ./src --format json`
- `suid usage ./src --filter Form --format json`
- `suid lint ./src --format json`
- `suid lint ./src --only deprecated --format json`
- `suid lint ./src --only a11y --format json`
- `suid lint ./src --only performance --format json`

### 变更日志与版本查询

- `suid changelog 5.22.0 --format json`
- `suid changelog 5.21.0..5.24.0 --format json`

### 组件检索

- `suid list --format json`
- `suid list --version 5.0.0 --format json`

## 问题反馈

### Ant Design 组件问题

先预览反馈内容，征得用户同意后再提交反馈。

```bash
suid bug --title "选择日期时日期选择器出现崩溃" \
  --reproduction "https://codesandbox.io/s/xxx" \
  --steps "1. 打开日期选择器 2. 点击选择日期" \
  --expected "正常选中日期" \
  --actual "组件报错崩溃" \
  --format json
```

确认无误后执行提交命令：

```bash
suid bug --title "选择日期时日期选择器出现崩溃" \
  --reproduction "https://codesandbox.io/s/xxx" \
  --steps "1. 打开日期选择器 2. 点击选择日期" \
  --expected "正常选中日期" \
  --actual "组件报错崩溃" \
  --submit
```

### 命令行工具问题

当 `suid`
命令出现运行崩溃、返回数据错误、参数失效、与其他命令表现不一致等情况时，均需提交问题报告。

```bash
suid bug-cli --title "v5.12.0 版本下查询按钮组件属性返回结果错误" \
  --description "查询 5.12.0 版本按钮组件属性时，结果中包含该版本不存在的属性" \
  --steps "1. 执行命令：antd info Button --version 5.12.0 --format json" \
  --expected "返回与 Ant Design 5.12.0 版本按钮组件接口匹配的属性" \
  --actual "返回结果中包含 5.16.0 版本才新增的 classNames 属性" \
  --format json
```

征得用户确认后再执行提交操作：

```bash
suid bug-cli --title "v5.12.0 版本下查询按钮组件属性返回结果错误" \
  --description "查询 5.12.0 版本按钮组件属性时，结果中包含该版本不存在的属性" \
  --steps "1. 执行命令：antd info Button --version 5.12.0 --format json" \
  --expected "返回与 Ant Design 5.12.0 版本按钮组件接口匹配的属性" \
  --actual "返回结果中包含 5.16.0 版本才新增的 classNames 属性" \
  --submit
```

## MCP 运行模式

若当前环境支持 MCP，可按以下配置运行命令行工具：

```json
{
  "mcpServers": {
    "suid": {
      "command": "suid",
      "args": [
        "mcp",
        "--version",
        "2.1.0"
      ]
    }
  }
}
```

该配置可在不联网的情况下，通过 MCP 调用结构化的 Ant
Design 相关查询工具。
', CURRENT_TIMESTAMP),
('SKILF_SUID_0900000000000000000000000', 'SKILBLTNSUID000000000000000000000000', 'references/utils-hooks.md', '# SUID 工具函数与 Hooks 完整参考

> 导入路径：`import { X } from ''@ead/suid-utils-react''`（包含所有 Hooks + 重导出
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
| `type`        | 请求方法              | `''GET'' \| ''POST''`     | `''GET''` |
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
| `type`         | 存储类型         | `''localStorage'' \| ''sessionStorage''` | `''sessionStorage''` |
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
  // useLockScroll(rootRef, ''strict''); // 同时锁定 body
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
| `formaterNumber(value, precision, thousand?, suffix?)` | 格式化数字，nil 值返回 `''-''` | `formaterNumber(1234.5, 2, true)` → `''1,234.50''`                     |
| `toChineseAmount(amount)`                              | 转中文大写金额               | `toChineseAmount(12345)` → `''壹万贰仟叁佰肆拾伍元整''`                |
| `toFileSize(size)`                                     | 字节转可读文件大小           | `toFileSize(1024)` → `''1.00KB''`                                      |
| `toThousandsAmount(amount, options?)`                  | 千分位格式化                 | `toThousandsAmount(1234567, { currency: true })` → `''¥1,234,567.00''` |

```tsx
formaterNumber(1234.5, 2, true, "元"); // ''1,234.50元''
formaterNumber(null, 2); // ''-''
toChineseAmount(-123.45); // ''负壹佰贰拾叁元肆角伍分''
toFileSize(1073741824); // ''1.00GB''
toThousandsAmount(12345.67, {
  currency: "USD",
  locales: "en-US",
}); // ''$12,345.67''
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
| `childrenKey`   | 子节点字段名        | `''children''` |
| `key`           | 节点值字段名        | `''value''`    |
| `strategy`      | 遍历策略            | `''pre''`      |
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
// keys: [''child-1'', ''child-2'', ''grandchild-1'']
// items: [{ value: ''child-1'', ... }, ...]

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
| `option.matchMode`   | `''once''` 任一匹配 / `''all''` 全部匹配         |

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
// { passed: false, error: ''密码长度不能少于8个字符'', analysisResult: {...} }
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
| `md5(data)`                           | MD5 哈希                       | `md5(''hello'')`                                             |
| `getUUID()`                           | 生成 UUID v4                   | `getUUID() // ''550e8400-...''`                              |
| `qs.stringify(obj)` / `qs.parse(str)` | 查询字符串序列化/解析          | `qs.stringify({ a: 1, b: 2 })` → `''a=1&b=2''`               |
| `tplMessage(msg, values)`             | 模板字符串插值                 | `tplMessage(''{name}是{age}岁'', { name: ''张三'', age: 25 })` |
| `formartUrl(base, path)`              | URL 拼接（处理斜杠）           | `formartUrl(''https://a.com'', ''/b'')` → `''https://a.com/b''`  |
| `blobToFile(res, fileName?)`          | Axios 响应转文件下载           | `blobToFile(response)`                                     |
| `pathMatchRegexp(pattern, pathname)`  | 路径匹配（path-to-regexp）     | `pathMatchRegexp(''/user/:id'', ''/user/123'')`                |
| `isDeepEqual(a, b, ignoreKeys?)`      | 深比较                         | `isDeepEqual(obj1, obj2, [''timestamp''])`                   |
| `isMobile()`                          | 移动设备检测                   | `isMobile() // boolean`                                    |
| `CONST_GLOBAL.TOKEN_KEY`              | Token 存储 key 常量            | —                                                          |
| `CONST_GLOBAL.CURRENT_USER`           | 当前用户存储 key               | —                                                          |
| `CONST_GLOBAL.CURRENT_LOCALE`         | 当前语言存储 key               | —                                                          |
| `AUTH_POLICY.ADMIN`                   | 管理员策略常量                 | —                                                          |
| `API_GATEWAY`                         | API 网关路径常量               | —                                                          |
| `API_CONTEXTS_V6` / `API_CONTEXTS_V7` | V6/V7 服务上下文               | —                                                          |
', CURRENT_TIMESTAMP),
('SKILF_EADP_0100000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/api.md', '# API Interface

Patterns and conventions for API interfaces in EADP/SEI backend development.

## Positioning

API interfaces are Feign client contracts with Spring MVC annotations + Swagger `@Operation`. They define the transport contract only — no business logic, persistence, or transaction concerns.

## Base API Interfaces

### BaseEntityApi<T extends BaseEntityDto> — Minimal CRUD

```java
ResultData<T> save(@RequestBody @Valid T dto)       // POST /save
ResultData<?> delete(@PathVariable String id)        // DELETE /delete/{id}
ResultData<T> findOne(@RequestParam String id)       // GET /findOne
```

### FindAllApi<T> — List All

```java
ResultData<List<T>> findAll()                        // GET /findAll
ResultData<List<T>> findAllUnfrozen()                // GET /findAllUnfrozen
```

### FindByPageApi<T> — Paginated Query

```java
ResultData<PageResult<T>> findByPage(@RequestBody Search search)  // POST /findByPage
```

### BaseTreeApi<T> — Tree Operations (extends BaseEntityApi)

```java
ResultData<?> move(@RequestBody TreeNodeMoveParam)   // POST /move
ResultData<List<T>> getAllRootNode()                  // GET /getAllRootNode
ResultData<T> getTree(@RequestParam String nodeId)    // GET /getTree
ResultData<List<T>> getChildrenNodes(...)             // GET /getChildrenNodes
ResultData<List<T>> getParentNodes(...)               // GET /getParentNodes
ResultData<List<T>> queryTree(@RequestParam String)   // GET /queryTree
```

## Composing Custom API Interfaces

Compose base interfaces as needed:

```java
@Valid
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}", path = FooApi.PATH)
public interface FooApi extends BaseEntityApi<FooDto>, FindAllApi<FooDto>, FindByPageApi<FooDto> {
    String PATH = "foo";

    @PostMapping(path = "query", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "分页查询业务数据")
    ResultData<PageResult<FooDto>> query(@RequestBody FooQuickQueryParam queryParam);

    @GetMapping(path = "findByCode")
    @Operation(summary = "通过代码获取业务数据")
    ResultData<FooDto> findByCode(@RequestParam("code") String code);

    @PostMapping(path = "findByIds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按ID集合查询业务数据")
    ResultData<List<FooDto>> findByIds(@RequestBody List<String> ids);
}
```

### Common Compositions

**CRUD + paging module**:
```java
public interface EmployeeApi extends BaseEntityApi<EmployeeDto>,
        FindByPageApi<EmployeeDto>,
        ExportTableDataApi {
}
```

**Tree module**:
```java
public interface MenuApi extends BaseTreeApi<MenuDto> {
}
```

**Rich aggregate module**:
```java
public interface OrganizationApi extends BaseTreeApi<OrganizationDto>,
        FindByPageApi<OrganizationDto>,
        DataAuthTreeEntityApi<OrganizationDto>,
        ExportTableDataApi {
}
```

**Non-CRUD protocol module** (does NOT extend BaseEntityApi):
```java
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}")
public interface AuthenticationApi {
    // login, logout, token, SSO, OAuth endpoints explicitly
}
```

## Core Conventions

### 1. Use @FeignClient as the API entry marker

```java
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}", path = ProjectApi.PATH)
public interface ProjectApi extends BaseEntityApi<ProjectDto> {
    String PATH = "project";
}
```

- Prefer a `PATH` constant for the module root path.
- Use `path = XxxApi.PATH` for consistency and reuse.

### 2. Put HTTP contract annotations on every custom method

- `@GetMapping` for simple reads with query params.
- `@PostMapping(..., consumes = MediaType.APPLICATION_JSON_VALUE)` for complex queries and request bodies.
- `@DeleteMapping` when deleting by path variable.

Examples:

```java
@GetMapping(path = "findByCode")
ResultData<EmployeeDto> findByCode(@RequestParam("code") String code);

@PostMapping(path = "queryEmployees", consumes = MediaType.APPLICATION_JSON_VALUE)
ResultData<PageResult<EmployeeDto>> queryEmployees(@RequestBody EmployeeQuickQueryParam queryParam);

@DeleteMapping(path = "deleteAttrDefine/{id}")
ResultData<Void> deleteAttrDefine(@PathVariable("id") String id);
```

### 3. Standardize return types

Business APIs usually return:

- `ResultData<Dto>`
- `ResultData<List<Dto>>`
- `ResultData<PageResult<Dto>>`
- `ResultData<Void>`

Do not default to raw DTOs, raw lists, or `ResponseEntity` unless the real endpoint behavior requires it.

### 4. Add @Operation metadata

```java
@Operation(summary = "通过代码获取业务数据", description = "通过代码获取业务数据")
```

- Keep `summary` short and action-oriented.
- Keep `description` as a fuller restatement when useful.

### 5. Validation on API contract

```java
@Valid
public interface ProjectApi extends BaseEntityApi<ProjectDto> {
}

ResultData<SessionUserResponse> login(@RequestBody @Valid LoginRequest loginRequest);
```

## BPM API

When the module enters workflow, the API extends `BpmDefaultBaseApi`:

```java
@Valid
@FeignClient(name = "${sei.feign.client.sei-basic:sei-basic}", path = ContractApi.PATH)
public interface ContractApi extends BaseEntityApi<ContractDto>,
        FindByPageApi<ContractDto>,
        BpmDefaultBaseApi<ContractDto> {
    String PATH = "contract";
}
```

## Naming Conventions

- Interface names use `XxxApi`.
- DTO names use `XxxDto`.
- Query objects use `XxxQueryParam` or `XxxQuickQueryParam`.
- Root path usually matches module name.
- Method names are verb-first and business-specific: `findByCode`, `findByIds`, `queryEmployees`.

Avoid vague names like `getData`, `handle`, or `process`.

## What the API Should NOT Include

- Transaction logic
- DAO access
- BeanMapper/ModelMapper conversion
- EDM binding logic
- BPM state update logic

Those belong in the Service or Controller layers.

## Best Practices

1. Treat API interfaces as Feign contracts, not service implementation abstractions.
2. Import base interfaces from `com.changhong.sei.core.api`.
3. Reuse existing base APIs before adding custom CRUD/page signatures.
4. Use `@FeignClient` and a stable module `path`.
5. Add REST mapping annotations on every custom method.
6. Use `MediaType.APPLICATION_JSON_VALUE` for POST methods with JSON request bodies.
7. Keep return values wrapped in `ResultData`.
8. Add `@Operation` metadata for public API methods.
9. Put validation annotations on request bodies and parameters.
10. Keep the interface free of business logic, persistence logic, and transaction concerns.
', CURRENT_TIMESTAMP),
('SKILF_EADP_0200000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/context-util.md', '# ContextUtil 上下文工具类

`ContextUtil` 是 SEI 框架的核心工具类，提供统一的会话上下文访问能力。基于 `ThreadLocal` 机制，在同一线程内任意位置均可调用，无需层层传参。

## 核心机制

### ThreadLocal 生命周期

```
请求进入 → ThreadLocalTranVarFilter (传播Header→TranVar)
         → SessionUserFilter (JWT解析→SessionUser→SessionContext→ThreadLocal)
         → 业务代码内任意调用 ContextUtil.xxx()
         → 请求结束 → MDC清理
```

**关键规则**：
- Web 请求：框架自动初始化，无需手动管理
- 测试代码：必须手动调用 `ThreadLocalHolder.begin()` / `ThreadLocalHolder.end()`
- 异步任务：必须使用 `ContextTaskDecorator` 传递上下文
- 响应式编程：必须使用 `ReactiveContextUtil` 包装

### 两层存储

| 存储层 | API | 用途 | 传播性 |
|--------|-----|------|--------|
| LocalVar | `ThreadLocalUtil.setLocalVar/getLocalVar` | SessionContext（会话数据） | 仅当前线程 |
| TranVar | `ThreadLocalUtil.setTranVar/getTranVar` | Token、projectId 等需跨服务传播的数据 | 通过 Feign Header 自动传播 |

## API 分类参考

### 1. 获取当前会话用户

```java
// 用户ID（平台唯一）
String userId = ContextUtil.getUserId();

// 用户账号
String account = ContextUtil.getUserAccount();

// 用户名
String userName = ContextUtil.getUserName();

// 所属组织ID（仅 UserType.Employee 有效）
String orgId = ContextUtil.getUserOrgId();

// 租户代码
String tenantCode = ContextUtil.getTenantCode();

// 是否匿名用户（无 sessionId 或 userId=anonymous）
boolean isAnonymous = ContextUtil.isAnonymous();

// 获取完整 SessionUser 对象
SessionUser sessionUser = ContextUtil.getSessionUser();
```

**SessionUser 关键字段**：

| 字段 | 说明 | 匿名用户默认值 |
|------|------|---------------|
| sessionId | 会话ID | null |
| token | JWT Token | null |
| userId | 用户ID | "anonymous" |
| account | 主账号 | "anonymous" |
| loginAccount | 当前登录账号 | "anonymous" |
| userName | 用户名 | "anonymous" |
| tenantCode | 租户代码 | null |
| orgId | 组织ID | null |
| userType | 用户类型 | Employee |
| authorityPolicy | 权限策略 | NormalUser |
| ip | 客户端IP | "Unknown" |

### 2. 获取会话上下文

```java
// 获取完整会话上下文（含 traceId、projectId、sessionUser、workbench）
SessionContext context = ContextUtil.getSessionContext();

// 直接获取上下文中的字段
String traceId = context.getTraceId();
String projectId = context.getProjectId();
String sid = context.getSid();
String token = context.getToken();
String tenantCode = context.getTenantCode();
Map<String, Object> otherInfo = context.getOtherInfo();
```

**SessionContext 构造**：
```java
new SessionContext(traceId)                                    // 仅traceId
new SessionContext(traceId, projectId, sessionUser)            // 标准
new SessionContext(traceId, projectId, sessionUser, workbench) // 含工作台
```

### 3. 国际化与语言环境

```java
// 获取当前语言环境
Locale locale = ContextUtil.getLocale();

// 获取当前语言字符串（如 "zh_CN"、"en_US"）
String lang = ContextUtil.getLocaleLang();

// 获取默认语言（始终返回 Locale.CHINA）
String defaultLang = ContextUtil.getDefaultLanguage(); // "zh_CN"
Locale defaultLocale = ContextUtil.getDefaultLocale(); // Locale.CHINA

// 设置当前语言环境
ContextUtil.setLocale(Locale.US);

// 解析 Accept-Language 请求头值
Locale parsed = ContextUtil.parseLanguage("zh-CN,zh;q=0.9,en;q=0.8"); // → Locale.CHINA
```

### 4. 国际化消息

**获取单个消息**：
```java
// 无参数
String msg = ContextUtil.getMessage("core_service_00001");

// 带参数（{0}, {1}...占位）
String msg = ContextUtil.getMessage("core_service_00003", "paramA", "paramB");
```

**指定语言环境**：
```java
String msg = ContextUtil.getMessage("key", new Object[]{"arg1"}, Locale.US);
String msg = ContextUtil.getMessage("key", new Object[]{"arg1"}, "默认值", Locale.US);
```

**规范**：所有面向用户的消息必须通过 `getMessage()` 国际化，禁止硬编码中英文。

### 5. 配置属性

```java
// 获取配置值
String appName = ContextUtil.getProperty("spring.application.name");
int port = ContextUtil.getProperty("server.port", Integer.class, 8080);

// 带默认值
String env = ContextUtil.getProperty("sei.application.env", "dev");

// 必须存在的配置（不存在抛异常）
String required = ContextUtil.getRequiredProperty("some.required.key");

// 占位符解析
String resolved = ContextUtil.resolvePlaceholders("${spring.application.name}");

// 检查是否存在
boolean exists = ContextUtil.containsProperty("some.key");
```

### 6. 项目ID与工作台

```java
// 获取当前项目ID（优先级：SessionContext > workbench.project.id > TranVar > 默认）
String projectId = ContextUtil.getProjectId();

// 获取当前个人工作台
CurrentMyWorkbench workbench = ContextUtil.getMyWorkbench();
```

**projectId 获取优先级**：
1. SessionContext 中直接存储的 projectId（如果不是默认值）
2. 当前工作台中的 project.id
3. 线程全局变量中的 `HEADER_PROJECT_KEY`
4. 返回默认值 `Constants.DEFAULT_PARENT_ENTITY_ID`

### 7. Token 与 JWT

```java
// 获取当前用户 Token
String token = ContextUtil.getToken();

// 生成 Token（默认不过期）
String token = ContextUtil.generateToken(sessionUser);

// 生成 Token（指定过期时间，秒）
String token = ContextUtil.generateToken(sessionUser, 3600);

// 解析 Token 获取 SessionUser
SessionUser user = ContextUtil.getSessionUser(token);
```

### 8. Spring Bean 获取

```java
// 按类型获取
CacheBuilder cacheBuilder = ContextUtil.getBean(CacheBuilder.class);

// 按名称获取
Object bean = ContextUtil.getBean("someBeanName");
```

### 9. 应用与环境信息

```java
// 应用代码（默认从 spring.application.name 取，无则返回 "sei"）
String appCode = ContextUtil.getAppCode();

// 运行环境（从 sei.application.env 取）
String env = ContextUtil.getEnv();

// 获取 TraceId（用于链路追踪，不存在则自动生成）
String traceId = ContextUtil.getTraceId();
```

### 10. 版本信息

```java
// 获取当前应用版本
Version currentVersion = ContextUtil.getCurrentVersion();
currentVersion.getName();                  // 应用代码
currentVersion.getCurrentVersion();         // 版本号
currentVersion.getCompleteVersionString();  // 完整版本字符串
currentVersion.getBuildTime();             // 构建时间

// 获取 SEI 平台版本
Version platformVersion = ContextUtil.getPlatformVersion();

// 获取所有依赖的版本信息
Set<Version> deps = ContextUtil.getDependVersions();
```

### 11. Header 常量

```java
ContextUtil.HEADER_TOKEN_KEY       // "Authorization"
ContextUtil.HEADER_PROJECT_KEY     // "x-project"
ContextUtil.HEADER_WORKBENCH_KEY   // "x-workbench"
ContextUtil.TRACE_ID               // "traceId"
ContextUtil.TRACE_PATH             // "tracePath"
```

## 异步任务中的上下文传递

### ContextTaskDecorator（推荐）

配置一个 `TaskExecutor` 使用 `ContextTaskDecorator`：

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new ContextTaskDecorator());
    return executor;
}
```

`ContextTaskDecorator` 自动将父线程的 TranVars、MDC、SessionContext 传递到子线程，执行完毕后自动清理。

### 手动传递

如果无法使用 TaskDecorator，可以手动传递：

```java
// 父线程
Map<String, Object> transMap = ThreadLocalHolder.getTranVars();
SessionContext context = ContextUtil.getSessionContext();
Map<String, String> mdcMap = MDC.getCopyOfContextMap();

// 子线程
try {
    ThreadLocalHolder.begin(transMap);
    ThreadLocalUtil.setLocalVar(SessionContext.class.getSimpleName(), context);
    if (mdcMap != null) MDC.setContextMap(mdcMap);
    // 业务逻辑...
} finally {
    ThreadLocalHolder.end();
    MDC.clear();
}
```

## 响应式编程中的上下文传递

在 WebFlux 响应式流中使用 `ReactiveContextUtil`（位于 `sei-cloud` 模块）：

```java
// 方式一：包装 Mono/Flux，内部直接使用 ContextUtil
public Mono<Result> doSomething() {
    return ReactiveContextUtil.mono(Mono.fromCallable(() -> {
        SessionUser user = ContextUtil.getSessionUser();  // 正常工作
        String tenantCode = ContextUtil.getTenantCode();   // 正常工作
        return doBusiness(user);
    }));
}

// 方式二：包装阻塞调用，自动在 boundedElastic 调度器上执行
public Mono<Result> doBlocking() {
    return ReactiveContextUtil.mono(() -> {
        SessionUser user = ContextUtil.getSessionUser();
        return doBlockingBusiness(user);
    });
}

// 方式三：包装 Runnable
public Mono<Void> doAsync() {
    return ReactiveContextUtil.mono(() -> {
        log.info("用户: {}", ContextUtil.getUserName());
    });
}
```

**底层原理**：
1. `ReactiveContextFilter` 将 ThreadLocal 上下文写入 Reactor Context
2. `ReactiveContextUtil.mono/flux` 方法从 Reactor Context 恢复 ThreadLocal
3. 流结束后自动清理

## 测试中的 ContextUtil

### 使用 BaseUnit5Test（推荐）

```java
public class FooTest extends BaseUnit5Test {

    @Test
    public void testWithContext() {
        // BaseUnit5Test 已在 @BeforeEach 中初始化了 SessionUser
        String userId = ContextUtil.getUserId();
        String tenantCode = ContextUtil.getTenantCode();
    }
}
```

**BaseUnit5Test 生命周期**：
```
@BeforeAll   → ThreadLocalHolder.begin()
@BeforeEach  → mockUser.mockUser(properties) → 设置 SessionContext 到 ThreadLocal
  ... @Test 方法执行 → ContextUtil 可用 ...
@AfterEach   → 计时统计
@AfterAll    → ThreadLocalHolder.end()
```

### 手动初始化（不使用基类时）

```java
@Test
public void testSomething() {
    ThreadLocalHolder.begin();
    try {
        SessionUser sessionUser = new SessionUser();
        sessionUser.setUserId("test-user-001");
        sessionUser.setAccount("testuser");
        sessionUser.setUserName("测试用户");
        sessionUser.setTenantCode("10044");
        ContextUtil.generateToken(sessionUser);
        SessionContext context = new SessionContext("trace-001", "project-001", sessionUser);
        ThreadLocalUtil.setLocalVar(SessionContext.class.getSimpleName(), context);
        ThreadLocalUtil.setTranVar(ContextUtil.HEADER_TOKEN_KEY, sessionUser.getToken());

        String tenantCode = ContextUtil.getTenantCode(); // "10044"
    } finally {
        ThreadLocalHolder.end();
        MDC.clear();
    }
}
```

### 使用 MockUser 接口

```java
@Autowired
private MockUser mockUser;

// 按租户+账号模拟
SessionUser user = mockUser.mockUser("10044", "admin");

// 按配置模拟
SessionUser user = mockUser.mockUser(mockUserProperties);

// 带项目ID模拟
SessionUser user = mockUser.mockUser("10044", "admin", "project-001");

// 直接设置已有的 SessionUser
SessionUser user = mockUser.mock(sessionUser);
```

## 常见问题

**Q: ContextUtil.getSessionUser() 返回匿名用户？**
A: 检查请求是否经过 `SessionUserFilter`（需要 JWT Token），或者 `MockUser.mockCurrentUser()` 是否调用。

**Q: 异步线程中 ContextUtil 为 null？**
A: 确保使用了 `ContextTaskDecorator`，或者手动传递上下文。

**Q: ContextUtil.getTenantCode() 返回 null？**
A: `SessionUser.tenantCode` 未设置。对于不需要租户的场景，接口实现 `ITenant` 的实体的查询会被自动过滤为 null。

**Q: 日志中 MDC 字段丢失？**
A: `SessionUserFilter` 在 finally 块中清理了 MDC。如需在 finally 后记录日志，需重新设置 MDC。
', CURRENT_TIMESTAMP),
('SKILF_EADP_0300000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/controller.md', '# Controller

Patterns and conventions for controller implementations in EADP/SEI backend development.

## Positioning

Controllers implement the API interface directly and delegate business logic to services. They handle DTO conversion, result adaptation, and lightweight request-context decisions.

Key points:
- Implement the `XxxApi` interface directly — HTTP annotations come from the interface.
- Extend `BaseEntityController<Entity, Dto>` or `BaseTreeController<Entity, Dto>` for standard modules.
- DTO conversion uses `convertToDto`/`convertToEntity` from the base controller (ModelMapper-based).
- Services do NOT implement the API; controllers bridge API ↔ Service.

## Base Controllers

| Base Class | Use When |
|---|---|
| `BaseEntityController<E, D>` | Standard entity CRUD module |
| `BaseTreeController<E, D>` | Tree-structured entity module |

### BaseController<E, D> provides:

- `convertToDto(entity)` / `convertToEntity(dto)` — ModelMapper-based conversion
- `convertToDtos(entities)` / `convertToEntities(dtos)` — batch conversion
- `convertToDtoPageResult(pageResult)` — pagination result conversion
- `checkDto(dto)` — null check
- `customConvertToEntityMapper()` / `customConvertToDtoMapper()` — override for custom mapping

## Core Conventions

### 1. Implement the API interface directly

```java
@RestController
@Tag(name = "ProjectApi", description = "项目管理服务")
@RequestMapping(path = ProjectApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProjectController extends BaseEntityController<Project, ProjectDto>
        implements ProjectApi {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<Project> getService() {
        return service;
    }
}
```

Because the controller implements the API interface, the HTTP annotations declared on the API methods are reused by Spring MVC.

### 2. Implement getService()

This is required when extending `BaseEntityController` or `BaseTreeController`:

```java
@Override
public BaseEntityService<Project> getService() {
    return service;
}

@Override
public BaseTreeService<Organization> getService() {
    return service;
}
```

### 3. Match class-level mapping to API root path

```java
@RequestMapping(path = ProjectApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
```

- Reuse `XxxApi.PATH` when the API interface defines it.
- Set `produces = MediaType.APPLICATION_JSON_VALUE`.
- Add `@Tag` for OpenAPI grouping.

### 4. Inject services, not API interfaces

Controllers inject domain services. They do NOT inject the matching `XxxApi` interface.

## DTO Conversion

### Use base controller conversion helpers

```java
// Single conversion
return ResultData.success(convertToDto(service.findByCode(code)));

// List conversion
return ResultData.success(convertToDtos(service.findByIds(ids)));

// Page result conversion
return convertToDtoPageResult(service.findByPage(search));
```

### Customize mapping when necessary

```java
@Override
protected void customConvertToDtoMapper() {
    PropertyMap<Employee, EmployeeDto> propertyMap = new PropertyMap<>() {
        @Override
        protected void configure() {
            map().setOrganizationId(source.getOrganizationId());
            map().setUserAccount(source.getUser().getAccount());
        }
    };
    dtoModelMapper.addMappings(propertyMap);
}
```

### Override conversion methods when needed

Some controllers override `convertToDto` directly for richer custom assembly. This is appropriate when:
- Nested associations need special flattening
- Null handling is non-trivial
- The default mapper is not enough

## Result Adaptation

### Return ResultData consistently

Most controller methods return:
- `ResultData<Dto>`
- `ResultData<List<Dto>>`
- `ResultData<PageResult<Dto>>`
- `ResultData<Void>`

### Adapt service-layer result objects

Services return `OperateResult`/`OperateResultWithData`. Controllers adapt:

```java
return ResultDataUtil.convertFromOperateResult(service.copyToEmployees(copyParam));

return ResultDataUtil.convertFromResponseData(responseData, convertToDtos(responseData.getData()));
```

Do not force every service to already return the final DTO-shaped `ResultData` — that is the controller''s job.

## Controller Templates

### Standard entity controller

```java
@RestController
@Tag(name = "YourApi", description = "业务服务")
@RequestMapping(path = YourApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class YourController extends BaseEntityController<YourEntity, YourDto>
        implements YourApi {

    private final YourService service;

    public YourController(YourService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<YourEntity> getService() {
        return service;
    }

    @Override
    public ResultData<YourDto> findByCode(String code) {
        return ResultData.success(convertToDto(service.findByCode(code)));
    }
}
```

### Tree controller

```java
@RestController
@Tag(name = "OrganizationApi", description = "组织机构服务")
@RequestMapping(path = OrganizationApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class OrganizationController extends BaseTreeController<Organization, OrganizationDto>
        implements OrganizationApi {

    @Override
    public BaseTreeService<Organization> getService() {
        return service;
    }
}
```

### Non-entity protocol controller

```java
@RestController
@Tag(name = "AuthenticationApi", description = "账户认证服务")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthenticationController implements AuthenticationApi {
}
```

## Audit and Cross-Cutting Concerns

### Audit integration

```java
public class ProjectController extends BaseEntityController<Project, ProjectDto>
        implements ProjectApi, AuditDtoApi<ProjectDto> {
}

@EnableAudit(id = "#dto.id", description = "保存")
public ResultData<ProjectDto> save(ProjectDto dto) {
    return super.save(dto);
}
```

### Access log integration

```java
@AccessLog(AccessLog.FilterReply.DENY)
public ResultData<SessionUserResponse> login(LoginRequest loginRequest) {
    ...
}
```

## Export-Related Patterns

Entity controllers with export capability commonly override base hooks:

- `constructExportDataFields`
- `sortExportTableData`
- `exportTableData(List<DataField> dataFields)`
- `convertField`

## Controller Responsibility Boundaries

Controllers may:
- Convert entities to DTOs
- Call multiple services for composition
- Adapt `OperateResult` or `ResponseData`
- Apply request-context checks (current tenant, current user)
- Enforce lightweight permission gates
- Enrich export field definitions

Controllers should NOT:
- Access DAO/repository classes directly
- Manage transactions
- Implement low-level persistence logic
- Contain broad domain workflows that belong in services

## Best Practices

1. Implement the API interface directly instead of duplicating endpoint contracts.
2. Reuse API interface mappings rather than rewriting method annotations in controllers.
3. Extend `BaseEntityController` or `BaseTreeController` when the entity type fits.
4. Set class-level `@RequestMapping(..., produces = MediaType.APPLICATION_JSON_VALUE)`.
5. Use `@Tag` for OpenAPI grouping on public controllers.
6. Inject domain services, not the matching API interface.
7. Use base controller conversion helpers (`convertToDto`, `convertToDtoPageResult`) for DTO transformation.
8. Adapt framework result objects with `ResultDataUtil` when necessary.
9. Keep DAO access and transaction management out of controllers.
10. Override `customConvertToDtoMapper()` for custom field mapping, don''t use `BeanMapper` directly.
', CURRENT_TIMESTAMP),
('SKILF_EADP_0400000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/dao-impl.md', '# DAO Implementation

Patterns and conventions for DAO extension implementations in EADP/SEI backend development.

## Positioning

`DaoImpl` classes implement `XxxExtDao`, NOT the main `XxxDao` interface. Most DAOs do NOT need a `DaoImpl` — only write one when custom JPQL/EntityManager logic is truly needed.

Key points:
- `XxxDao` is the primary Spring Data JPA repository interface — no manual implementation needed.
- `XxxExtDao` declares custom extension methods.
- `impl/XxxDaoImpl` implements only the extension interface.
- `DaoImpl` extends `BaseEntityDaoImpl<Entity>` and uses `EntityManager`, `QuerySql`, `PageResultUtil`.
- This codebase is JPA-only — do NOT reference MyBatis patterns.

## Core Conventions

### 1. Implement XxxExtDao, not XxxDao

```java
public class EmployeeDaoImpl extends BaseEntityDaoImpl<Employee> implements EmployeeExtDao {
}

public class PositionDaoImpl extends BaseEntityDaoImpl<Position> implements PositionExtDao {
}
```

The main `XxxDao` remains a Spring Data repository interface and does not need a manual implementation class.

### 2. Extend BaseEntityDaoImpl<Entity>

This provides:
- Access to the shared `entityManager`
- Framework helper methods such as `findFirstByFilters`
- Entity metadata integration

### 3. Use constructor injection with EntityManager

```java
public EmployeeDaoImpl(EntityManager entityManager) {
    super(Employee.class, entityManager);
}
```

## Recommended DaoImpl Template

```java
package com.changhong.sei.xxx.dao.impl;

import com.changhong.sei.core.dao.impl.BaseEntityDaoImpl;
import com.changhong.sei.core.dao.impl.PageResultUtil;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.entity.search.QuerySql;
import com.changhong.sei.xxx.dao.YourExtDao;
import com.changhong.sei.xxx.dto.search.YourQuickQueryParam;
import com.changhong.sei.xxx.entity.YourEntity;
import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.Map;

public class YourDaoImpl extends BaseEntityDaoImpl<YourEntity> implements YourExtDao {

    public YourDaoImpl(EntityManager entityManager) {
        super(YourEntity.class, entityManager);
    }

    @Override
    public PageResult<YourEntity> query(YourQuickQueryParam queryParam) {
        String select = "select e ";
        String fromAndWhere = "from YourEntity e where e.tenantCode = :tenantCode ";
        Map<String, Object> params = new HashMap<>();
        params.put("tenantCode", queryParam.getTenantCode());

        QuerySql querySql = new QuerySql(select, fromAndWhere);
        querySql.setOrderBy("order by e.code");
        return PageResultUtil.getResult(entityManager, querySql, params, queryParam);
    }
}
```

## Common Implementation Patterns

### Dynamic page query with QuerySql

Most common extension pattern:

```java
String select = "select e ";
String fromAndWhere = "from Employee e where e.tenantCode = :tenantCode ";
Map<String, Object> sqlParams = new HashMap<>();
sqlParams.put("tenantCode", ContextUtil.getTenantCode());

if (StringUtils.isNotBlank(quickSearchValue)) {
    fromAndWhere += "and (e.code like :quickSearchValue or e.user.userName like :quickSearchValue) ";
    sqlParams.put("quickSearchValue", "%" + quickSearchValue + "%");
}

QuerySql querySql = new QuerySql(select, fromAndWhere);
querySql.setOrderBy("order by e.code");
return PageResultUtil.getResult(entityManager, querySql, sqlParams, queryParam);
```

Use this when:
- Filters are conditional
- Sorting needs defaults
- The result is paged
- Query parameters are assembled from a query object

### Uniqueness and existence checks with manual JPQL

```java
String sql = "select r.id from User r " +
        "where r.account = :account " +
        "and r.tenantCode = :tenantCode " +
        "and r.id <> :id ";
Query query = entityManager.createQuery(sql);
query.setParameter("account", account);
query.setParameter("tenantCode", ContextUtil.getTenantCode());
query.setParameter("id", id);
List results = query.getResultList();
return !results.isEmpty();
```

### Custom save behavior

```java
if (isNew) {
    entityManager.persist(entity);
    return entity;
} else {
    return entityManager.merge(entity);
}
```

Use this only when the module truly needs persistence behavior beyond the framework defaults.

### DTO/read-model projection queries

```java
String select = "select new com.changhong.sei.basic.dto.EmployeeBriefInfo(e.id, e.code, u.userName, o.name) ";
```

Acceptable for tightly scoped read models where returning entities would be wasteful.

## Framework Helpers

### PageResultUtil

Standard helper for dynamic paged JPQL execution:

```java
PageResultUtil.getResult(entityManager, querySql, sqlParams, queryParam)
```

### QuerySql

Separates `select`, `from + where`, and optional `order by` to keep dynamic JPQL assembly manageable.

### Inherited helper methods from BaseEntityDaoImpl

- `findFirstByFilters(search)`

Prefer these helpers over rebuilding the same persistence logic manually.

## Tenant and Context Handling

Many extension DAOs are tenant-aware:

```java
sqlParams.put("tenantCode", ContextUtil.getTenantCode());
entity.setTenantCode(ContextUtil.getTenantCode());
entity.setLastEditorId(ContextUtil.getUserId());
```

This is acceptable in the DAO extension layer when persistence logic depends on framework context.

## BaseEntityDaoImpl Auto-Handles

- ID generation (`IdGenerator.nextIdStr()`)
- Audit fields auto-fill (creatorId, createdDate, etc.)
- Tenant code auto-fill from `ContextUtil.getTenantCode()`
- Project ID auto-fill from `ContextUtil.getProjectId()`
- Soft delete (if `ISoftDelete` is implemented)
- Tenant isolation (auto-appends `tenant_code = ?` filter)
- Project isolation (auto-appends `project_id = ?` filter)
- Optimistic lock exception handling

## What DaoImpl Should Handle

Good responsibilities:
- Dynamic JPQL assembly
- Custom paging queries
- Uniqueness checks with exclusion logic
- Specialized custom save behavior
- Projection queries

Avoid putting into DaoImpl:
- HTTP or controller logic
- Business workflows that belong in services
- Transaction boundary decisions
- DTO transport concerns outside tightly scoped projections

## Naming and Packaging

| Type | Convention | Example |
|---|---|---|
| Extension interface | `XxxExtDao` | `EmployeeExtDao` |
| Implementation | `impl/XxxDaoImpl` | `impl/EmployeeDaoImpl` |
| Package | `dao.impl` | `com.changhong.sei.xxx.dao.impl` |

The implementation class name matches the main DAO name, even though it technically implements the extension interface.

## Best Practices

1. Implement `XxxExtDao` in `DaoImpl`, not the main `XxxDao`.
2. Extend `BaseEntityDaoImpl<Entity>` for standard custom DAO implementations.
3. Use constructor injection with `EntityManager`.
4. Use `QuerySql` and `PageResultUtil` for dynamic paged JPQL queries.
5. Use manual JPQL with `EntityManager` for custom uniqueness and existence checks.
6. Keep simple repository methods in the main DAO interface.
7. Use context-aware tenant and audit filling only when the module requires it.
8. Keep service-level business rules out of `DaoImpl`.
9. Return entities or focused read-model projections, not transport-layer DTO contracts.
10. Do NOT reference MyBatis/XML implementation patterns — this codebase is JPA-only.
', CURRENT_TIMESTAMP),
('SKILF_EADP_0500000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/dao.md', '# DAO

Patterns and conventions for DAO interfaces in EADP/SEI backend development.

## Positioning

The DAO layer is primarily JPA-based and framework-integrated. This codebase does NOT use MyBatis.

Key points:
- Standard entity DAOs extend `BaseEntityDao<Entity>`.
- Tree entity DAOs extend `BaseTreeDao<Entity>`.
- Complex custom queries are split into `XxxExtDao`.
- The main `XxxDao` usually combines the framework base DAO with the extension interface.
- Simple CRUD and derived queries stay in `XxxDao`.
- Complex paging, dynamic JPQL, and custom save behavior move into `XxxDaoImpl`.

## Base DAO Classes

| Base Class | Use When |
|---|---|
| `BaseEntityDao<T extends BaseEntity>` | Normal entity module |
| `BaseTreeDao<T extends BaseEntity>` | Tree-structured entity module |

`BaseEntityDao<T>` extends `BaseDao<T, String>` and provides these methods (no need to redeclare):

| Method | Description |
|---|---|
| `findOne(id)` | Find by ID |
| `findAll()` | Find all (respects tenant/project/soft-delete) |
| `findAllUnfrozen()` | Find all not frozen |
| `findAllWithDelete()` | Find all including soft-deleted |
| `findByProperty(property, value)` | Single property query |
| `findFirstByProperty(property, value)` | Single property, returns first match |
| `findByFilter(SearchFilter)` | Single filter query |
| `findByFilters(Search)` | Multi-filter query |
| `findByPage(Search)` | Paginated query |
| `isExistsByProperty(property, value)` | Existence check |
| `isCodeExists(code, id)` | Code uniqueness check |
| `findListByProperty(property, value)` | List by property |
| `create(entity)` | Create entity |
| `save(Collection)` | Save collection |
| `delete(Collection ids)` | Delete by ID collection |
| `evict()` / `evict(id)` / `evictAll()` | L2 cache operations |

## Core Conventions

### 1. Choose the correct base DAO

```java
public interface EmployeeDao extends BaseEntityDao<Employee>, EmployeeExtDao {
}

public interface PositionDao extends BaseEntityDao<Position>, PositionExtDao {
}

public interface OrganizationDao extends BaseTreeDao<Organization> {
}

public interface AccountDao extends BaseEntityDao<Account> {
}
```

### 2. Compose with ExtDao for non-trivial custom queries

This is a key real-world pattern:

```java
public interface EmployeeDao extends BaseEntityDao<Employee>, EmployeeExtDao {
}

public interface PositionDao extends BaseEntityDao<Position>, PositionExtDao {
}
```

Use `XxxExtDao` for:
- Dynamic paging queries
- Manual save variants
- Custom uniqueness checks
- Query shapes awkward as derived JPA methods
- JPQL assembled from runtime conditions

If no custom extension is needed, `ExtDao` can be omitted.

### 3. Keep simple queries in the main DAO interface

The main DAO interface commonly contains:
- Derived finder methods
- Small `@Query` methods
- `@Modifying` update statements

Examples:

```java
List<Employee> findByOrganizationId(String organizationId);

List<Position> findAllByOrganizationIdOrderByCode(String organizationId);

Account findByAccountAndTenantCode(String account, String tenant);

@Query("select e from Employee e where e.code in :codes and e.tenantCode = :tenantCode")
List<Employee> findByCodeInAndTenantCode(@Param("codes") Collection<String> codes,
                                         @Param("tenantCode") String tenantCode);

@Modifying
@Query("update Account a set a.password = :password where a.id = :id")
int updatePassword(@Param("id") String id, @Param("password") String password);
```

### 4. Use Spring Data JPA method naming where it stays readable

Examples:
- `findByOrganizationId`
- `findByOrganizationIdAndUserFrozenFalse`
- `findByTenantCodeAndUserUserAuthorityPolicy`
- `findByCodeAndTenantCode`
- `findAllByOrganizationIdOrderByCode`

Prefer derived methods when the query is simple and the property path is readable. Use `@Query` or `ExtDao` when the method name becomes unwieldy.

### 5. Use @Query and @Modifying for direct JPQL updates or special lookups

```java
@Modifying
@Query("update Position o set o.dimensionId = :dimensionId where o.dimensionId = '''' and o.tenantCode = :tenantCode")
void updateDimensionId(@Param("dimensionId") String dimensionId,
                       @Param("tenantCode") String tenantCode);

@Query("select max(t.lastEditedDate) FROM Organization t")
Date findMaxUpdateDate();
```

Prefer JPQL. Do not assume native SQL unless the module truly requires it.

## DAO Templates

### Standard entity DAO

```java
@Repository
public interface YourDao extends BaseEntityDao<YourEntity>, YourExtDao {
    List<YourEntity> findByOrganizationId(String organizationId);
    YourEntity findByCodeAndTenantCode(String code, String tenantCode);
}
```

### Tree DAO

```java
@Repository
public interface YourTreeDao extends BaseTreeDao<YourTreeEntity> {
    YourTreeEntity findByParentIdIsNullAndId(String id);
}
```

### DAO with direct JPQL update

```java
@Repository
public interface YourDao extends BaseEntityDao<YourEntity> {
    @Modifying
    @Query("update YourEntity e set e.frozen = :frozen where e.userId = :userId")
    int updateFrozen(@Param("userId") String userId, @Param("frozen") boolean frozen);
}
```

### Extension DAO interface

```java
public interface YourExtDao {
    Boolean isCodeExist(String code, String id);
    PageResult<YourEntity> queryEntities(YourQuickQueryParam queryParam, List<String> excludeIds, String tenantCode);
}
```

## When To Use ExtDao

Put methods into `ExtDao` when they need:
- `EntityManager`
- Dynamic JPQL string assembly
- `QuerySql`
- `PageResultUtil`
- Non-standard save semantics
- Richer paging behavior than derived JPA methods provide

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Main DAO | `XxxDao` | `EmployeeDao` |
| Extension interface | `XxxExtDao` | `EmployeeExtDao` |
| Implementation class | `impl/XxxDaoImpl` | `impl/EmployeeDaoImpl` |

Method naming:
- `findBy...`, `findAllBy...` — read queries
- `is...Exist` — existence checks
- `update...` — update operations
- `query...` — complex/custom queries

## What To Prefer

1. Standard JPA-style methods stay in `XxxDao`.
2. Dynamic custom methods move to `XxxExtDao` + `impl/XxxDaoImpl`.
3. Prefer JPQL over raw SQL.
4. Reuse `BaseEntityDao` and `BaseTreeDao` instead of inventing custom DAO bases.
5. Do NOT reference MyBatis patterns — this codebase is JPA-only.

## Best Practices

1. Extend `BaseEntityDao` or `BaseTreeDao` according to entity type.
2. Compose with `XxxExtDao` for dynamic or non-trivial custom queries.
3. Keep simple finder methods in the main DAO interface.
4. Use `@Query` and `@Modifying` for concise static JPQL and updates.
5. Use Spring Data method naming when it stays readable.
6. Keep DTO-centric shaping out of ordinary DAO methods.
7. Pass tenant code explicitly where cross-tenant ambiguity matters.
8. Return `PageResult<Entity>` from extension paging methods.
9. Keep complex query assembly out of services when it clearly belongs in DAO extensions.
10. Do NOT reference MyBatis/XML implementation patterns.
', CURRENT_TIMESTAMP),
('SKILF_EADP_0600000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/dto.md', '# DTO (Data Transfer Object)

Patterns and conventions for DTO design in EADP/SEI backend development.

## Base DTO

All business DTOs should extend `BaseEntityDto` (has `id` field):

```java
import com.changhong.sei.core.dto.BaseEntityDto;

public class FooDto extends BaseEntityDto {
    private String name;
    // Only include fields needed by API consumers
}
```

## Key Framework DTOs

| DTO | Purpose |
|---|---|
| `BaseEntityDto` | Base DTO with `id` field |
| `ResultData<T>` | Unified API response wrapper (success/fail/exception) |
| `PageResult<T>` | Pagination response (page, records, total, rows) |
| `PageInfo` | Pagination request (page, rows; defaults: 1, 15) |
| `Search` | Query config (filters, sortOrders, pageInfo, quickSearch) |
| `SearchFilter` | Single filter condition |
| `SearchOrder` | Sort definition |
| `TreeEntity<T>` / `TreeEntityOfAsync<T>` | Tree node wrapper |

## ResultData Usage

```java
ResultData.success(data)
ResultData.success("message", data)
ResultData.fail("error message")
ResultData.exception("exception message")
```

## DTO Patterns

### Standard Response DTO

```java
public class ContractDto extends BaseEntityDto {

    private String code;
    private String name;
    private String type;
    private BigDecimal amount;

    // Organization for BPM
    private String organizationId;
    private String organizationName;

    // Workflow
    private String flowStatus;

    // EDM attachment bindings
    private String mainAttachmentBindingId;
    private String scanAttachmentBindingId;

    // Document IDs from frontend (for save)
    private List<String> mainDocIds;
    private List<String> scanDocIds;
}
```

### Save Request DTO

```java
public class ContractSaveRequest extends BaseEntityDto {

    @NotBlank(message = "单据编号不能为空")
    @Size(max = 50, message = "单据编号长度不能超过50")
    private String code;

    @NotBlank(message = "单据名称不能为空")
    @Size(max = 200, message = "单据名称长度不能超过200")
    private String name;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remarks;

    // EDM document IDs from frontend
    private List<String> mainDocIds;
    private List<String> scanDocIds;

    // Binding IDs (for existing records)
    private String mainAttachmentBindingId;
    private String scanAttachmentBindingId;
}
```

### Query Parameter DTO

Use a dedicated parameter object for complex queries, not `extends Search`:

```java
public class ContractQuickQueryParam {
    private String code;
    private String name;
    private String type;
    private String status;
    private String flowStatus;
    private String organizationId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}
```

Convert to `Search` in the service or controller:

```java
Search search = new Search(param);
search.addFilter(new SearchFilter("organization.id", param.getOrganizationId(), SearchFilter.Operator.EQ));
return findByPage(search);
```

## DTO Conversion

DTO conversion is handled in controllers using base controller helpers:

```java
// In controller — uses ModelMapper from BaseController
convertToDto(entity)           // Entity → DTO
convertToEntity(dto)           // DTO → Entity
convertToDtos(entities)        // List<Entity> → List<DTO>
convertToDtoPageResult(page)   // PageResult<Entity> → PageResult<DTO>
```

Override `customConvertToDtoMapper()` for custom field mapping:

```java
@Override
protected void customConvertToDtoMapper() {
    PropertyMap<Employee, EmployeeDto> propertyMap = new PropertyMap<>() {
        @Override
        protected void configure() {
            map().setOrganizationId(source.getOrganizationId());
            map().setUserAccount(source.getUser().getAccount());
        }
    };
    dtoModelMapper.addMappings(propertyMap);
}
```

## BPM-Specific DTOs

### BpmInvokeParams

Received from BPM engine during workflow callbacks:

```java
import com.changhong.sei.bpm.dto.vo.BpmInvokeParams;

@Override
public ResultData<Void> afterStartFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    String nodeCode = invokeParams.getNodeCode();
    String nodeName = invokeParams.getNodeName();
    String userId = invokeParams.getStartUserId();
    // ... business logic
    return ResultData.success();
}
```

### BpmReturnParams

Return parameters to BPM engine:

```java
import com.changhong.sei.bpm.dto.vo.BpmReturnParams;

@Override
public ResultData<BpmReturnParams> triggerTaskService(BpmInvokeParams invokeParams) {
    BpmReturnParams returnParams = new BpmReturnParams();
    returnParams.setImmediateTriggerTask(true);
    returnParams.setReceiverUserId("SYSTEM_USER");
    returnParams.setReceiveOpinion("自动通过");
    return ResultData.success(returnParams);
}
```

### PropertiesAndValuesVo

Return entity properties to BPM:

```java
import com.changhong.sei.bpm.dto.vo.PropertiesAndValuesVo;

@Override
public ResultData<PropertiesAndValuesVo> propertiesAndValues(
    String businessEntityCode, String businessId) {
    Contract entity = contractDao.findById(businessId);
    PropertiesAndValuesVo values = new PropertiesAndValuesVo();
    values.setBusinessCode(entity.getCode());
    values.setBusinessName(entity.getName());
    values.setBusinessMoney(entity.getAmount().toString());
    values.setOrgId(entity.getOrganizationId());
    return ResultData.success(values);
}
```

### PropertiesAllExplainVo

Return property explanations to BPM:

```java
import com.changhong.sei.bpm.dto.vo.PropertiesAllExplainVo;

@Override
public ResultData<List<PropertiesAllExplainVo>> propertiesAllExplain(
    String businessEntityCode) {
    List<PropertiesAllExplainVo> explains = new ArrayList<>();
    PropertiesAllExplainVo codeExplain = new PropertiesAllExplainVo();
    codeExplain.setCode("code");
    codeExplain.setName("单据编号");
    codeExplain.setInitValue("");
    codeExplain.setRemark("业务单据的唯一标识");
    explains.add(codeExplain);
    return ResultData.success(explains);
}
```

## EDM-Specific DTOs

### DocumentResponse

```java
import com.changhong.sei.edm.dto.DocumentResponse;

@Override
public ResultData<List<DocumentResponse>> getMainAttachments(String contractId) {
    Contract entity = contractDao.findById(contractId);
    String bindingId = entity.getMainAttachmentBindingId();
    ResultData<List<DocumentResponse>> result =
        documentManager.getEntityDocumentInfos(bindingId);
    return result;
}
```

### UploadResponse

```java
import com.changhong.sei.edm.dto.UploadResponse;

// Upload from file
File file = new File("path/to/file.pdf");
UploadResponse uploadResponse = documentManager.uploadDocument(file);
String docId = uploadResponse.getDocId();
```

## SearchFilter Factory Methods

| Method | SQL |
|---|---|
| `eq(field, value)` | = |
| `ne(field, value)` | != |
| `like(field, value)` | LIKE %val% |
| `leftLike(field, value)` | LIKE val% |
| `rightLike(field, value)` | LIKE %val |
| `gt(field, value)` | > |
| `ge(field, value)` | >= |
| `lt(field, value)` | < |
| `le(field, value)` | <= |
| `in(field, collection)` | IN (...) |
| `notin(field, collection)` | NOT IN (...) |
| `isNull(field)` | IS NULL |
| `isNotNull(field)` | IS NOT NULL |
| `isBlank(field)` | IS NULL OR ='''' |
| `notBlank(field)` | IS NOT NULL AND !='''' |

## Best Practices

1. Extend `BaseEntityDto` for all business DTOs.
2. Use JSR-303 validation annotations on request DTOs.
3. Always use `ResultData` and `PageResult` for API responses.
4. Use controller `convertToDto`/`convertToEntity` for Entity↔DTO conversion, not `BeanMapper` directly.
5. Return binding IDs to frontend for attachment components.
6. Accept docId lists from frontend for EDM binding.
7. Use `@JsonFormat` for date fields.
8. Use dedicated query parameter objects, convert to `Search` in service.
9. Use descriptive names (e.g., `ContractSaveRequest`, `ContractQuickQueryParam`).
10. Keep request DTOs, response DTOs, and query DTOs explicit instead of one catch-all class.
', CURRENT_TIMESTAMP),
('SKILF_EADP_0700000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/entity.md', '# Entity

Patterns and conventions for entity design with sei-core framework features and EDM/BPM integration.

## Base Class Hierarchy

```
AbstractEntity<ID>           (Persistable<ID>, Serializable)
  └── BaseEntity             (id: String, @Id, @Column(length=36))
        └── BaseAuditableEntity  (creatorId/Name/Account, createdDate,
                                   lastEditorId/Name/Account, lastEditedDate)
              └── YourEntity     (+ implements ITenant, ISoftDelete, etc.)
```

### BaseEntity

- ID is always `String`, generated by `IdGenerator.nextIdStr()` (36-char UUID).
- Do NOT use `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- `isNew()` checks if id is blank (from `AbstractEntity`).

### BaseAuditableEntity

Provides audit fields (all getters annotated `@JsonIgnore`):

| Java Field | DB Column | Updatable |
|---|---|---|
| creatorId | creator_id | No |
| creatorAccount | creator_account | No |
| creatorName | creator_name | No |
| createdDate | created_date | No |
| lastEditorId | last_editor_id | Yes |
| lastEditorAccount | last_editor_account | Yes |
| lastEditorName | last_editor_name | Yes |
| lastEditedDate | last_edited_date | Yes |

## Required Annotations on Every Entity

```java
@Entity
@Table(name = "table_name")
@Access(AccessType.FIELD)
public class FooEntity extends BaseAuditableEntity {
    // fields...

    @Override
    @Transient
    public String getDisplay() {
        return name; // return display-friendly value
    }
}
```

## Feature Interfaces

Implement as needed:

| Interface | Adds | DB Column | Auto-behavior |
|---|---|---|---|
| `ITenant` | tenantCode | tenant_code | Auto-fills from ContextUtil; queries auto-filter by tenant |
| `IProjectEntity` | projectId | project_id | Auto-fills from ContextUtil; queries auto-filter by project |
| `ISoftDelete` | deleted (Long) | deleted | `delete()` sets `System.currentTimeMillis()` instead of physical delete |
| `IFrozen` | frozen (Boolean) | frozen | `findAllUnfrozen()` excludes frozen records |
| `ICodeUnique` | code | code | preInsert/preUpdate auto-checks code uniqueness |
| `IDataAuthEntity` | — | — | Enables `getUserAuthorizedEntities()` for permission-filtered queries (on DTO) |
| `IParentEntity` | parentEntityId | — | Enables cascading data permission |

### ITenant — Multi-Tenant Isolation

```java
@Entity
public class FooEntity extends BaseAuditableEntity implements ITenant {
    @Column(name = "tenant_code", length = 36)
    private String tenantCode;

    @Override
    public String getTenantCode() { return tenantCode; }
    @Override
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
}
```

Auto-behavior:
- On create: auto-fills `tenantCode` from `ContextUtil.getTenantCode()` if blank
- On query: all `findAll*` and `findByFilters` methods auto-append `tenant_code = ?`
- `BaseDaoImpl.buildPredicatesFromFilters()` appends tenant filter automatically

### IProjectEntity — Project Isolation

```java
@Entity
public class FooEntity extends BaseAuditableEntity implements IProjectEntity {
    @Column(name = "project_id", length = 36)
    private String projectId;

    @Override
    public String getProjectId() { return projectId; }
    @Override
    public void setProjectId(String projectId) { this.projectId = projectId; }
}
```

Auto-behavior:
- On create: auto-fills `projectId` from `ContextUtil.getProjectId()`, defaults to `"none"`
- On query: auto-appends `project_id = ?` filter
- `findAllUnfrozenIgnoreProject()` skips project filter (for admin/permission scenarios)

### ISoftDelete — Logical Delete

```java
@Entity
public class FooEntity extends BaseAuditableEntity implements ISoftDelete {
    @Column(name = "deleted")
    private Long deleted = 0L;

    @Override
    public Long getDeleted() { return deleted; }
    @Override
    public void setDeleted(Long deleted) { this.deleted = deleted; }
}
```

Auto-behavior:
- `delete(entity)` sets `deleted = System.currentTimeMillis()` instead of physical delete
- All query methods auto-append `deleted = 0` filter
- `findAllWithDelete()` returns records regardless of soft-delete status
- `findByIds()` filters out soft-deleted records

### IFrozen — Frozen Status

```java
@Entity
public class FooEntity extends BaseAuditableEntity implements IFrozen {
    @Column(name = "frozen")
    private Boolean frozen = false;

    @Override
    public Boolean getFrozen() { return frozen; }
    @Override
    public void setFrozen(Boolean frozen) { this.frozen = frozen; }
}
```

Auto-behavior:
- `findAllUnfrozen()` excludes `frozen = true` records
- `getUserAuthorizedEntities()` respects frozen status
- Data permission queries can optionally include frozen entities

### ICodeUnique — Code Uniqueness

```java
@Entity
public class FooEntity extends BaseAuditableEntity implements ICodeUnique {
    @Column(name = "code", length = 50)
    private String code;

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }
}
```

Auto-behavior:
- `BaseService.preInsert()` and `preUpdate()` auto-check code uniqueness
- If also implements `ITenant`, checks uniqueness within tenant scope
- `BaseDao.isCodeExists(code, id)` and `isCodeExists(tenantCode, code, id)` available

### IDataAuthEntity — Data Permission

```java
// In DTO module
public class FooDto extends BaseEntityDto implements IDataAuthEntity {
    // implement getId(), getCode(), getName()
}
```

Enables: `getUserAuthorizedEntities()` for permission-filtered queries.

### IParentEntity — Cascading Data Permission

```java
@Entity
public class FooEntity extends BaseAuditableEntity implements IParentEntity {
    @Column(name = "parent_entity_id", length = 36)
    private String parentEntityId;

    @Override
    public String getParentEntityId() { return parentEntityId; }
}
```

Constant: `IParentEntity.PARENT_ENTITY_ID_NONE = "none"` — indicates root-level entity.

## Auto-Filtering Behavior

All query methods in `BaseDaoImpl` automatically apply these filters based on implemented interfaces:

| Interface | Auto-Filter | Applied In |
|---|---|---|
| ISoftDelete | `deleted = 0` | findAll, findByFilter, findByFilters, findByPage, findListByProperty |
| ITenant | `tenant_code = ?` (from ContextUtil) | Same as above |
| IProjectEntity | `project_id = ?` (from ContextUtil) | Same as above |
| IFrozen | `frozen = false` | findAllUnfrozen only |

Exceptions: `findAllWithDelete()` skips soft-delete filter. `findAllUnfrozenIgnoreProject()` skips project filter.

## Field Types

### String Fields

```java
@Column(name = "code", nullable = false, length = 50, unique = true)
private String code;
```

### Numeric Fields

```java
// Integer
@Column(name = "count", nullable = false)
private Integer count;

// BigDecimal (for money)
@Column(name = "amount", precision = 18, scale = 2)
private BigDecimal amount;

// Double
@Column(name = "rate", precision = 10, scale = 4)
private Double rate;
```

### Date Fields

```java
@Column(name = "created_date")
private Date createdDate;

// For datetime with precision
@Temporal(TemporalType.TIMESTAMP)
@Column(name = "created_time")
private Date createdTime;

// For date only (no time)
@Temporal(TemporalType.DATE)
@Column(name = "contract_date")
private Date contractDate;
```

### Boolean Fields

```java
@Column(name = "is_active")
private Boolean active;
```

### Enum Fields

```java
public enum ContractStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    DELETED
}

@Column(name = "status", length = 20)
@Enumerated(EnumType.STRING)
private ContractStatus status;
```

### Text/Large Fields

```java
// Long text (VARCHAR)
@Column(name = "description", length = 1000)
private String description;

// CLOB/TEXT (for very long content)
@Lob
@Column(name = "content", columnDefinition = "TEXT")
private String content;
```

## EDM Attachment Fields

Add one binding ID field per attachment category:

```java
@Column(name = "main_attachment_binding_id", length = 50)
private String mainAttachmentBindingId;

@Column(name = "scan_attachment_binding_id", length = 50)
private String scanAttachmentBindingId;

@Column(name = "approval_attachment_binding_id", length = 50)
private String approvalAttachmentBindingId;
```

## BPM Workflow Fields

Required for BPM-enabled entities:

```java
// FlowStatus enum: InReview/End/Termination
@Column(name = "flow_status", length = 20)
private FlowStatus flowStatus;

@Column(name = "organization_id", length = 50)
private String organizationId;

@Column(name = "organization_name", length = 200)
private String organizationName;
```

## Indexes

```java
@Table(name = "contract", indexes = {
    @Index(name = "idx_code", columnList = "code"),
    @Index(name = "idx_org_status", columnList = "organization_id, flow_status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_code", columnNames = {"code"})
})
```

## Complete Entity Example

```java
package com.changhong.sei.contract.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import com.changhong.sei.bpm.dto.status.FlowStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "contract",
    indexes = {
        @Index(name = "idx_code", columnList = "code"),
        @Index(name = "idx_org_status", columnList = "organization_id, flow_status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_code", columnNames = {"code"})
    }
)
@Access(AccessType.FIELD)
public class Contract extends BaseAuditableEntity {

    // ========== Business Fields ==========

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Temporal(TemporalType.DATE)
    @Column(name = "contract_date")
    private Date contractDate;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // ========== EDM Attachment Fields ==========

    @Column(name = "main_attachment_binding_id", length = 50)
    private String mainAttachmentBindingId;

    @Column(name = "scan_attachment_binding_id", length = 50)
    private String scanAttachmentBindingId;

    @Column(name = "approval_attachment_binding_id", length = 50)
    private String approvalAttachmentBindingId;

    // ========== BPM Workflow Fields ==========

    @Column(name = "flow_status", length = 20)
    private FlowStatus flowStatus;

    @Column(name = "organization_id", length = 50)
    private String organizationId;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }

    // Getters and Setters
}
```

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Table name | snake_case | `contract`, `contract_detail` |
| Column name | snake_case | `organization_id`, `flow_status` |
| Java field | camelCase | `organizationId`, `flowStatus` |

## Best Practices

1. Extend `BaseAuditableEntity` when audit fields are needed; `BaseEntity` otherwise.
2. Always add `@Entity`, `@Table`, `@Access(AccessType.FIELD)`.
3. Override `@Transient getDisplay()` on every entity.
4. Implement feature interfaces (`ITenant`, `ISoftDelete`, etc.) as needed.
5. Use `BigDecimal` for money; `Long deleted = 0L` for soft delete (not `Boolean isDeleted`).
6. ID is `String` (UUID) — do NOT use `@GeneratedValue`.
7. Add indexes for frequently queried fields.
8. Use unique constraints for business keys (like code).
9. Add EDM binding ID fields per attachment category.
10. Add BPM fields (`flowStatus`, `organizationId`, `organizationName`) when workflow is required.
', CURRENT_TIMESTAMP),
('SKILF_EADP_0800000000000000000000000', 'SKILBLTNEADP000000000000000000000000', 'references/service.md', '# Service & Query System

Patterns and conventions for service implementations and the search/query system in EADP/SEI backend development.

## Positioning

The service layer is the main place for business rules, data validation, persistence orchestration, cascading operations, cache handling, and transaction boundaries.

Key points:
- Services extend `BaseEntityService<T>` (or `BaseTreeService<T>`), they do NOT implement the API interface.
- Controllers implement the API contract and call services.
- Services mostly work with entities, framework result types, `Search`, and DAO objects.
- DTO conversion is handled in controllers, not in services.
- Services return `OperateResult`/`OperateResultWithData` for writes; controllers adapt to `ResultData`.

## Core Conventions

### 1. Extend the framework base service

```java
@Service
public class EmployeeService extends BaseEntityService<Employee> {
}

@Service
public class OrganizationService extends BaseTreeService<Organization> {
}

@Service
public class ProjectService extends BaseEntityService<Project> {
}
```

Use:
- `BaseEntityService<T>` for normal entity modules
- `BaseTreeService<T>` for tree-structured entities

Do NOT default to `implements XxxApi` — that is the controller''s job.

### 2. Override getDao()

Standard services wire their main DAO into the framework:

```java
@Override
protected BaseEntityDao<Employee> getDao() {
    return employeeDao;
}

@Override
protected BaseTreeDao<Organization> getDao() {
    return organizationDao;
}
```

This is a required integration point for framework CRUD, query, and tree behavior.

### 3. Keep service dependencies domain-oriented

Real services depend on:
- Their main DAO
- Related DAOs
- Sibling services
- Infrastructure helpers (SerialGenerator, AsyncRunUtil, etc.)
- Cache, async, and integration managers when needed

### 4. Put write operations under transactions

```java
@Transactional(rollbackFor = Exception.class)
public OperateResultWithData<Project> save(Project entity) {
    // validate and enrich
    return super.save(entity);
}
```

Use transactions on data-changing methods. Read methods normally do not require them.

## Return Type Conventions

| Method Type | Return Type | Example |
|---|---|---|
| Save | `OperateResultWithData<T>` | `save(entity)` |
| Delete, move, copy | `OperateResult` | `delete(id)` |
| Framework wrapper | `ResponseData<T>` | When caller expects that wrapper |
| API-facing | `ResultData<T>` | When method directly serves controller output |

Do NOT force every service method to return `ResultData` — use `OperateResult` or `OperateResultWithData` for framework-style write flows.

```java
public OperateResultWithData<Employee> save(Employee entity) { ... }
public OperateResult delete(String projectId) { ... }
public ResponseData<List<Organization>> findByCorpCode(String corporationCode) { ... }
public ResultData<ProjectInfoDto> getProjectInfo(String projectId) { ... }
```

## Lifecycle Hooks

Override these in the service instead of scattering logic everywhere:

### preInsert — Before Create

```java
@Override
protected OperateResultWithData<FooEntity> preInsert(FooEntity entity) {
    if (getService().isExistsByProperty("code", entity.getCode())) {
        return OperateResultWithData.operationFailureWithData(entity, "Code already exists");
    }
    return super.preInsert(entity);
}
```

Default behavior: checks `ICodeUnique` if implemented.

### preUpdate — Before Update

Default behavior: checks `ICodeUnique` if implemented.

### preDelete — Before Delete

Common pattern for referential and business checks:

```java
@Override
protected OperateResult preDelete(String id) {
    if (positionService.isExistsByProperty("organization.id", id)) {
        return OperateResult.operationFailure("00042");
    }
    return super.preDelete(id);
}
```

### Override save

Use to:
- Generate codes or serial numbers
- Enforce uniqueness
- Initialize defaults
- Perform parent/child state checks
- Trigger side effects after successful save

### Override delete

Use when deletion requires extra cleanup, cascades, cache eviction, or integration callbacks.

## Validation Patterns

### Validate before calling super.save

```java
@Override
@Transactional(rollbackFor = Exception.class)
public OperateResultWithData<Project> save(Project entity) {
    Project existProject = dao.findFirstByProperty(Project.FIELD_NAME, entity.getName());
    if (Objects.nonNull(existProject) && !StringUtils.equals(existProject.getId(), entity.getId())) {
        return OperateResultWithData.operationFailure("project_031", entity.getName());
    }
    return super.save(entity);
}
```

### Prefer framework result failures over ad hoc exceptions

```java
return OperateResult.operationFailure("00042");
return OperateResultWithData.operationFailure("project_001");
return ResultData.fail(ContextUtil.getMessage("account_0016"));
```

Throw exceptions only when the failure is truly exceptional.

## Search & Query System

### Search Object

`Search` is the unified query configuration object.

```java
Search search = Search.of()
    .addFilter(SearchFilter.eq("status", "ACTIVE"))
    .addFilter(SearchFilter.like("name", keyword))
    .addSortOrder(new SearchOrder("createdDate", SearchOrder.Direction.DESC))
    .setPageInfo(PageInfo.of(1, 20));
```

Search fields:
- `quickSearchProperties` — Collection of field names for quick search
- `quickSearchValue` — Quick search keyword (applies LIKE to all quickSearchProperties)
- `filters` — List of `SearchFilter` conditions
- `sortOrders` — List of `SearchOrder` definitions
- `pageInfo` — `PageInfo` with page/rows

### SearchFilter Operators

| Operator | SQL Equivalent | Factory Method |
|---|---|---|
| EQ | = | `SearchFilter.eq(field, value)` |
| NE | != | `SearchFilter.ne(field, value)` |
| LK | LIKE %val% | `SearchFilter.like(field, value)` |
| LLK | LIKE val% | `SearchFilter.leftLike(field, value)` |
| RLK | LIKE %val | `SearchFilter.rightLike(field, value)` |
| NC | NOT LIKE %val% | — |
| GT | > | `SearchFilter.gt(field, value)` |
| GE | >= | `SearchFilter.ge(field, value)` |
| LT | < | `SearchFilter.lt(field, value)` |
| LE | <= | `SearchFilter.le(field, value)` |
| IN | IN (...) | `SearchFilter.in(field, collection)` |
| NOTIN | NOT IN (...) | `SearchFilter.notin(field, collection)` |
| BT | BETWEEN a AND b | — (pass array/collection of 2 values) |
| NU | IS NULL | `SearchFilter.isNull(field)` |
| NN | IS NOT NULL | `SearchFilter.isNotNull(field)` |
| BK | IS NULL OR ='''' | `SearchFilter.isBlank(field)` |
| NB | IS NOT NULL AND !='''' | `SearchFilter.notBlank(field)` |

Constructor shortcut: `new SearchFilter("name", "value")` defaults to EQ operator.

### Date Handling in Queries

BaseDaoImpl handles date queries specially:
- If the date has no time component (00:00:00), EQ becomes a range: `>= date AND < date+1`
- LE with zero-time becomes `< date+1` (includes the full day)
- Supports `java.util.Date`, `LocalDate`, `LocalDateTime` type conversion

### Sorting

Default sort order (when no `SearchOrder` specified):
1. If implements `IRank`: ascending by `rank`
2. If extends `BaseAuditableEntity`: descending by `createdDate`
3. If extends `BaseEntity`: descending by `id`

When `SearchOrder` is provided, `IRank` sort is still appended as secondary.

### Pagination

```java
// Request
PageInfo pageInfo = PageInfo.of(1, 20);  // page 1, 20 rows
search.setPageInfo(pageInfo);

// Response
PageResult<FooEntity> result = service.findByPage(search);
result.getPage();     // current page
result.getRecords();  // total count
result.getTotal();    // total pages
result.getRows();     // ArrayList<FooEntity> data
```

### SearchParam and QuickSearchParam

Front-end oriented parameter objects:
- `SearchParam` — advanced search with filters/sortOrders/pageInfo
- `QuickSearchParam` — quick search with quickSearchValue/quickSearchProperties/sortOrders/pageInfo
- Both can be passed to `new Search(param)` constructor for conversion

### Query Usage in Services

```java
Search search = new Search(param);
search.addFilter(new SearchFilter("organization.id", param.getOrganizationId(), SearchFilter.Operator.EQ));
return findByPage(search);
```

### Service Layer Query Methods (inherited from BaseService)

| Method | Description | Returns |
|---|---|---|
| `save(entity)` | Create or update (checks `isNew()`) | OperateResultWithData |
| `delete(id)` | Delete by ID (soft if `ISoftDelete`) | OperateResult |
| `findOne(id)` | Find by ID | T or null |
| `findAll()` | Find all (respects tenant/project/soft-delete) | List<T> |
| `findByIds(ids)` | Find by ID collection (filters soft-deleted) | List<T> |
| `findAllUnfrozen()` | Find all not frozen | List<T> |
| `findByProperty(prop, val)` | Single property EQ, fails if multiple | T or null |
| `findFirstByProperty(prop, val)` | Single property EQ, returns first match | T or null |
| `findListByProperty(prop, val)` | Single property EQ | List<T> |
| `isExistsByProperty(prop, val)` | Existence check | boolean |
| `findByFilter(SearchFilter)` | Single filter | List<T> |
| `findByFilters(Search)` | Multi-filter with sort | List<T> |
| `findOneByFilters(Search)` | Multi-filter, fails if multiple | T or null |
| `findFirstByFilters(Search)` | Multi-filter, returns first | T or null |
| `findByPage(Search)` | Paginated query | PageResult<T> |
| `count(Search)` | Count with filters | long |

### Data permission methods (in BaseEntityService)

- `getUserAuthorizedEntities(featureCode)` — current user''s authorized entities
- `findAllAuthEntityData()` — all data permission entities
- `getAuthEntityDataByParentEntityId(parentId)` — cascading data permission

## EDM Integration

### Bind documents on save

```java
@Override
@Transactional(rollbackFor = Exception.class)
public OperateResultWithData<Contract> save(Contract entity) {
    OperateResultWithData<Contract> result = super.save(entity);
    if (result.successful()) {
        if (StringUtils.isNotBlank(entity.getMainAttachmentBindingId())) {
            documentManager.bindDocuments(entity.getMainAttachmentBindingId(), entity.getMainDocIds());
        }
    }
    return result;
}
```

### Unbind documents on delete

```java
@Override
protected OperateResult preDelete(String id) {
    Contract entity = getDao().findOne(id);
    if (entity != null && StringUtils.isNotBlank(entity.getMainAttachmentBindingId())) {
        documentManager.unbindAllDocuments(entity.getMainAttachmentBindingId());
    }
    return super.preDelete(id);
}
```

## BPM Integration

### beforeStartFlow — Validate business data before workflow starts

```java
@Override
public ResultData<Void> beforeStartFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    Contract entity = getDao().findOne(businessId);
    if (entity.getAmount() == null || entity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        return ResultData.fail("金额必须大于0才能发起审批");
    }
    return ResultData.success();
}
```

### afterStartFlow / afterEndFlow — Update status in callbacks

```java
@Override
public ResultData<Void> afterStartFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    Contract entity = getDao().findOne(businessId);
    entity.setFlowStatus(FlowStatus.InReview);
    getDao().save(entity);
    return ResultData.success();
}

@Override
public ResultData<Void> afterEndFlow(BpmInvokeParams invokeParams) {
    String businessId = invokeParams.getBusinessId();
    Contract entity = getDao().findOne(businessId);
    entity.setFlowStatus(FlowStatus.End);
    getDao().save(entity);
    return ResultData.success();
}
```

## Cache and After-Commit Handling

### Use framework cache helpers

```java
return cacheBuilder.get(CACHE_NAME_PROJECT, projectId, () -> { ... });
cacheBuilder.evict(CACHE_NAME_PROJECT, projectId);
```

### Prefer after-commit callbacks for cache eviction

```java
TransactionUtil.afterCommit(() -> {
    cacheBuilder.evict(SeiDefaultCacheKey.CACHE_NAME_PROJECT, projectId);
});
```

## Entity Enrichment Patterns

Services often enrich entities after DAO reads:
- Load profile fields
- Fill display or remark fields
- Attach related account or organization information
- Normalize missing attribute value rows

## Cascading and Multi-Service Orchestration

Service methods frequently orchestrate multiple services and DAOs in one transaction:
- Saving employee data also creates/updates user, profile, and account records
- Deleting a project also removes project members
- Copying a position optionally copies feature-role relations

## Service Responsibility Boundaries

Services should:
- Own core business validation
- Coordinate multiple repositories or services
- Define transaction boundaries
- Build framework queries
- Perform cascade maintenance
- Manage cache and integration side effects

Services should NOT:
- Expose HTTP annotations
- Define request mappings
- Depend on controller concerns
- Perform DTO transport shaping as their main purpose

## Best Practices

1. Extend `BaseEntityService` or `BaseTreeService` for standard business entities.
2. Override `getDao()` to connect the service to the framework.
3. Put write operations under `@Transactional(rollbackFor = Exception.class)`.
4. Use lifecycle hooks (`preInsert`, `preUpdate`, `preDelete`) for validation.
5. Use `OperateResult` and `OperateResultWithData` for framework-style write flows.
6. Use `Search`, `SearchFilter`, and `PageResult` for business query composition.
7. Keep DTO conversion primarily in controllers, not in services.
8. Handle cache eviction and after-commit side effects explicitly when the module uses caching.
9. Allow pragmatic orchestration across multiple services and DAOs.
10. Keep transport concerns out of the service layer.
', CURRENT_TIMESTAMP);

-- ============================ 把 builtin:<name> 绑定重指到真实种子 id ============================
-- 历史 V11 曾把 agent 绑定改写成 builtin:<name> synthetic id（oc_agent_skill.skill_id 无 FK）。
-- 现统一为真实 oc_skill id；无 builtin:* 绑定时 UPDATE 影响 0 行，幂等。
UPDATE oc_agent_skill SET skill_id = 'SKILBLTNSUID000000000000000000000000' WHERE skill_id = 'builtin:suid';
UPDATE oc_agent_skill SET skill_id = 'SKILBLTNEADP000000000000000000000000' WHERE skill_id = 'builtin:eadp-backend';
UPDATE oc_agent_skill SET skill_id = 'SKILBLTNPLAN000000000000000000000000' WHERE skill_id = 'builtin:project-planning';
UPDATE oc_agent_skill SET skill_id = 'SKILBLTNFEAT000000000000000000000000' WHERE skill_id = 'builtin:feature-design';

