# TASK-20260514-029-04 OpenAPI 路径补全与 SDK 三模式示例归口

状态：Completed
优先级：Critical
类型：子任务
父任务：[TASK-20260514-029](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)、[REP-20260521](../../docs/reports/REP-20260521-project-progress-codex-uiux-reprioritization.md)

## 背景

TASK-20260514-029 的 Coverage Matrix、provider-catalog、conformance accepted-exceptions、public-api-compatibility.md 已完整归口。
当前剩余缺口：

1. `public-openapi.json` 缺少功能性服务 API 中属于 `core` / `supporting` 的路径条目：
   - `/v1/files`（list、create、delete、content）
   - `/v1/uploads`（create、add part、complete、cancel）
   - `/v1/embeddings`
   - `/v1/audio/transcriptions`、`/v1/audio/speech`
   - `/v1/images/generations`
   - `/v1/moderations`
   - `/v1/realtime/client_secrets`
   - Gemini/Vertex generateContent 路径（`/v1beta/models/{model}:generateContent`）
2. `public-sdk-examples.md` 缺少按三种接入模式区分的 SDK 示例说明：
   - OpenAI Direct native（调用官方账号 Responses、Conversations、file_search 等）
   - OpenAI-compatible Generic（只能 chat/tools/streaming）
   - 自定义 provider adapter（Anthropic native、Gemini native）

## 目标

- 在 `docs/openapi/public-openapi.json` 中补全 `core` 与 `supporting` 分类下所有已实现端点的路径声明。
- 在 `docs/public-sdk-examples.md` 中补充三模式接入说明与 SDK 示例索引。
- 更新 `docs/functional-service-api-coverage-matrix.md` 中的"后续派生"状态为"已完成"。
- 父任务 TASK-20260514-029 归档至 `tasks/done/`。

## 非目标

- 不实现任何新的后端端点。
- 不补充 Fine-tuning、Batches、Evals、Administration 相关路径。
- 不修改 provider-catalog.json（已完成）。

## 输入

- `docs/openapi/public-openapi.json`（当前已有路径列表）
- `src/main/resources/functional-service-api-coverage-matrix.json`（事实源）
- `docs/public-sdk-examples.md`（当前内容）
- `docs/public-api-compatibility.md`（已完成，不变）

## 输出

- 补全后的 `docs/openapi/public-openapi.json`
- 更新后的 `docs/public-sdk-examples.md`（含三模式区分说明）
- 更新 `docs/functional-service-api-coverage-matrix.md` 状态
- 父任务文件移至 `tasks/done/`

## 影响范围

- `docs/openapi/public-openapi.json`
- `docs/public-sdk-examples.md`
- `docs/functional-service-api-coverage-matrix.md`
- `tasks/done/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md`（归档）

## 依赖

- TASK-20260514-029 前三个子任务（均已归档）

## 风险

- OpenAPI 路径声明过于宽泛会误导客户误以为已实现全量功能；需确保每条路径都有清晰的 summary 说明能力边界。

## 验收标准

- `public-openapi.json` 的路径集合与 coverage-matrix.json 中 status 为 core/supporting/governance 的 openAiStandardPaths 一一对应。
- `public-sdk-examples.md` 明确区分三种接入模式，每种配有 SDK 代码片段。
- Gradle 强类型编译通过（`.\gradlew.bat compileJava -x test`）。

## 测试边界

- 本切片为文档类变更，不新增后端测试。
- 后续恢复测试时可补充 `PublicOpenApiSnapshotTests` 对新增路径的 snapshot 断言。

## 当前状态

- 2026-05-20：子任务创建，进入 In Progress。
- 2026-05-21：项目进度复核后确认本任务为当前 P0-CODEX 最高优先执行项，先补文档/任务索引，再推进 OpenAPI 与 SDK 收尾。
- 2026-05-21：已补全 OpenAPI 生成器和快照、SDK 三模式示例、coverage matrix 派生状态，并随父任务归档至 `tasks/done/`。

## 实现结果

- `PublicDocsBundleService` 补齐 Files、Uploads、Embeddings、Audio transcriptions/speech、Images generations、Moderations、Realtime client secrets 与 Gemini generateContent 的 OpenAPI 路径、参数和 request body 描述。
- `docs/openapi/public-openapi.json` 已由已编译生成器机械刷新，版本更新为 `2026.05.21`。
- `docs/public-sdk-examples.md` 已明确三种接入模式：OpenAI Direct native、OpenAI-compatible Generic、自定义 provider adapter。
- `docs/functional-service-api-coverage-matrix.md` 已标记派生状态，说明测试按用户当前策略延后。

## 验证结果

- `docs/openapi/public-openapi.json` 可解析。
- `src/main/resources/functional-service-api-coverage-matrix.json` 可解析。
- OpenAPI 快照包含本任务要求的 15 个目标路径。
- `.\gradlew.bat compileJava -x test` 通过。
- 目标文件 `git diff --check` 通过。
