# SUID 导航与布局组件

> 导入: `import { X } from '@ead/suid'`
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
- **导入**: `import { Row, Col } from '@ead/suid'` 或 `import { Grid } from '@ead/suid'`
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
- **关键 Props**: `orientation`('horizontal'|'vertical'), `onResize`, `onResizeEnd`, `lazy`
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
- **关键 Props**: `size`, `orientation`('vertical'|'horizontal'), `align`, `wrap`, `separator`
- **子组件**: `Space.Compact`

## 导航

### Menu
菜单导航
- **何时选用**: 侧边栏/顶栏导航菜单
- **关键 Props**: `items`, `mode`('vertical'|'horizontal'|'inline'), `selectedKeys`, `openKeys`, `theme`
- **关键 Events**: `onClick`, `onSelect`, `onOpenChange`

### Tabs
标签页
- **何时选用**: 内容分区切换
- **关键 Props**: `activeKey`, `items`, `type`('line'|'card'|'editable-card'), `tabPlacement`, `onChange`
- **关键 Slots**: `tabBarExtraContent`(left/right)

### Breadcrumb
面包屑导航
- **何时选用**: 需要展示当前页面在导航层级中的位置
- **关键 Props**: `items`, `separator`, `itemRender`

### Anchor
锚点导航
- **何时选用**: 页面内段落快速跳转
- **关键 Props**: `items`, `affix`, `offsetTop`, `direction`('vertical'|'horizontal')

### Steps
步骤条
- **何时选用**: 引导用户完成分步任务
- **关键 Props**: `current`, `items`, `status`, `type`('default'|'dot'|'inline'|'navigation'|'panel'), `orientation`

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
- **关键 Props**: `orientation`('horizontal'|'vertical'), `titlePlacement`, `variant`('dashed'|'dotted'|'solid')`

### Affix
固钉
- **何时选用**: 需要元素在滚动时固定
- **关键 Props**: `offsetTop`, `offsetBottom`, `target`, `onChange`
