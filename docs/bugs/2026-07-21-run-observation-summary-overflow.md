# Run Observation 摘要超长写入失败

## 现象

Agent 返回较长执行摘要后，写入 `oc_run_observation` 报错：

```text
value too long for type character varying(500)
```

## 根因

`oc_run_observation.summary` 的数据库与实体长度均为 500，但统一写入入口
`ProgressService.appendObservation()` 原样保存调用方传入的摘要。自动执行结果、失败原因或人工备注
超过限制时，异常直到数据库 flush 阶段才暴露。

## 修复

在 `ProgressService.appendObservation()` 持久化前统一将 `summary` 截断到最多 500 个 Unicode
code point。使用 `String.offsetByCodePoints()` 计算截断位置，避免按 UTF-16 code unit 截断时破坏
emoji 等非 BMP 字符的代理对。`null` 和未超长字符串保持不变。

## 验证

- 回归用例使用 499 个 ASCII 字符、1 个 emoji 和尾随文本构造超长摘要。
- 验证保存值恰好包含 500 个 Unicode code point，并完整保留边界处 emoji。
- 临时撤回截断调用后用例按预期失败，恢复修复后 `ProgressServiceTest` 通过。

## 预防

数据库受限字段应在最靠近持久化的统一服务入口执行最终边界保护。涉及用户可见 Unicode 文本时，
长度限制应明确按 code point 处理，不能直接把 Java `String.length()` 当作字符数量。
