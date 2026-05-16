# REQ-20260514-009 OpenAI API 全量覆盖任务体系

## 背景

用户追问如果要完全彻底覆盖 OpenAI API，还需要怎么做，并要求补全 task、细分 task。上一轮审计已经确认：当前项目不是 OpenAI API 全量实现，也不是全量参数兼容；当前只是 OpenAI-compatible 核心 Chat/Responses 与部分官方资源生命周期的基础兼容。

要达到“完全彻底覆盖”，需要把 OpenAI API Reference 的资源族、对象生命周期、参数、headers、错误形态、streaming/realtime 事件、真实 smoke 和公开文档都纳入统一任务体系，不能只用一个笼统任务承接。

## 目标

- 建立 OpenAI API 全量覆盖父任务和可独立验收的子任务。
- 把 Chat/Responses、Conversations/Webhooks、Audio/Images/Videos、Files/Uploads/Batches、Fine-tuning、Vector Stores、Containers、Skills、Realtime、Administration、Legacy API、文档与 conformance 全部拆清楚。
- 保留“必须实现”“可声明 out-of-scope”“需要组织权限或真实 key 验证”的边界。
- 为后续逐个推进提供明确优先级、输入、输出、验收标准和测试边界。

## 非目标

- 不在本轮直接实现所有 OpenAI API。
- 不把第三方 OpenAI-compatible provider 等同于 OpenAI Direct 全量 API。
- 不默认向普通用户开放 OpenAI Administration API；该类 API 需要单独权限、审计和租户隔离。

## 覆盖范围

- 核心生成：Chat Completions、Responses、Conversations、Webhooks。
- 多模态：Audio、Images、Videos、Embeddings、Moderations。
- 对象生命周期：Files、Uploads、Batches、Models、Fine-tuning。
- 长上下文与工具资源：Vector Stores、Containers、Skills、Code Interpreter 文件。
- 实时能力：Realtime sessions、transcription sessions、client secrets、calls、WebRTC/WebSocket events。
- 管理与企业：Organization、Projects、Users、Service accounts、API keys、Rate limits、Usage、Costs、Audit logs、Certificates。
- Legacy：Assistants/Threads/Runs、Completions、Beta/preview 兼容面。
- 横切：认证与 headers、错误模型、idempotency、streaming event schema、OpenAPI、SDK examples、真实 smoke harness。

## 验收标准

- 新增父任务和细分子任务均为本地 Markdown task spec。
- 每个子任务都包含背景、目标、非目标、输入、输出、影响范围、依赖、风险、验收标准、测试边界和关联文档。
- `tasks/index.md` 能看到全量覆盖任务体系和优先级。
- 既有 `TASK-20260514-013`、`014`、`015` 不丢失，作为本体系的上游包任务继续关联。

## 状态

Done。已输出任务拆解报告并创建 OpenAI 全量覆盖父任务与细分子任务。

