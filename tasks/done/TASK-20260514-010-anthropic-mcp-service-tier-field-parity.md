# TASK-20260514-010 Anthropic MCP/service tier/container/context management 字段下发

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-006](../done/TASK-20260514-006-community-home-api-docs-refresh.md)  
上游来源：[REP-20260514 主流厂商 API/changelog 复核](../../docs/reports/REP-20260514-mainstream-api-changelog-refresh.md)、[REQ-20260514-007](../../docs/requirements/REQ-20260514-007-mainstream-api-parity-backlog-closure.md)

## 本轮设计

- `service_tier`、`container`、`metadata` 走 Anthropic SDK 一等字段。
- `context_management` 和 `mcp_servers` 走 SDK additional body properties，并在需要 MCP 时补 `anthropic-beta: mcp-client-2025-04-04`。
- `mcp_servers` 默认要求请求内提供 `x_ai_gateway_mcp_allowlist` 或 `x_ai_gateway_allow_mcp_servers=true`，避免社区用户任意注入远程 MCP server。

## 背景

Anthropic Messages API 已把 `service_tier`、MCP connector、Files API、extended thinking、`container` 与 `context_management` 等能力前移。当前项目本轮已补官方 snake_case、`thinking` capture、Native `tool_choice` 与 thinking budget，但 MCP/service tier 等扩展字段还没有形成受控下发与 beta header 策略。

## 目标

- 明确 Anthropic 扩展字段的支持等级、beta header 依赖和安全边界。
- 对 `service_tier`、`mcp_servers`、`container`、`context_management`、`metadata` 建立受控 pass-through。
- 为 MCP connector 增加 allowlist/denylist 与审计字段，避免社区用户任意注入外部 server。

## 非目标

- 不实现本地 STDIO MCP 转远程 MCP。
- 不把 Anthropic MCP connector 泛化到所有 provider。

## 输入

- Anthropic Messages API、release notes、MCP connector、service tiers 官方文档。
- 当前 `AnthropicMessagesRequest`、`AnthropicMessagesRequestMapper`、`AnthropicNativeGatewayChatRuntime`。

## 输出

- Anthropic 扩展字段治理策略与实现。
- beta header 配置化能力。
- Mapper/runtime/conformance 测试。

## 验收标准

- 支持字段可下发、不可支持字段可解释降级。
- MCP server 必须通过管理端配置或 allowlist，不允许无治理透传。
- service tier 与 usage 返回在日志/trace 中可观测。

## 测试边界

- Java mapper/runtime 单测。
- 安全策略单测。
- 缺真实 Anthropic key 时 smoke 分类 SKIPPED。

## 完成结果

- `AnthropicMessagesRequest` 与 mapper 支持 `x_ai_gateway_mcp_allowlist`、`x_ai_gateway_allow_mcp_servers` 和 `anthropic-beta` header。
- `mcp_servers` 默认要求 allowlist 或显式治理开关；server URL 必须是 HTTP(S)，并按 host 或完整 URL 匹配 allowlist。
- `AnthropicNativeGatewayChatRuntime` 下发 `service_tier`、`container`、`metadata`、`context_management` 与 `mcp_servers`，并自动合并 `anthropic-beta: mcp-client-2025-04-04`。

## 验证记录

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.AnthropicNativeGatewayChatRuntimeTests"
```

结果：通过。
