# 云端 Request Filter 结构化规则

日期：2026-05-06  
关联需求：[REQ-20260506-016 云端 Request Filter 高级规则、审计与 UI](requirements/REQ-20260506-016-cloud-request-filter-audit-ui.md)  
关联任务：[TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI](../tasks/done/TASK-20260506-020-cloud-request-filter-audit-ui.md)

## 背景

CLI/IDE 云端代理接入后，request filter 不应只处理 canonical message text。真实运行中还需要处理 `providerExtensions`、tool schema、file/image metadata 等结构化字段，避免 secret、内部字段或不兼容 schema 直接透传到上游 provider。

## 规则模型

规则由 `CloudCliRequestFilterRule` 表示，核心字段如下：

| 字段 | 说明 |
| --- | --- |
| `ruleId` | 规则唯一标识，命中审计会记录该值。 |
| `action` | 支持 `REPLACE`、`REMOVE`、`MASK`、`REDACT`、`DENY`。 |
| `clientFamilies` | 可选客户端族限制，例如 `cursor`、`codex`、`gemini_cli`。 |
| `role` | 仅文本消息过滤使用，可限制 message role。 |
| `contains` | 文本或结构化字段命中条件。 |
| `replacement` | `REPLACE` 使用的替换值。 |
| `target` | 过滤目标，默认 `message_text`。 |
| `path` | 结构化目标路径，支持点路径与 `[*]` 数组通配。 |

## 过滤目标

| target | path 示例 | 说明 |
| --- | --- | --- |
| `message_text` | 空 | 过滤 canonical chat message text，兼容既有规则。 |
| `provider_extensions` | `$.metadata.api_key` | 过滤 `providerExtensions` 内结构化字段。 |
| `json_path` | `$.providerExtensions.metadata.api_key` | 从规范化请求体根节点执行受控点路径。 |
| `tool_schema` | `$.properties.apiKey.description` | 对每个 tool 的 `inputSchema` 执行路径过滤。 |
| `file_metadata` | `name`、`mime_type`、`uri` | 过滤 file/image content item 的 metadata。 |

## 审计字段

运行结果由 `CloudCliRequestFilterResult` 返回：

| 字段 | 说明 |
| --- | --- |
| `appliedRuleIds` | 实际执行并改变请求或拒绝请求的规则。 |
| `skippedRuleIds` | client family、路径或条件不匹配而跳过的规则。 |
| `hits` | 命中摘要，包含 `ruleId`、`action`、`target`、`path` 和脱敏 `summary`。 |
| `denied` | 是否被 `DENY` 规则拒绝。 |
| `denyRuleId` | 拒绝请求的规则 id。 |
| `denyReason` | 拒绝原因摘要，不包含原始 secret。 |

`GatewayChatExecutionService` 会把命中摘要写入 route body 的 `x_ai_gateway_filter` metadata，供 request trace、system event 和 Admin UI 后续读取。

## 安全边界

- 命中摘要只记录目标、路径和动作，不保存原始敏感值。
- `DENY` 命中后立即停止后续规则处理并拒绝转发。
- 结构化路径只支持受控点路径和 `[*]` 通配，未引入完整 JSONPath 表达式执行能力。
- 本功能用于云端代理治理和兼容性处理，不用于绕过 provider 自身安全策略。
