# SUID 业务扩展组件 (ext-suid)

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
| `rowKey` | 数据唯一键 | `string` | `'id'` |
| `toolbar` | 工具栏配置 `{ left, right }` | `Toolbar` | - |
| `header` | 表格容器头部（BannerTitle配置） | `ExtTableHead` | - |
| `showQuickSearch` | 是否显示快速搜索框 | `boolean` | `true` |
| `quickSearchFields` | 搜索字段，`['*']` 全字段 | `string[] \| SearchProperty[]` | - |
| `quickSearchWidth` | 搜索框宽度（toolbar的left定义后才生效） | `number` | - |
| `storageCfg` | 个性化存储配置 | `StorageCfg` | - |
| `sort` | 排序配置 `{ fields: [{name, sortOrder}] }` | `Sort` | - |
| `cascade` | 级联参数（附加请求参数） | `Record<string, any>` | - |
| `pagination` | 分页配置 | `boolean \| ExtTablePagination` | - |
| `size` | 尺寸 | `'middle' \| 'small' \| 'large'` | `'middle'` |

- **ExtColumnProps 扩展字段**:

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `width` | 列宽 | `80` |
| `expandUnusedSpace` | 占满表格剩余空间（设为true后不可调整列宽） | - |
| `hidden` | 是否隐藏 | - |
| `notExport` | 导出时不包含此列 | - |
| `dataType` | 数据类型：`text/date/datetime/number/year/month/boolean` | `'text'` |
| `titleAlias` | 列标题别名（title非string时需设置） | - |

- **storageCfg 配置**:

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `storageType` | `'local' \| 'remote'` | `'remote'` |
| `dataView` | 启用数据视图存储 | - |
| `rowColumn` | 启用表格行列存储 | - |
| `storageId` | 全局唯一ID（不设则自动hash） | - |

- **示例**:
```tsx
import { ExtTable } from '@ead/suid';
import type { ExtTableProps } from '@ead/suid';
import { StoreOption } from '@ead/suid-utils-react';
import { ReloadOutlined } from '@ead/suid-icons';

const columns: ExtTableProps<DataType>['columns'] = [
  { title: '名称', dataIndex: 'name', expandUnusedSpace: true },
  { title: '状态', dataIndex: 'status', width: 100 },
];

<ExtTable
  columns={columns}
  store={{ url: '/api/list', type: 'POST' }}
  remotePaging
  quickSearchFields={['name', 'code']}
  toolbar={{
    left: <Button>新增</Button>,
    right: <Button icon={<ReloadOutlined />} />,
  }}
  storageCfg={{ storageType: 'local', rowColumn: true }}
  sort={{ fields: [{ name: 'createTime', sortOrder: 'desc' }] }}
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
| `rowKey` | 数据唯一键 | `string` | `'id'` |
| `multiple` | 是否多选 | `boolean` | - |
| `remotePaging` | 是否远程分页 | `boolean` | - |
| `extFields` | 扩展字段（从 onChange ext 中获取） | `string[]` | - |
| `cascade` | 级联条件配置 | `Cascade` | - |
| `quickSearchFields` | 搜索字段 | `string[]` | `['code','name']` |
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
import { ComboList, Form } from '@ead/suid';
import { StoreOption } from '@ead/suid-utils-react';

// 本地数据
<ComboList
  rowKey="code"
  dataSource={[{ name: '北京', code: '001' }]}
  quickSearchFields={['name']}
  reader={{ textField: 'name', descField: 'code' }}
  onChange={(value, ext) => {}}
/>

// 远程接口 + 扩展字段 + 级联
<ComboList
  rowKey="code"
  store={{ url: '/api/users', type: 'POST' }}
  extFields={['userCode']}
  multiple
  remotePaging
  cascade={{
    fields: [{ formItemName: 'deptId', valueField: 'deptCode' }],
    params: { corpCode: 'Q000' },
  }}
  reader={{ textField: (u) => `${u.userName}-${u.code}`, extFields: ['code'] }}
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
- **关键 Props**: `precision`(默认2), `precisionType`('round'|'floor'|'ceil'), `thousand`(千分位), `textAlign`('left'|'center'|'right'), `selectMode`

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
| `reader` | 读取器 `{ title: 'title', value: 'key' }` | `FilterViewReader` | - |
| `labelTitle` | 内置标题 | `string` | - |
| `rowKey` | 唯一键属性 | `string` | `'key'` |
| `defaultValue` | 默认选中值（number为索引，string为rowKey值） | `number[] \| string[]` | - |
| `selectedKeys` | 受控选中值 | `string[]` | - |
| `multiple` | 是否多选 | `boolean` | - |
| `allowClear` | 允许清除 | `boolean` | `false` |
| `listBeforeExtra` | 列表顶部额外内容 | `ReactNode` | - |
| `listAfterExtra` | 列表底部额外内容 | `ReactNode` | - |
| `onChange` | 值变化回调 | `(value?: any) => void` | - |

```tsx
<FilterView
  dataSource={[{ title: '全部', key: 'ALL' }, { title: '草稿', key: 'INIT' }]}
  labelTitle="审批状态"
  defaultValue={['ALL']}
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
| `roundType` | 舍入类型 | `'round' \| 'ceil'` | `'round'` |
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
- **关键 Props**: `invoiceType`('G'普通|'E'电子|'FE'全电), `items`, `totalMoney`, `buyerName`, `sellerName`

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
| `actionType` | 显示类型 | `'icon' \| 'title' \| 'both'` | `'icon'` |
| `color` | 颜色 | `'default' \| 'primary' \| 'danger' \| PresetColors` | `'default'` |
| `variant` | 样式 | `'filled' \| 'text' \| 'link'` | `'text'` |
| `disabled` | 禁用 | `boolean` | - |
| `ignoreGlobal` | 忽略全局 ConfigProvider 的 actionButton 配置 | `boolean` | - |

- **全局控制**: 通过 `ConfigProvider` 的 `actionButton={{ actionType }}` 统一控制显示类型

```tsx
import { ActionButton } from '@ead/suid';
import { EditOutlined, DeleteOutlined } from '@ead/suid-icons';

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
import { AuthAction, Button } from '@ead/suid';
import type { IAuthActionItem } from '@ead/suid';

// 推荐：items 写法
const items: IAuthActionItem[] = [
  { authCode: 'CREATE', children: <Button>创建</Button> },
  { authCode: 'DELETE', children: <Button danger>删除</Button> },
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
