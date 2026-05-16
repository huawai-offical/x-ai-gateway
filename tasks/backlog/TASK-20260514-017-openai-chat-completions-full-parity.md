# TASK-20260514-017 OpenAI Chat Completions 全参数与对象生命周期

状态：Backlog  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-013](TASK-20260514-013-openai-chat-responses-native-parity.md)

## 背景

当前 Chat Completions DTO 只覆盖核心字段，无法声明全参数兼容。官方 Chat API 还包含 object retrieve/update/delete/messages、store、metadata、response_format、web_search_options、prediction、modalities、service_tier、prompt cache、stream options 等参数和对象语义。

## 目标

- 补齐 Chat Completions create 全参数保真、转换或明确拒绝。
- 补齐 stored chat completion 的 list/get/update/delete/messages 入口和权限边界。
- 统一 Chat streaming event、usage、tool_calls、response_format 和错误形态。

## 非目标

- 不实现 Responses 原生执行器。
- 不覆盖非 OpenAI Direct 的全量 Chat 参数承诺。

## 输入

- 官方 Chat Completions API Reference。
- `OpenAiChatCompletionRequest`、`OpenAiChatCompletionRequestMapper`、`OpenAiNativeGatewayChatRuntime`。

## 输出

- Chat 参数 parity matrix。
- 代码实现、降级策略和单元测试。
- public docs 与 SDK examples 更新。

## 影响范围

- `protocol/ingress/openai`、`gateway/core/execution`、`provider/adapter/openai`、conformance fixtures、public OpenAPI。

## 依赖

- `TASK-20260514-030` 的 headers/errors/idempotency 基线。
- Spring AI SDK 字段支持或自建 passthrough executor。

## 风险

- 直接透传未知字段可能破坏第三方兼容站点。
- stored chat completion 需要本地对象权限模型。

## 验收标准

- 官方 Chat create 参数逐项有 `pass-through/translated/rejected` 状态。
- Stored Chat lifecycle endpoint 可测试。
- 至少覆盖 15 个当前缺失参数的回归测试。

## 测试边界

- Mapper/runtime 单测。
- Controller WebFlux tests。
- OpenAI Direct 真实 smoke 可选，缺 key 时 skipped。

## 已完成切片

- [TASK-20260515-003 OpenAI Chat Completions Create 参数保真基线](../done/TASK-20260515-003-openai-chat-create-parameter-parity-foundation.md)：Chat create DTO 显式接收常用官方参数，mapper 写入 `providerExtensions`，native runtime 下发可由 Spring AI 原生承接的字段，并对复杂对象使用 `extraBody` flatten 保真。
- [TASK-20260515-004 OpenAI Stored Chat Completions 生命周期基线](../done/TASK-20260515-004-openai-stored-chat-lifecycle-foundation.md)：Chat create 的 `store=true` 会保存 `chatcmpl_` 本地资源，并补齐 list/get/update/delete/messages 入口、metadata update、软删除、基线分页和 WebFlux/service 回归。
- [TASK-20260515-005 OpenAI Chat legacy functions/function_call 语义兼容](../done/TASK-20260515-005-openai-chat-legacy-functions-tool-choice-compatibility.md)：deprecated `functions/function_call` 现在转换到 canonical tools/tool_choice，OpenAI Direct 不再重复下发 raw legacy 字段，同时修正 Spring AI FunctionTool name/description 参数顺序。
- [TASK-20260515-006 OpenAI Chat response_format 强类型映射](../done/TASK-20260515-006-openai-chat-response-format-typed-mapping.md)：Chat `response_format` 现在映射到 Spring AI `ResponseFormat`，覆盖 `text/json_object/json_schema`、schema/name/strict 保真、非法 type 前置 OpenAI-style 错误，以及 `extraBody` 重复字段清理。
- [TASK-20260515-007 OpenAI Chat modalities/audio/web_search_options 强类型映射](../done/TASK-20260515-007-openai-chat-modalities-audio-web-search-typed-mapping.md)：Chat `modalities`、`audio` 与 `web_search_options` 现在映射到 Spring AI typed request 字段，非法 enum/shape 前置 OpenAI-style 错误，并清理 `extraBody` 重复字段。
- [TASK-20260515-008 OpenAI Chat 参数兼容证明、公开文档与 SDK 示例](../done/TASK-20260515-008-openai-chat-conformance-docs-sdk-evidence.md)：新增 Chat 参数级 parity matrix，更新 public OpenAPI/docs bundle/SDK advanced example，并用文档测试锁定 `response_format`、`tools/tool_choice`、`store/metadata`、`modalities/audio`、`web_search_options` 等关键字段。
- [TASK-20260515-014 OpenAI Streaming Event Usage 与 Sequence 基线](../done/TASK-20260515-014-openai-streaming-event-usage-sequence-baseline.md)：Chat stream 支持 `stream_options.include_usage` usage chunk，同一 stream 内 chunk `id`/`created` 保持一致，并用 WebFlux 测试锁定。

## 剩余切片

- Chat create 主要复杂对象已完成 typed mapping，参数级 conformance/docs/SDK 证明也已补齐；剩余需要真实 smoke 证据。
- Stored Chat Completion 已有内存级 cursor/metadata filter 基线；大数据量下的数据库级 pagination、索引与 filter 优化仍需要联动 `TASK-20260514-030` 的 pagination 横切任务。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
