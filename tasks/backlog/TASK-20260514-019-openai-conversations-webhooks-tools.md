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

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

