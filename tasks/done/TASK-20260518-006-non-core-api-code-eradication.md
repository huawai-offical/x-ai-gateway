# TASK-20260518-006 非核心 API 兼容代码彻底清理

状态：Done
优先级：Critical
类型：父任务
上游来源：[REQ-20260518-006](../../docs/requirements/REQ-20260518-006-non-core-api-code-eradication.md)

## 背景

上一轮范围收窄已经删除公开 API 入口，但为了兼容历史数据，内部仍保留 Batch/Tuning/Anthropic Message Batch 的 enum、resource type、capability 分支、async resource 方法和部分测试。用户本轮明确要求不兼容历史数据，彻底清理，不保留兼容性代码。

## 目标

- 删除非核心官方 API 的内部类型定义、执行计划、canonical mapping、resource service 和 policy 分支。
- 删除或改写依赖这些类型的测试，确保测试只覆盖当前产品范围。
- 更新文档和任务，明确本轮不保留历史兼容层。
- 保留 Vector Store File Batches 等对话/RAG 支撑能力。

## 非目标

- 不删除平台自身 Admin Console 和运维后台。
- 不删除 Chat/Responses/Messages/GenerateContent/tools/function calling。
- 不删除 Files、Uploads、Models、Embeddings、Vector Stores 与 Vector Store File Batches。
- 不做旧数据迁移、旧客户端兼容或灰度保留。

## 输入

- `REQ-20260518-006`
- 用户本轮“不需要兼容历史数据”的明确口径
- 残留代码搜索结果：`BATCH_*`、`TUNING_*`、`ANTHROPIC_MESSAGE_BATCH*`、`TranslationResourceType.BATCH/TUNING`

## 输出

- 删除后的 enum/resource/service/policy/canonical/test 代码。
- 更新后的 docs/task 状态与验证记录。
- 残留搜索与定向测试结果。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/site/`
- `src/test/java/com/prodigalgal/xaigateway/`
- `docs/requirements/`
- `tasks/index.md`

## 依赖

- 现有 public API 范围已经由 `REQ-20260518-005` 收窄。
- Vector Store File Batches 必须被识别为保留能力。

## 风险

- 删除 enum 常量会触发较多编译错误，需要逐项消除调用点。
- 同名 `batch` 能力可能误伤 Vector Store File Batches。
- 任务文档如果继续写“历史兼容保留”，会和用户本轮要求冲突。

## 验收标准

- 内部类型和 service 不再保留 OpenAI Batch、Fine-tuning/Tuning、Anthropic Message Batch。
- 非核心 API 不再以 unsupported 或 legacy 分支存在于 capability/policy/execution matrix。
- `rg` 搜索确认关键残留符号已消失。
- 定向测试通过，或记录具体失败与原因。

## 测试边界

- `GatewayRequestFeatureServiceTests`
- `ExecutionSupportMatrixServiceTests`
- `SiteCapabilityTruthServiceTests`
- `NonChatRoutePolicyServiceTests`
- `GatewayPublicResourceServiceTests`
- `GatewayAsyncResource*Tests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- 必要时补 `compileJava` / `compileTestJava`

## 关联文档

- [REQ-20260518-006](../../docs/requirements/REQ-20260518-006-non-core-api-code-eradication.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [TASK-20260518-005](../done/TASK-20260518-005-functional-service-api-scope-pruning.md)

## 子任务

- [TASK-20260518-006-01 类型、策略与 capability 残留清理](TASK-20260518-006-01-non-core-type-policy-eradication.md)：Done
- [TASK-20260518-006-02 Async resource service 非核心方法删除](TASK-20260518-006-02-non-core-resource-service-eradication.md)：Done
- [TASK-20260518-006-03 Docs、catalog、conformance 与测试闭环](TASK-20260518-006-03-non-core-docs-tests-closure.md)：Done

## 当前状态

- 2026-05-18：任务创建，开始清理内部兼容代码。
- 2026-05-19：已完成代码清理、残留搜索、定向测试和文档回写，任务归档。

## 实现结果

- 删除 `TranslationOperation`、`TranslationResourceType`、`InteropFeature`、`GatewayAsyncResourceType` 中的 OpenAI Batch、Fine-tuning/Tuning、Anthropic Message Batch 非核心语义。
- 删除 `GatewayAsyncResourceService` 中 Batch/Tuning/Anthropic Message Batch 的 create/get/list/cancel、状态同步、Google native batch view、tuning event/checkpoint 等方法和 helper。
- 删除 Fine-tuned model registry/delete service、Anthropic/Gemini/OpenAI 非核心 controller/mapper/encoder/tests/fixtures。
- 删除 capability snapshot 的 `supportsBatches/supportsTuning` 字段和 changelog 列，更新 provider catalog、public docs、OpenAPI snapshot 与 conformance fixture。
- 将测试示例从 `batch` 官方 API 语义改为 conversation/file 支撑语义，保留 Vector Store File Batch 的 RAG ingestion 边界。

## 验证结果

```powershell
rg -n "fine_tuning|FineTuned|Fine-tuning|fine-tun|tuning|message_batches|Message Batches|messages/batches|batch_generate_content|batchGenerateContent|batch prediction|/v1/batches|/api/v1/tunings|training_file|validation_file|BATCH_CREATE|TUNING_CREATE|ANTHROPIC_MESSAGE_BATCH|supportsBatches|supportsTuning|supports_batches|supports_tuning" src/main/java src/test/java src/test/resources src/main/resources -g "*.java" -g "*.json" -g "*.yaml"
```

结果：无匹配。

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceCanonicalizerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AsyncResourceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.AsyncResourceAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.GatewayPublicResourceControllersTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GoogleNativeNamespaceControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceTests"
```

结果：通过，`BUILD SUCCESSFUL`。

```powershell
.\gradlew.bat test
```

结果：通过，`BUILD SUCCESSFUL`。完整测试覆盖 734 tests，确认 context load、E2E smoke、OpenAI Direct resource smoke 和异常处理切片均已恢复。

## 遗留问题

- 无需保留历史兼容代码；本轮已按用户要求删除。
- 后续真实 smoke 和功能推进应从 `TASK-20260514-031`、`TASK-20260514-030`、`TASK-20260514-017`、`TASK-20260514-018` 中按 P0 顺序拆分执行。
