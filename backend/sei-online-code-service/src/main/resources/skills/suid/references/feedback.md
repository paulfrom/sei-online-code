# SUID 反馈交互组件

> 导入: `import { X } from '@ead/suid'`
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
- **关键 Props**: `open`, `placement`('top'|'right'|'bottom'|'left'), `title`, `onClose`, `size`
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
messageApi.success('操作成功');
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
- **关键 Props**: `content`, `title`, `trigger`('hover'|'click'), `placement`, `onOpenChange`

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
- **关键 Props**: `type`('success'|'info'|'warning'|'error'), `title`, `description`, `closable`, `showIcon`
- **子组件**: `Alert.ErrorBoundary`

### Result
结果页
- **何时选用**: 操作完成后的结果反馈
- **关键 Props**: `status`('success'|'error'|'info'|'warning'|'404'|'403'|'500'), `title`, `subTitle`, `extra`, `icon`
