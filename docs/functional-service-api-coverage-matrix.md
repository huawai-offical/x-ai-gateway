# 功能性服务 API Coverage Matrix

状态：Derived Complete (tests deferred)
日期：2026-05-21
关联任务：[TASK-20260514-029-01](../tasks/done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md)、[TASK-20260514-029-04](../tasks/done/TASK-20260514-029-04-openapi-coverage-sdk-finalization.md)

## 事实源

机器可读源文件：

```text
src/main/resources/functional-service-api-coverage-matrix.json
```

该矩阵只描述 x-ai-gateway 当前产品范围：对话、streaming、tools/function calling、多模态输入输出，以及直接支撑这些能力的模型发现、RAG/file_search、认证、限流、审计、usage 和 smoke。它不是 OpenAI、Anthropic、Gemini、Vertex 或 Codex 官方 API 全量覆盖清单。

当前控制台中的 `能力矩阵` 页面已下线；这里保留的是 docs 内的事实源矩阵，用于约束公开 API、catalog 和测试口径，不表示仍存在对应前端主入口。

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

该矩阵已开始向以下文件收敛：

| 派生目标 | 状态 | 说明 |
| --- | --- | --- |
| `docs/openapi/public-openapi.json` | Done | `TASK-20260514-029-04` 已补全 core/supporting/governance 已实现路径，不声明 out_of_scope API。 |
| `src/main/resources/provider-catalog.json` | Done | 已按功能性服务 API 范围收紧 unsupportedFeatures 与 provider 边界。 |
| `docs/public-api-compatibility.md` | Done | 已明确 OpenAI Direct、OpenAI-compatible Generic、Anthropic/Gemini/Vertex/Codex native 边界。 |
| `docs/public-sdk-examples.md` | Done | `TASK-20260514-029-04` 已补充 OpenAI Direct native、OpenAI-compatible Generic、自定义 provider adapter 三模式示例。 |
| `src/test/resources/conformance/endpoint-conformance-matrix.json` | Done | 已按功能性服务 API 范围承接 endpoint conformance，并移除 Audio translations、Images edits/variations 等非支撑端点。 |
| `src/test/resources/conformance/accepted-exceptions.json` | Done | 已将非核心 API 纳入 accepted exceptions 或 out-of-scope 决策。 |

当前用户要求先不执行测试。本轮只做文档/快照派生、JSON 解析和强类型编译；恢复测试后应补 coverage matrix consistency tests，至少校验 out-of-scope 不再被 public docs 或 catalog 宣称为 supported。
