## 项目概述
使用 React 18 + TypeScript + Less，UI 组件库为 `@ead/suid`,使用suid skill进行前端页面开发

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Umi 4（含插件体系） |
| 状态管理 | dva（Umi `@umijs/plugins/dist/dva`） |
| 组件库 | `@ead/suid`（基于 Ant Design 的内部封装） |
| 工具函数 | `@ead/suid-utils-react`（含 request、useUserContext、qs、storage 等） |
| 样式 | `@ead/antd-style`（cssinjs）+ Less |
| 微前端 | qiankun slave 模式 |
| 国际化 | Umi locale 插件 + dayjs |
| 包管理 | pnpm（配置在 Umi `npmClient` 中，但实际依赖用 yarn 管理） |

## 常用命令

```bash
# 开发（直连后台）
yarn start

# 分析构建产物
yarn analyze

# ESLint 自动修复
yarn lint-fix:script

# Stylelint 检查
yarn lint:style

# Stylelint 自动修复
yarn lint-fix:style

# 全部 lint
yarn lint

# 格式化
yarn prettier
```

## 架构核心要点

### 路由体系

路由定义在 `config/router.config.ts`，采用嵌套结构：

- `/` → `AuthLayout`（需登录的页面）：`/dashboard`、`/demo`
- `/user` → `LoginLayout`（仅非生产环境）：`/user/login`

`AuthLayout` 检查 `currentUser` 是否为空，为空则重定向到登录页。生产环境直接渲染 `<Outlet />` 不做本地鉴权。

### 页面目录结构

每个页面菜单按如下标准结构组织（申请单页面除外，其结构后续单独约定）：

```
src/pages/
├── PageName/          # 页面菜单名称
│   ├── index.jsx      # 数据列表/页面主入口
│   ├── model.js       # dva 数据模型（使用 @ead/suid 的 dva）
│   ├── service.js     # API 服务层
│   └── components/    # 当前页面的子组件
```

- `index.jsx` — 页面主入口组件，负责数据展示与交互
- `model.js` — dva model，管理当前页面的状态与 effects
- `service.js` — 封装当前页面的 API 请求
- `components/` — 仅在当前页面范围内使用的子组件
- 新建文件统一用 `.js`/`.jsx`，不新建 `.ts`/`.tsx`（除非仅修改已有 TS 文件）

### 状态管理（dva）

使用 dva 进行全局状态管理，当前只有 `global` model（`src/models/global.ts`），包含：

- 路由位置追踪（`locationPathName`、`locationQuery`）
- 语言切换（`zh-CN` / `en-US`）
- 权限功能项获取（仅非生产环境）

### 使用前必读

**当使用 `@ead/suid` 中的任何组件时，使用suid技能进行确认是否符合规范，或参考其他页面的实现方式。**


### 组件选型规则（优先 SUID 扩展组件）

开发或生成 UI 时，按场景优先选用下表组件；**不得**在可适用场景下默认使用 antd 原生等价组件。

| 场景 | 优先组件 | 勿默认使用 | 说明 |
|------|----------|------------|------|
| 下拉列表（单选/多选、远程/静态选项） | `ComboList` | `Select` | 含搜索、回填、联动填字段等能力 |
| 下拉树（组织树、级联树选择） | `ComboTree` | `TreeSelect` | 含 quickSearch、节点回填等 |
| 表单输入金额 | `MoneyInput` | `InputNumber` | 金额精度、格式化由组件统一处理 |
| 表格/列表页展示金额 | `Money` | 纯文本 / 手动 `toFixed` | 列 `align: 'right'`，保持金额展示一致 |
| 数据列表/主表格 | `ExtTable` | `Table` | 默认：含搜索、筛选、分页、列设置等 |
| 表格/列表日期范围筛选 | `FilterDate` | `DatePicker.RangePicker` | 用于 ExtTable 工具栏等日期区间筛选，支持 presets |
| 表格/列表视图/状态筛选 | `FilterView` | `Select` / `Tabs` / `Segmented` | 工具栏枚举/状态快速筛选；**非**多条件 Form 搜索区 |
| 区域需要滚动条 | `Scrollbar` | 原生 `overflow: auto` | 页面、表单、表格容器默认可滚动区域 |
| 附件上传/管理 | `Attachment` | `Upload` | 含上传、下载、预览、业务实体绑定等能力 |

**`Table` 降级条件**（仅当 `ExtTable` 无法满足时使用原生 `Table`）：

- 嵌入 Modal/Drawer 内的极简只读小表，不需要 ExtTable 工具栏与 store 集成
- 纯展示型子表格，无分页/筛选/远程数据源
- ExtTable 的 props 约束与布局冲突（如固定高度嵌套、非标准数据源形态）

选用扩展组件前仍须查阅 API（见「使用前必读」）；`Select` / `TreeSelect` / `Table` / `InputNumber` / `DatePicker.RangePicker` / `Upload` 仅作为**明确不满足**上表场景时的降级方案。

### useUserContext

来自 `@ead/suid-utils-react`，提供用户上下文：

```ts
const { currentUser, setCurrentUser, setCurrentAuth, setSessionId, setCurrentPolicy, currentLocale } = useUserContext();
```
### 不需要提交测试文件
