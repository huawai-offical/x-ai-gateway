# REQ-20260514-007 主流 API parity 剩余 backlog 闭环

## 背景

`REP-20260514 主流厂商 API/changelog 复核` 已把官方文档中仍有差距的主流 API 参数拆成三个 High backlog：OpenAI/xAI Responses 字段保真、Anthropic MCP/service tier/container/context management 受控下发、Gemini thinkingConfig/toolConfig/URL context/Grounding parity。用户要求继续推进项目并依次办结所有 task，本轮以这三个剩余 backlog 为闭环范围。

## 目标

- 依次闭环 TASK-20260514-009、TASK-20260514-010、TASK-20260514-011。
- 对官方已稳定且 SDK/运行时可承接的参数实现受控下发或明确降级。
- 对涉及外部连接或高风险 provider-specific 能力的字段增加治理边界，避免静默透传。
- 完成单元测试、必要的编译验证，并回写任务状态。

## 非目标

- 不实现本地 MCP STDIO 到远程 MCP 的桥接。
- 不替代 OpenAI/xAI 原生 Responses 全量 HTTP passthrough 执行器；本轮在现有 native/chat runtime 中做字段保真和可解释降级。
- 不实现 Gemini Live/Interactions API 的完整替代。

## 任务范围

- [TASK-20260514-009 OpenAI/xAI Responses 字段 parity 与 cache/header 保真](../../tasks/done/TASK-20260514-009-openai-xai-responses-field-parity.md)
- [TASK-20260514-010 Anthropic MCP/service tier/container/context management 字段下发](../../tasks/done/TASK-20260514-010-anthropic-mcp-service-tier-field-parity.md)
- [TASK-20260514-011 Gemini thinkingConfig/toolConfig/URL context/Grounding 参数 parity](../../tasks/done/TASK-20260514-011-gemini-thinking-toolconfig-grounding-parity.md)

## 验收标准

- OpenAI/xAI Responses 的 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`top_logprobs`、`truncation`、`text` 等字段不会静默丢失；无法由 Chat-compatible runtime 原生承接的字段进入 extra body 或降级说明。
- Anthropic 的 `service_tier`、`container`、`metadata`、`context_management`、`mcp_servers` 有受控下发；`mcp_servers` 需要 allowlist 或显式治理开关。
- Gemini 的 `thinkingConfig`、`toolConfig.functionCallingConfig`、URL context、Google Search grounding 可由 native runtime 解析和下发；未授权高风险工具有明确错误。
- 三个任务均有测试记录，完成后移动到 `tasks/done/`。

## 风险

- 主流 SDK 对前沿字段的类型支持不完全；需要优先使用 SDK 已公开 builder/fromJson 能力，无法覆盖时使用可解释降级。
- MCP/grounding 会触发外部网络访问，必须设置显式治理边界。
- 现有工作区已有大量未提交文件，本轮只处理相关文件，不回退无关变更。

## 实现结果

- OpenAI/xAI：Responses ingress 继续保留原始请求字段，Native OpenAI-compatible runtime 下发 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`top_logprobs`、`safety_identifier`、`verbosity` 与 metadata；Responses-only 字段进入 extra body；xAI/Grok 请求按 `prompt_cache_key` 生成 `x-grok-conv-id`。
- Anthropic：Messages ingress 支持受控 `mcp_servers`，要求 allowlist 或显式治理开关；Native runtime 下发 `service_tier`、`container`、`metadata`、`context_management` 与 `mcp_servers`，并自动合并 MCP beta header。
- Gemini：generateContent ingress 捕获 `generationConfig.thinkingConfig` 到 canonical reasoning；Native runtime 通过 SDK `fromJson` 保留 `thinkingConfig`、`toolConfig.functionCallingConfig`、`googleSearch`、`urlContext` 等官方字段；`googleMaps` 默认要求显式允许。

## 验证结果

已通过聚焦回归：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.AnthropicNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests"
```

验证结论：OpenAI/xAI、Anthropic、Gemini 三个 High backlog 均已闭环；未执行全量 `test`，本轮只运行相关 mapper/runtime/controller/interop 聚焦套件。

## 状态

Done。
