# TASK-20260523-005-02 厂商 file* 编排覆盖与能力矩阵对齐

## 任务类型

子任务

## 背景

来源：`tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

项目已有 OpenAI-style `files`、`uploads` 入口和 `GatewayFileService` 上游绑定逻辑，但 OpenAI-compatible generic、Anthropic、Gemini/Vertex 的文件对象能力在 capability matrix、preset 展示和运行时可执行路径之间仍存在保守标注或展示不一致。用户要求各厂商 `file*` 接口需要实现，因此需要单独把文件对象生命周期做成可验证闭环。

## 目标

- 复核并补齐 `POST /v1/files`、`GET /v1/files`、`GET /v1/files/{fileId}`、`GET /v1/files/{fileId}/content`、`DELETE /v1/files/{fileId}`。
- 复核并补齐 `POST /v1/uploads`、parts、complete、cancel 的能力判定。
- 对齐 OpenAI Direct、OpenAI-compatible、Anthropic、Gemini、Vertex 的文件对象直连或互转路径。
- 修正 capability matrix、provider catalog/preset 的 file* 支持说明。

## 非目标

- 不把无下载能力的上游文件强行伪装为可 content download。
- 不在本任务里实现 image/audio 原生互转。

## 上游来源

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`
- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

## 输入

- `GatewayFileService`
- `OpenAiFilesController`
- `OpenAiUploadsController`
- `SiteCapabilityTruthService`
- `ExecutionSupportMatrixService`
- `provider-catalog.json`

## 输出

- file* 能力判定与运行时执行路径一致。
- 对应单元测试和文档回写。

## 影响范围

- 文件对象服务、能力矩阵、厂商目录、预设导入、provider catalog。

## 依赖

- 已有上游文件绑定表和 lineage。
- Anthropic beta Files API。
- Gemini Files API。

## 风险

- 不同厂商文件生命周期和下载能力差异较大。
- OpenAI-compatible 站点可能只兼容 chat/models，不兼容 files。

## 验收标准

- [x] `file*` 每个路径都有明确支持级别或 blocked reason。
- [x] 厂商目录和预设导入展示同一套 file* 支持事实源。
- [x] 上游文件 binding 测试覆盖 OpenAI、Anthropic、Gemini/Vertex。
- [x] uploads 生命周期测试覆盖 create、part、complete、cancel。

## 测试边界

- 文件服务单元测试。
- capability matrix 单元测试。
- provider catalog/preset 展示测试。
- 真实 smoke 另行按成本和凭证排期。

## 关联文档

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`

## 关联任务

- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

## 当前推进记录

- 2026-05-23：按用户“后续把 image*、audio*、file* 的各个厂家接口继续完善推进”要求，先进入 file* 编排切片。该切片优先复核现有 OpenAI-style、Anthropic beta Files API、Gemini/Vertex Files API 与本地 lineage 的一致性，再回写 capability matrix 和测试。
- 2026-05-23：完成 file* / uploads 能力事实源收口。OpenAI-compatible Generic、DeepSeek、Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Mistral、Together、Fireworks、OpenRouter 在 capability snapshot 声明 `supports_files=true` / `supports_uploads=true` 时开放 gateway orchestration；xAI、Perplexity、Cohere、Jina、Dify 等 catalog 已明确排除 object lifecycle 的站点继续给出 blocked reason。
- 2026-05-23：`ResourceSurfaceRegistry` 已把 `file_list`、`file_get`、`file_content_get`、`file_delete`、`upload_get`、`upload_part_add`、`upload_complete`、`upload_cancel` 纳入 provider surface；`SurfaceCapabilityView` 增加 `routeSelectionMode`，厂商详情能展示 `LOCAL_CATALOG` / `STORED_LINEAGE` 等非 catalog selection 路径。

## 实现结果

- `ExecutionSupportMatrixService`、`SiteCapabilityTruthService`、`UpstreamSitePolicyService` 已对齐 OpenAI-compatible file/uploads 能力判定，不再把 `/v1/files`、`/v1/uploads` 作为 family-wide accepted exception。
- `EndpointConformanceMatrixTests` 与 conformance baseline 已补齐 OpenAI-compatible file/uploads 全生命周期：
  - `POST /v1/files`
  - `GET /v1/files`
  - `GET /v1/files/{fileId}`
  - `GET /v1/files/{fileId}/content`
  - `DELETE /v1/files/{fileId}`
  - `POST /v1/uploads`
  - `GET /v1/uploads/{uploadId}`
  - `POST /v1/uploads/{uploadId}/parts`
  - `POST /v1/uploads/{uploadId}/complete`
  - `POST /v1/uploads/{uploadId}/cancel`
- `accepted-exceptions.json` 已移除 `openai-compatible-object-lifecycle-blocked`。
- `site-conformance-fixtures.json` 增加 OpenAI-compatible file orchestration 成功样本，并保留 xAI provider boundary blocked 样本，避免把所有 compatible 站点一概标为 file lifecycle 可用。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ResourceSurfaceRegistryTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiUploadsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiFilesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceAnthropicTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"`

## 遗留问题

- 本任务未执行真实上游 smoke。OpenAI-compatible file/uploads 运行时仍保留上游真实错误和 traceId，不把 chat 兼容性外推为实际 files/uploads 成功。
- 厂商原生 audio/image 互转已由 `TASK-20260523-005-03` 归档完成。

## 当前状态

Done
