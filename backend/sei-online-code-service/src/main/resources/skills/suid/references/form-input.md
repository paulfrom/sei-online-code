# SUID 表单输入组件

> 导入: `import { X } from '@ead/suid'`
> 完整 API 参考: `components/<组件名>/index.en-US.md`

## 文本输入

### Input
基础文本输入框，支持前后缀、清除、密码、搜索、OTP 等变体
- **何时选用**: 文本输入场景
- **关键 Props**: `value`, `onChange`, `allowClear`, `disabled`, `variant`('outlined'|'borderless'|'filled'|'underlined')
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
- **关键 Props**: `value`, `options`, `onChange`, `mode`('multiple'|'tags'), `showSearch`
- **子组件**: `Select.Option`, `Select.OptGroup`
- **示例**:
```tsx
<Select
  options={[{ label: 'A', value: 'a' }, { label: 'B', value: 'b' }]}
  showSearch
  onChange={handleChange}
/>
```

### Radio
单选框，支持按钮样式
- **何时选用**: 选项数量 < 5 的单选场景
- **关键 Props**: `value`, `options`, `onChange`, `optionType`('default'|'button')
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
- **关键 Props**: `value`, `picker`('date'|'week'|'month'|'quarter'|'year'), `format`, `onChange`, `disabled`
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
- **关键 Props**: `action`, `fileList`, `beforeUpload`, `listType`('text'|'picture'|'picture-card'|'picture-circle'), `customRequest`
- **示例**:
```tsx
<Upload action="/api/upload" listType="picture-card">
  <Button icon={<UploadOutlined />}>上传</Button>
</Upload>
```

### ColorPicker
颜色选择器
- **何时选用**: 需要选择颜色
- **关键 Props**: `value`, `mode`('single'|'gradient'), `format`, `presets`, `onChange`

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
