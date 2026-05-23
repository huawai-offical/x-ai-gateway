# 多模态支撑参数边界

状态：Implemented
日期：2026-05-23
关联任务：[TASK-20260514-020](../tasks/done/TASK-20260514-020-openai-multimodal-supporting-parameters.md)

更新记录：2026-05-23 根据 [REQ-20260523-004](requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md) 和 [TASK-20260523-005-01](../tasks/done/TASK-20260523-005-01-openai-style-audio-image-resource-endpoints.md)，重新纳入 Audio translations 与 Images edits/variations 的 OpenAI-style 资源入口。

## 范围原则

本文件只记录功能性服务 API 的多模态支撑面，不作为 OpenAI Audio/Images/Videos 全量 parity 承诺。当前公开范围以 `src/main/resources/functional-service-api-coverage-matrix.json` 和 `docs/openapi/public-openapi.json` 为准。

## 支撑端点

| 端点 | 请求形态 | 支撑参数边界 | 说明 |
| --- | --- | --- | --- |
| `/v1/embeddings` | JSON | `model`、`input`、`encoding_format`、`dimensions`、`user` | 用于 RAG、检索和本地索引支撑；具体 provider 能力由路由候选决定。 |
| `/v1/audio/transcriptions` | multipart | `file`、`model`、`language`、`prompt`、`response_format`、`temperature`、`timestamp_granularities[]`、`include[]`、`stream` | multipart 字段按文本透传；Gemini/Vertex native 支持音频到文本转写。 |
| `/v1/audio/translations` | multipart | `file`、`model`、`prompt`、`response_format`、`temperature` | OpenAI-style 站点直连；Gemini/Vertex native 复用多模态音频输入并返回英文翻译文本；Anthropic 因无稳定 audio API 保持 blocked。 |
| `/v1/audio/speech` | JSON | `model`、`input`、`voice`、`response_format`、`speed` | 用于语音输出支撑；provider 不支持的参数由执行器或上游返回边界错误。 |
| `/v1/images/generations` | JSON | `model`、`prompt`、`size`、`quality`、`n`、`response_format` | 图片生成资源入口；Gemini/Vertex 原生 generation 已有执行器覆盖。 |
| `/v1/images/edits` | multipart | `image`、`mask`、`prompt`、`model`、`background`、`input_fidelity`、`n`、`output_compression`、`output_format`、`quality`、`response_format`、`size`、`user` | OpenAI-style 站点直连；厂商原生互转由后续任务补齐。 |
| `/v1/images/variations` | multipart | `image`、`model`、`n`、`response_format`、`size`、`user` | OpenAI-style 站点直连；Gemini/Vertex native 以参考图驱动 `editImage` 生成变化图并返回 `b64_json`；默认 OpenAI 模型仍为 `dall-e-2`。 |
| `/v1/moderations` | JSON | `input`、`model` | 用于对话和多模态输入的安全分类支撑。 |

## 范围外

| 能力 | 当前处理 |
| --- | --- |
| OpenAI 官方 Videos parity | 不进入当前功能性服务 API。已有 `/api/v1/videos/*` 属于 gateway 自身 provider-specific media task surface，不等同于 OpenAI 官方 `/v1/videos/*` 兼容承诺。 |

## 验收边界

- public OpenAPI 声明 Audio translations 与 Images edits/variations，并写明 OpenAI-style 直连边界。
- route semantics 把 Audio translations、Images edits、Images variations 解析为可路由资源能力。
- provider capability view 暴露 `audio_translation`、`image_edit`、`image_variation`；Gemini/Vertex 的 `audio_translation` 与 `image_variation` 已进入 native executor，Anthropic 仅保留 Messages 图片输入理解，不把 audio 或图片生成/编辑/variation 标为可执行资源。
- 当前按用户要求不执行真实 smoke；后续真实 key 验证由 `TASK-20260514-031` 承接。
