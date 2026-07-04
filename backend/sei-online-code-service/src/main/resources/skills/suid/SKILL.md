---
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
| `@ead/suid`             | **所有组件**（基础 + 业务扩展），统一从此包导入                                    | `import { ExtTable, Button } from '@ead/suid'`                      |
| `@ead/suid-icons`       | **所有图标**（Outlined 369 + Filled 60），禁止从 `@ead/suid` 导入图标              | `import { SearchOutlined } from '@ead/suid-icons'`                  |
| `@ead/antd-style`       | **CSS-in-JS 样式**，使用 `createStyles`                                            | `import { createStyles } from '@ead/antd-style'`                    |
| `@ead/suid-utils-react` | **React Hooks + 工具函数**（useStore、storage 等），自动重导出 `@ead/suid-utils`   | `import { useStore, storage, util } from '@ead/suid-utils-react'`   |
| `@ead/suid-utils`       | **纯 JS 工具函数**（request、Decimal、tree 等），被 `@ead/suid-utils-react` 重导出 | `import { request, util, exportJsonToXlsx } from '@ead/suid-utils'` |

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
import { useStore, type StoreOption } from '@ead/suid-utils-react';

// StoreOption 同时用于 useStore Hook 和 ExtTable/ComboList/ListCard 的 store 属性
const store: StoreOption = {
  url: '/api/list',
  type: 'POST',  // 'GET' | 'POST'
  params: {},    // 附加参数
  autoLoad: true, // 自动请求
};

// Hook 用法：管理 loading/error/request 状态
const { data, dataLoading, getData, setStore } = useStore(store);

// 组件 props 用法
<ExtTable store={store} remotePaging />
<ComboList store={store} reader={{ textField: 'label', valueField: 'value' }} />
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

formaterNumber(1234.5, 2, true, "元"); // '1,234.50元'
toChineseAmount(12345); // '壹万贰仟叁佰肆拾伍元整'
toFileSize(1073741824); // '1.00GB'
toThousandsAmount(1234567, {
  currency: true,
}); // '¥1,234,567.00'
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
| 平滑滚动到元素                            | `scrollToElement('#id')`                                     | `element.scrollIntoView`       |
| 密码强度校验                              | `checkStrongPassword(password)`                              | 自行正则                       |
| 图片压缩                                  | `compressImageFile(file, { maxWidth, quality })`             | Canvas API                     |
| UUID 生成                                 | `getUUID()`                                                  | `crypto.randomUUID()`          |
| MD5 哈希                                  | `md5(data)`                                                  | Web Crypto API                 |
| 移动端设备检测                            | `useMobile()` Hook                                           | `isMobile()` 函数              |
| 移动端滚动锁定                            | `useLockScroll(ref, shouldLock)`                             | CSS `overflow: hidden`         |
| 分页无限滚动                              | `usePagedInfiniteScroll(service, { pageSize })`              | ahooks `useInfiniteScroll`     |
| 触摸手势追踪                              | `useTouchState()`                                            | Touch Events API               |
| 获取当前用户信息                          | `useUserContext()` / `getContextUser()`                      | `CONST_GLOBAL.CURRENT_USER`    |
| 快捷键绑定                                | `useHotkeys('ctrl+s', handler)`                              | `addEventListener('keydown')`  |
| 模板字符串插值                            | `tplMessage('{name}是{age}岁', values)`                      | `String.replace`               |
| URL 路径匹配                              | `pathMatchRegexp('/user/:id', pathname)`                     | `path-to-regexp`               |
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
    { field: 'code', title: '公司代码' },
    { field: 'name', title: '公司名称' },
  ]}
  quickSearchPlaceHolder="请输入代码或名称"
  searchRealTime  // 输入实时搜索（默认 true）
  store={{ url: '/api/list', type: 'POST' }}
  remotePaging
/>
```

#### 模式二：外部筛选 Form + cascade 传参（多条件复杂筛选）

```tsx
import React, { useState, useCallback, useMemo, useRef } from 'react';
import { ExtTable, Form, Input, ComboList, Button } from '@ead/suid';
import type { ExtTableRef } from '@ead/suid';
import { SearchOutlined, ReloadOutlined } from '@ead/suid-icons';

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
    <ComboList store={{ url: '/api/enums/status' }} reader={{ textField: 'label', valueField: 'value' }} allowClear />
  </Form.Item>
  <Form.Item>
    <Button htmlType="submit" type="primary" icon={<SearchOutlined />}>搜索</Button>
    <Button onClick={handleReset} icon={<ReloadOutlined />}>重置</Button>
  </Form.Item>
</Form>

<ExtTable
  ref={tableRef}
  cascade={cascade}
  store={{ url: '/api/list', type: 'POST' }}
  remotePaging
/>
```

> **`FilterView` 的正确用法**：独立的枚举/状态下拉选择器，用于表格/列表工具栏中的单条件快速筛选。

```tsx
// ✅ 正确：FilterView 作为工具栏独立筛选器
<FilterView
  dataSource={[{ title: '全部', key: 'ALL' }, { title: '草稿', key: 'INIT' }, { title: '已发布', key: 'PUBLISHED' }]}
  labelTitle="状态"
  defaultValue={['ALL']}
  onChange={(val) => {
    setFilter((f) => ({ ...f, status: val }));
    tableRef.current?.reloadData();
  }}
/>

// 或使用远程数据
<FilterView
  store={{ url: '/api/enums/status' }}
  reader={{ title: 'title', value: 'key' }}
  labelTitle="状态"
  onChange={(val) => {
    setFilter((f) => ({ ...f, status: val }));
    tableRef.current?.reloadData();
  }}
/>
```

### 标准增删改查页面

```tsx
import React, { useState, useRef } from 'react';

import {
  ExtTable, ExtModal, Form, Input, Button, ActionButton, Popconfirm, message,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';

import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ead/suid-icons';

import { createStyles } from '@ead/antd-style';

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

  const columns: ExtTableProps<DataType>['columns'] = [
    {
      title: '操作',
      dataIndex: 'id',
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
    { title: '名称', dataIndex: 'name', expandUnusedSpace: true },
  ];

  const handleDelete = async (id: string) => {
    // ... call delete API ...
    message.success('删除成功');
    tableRef.current?.reloadData();  // ✅ 刷新表格数据
  };

  const handleSave = async (values: any) => {
    // ... call save API ...
    message.success('保存成功');
    setModalOpen(false);
    setEditingRecord(null);
    tableRef.current?.reloadData();  // ✅ 刷新表格数据
  };

  return (
    <div className={styles.page}>
      <ExtTable
        ref={tableRef}
        columns={columns}
        store={{ url: '/api/list', type: 'POST' }}
        remotePaging
        showQuickSearch
        quickSearchFields={[{ field: 'name', title: '名称' }]}
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
        title={editingRecord ? '编辑' : '新增'}
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
// import { Table, Select } from 'antd';
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
import { SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ead/suid-icons';

<SearchOutlined style={{ fontSize: 16, color: token.colorPrimary }} />
<SyncOutlined spin />
<SyncOutlined rotate={180} />
<LoadingOutlined />  {/* Loading 图标自动 spin */}
```

### 双色图标（Filled 图标）

```tsx
import { CheckCircleFilled, setTwoToneColor } from '@ead/suid-icons';

setTwoToneColor('#1890ff');                     // 全局设置主色
<CheckCircleFilled twoToneColor="#eb2f96" />    // 单个覆盖
<CheckCircleFilled twoToneColor={['#eb2f96', '#f5222d']} /> // 主色+辅色
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
