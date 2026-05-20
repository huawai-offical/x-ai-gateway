# REQ-20260518-006 非核心 API 兼容代码彻底清理

状态：Done
日期：2026-05-18
完成日期：2026-05-19
上游来源：用户明确要求“不需要兼容历史数据，彻底清理干净，不要遗留兼容性代码，整理代码文件，优化代码架构”。

## 背景

`REQ-20260518-005` 已将产品范围收窄为对话、streaming、tools/function calling 与必要的文件/RAG 支撑面，并删除了 Fine-tuning、Batches、Anthropic Message Batches、Gemini Batches、public Tunings 等公开入口。但上一轮为了降低迁移风险，仍在部分内部 enum、async resource、capability、routing、observability 与测试中保留了 Batch/Tuning 兼容语义。

用户本轮明确取消历史兼容要求，因此这些残留不应继续以 unsupported、legacy 或 history-compatible 的形式存在。代码事实源需要从“公开不支持”进一步收紧为“产品内不存在”，避免后续开发、文档生成或 capability matrix 又把非核心 API 重新带回。

## 目标

- 从内部协议 enum、resource type、feature type、canonical mapping 和 execution plan 中删除 OpenAI Batch、Fine-tuning/Tuning、Anthropic Message Batch 语义。
- 删除 `GatewayAsyncResourceService` 中不再有公开入口调用的 Batch/Tuning/Message Batch 方法、分支和 helper。
- 删除或收窄 capability、site policy、route policy、execution support matrix 中的非核心兼容判断，不再保留“unsupported 兼容层”。
- 清理 DTO、测试、fixture、文档与 task 里的历史兼容描述，避免继续声明“为历史数据保留”。
- 保留 Vector Store File Batches，因为它属于 Responses `file_search` 的 ingestion 支撑能力，不等同于 OpenAI `/v1/batches`。

## 非目标

- 不删除 Chat Completions、Responses、Messages、GenerateContent、streaming、tools/function calling。
- 不删除 Files、Uploads、Models、Embeddings、Vector Stores、Vector Store Files、Vector Store File Batches 等对话/RAG/tools 支撑面。
- 不删除平台自身 Admin Console、运营后台、凭证管理、计费、观测和审计能力。
- 不追求对旧数据库记录、旧 API 客户端或旧任务文件的兼容迁移。

## 范围

- `gateway/core/interop`：operation、resource type、feature、semantics、execution support、capability truth。
- `gateway/core/resource`：async/public resource service 与 async resource type。
- `gateway/core/execution`、`gateway/core/site`：执行后端策略、site policy、route policy。
- public docs、OpenAPI、provider catalog、conformance fixture、单元测试与任务文档。
- 只清理 OpenAI `/v1/batches`、Fine-tuning/Tuning、Anthropic Message Batch、Gemini/Vertex provider batch prediction 这类非核心官方 API；不触碰 Vector Store File Batch。

## 风险

- `batch` 名称在 OpenAI `/v1/batches` 和 Vector Store File Batches 中同时存在，清理时必须按 resource type 与路径区分。
- 删除 enum 常量会暴露编译错误，需要同步修正测试与策略分支，而不是用空分支掩盖。
- 如果 docs/catalog/conformance 仍残留旧能力，后续 public snapshot 可能继续漂移。

## 验收标准

- `TranslationOperation`、`TranslationResourceType`、`InteropFeature` 不再包含 Batch/Tuning/Anthropic Message Batch 语义。
- `GatewayAsyncResourceType` 与 `GatewayAsyncResourceService` 不再包含 OpenAI Batch、Fine-tuning/Tuning、Anthropic Message Batch 方法或分支。
- Capability、policy、execution matrix 不再用 unsupported 分支保留这些非核心能力。
- 代码搜索中不再出现 `BATCH_CREATE`、`TUNING_CREATE`、`ANTHROPIC_MESSAGE_BATCH`、`TranslationResourceType.BATCH`、`TranslationResourceType.TUNING` 等残留。
- 公开 docs/OpenAPI/catalog/conformance 不再声明这些非核心能力。
- 相关定向测试通过，并记录命令与结果。

## 测试边界

- 编译级验证：`compileJava`、`compileTestJava` 或覆盖同等范围的定向 `test`。
- Interop/resource/policy/docs/catalog/OpenAPI 定向测试。
- 不做真实 provider smoke；本轮为代码结构清理与本地单元验证。

## 关联文档

- [REQ-20260518-005 对话与 Tools 功能性服务 API 范围收窄](REQ-20260518-005-functional-service-api-scope.md)
- [ADR-0010 对话与 Tools 功能性服务 API 作为产品范围](../decisions/ADR-0010-functional-service-api-scope.md)
- [TASK-20260518-006 非核心 API 兼容代码彻底清理](../../tasks/done/TASK-20260518-006-non-core-api-code-eradication.md)

## 实施结果

- 删除 OpenAI `/v1/batches`、Fine-tuning/Tuning、Anthropic Message Batch、Gemini/Vertex provider batch prediction 的内部 enum、resource type、feature、canonical mapping、execution policy、site policy 和 async resource service 分支。
- 删除 `FineTunedModelRegistrationService`、OpenAI fine-tuned model delete service、Anthropic/Gemini/OpenAI 非核心 controller/mapper/encoder 与对应 tests/fixtures。
- `GatewayAsyncResourceService` 仅保留 Upload、Video、Music、Response、Conversation、Vector Store、Vector Store File、Vector Store File Batch 等功能性服务支撑能力。
- `SiteCapabilitySnapshotEntity`、capability matrix、provider catalog、public docs、OpenAPI snapshot 与 conformance fixture 不再保留 Batch/Tuning capability 标记。
- Gemini/Vertex 本地 Upload 测试已从 `batch` 示例改为通用 conversation/file 支撑示例，避免测试语义继续指向已删除官方 Batch API。
- `REQ-20260518-005` 和 `TASK-20260518-005` 中“历史兼容层保留”的遗留描述已改为由本需求清除。

## 验证结果

残留关键词搜索：

```powershell
rg -n "fine_tuning|FineTuned|Fine-tuning|fine-tun|tuning|message_batches|Message Batches|messages/batches|batch_generate_content|batchGenerateContent|batch prediction|/v1/batches|/api/v1/tunings|training_file|validation_file|BATCH_CREATE|TUNING_CREATE|ANTHROPIC_MESSAGE_BATCH|supportsBatches|supportsTuning|supports_batches|supports_tuning" src/main/java src/test/java src/test/resources src/main/resources -g "*.java" -g "*.json" -g "*.yaml"
```

结果：无匹配。

定向测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceCanonicalizerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AsyncResourceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.AsyncResourceAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.GatewayPublicResourceControllersTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GoogleNativeNamespaceControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceTests"
```

结果：通过，`BUILD SUCCESSFUL`。

完整后端测试：

```powershell
.\gradlew.bat test
```

结果：通过，`BUILD SUCCESSFUL`。本轮额外修复了完整测试暴露出的 Spring context 构造器注入、测试 probe controller 扫描冲突、OpenAI Direct resource smoke family 数量断言，以及 Anthropic E2E 请求字段 `max_tokens` 标准化问题。

## 遗留问题与后续建议

- 本轮不做真实 provider smoke；后续按 `TASK-20260514-031` 只覆盖对话、tools、files/uploads、models、RAG/file_search 与 realtime client secret 等功能性服务面。
- `Vector Store File Batches` 继续保留，原因是它属于 Responses `file_search` 本地 ingestion 支撑，不等同于 OpenAI `/v1/batches`。
