# SUID 通用基础组件

> 导入: `import { X } from '@ead/suid'`
> 完整 API 参考: `https://sei.changhong.com/suid-react-v2/components/<组件名>`

## 图标使用
图标**必须**从 `@ead/suid-icons` 导入，禁止从 `@ead/suid` 或 `@ant-design/icons` 导入：
```tsx
import { SearchOutlined, PlusOutlined } from '@ead/suid-icons';
<SearchOutlined spin style={{ fontSize: 16, color: '#1890ff' }} />
```
- **关键 Props**: `spin`, `rotate`, `style`(`fontSize`,`color`), `component`(自定义SVG)

## CSS-in-JS（createStyles）
样式使用 `@ead/antd-style` 的 `createStyles`，基于 antd-style：
```tsx
import { createStyles } from '@ead/antd-style';

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
- **关键 Props**: `type`('primary'|'dashed'|'link'|'text'|'default'), `variant`, `color`, `size`('large'|'middle'|'small'), `loading`, `icon`, `disabled`, `danger`, `onClick`
- **示例**:
```tsx
<Button type="primary" loading={submitting} onClick={handleSubmit}>提交</Button>
<Button danger type="primary" icon={<DeleteOutlined />}>删除</Button>
```

## ConfigProvider
全局配置，支持主题、语言、方向、尺寸、actionButton 统一配置
- **关键 Props**: `theme`, `locale`, `direction`('ltr'|'rtl'), `componentSize`, `variant`('outlined'|'filled'|'borderless'), `actionButton`(`{ actionType: 'icon'|'title'|'both' }`)
- **示例**:
```tsx
<ConfigProvider
  theme={{ token: { colorPrimary: '#1890ff' } }}
  componentSize="middle"
  actionButton={{ actionType: 'icon' }}
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
import { App } from '@ead/suid';

// 根组件
<ConfigProvider><App><MainContent /></App></ConfigProvider>

// 子组件中使用
function MainContent() {
  const { message, modal } = App.useApp();
  const handleClick = () => message.success('操作成功');
}
```

## Calendar
日历
- **关键 Props**: `value`, `mode`('month'|'year'), `cellRender`, `fullscreen`, `disabledDate`
- **关键 Events**: `onChange`, `onPanelChange`, `onSelect`

## Collapse
折叠面板
- **关键 Props**: `activeKey`, `accordion`, `items`, `collapsible`('header'|'icon'|'disabled'), `ghost`
- **关键 Events**: `onChange`
