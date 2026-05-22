# TASK-20260514-020 OpenAI 多模态支撑参数边界收紧

状态：Completed
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](../done/TASK-20260514-014-openai-resource-family-coverage-gap.md)、[REP-20260521](../../docs/reports/REP-20260521-functional-scope-backlog-closeout.md)

## 背景

当前 coverage matrix 只把多模态支撑面限定为 `/v1/embeddings`、`/v1/audio/transcriptions`、`/v1/audio/speech`、`/v1/images/generations`、`/v1/moderations`。这些端点已进入公开 OpenAPI，但 controller 和文档仍需要进一步固定参数边界，避免客户误解为官方 Audio/Images 全量 parity。

## 目标

- 固定 Embeddings `input/model/encoding_format/dimensions/user` 的处理状态。
- 固定 Audio transcriptions 与 speech 的 multipart/JSON 参数边界。
- 固定 Images generations 的 JSON 参数边界。
- 固定 Moderations 的输入、模型和响应结构边界。
- 将 Audio translations、Images edits/variations、Videos parity 标记为当前产品范围外或后续独立评估，不作为本任务必做项。

## 非目标

- 不追求官方 Audio/Images 全量参数 parity。
- 不把 Videos parity 纳入当前功能性服务 API。
- 不保证第三方 provider 支持所有 OpenAI 多模态参数。
- 不执行真实 smoke。

## 输入

- `src/main/resources/functional-service-api-coverage-matrix.json`
- `OpenAiAudioController`、`OpenAiImagesController`、Embeddings/Moderations resource executors。
- `docs/openapi/public-openapi.json`

## 输出

- 多模态支撑参数边界矩阵。
- 必要 controller 参数接收或显式拒绝实现。
- public OpenAPI 与 public docs 边界说明。

## 影响范围

- OpenAI resource controllers、multipart executor、file refs、public OpenAPI、SDK examples。

## 依赖

- `TASK-20260514-031` 成本与真实 smoke 防护。

## 风险

- 多模态真实 smoke 成本高，继续由 `TASK-20260514-031` 控制。
- multipart 字段类型容易和 WebFlux binding 冲突。

## 验收标准

- coverage matrix 中列入的多模态路径都有参数处理状态。
- 未进入 coverage matrix 的 Audio translations、Images edits/variations、Videos 不再被写成当前 backlog 必做项。
- public OpenAPI 与 SDK/docs 不暗示官方全量多模态 parity。

## 测试边界

- 本任务当前按用户要求不执行测试。
- 后续恢复测试时补 Controller multipart tests 与 resource executor tests。
- 真实 smoke 按 key 和预算分类执行，由 `TASK-20260514-031` 承接。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [REP-20260521 功能性服务 API Backlog 收口审计](../../docs/reports/REP-20260521-functional-scope-backlog-closeout.md)
- [多模态支撑参数边界](../../docs/multimodal-supporting-parameters.md)

## 实现结果

- `OpenAiAudioController` 保留 `/v1/audio/transcriptions` 与 `/v1/audio/speech`，删除 `/v1/audio/translations`。
- `OpenAiImagesController` 保留 `/v1/images/generations`，删除 `/v1/images/edits` 与 `/v1/images/variations`。
- `TranslationOperation`、`InteropFeature`、route semantics、capability truth、admin capability view 与 conformance matrix 已移除 `audio_translation`、`image_edit`、`image_variation`。
- `/v1/audio/transcriptions` multipart 参数补充 `timestamp_granularities[]`、`include[]`、`stream`，并重新生成 public OpenAPI 快照。
- 新增 `docs/multimodal-supporting-parameters.md` 固定支撑端点与范围外边界。

## 验证记录

- `.\gradlew.bat compileJava -x test`
- `.\gradlew.bat compileTestJava -x test`
- `docs/openapi/public-openapi.json` 与 `src/test/resources/conformance/endpoint-conformance-matrix.json` JSON 解析通过。
- `src/main`、`src/test`、`docs/openapi` 范围内不再出现 `audio_translation`、`image_edit`、`image_variation` 或对应 `/v1/audio/translations`、`/v1/images/edits`、`/v1/images/variations` 路径引用。
- 未执行单元测试与真实 smoke，符合用户当前“先不做测试”的要求；真实 key 验证仍由 `TASK-20260514-031` 承接。

## 当前状态

- 2026-05-21：从“参数 parity”收紧为“多模态支撑参数边界”，保留在 Backlog，等待后续非测试实现切片。
- 2026-05-21：进入 In Progress，优先补 Audio transcription 当前已支持路径的参数接收与公开 OpenAPI 描述。
- 2026-05-21：已完成代码、OpenAPI、conformance fixture 与文档收紧，移动到 Done。
