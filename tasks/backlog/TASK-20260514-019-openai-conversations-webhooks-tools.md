# TASK-20260514-019 OpenAI Conversations、Webhooks 与 Responses 工具生态

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-013](TASK-20260514-013-openai-chat-responses-native-parity.md)

## 背景

官方 Responses 生态不止 function tools，还包括 Conversations、Items、Webhooks、file_search、web_search、computer_use、code_interpreter、image_generation、MCP 和 custom tools。当前 mapper 会跳过非 function tools。

## 目标

- 覆盖 Conversations 与 Conversation Items lifecycle。
- 支持 Webhooks endpoint、event verify 和重放保护。
- 为 Responses tools 建立 tool registry、参数保真和执行边界。
- 支持 MCP/custom tools 的声明、路由与降级说明。

## 非目标

- 不实现 Vector Stores 具体存储，file_search 依赖 `TASK-20260514-023`。
- 不实现 Containers 文件执行，code_interpreter 依赖 `TASK-20260514-024`。

## 输入

- 官方 Responses、Conversations、Webhooks 文档。
- 当前 tool mapping、gateway workbench、request log。

## 输出

- Conversations/Webhooks controllers。
- Responses tools compatibility matrix。
- Tool registry 与 conformance tests。

## 影响范围

- Responses mapper、tool registry、webhook security、portal/admin 调试页、public docs。

## 依赖

- `TASK-20260514-018` Responses 原生 executor。
- `TASK-20260514-030` webhook signature 与 idempotency。

## 风险

- Webhook 验签和重放保护不完善会产生安全风险。
- Tool side effects 需要审计和隔离。

## 验收标准

- Conversations lifecycle 可测试。
- Webhook signature verify 和 duplicate delivery 防护可测试。
- 非 function tools 不再静默跳过，至少有 supported/rejected 状态。

## 测试边界

- Controller/service 单测。
- Webhook signature negative tests。
- Tool registry conformance tests。

## 已完成切片

- [TASK-20260515-013 OpenAI Webhook Signature 与 Replay 防护基线](../done/TASK-20260515-013-openai-webhook-signature-replay-baseline.md)：已建立可复用 verifier，支持 Standard Webhooks headers、`whsec_`/raw secret、timestamp tolerance 与 replay marker。
- [TASK-20260516-014 OpenAI Responses Tools Registry 与非 function Tool 显式边界](../done/TASK-20260516-014-openai-responses-tool-registry-boundary.md)：Responses `function` tools 保持可用；built-in/MCP/custom/shell/apply_patch 等非 function tools 和对应 `tool_choice` 已显式拒绝，不再静默跳过。
- [TASK-20260516-015 OpenAI Conversations 本地 Lifecycle](../done/TASK-20260516-015-openai-conversations-local-lifecycle.md)：`/v1/conversations` 与 `/v1/conversations/{conversationId}/items` 已建立 gateway local lineage lifecycle，支持 create/retrieve/update/delete、item list 与批量边界。
- [TASK-20260516-016 OpenAI Webhooks 接收入口与事件落库](../done/TASK-20260516-016-openai-webhooks-ingress-event-persistence.md)：`POST /v1/webhooks/openai` 已复用 Standard Webhooks verifier，以 raw body 验签，把合法 event 保存为 `WEBHOOK_EVENT`，并对重复 delivery 或重复 event id 幂等返回。

## 剩余切片

- Hosted/MCP/custom tools 真实执行：按 `TASK-20260514-023` Vector Stores、`TASK-20260514-024` Containers 与本任务 tool registry 的边界逐步放行。
- Webhooks 调试视图：如后续运营排查需要，可基于 `WEBHOOK_EVENT` 与 request log 补 portal/admin 查询视图；本父任务当前不把 UI 作为 Webhooks ingress 的闭环条件。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
