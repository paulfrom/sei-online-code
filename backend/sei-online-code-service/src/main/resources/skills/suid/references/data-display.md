# SUID 数据展示组件

> 导入: `import { X } from '@ead/suid'`
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
- **关键 Props**: `title`, `actions`, `cover`, `extra`, `variant`('outlined'|'borderless')
- **子组件**: `Card.Grid`, `Card.Meta`(`avatar`,`title`,`description`)

### Carousel
轮播/走马灯
- **何时选用**: 需要循环展示内容
- **关键 Props**: `autoplay`, `effect`('scrollx'|'fade'), `dots`, `arrows`

## 标签/徽标

### Tag
标签标记
- **何时选用**: 需要标记和分类
- **关键 Props**: `color`, `closable`/`closeIcon`, `icon`, `variant`('filled'|'solid'|'outlined'), `disabled`
- **子组件**: `Tag.CheckableTag`, `Tag.CheckableTagGroup`

### Badge
徽标数字/状态点
- **何时选用**: 需要在元素旁显示数量或状态
- **关键 Props**: `count`, `dot`, `overflowCount`, `status`, `color`
- **子组件**: `Badge.Ribbon`

### Avatar
头像
- **何时选用**: 需要展示用户/实体头像
- **关键 Props**: `src`, `size`, `shape`('circle'|'square'), `icon`, `onError`
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
- **关键 Props**: `percent`, `type`('line'|'circle'|'dashboard'), `status`, `showInfo`, `strokeColor`

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
- **关键 Props**: `items`, `mode`('start'|'alternate'|'end'), `orientation`('vertical'|'horizontal'), `variant`

## 图片/二维码

### Image
图片预览
- **何时选用**: 需要可预览的图片展示
- **关键 Props**: `src`, `preview`, `fallback`, `placeholder`, `width`/`height`
- **子组件**: `Image.PreviewGroup`

### QRCode
二维码
- **何时选用**: 需要生成二维码
- **关键 Props**: `value`, `type`('canvas'|'svg'), `size`, `icon`, `errorLevel`

## 其他

### Empty
空状态
- **何时选用**: 无数据时的占位展示
- **关键 Props**: `description`, `image`, `imageStyle`

### Typography
排版文字，支持复制、编辑、省略
- **何时选用**: 文字展示，需要复制/编辑/省略等功能
- **关键 Props**: `copyable`, `editable`, `ellipsis`, `type`('secondary'|'success'|'warning'|'danger')
- **子组件**: `Typography.Text`, `Typography.Title`(`level` 1-5), `Typography.Paragraph`
