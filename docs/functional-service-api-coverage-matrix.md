# 功能性服务 API Coverage Matrix

状态：Draft
日期：2026-05-19
关联任务：[TASK-20260514-029-01](../tasks/done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md)

## 事实源

机器可读源文件：

```text
src/main/resources/functional-service-api-coverage-matrix.json
```

该矩阵只描述 x-ai-gateway 当前产品范围：对话、streaming、tools/function calling、多模态输入输出，以及直接支撑这些能力的模型发现、RAG/file_search、认证、限流、审计、usage 和 smoke。它不是 OpenAI、Anthropic、Gemini、Vertex 或 Codex 官方 API 全量覆盖清单。

## 当前分类

| 分类 | 含义 | 示例 |
| --- | --- | --- |
| `core` | 对话和工具主链能力 | Chat Completions、Responses、Messages、GenerateContent、streaming、function tools |
| `supporting` | 为对话/tools 提供上下文、文件、检索或状态支撑 | Files、Uploads、Vector Stores、file_search、embeddings、Conversations、Webhooks、Realtime client secret 基线 |
| `governance` | gateway 自身运营治理能力 | model discovery、credential smoke、usage budget guard、audit |
| `out_of_scope` | 不进入公开兼容承诺 | Fine-tuning、Batches、Evals、Admin、provider-specific batch/job/pipeline、非 Responses Codex 内部 API |

## Provider 边界

- OpenAI：保留 Chat、Responses、streaming、function tools、多模态、Files/Uploads、Vector Stores/file_search、本地 Conversations、OpenAI Webhooks、Realtime client secret 基线、models 与治理支撑。
- OpenAI-compatible：MiMo 等 compatible key 只作为 chat、streaming、function tools 的功能性验证来源，不推导 OpenAI Direct Files、Uploads、Realtime、Batches 等 object lifecycle。
- Anthropic：保留 Claude Messages、streaming、tool_use/thinking；不保留 Anthropic message batches、admin/eval 等 provider-specific 非核心 API。
- Gemini：保留 generateContent、streamGenerateContent、function calling、embeddings/files 支撑；不保留 batch prediction、tuning、pipeline/job/admin。
- Vertex：保留与 Gemini 对话和支撑面等价的 generateContent、embeddings/files；project/location 只是寻址和凭证边界，不扩展为 Vertex AI Platform 全量 API。
- Codex：只保留 ChatGPT 官方账号的 `/backend-api/codex/responses` smoke/proxy 边界，不注册为通用 provider catalog preset，不承诺非 Responses 内部 API。

## 后续派生

后续切片应优先让以下文件从该矩阵收敛：

- `docs/openapi/public-openapi.json`
- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`
- `docs/public-sdk-examples.md`
- `src/test/resources/conformance/endpoint-conformance-matrix.json`
- `src/test/resources/conformance/accepted-exceptions.json`

当前切片先建立 source 与文档，不执行自动生成和测试。恢复测试后应补 coverage matrix consistency tests，至少校验 out-of-scope 不再被 public docs 或 catalog 宣称为 supported。
