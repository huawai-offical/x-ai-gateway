# 多模态支撑参数边界

状态：Implemented (tests deferred)
日期：2026-05-21
关联任务：[TASK-20260514-020](../tasks/done/TASK-20260514-020-openai-multimodal-supporting-parameters.md)

## 范围原则

本文件只记录功能性服务 API 的多模态支撑面，不作为 OpenAI Audio/Images/Videos 全量 parity 承诺。当前公开范围以 `src/main/resources/functional-service-api-coverage-matrix.json` 和 `docs/openapi/public-openapi.json` 为准。

## 支撑端点

| 端点 | 请求形态 | 支撑参数边界 | 说明 |
| --- | --- | --- | --- |
| `/v1/embeddings` | JSON | `model`、`input`、`encoding_format`、`dimensions`、`user` | 用于 RAG、检索和本地索引支撑；具体 provider 能力由路由候选决定。 |
| `/v1/audio/transcriptions` | multipart | `file`、`model`、`language`、`prompt`、`response_format`、`temperature`、`timestamp_granularities[]`、`include[]`、`stream` | multipart 字段按文本透传；Gemini native 仅支持 transcription 文本路径，不提供 translation parity。 |
| `/v1/audio/speech` | JSON | `model`、`input`、`voice`、`response_format`、`speed` | 用于语音输出支撑；provider 不支持的参数由执行器或上游返回边界错误。 |
| `/v1/images/generations` | JSON | `model`、`prompt`、`size`、`quality`、`n`、`response_format` | 只保留 image generation；不保留 image edit/variation 入口。 |
| `/v1/moderations` | JSON | `input`、`model` | 用于对话和多模态输入的安全分类支撑。 |

## 范围外

| 能力 | 当前处理 |
| --- | --- |
| `/v1/audio/translations` | 已从 OpenAI ingress controller、route semantics、capability enum 和 conformance fixture 中移除。 |
| `/v1/images/edits` | 已从 OpenAI ingress controller、route semantics、capability enum 和 conformance fixture 中移除。 |
| `/v1/images/variations` | 已从 OpenAI ingress controller、route semantics、capability enum 和 conformance fixture 中移除。 |
| OpenAI 官方 Videos parity | 不进入当前功能性服务 API。已有 `/api/v1/videos/*` 属于 gateway 自身 provider-specific media task surface，不等同于 OpenAI 官方 `/v1/videos/*` 兼容承诺。 |

## 验收边界

- public OpenAPI 只声明支撑端点，不声明 Audio translations 或 Images edits/variations。
- route semantics 不再把 Audio translations 或 Images edits/variations 解析为可路由能力。
- provider capability view 不再暴露 `audio_translation`、`image_edit`、`image_variation`。
- 当前按用户要求不执行真实 smoke；后续真实 key 验证由 `TASK-20260514-031` 承接。
