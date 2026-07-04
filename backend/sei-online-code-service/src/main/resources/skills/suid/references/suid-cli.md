# Sui Design CLI

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
