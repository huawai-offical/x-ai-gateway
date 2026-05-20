# ADR-0010 对话与 Tools 功能性服务 API 作为产品范围

状态：Accepted
日期：2026-05-18

## 背景

项目此前为了评估 OpenAI Direct 能力，曾按 OpenAI 官方 API Reference 拆出全量覆盖任务。该路线会把 Fine-tuning、Batches、Evals、Administration、Videos、provider-specific batch/admin/fine-tuning 等能力都纳入 backlog。用户已经明确收窄产品目标：x-ai-gateway 的核心理念不是全量 API 覆盖，而是提供对话、streaming、tools/function calling、多模态输入输出，以及直接支撑这些能力的功能性服务 API；OpenAI、Anthropic、Gemini、Vertex、Codex 和其它主流 AI provider 都不追求全量官方 API。

## 决策

从 2026-05-18 起，x-ai-gateway 的公开 provider 兼容目标改为“对话、工具与多模态功能性服务 API”：

- 核心范围：Chat/Responses/Messages/GenerateContent、streaming、tools/function calling、多模态输入输出、model discovery、必要的文件/RAG/file_search 支撑、认证、错误模型、限流、审计、usage 和真实 smoke。
- 可保留范围：已经支撑对话/tools 的本地 Vector Store、file attachment、local ingestion、local search、Responses `file_search` 绑定。
- 范围外：Fine-tuning、Batches、Evals/Graders/Runs、官方 provider Administration API、provider-specific batch prediction/message batches、官方 Videos parity、Skills 分发 API、官方 Containers 全量 lifecycle，除非后续被单独产品化并重新建需求和任务。
- 跨 provider 收紧：Anthropic、Gemini、Vertex、Codex 统一按 OpenAI 标准功能区执行，只保留 Messages/GenerateContent/Responses、tools、embeddings/files 等可映射支撑面；Vertex 的 project/location 是寻址和凭证边界，Codex 只保留 Responses smoke/反代边界。
- 内部平台管理 API 不受影响：`/admin/*` 是 x-ai-gateway 自身运营管理面，不等同于 OpenAI/Anthropic/Gemini 官方 Admin API。

## 影响

- `TASK-20260514-016` 不再代表 OpenAI API 全量覆盖，而应收窄为对话、streaming、tools 与多模态功能性服务范围总控。
- Fine-tuning、Batches、Evals、Administration 等 backlog 任务应删除或改为范围外记录。
- Public docs、provider catalog、OpenAPI 和 conformance fixture 不应继续宣称这些非核心 API 是支持面或未来必做项。
- Smoke harness 的真实验证优先服务 Chat/Responses/Messages/GenerateContent/tools/RAG；Codex 只验证 Responses 标准区，不再为非核心官方 API 或 provider 内部 API 保留预算。

## 后果

- 产品面更小、更容易验证，也更贴近现有客户接入价值。
- 与官方 provider SDK 的全量资源兼容性会降低，需要在文档中明确“不是全量官方 API 替代”。
- 如果未来客户确实需要 Fine-tuning、Batches、Evals 或 Admin API，需要单独建需求、风险评估和任务树。
