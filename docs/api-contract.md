# 公共契约补充说明（v1.0）

本文件补充《实施方案》第 5 节没有写死、但实现必须一致处理的细节。类型与字段以 `campus-api` 为准。

## 错误码的使用范围

| 错误码 | 产生方 | 场景 |
|---|---|---|
| `SERVICE_NOT_FOUND` | 核心 | 注册中心没有该服务 ID |
| `SERVICE_DISABLED` | 核心 | 配置中该服务被禁用 |
| `INVALID_PARAMETER` | 核心或插件 | 请求非法、缺少有效查询条件、参数值非法 |
| `NO_MATCH` | 暂不使用 | 无匹配一律返回 `success=true` + 空 `items`（见 §5.4），该码保留但当前无场景 |
| `DATA_LOAD_FAILED` | 插件 | 数据文件缺失、表头错误或数据行格式错误 |
| `PLUGIN_LOAD_FAILED` | 核心 | 插件 JAR 无法加载，记录诊断信息，不影响其他插件 |
| `PLUGIN_EXECUTION_FAILED` | 核心 | 插件抛异常，或返回不符合下方"结果契约"的结果 |
| `INTERNAL_ERROR` | 核心 | 元信息读取失败、注册中心中的 ID 与元信息不一致 |

## 未知参数

请求中出现元信息未声明的参数时，插件**可以拒绝**（返回 `INVALID_PARAMETER`），**也可以忽略**。核心不校验参数集合，采用哪种策略必须写在该插件的 README 中。课程插件当前采用"拒绝"。

## "至少一个条件"

`QueryParameter.required` 只能表达单个参数是否必填，无法表达"多个参数至少提供一个"。这类规则由插件在无有效条件时返回 `INVALID_PARAMETER` 表达，提示文案写在 `message` 中；CLI 不得据此拒绝调用。

## 校验优先级

插件先校验请求，再检查数据可用性：请求本身非法时返回 `INVALID_PARAMETER`，即使数据文件同时不可用。

## 结果契约（核心强制校验）

核心在转发结果前检查，违反任一条即转换为 `PLUGIN_EXECUTION_FAILED`：

- `items` 不为 `null`；无匹配时为空列表且 `success=true`。
- `success=true` 时 `error` 必须为 `null`；`success=false` 时 `error` 不为 `null`。
- `items` 中每个元素的 `title` 非空、`fields` 非 `null`。
- 返回值的 `serviceId` 必须等于请求中的 `serviceId`。

## 版本兼容

`apiVersion` 形如 `主.次`（例如 `1.0`）。核心只接受主版本 `1`；主版本不符的插件在注册时被 `ServiceRegistry` 拒绝（`IllegalArgumentException`，含清晰原因），由调用方转为 `PLUGIN_LOAD_FAILED` 诊断。次版本变化视为兼容。

## 展示标签

`ResultItem.fields` 的键目前是英文字段名（如 `teacher`、`time`、`location`），契约中没有标签来源，CLI 无法通用地渲染"教师：…"。v1 的临时做法：CLI 对已知服务做字段名映射，未知插件按原始键输出。后续版本考虑在 `ResultItem` 中增加标签字段（属于不兼容变更，需提升主版本）。

## 服务启禁用

是否启用由外部配置决定，核心通过 `Predicate<String>` 注入调度器；插件自身不感知启禁用，也不应读取配置。
