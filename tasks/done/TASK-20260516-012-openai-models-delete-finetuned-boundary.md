# TASK-20260516-012 OpenAI Models Delete 与 Fine-tuned Model 删除边界

状态：Done
优先级：High
类型：子任务切片
父任务：[TASK-20260514-021](TASK-20260514-021-openai-files-uploads-models-functional-support.md)
上游来源：[TASK-20260514-014](../backlog/TASK-20260514-014-openai-resource-family-coverage-gap.md)、[TASK-20260514-015](../backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)、[REP-20260514 OpenAI API 兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 背景

官方 OpenAI Models API 暴露 `DELETE /v1/models/{model}`，用于删除 fine-tuned model，并要求组织 Owner 权限。本切片闭环前，项目只实现 `GET /v1/models` 和 `GET /v1/models/{model}`；`provider-catalog.json` 仍声明 `models_delete: DELETE /v1/models is not exposed in the current gateway.`，导致已实现的 fine-tuned model 注册/注销能力无法通过 OpenAI-compatible endpoint 使用。

项目已有 `FineTunedModelRegistrationService`，fine-tuning 成功后会把 tuned model 写入 `site_model_capability` 并注册自动 alias；取消或删除 tuning job 时也已有 unregister 逻辑。本切片需要把这条本地生命周期显式接入 OpenAI Models Delete，同时保护公共模型和跨 DistributedKey 模型不被误删。

## 目标

- 暴露 `DELETE /v1/models/{model}`。
- 仅允许删除当前 DistributedKey 下经 gateway fine-tuning/import 记录的 fine-tuned model 或其自动 alias。
- 删除时复用 `FineTunedModelRegistrationService.unregister`，清理 `site_model_capability` 与自动 alias/rules，并回写 tuning lineage metadata。
- 公共模型、未授权模型、非当前 DistributedKey lineage 模型必须被拒绝或返回 not_found，避免误删。
- 更新 provider catalog、public docs、public OpenAPI 与相关测试，避免事实源继续声明 endpoint 未暴露。

## 非目标

- 不在本切片调用真实 OpenAI 上游 `DELETE /v1/models/{model}`。
- 不实现 OpenAI 组织 Owner 权限模型；网关权限先以 DistributedKey 资源归属和本地 lineage 为边界。
- 不补齐 Fine-tuning events/checkpoints/pause/resume；events/checkpoints 后续由 `TASK-20260516-013` 闭环，pause/resume 仍归属 `TASK-20260514-022`。

## 输入

- 官方 OpenAI Models Delete API Reference。
- `OpenAiModelsController`、`ModelCatalogQueryService`、`FineTunedModelRegistrationService`。
- `gateway_async_resource` 中 tuning lineage metadata：`registered_model_key`、`registered_model_name`、`registered_aliases`。

## 输出

- OpenAI-compatible `DELETE /v1/models/{model}` endpoint。
- 本地 fine-tuned model deletion service。
- Controller/service negative tests。
- public OpenAPI、provider catalog 与公开兼容文档更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/main/resources/provider-catalog.json`
- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`
- `src/test/java/...`

## 依赖

- `TASK-20260514-021` Files/Uploads/Batches/Models 生命周期父任务。
- `TASK-20260516-011` 已补齐 Batches list 并建立本轮资源生命周期闭环节奏。
- 现有 fine-tuned model 注册 metadata 必须可被当前 DistributedKey 查询到。

## 风险

- 如果只按模型名称删除，可能误删公共模型或其他租户模型。
- 如果 alias 与 tuned model 的映射不是自动注册来源，不能擅自删除人工 alias。
- 真实 OpenAI owner-role 上游删除未做时，公开文档必须说明本切片是 gateway registry delete。

## 验收标准

- `DELETE /v1/models/{model}` 对当前 DistributedKey 下 gateway-registered fine-tuned model 返回 `{id, object:"model", deleted:true}`。
- 传入自动 alias 时同样能清理对应 tuned model lineage 和自动 alias。
- 传入可访问公共模型时返回 400，明确只能删除 gateway-registered fine-tuned model。
- 传入其他 DistributedKey 或未登记 model 时返回 404。
- `provider-catalog.json` 不再声明 `DELETE /v1/models` 未暴露，而是标注上游 owner-role passthrough 仍未实现。
- public docs 与 OpenAPI snapshot 包含 Models Delete 的本地边界说明。

## 测试边界

- `OpenAiModelsControllerTests`：delete success、public model negative、not found。
- 新增 service tests：按 registered model 和 alias 匹配、只清理当前 DistributedKey、metadata 回写。
- `ProviderCatalogLoaderTests`、`PublicDocsBundleServiceTests`、`PublicOpenApiSnapshotTests`。
- 不使用真实 OpenAI key，不调用真实上游。

## 关联文档

- [TASK-20260514-021](TASK-20260514-021-openai-files-uploads-models-functional-support.md)
- [REP-20260514 OpenAI API 兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)
- OpenAI Models Delete API Reference：`https://developers.openai.com/api/reference/resources/models/methods/delete`

## 当前状态

- 2026-05-16：已闭环。`DELETE /v1/models/{model}` 已暴露为 gateway-registered fine-tuned model registry delete；真实上游 owner-role delete passthrough 保留为后续增强边界。

## 实现结果

- 新增 `OpenAiFineTunedModelDeletionService`，按当前 DistributedKey 扫描 `TUNING` lineage，并仅匹配 metadata 中的 `registered_model_key`、`registered_model_name`、`registered_alias_key` 或 `registered_aliases`。
- 删除时复用 `FineTunedModelRegistrationService.unregister`，清理 `site_model_capability` 与自动 alias/rules，并回写 tuning metadata：移除 registered fields，写入 `model_delete_requested_id`、`model_deleted_at` 和 `model_deleted` event。
- `OpenAiModelsController` 新增 `DELETE /v1/models/{model}`，返回 OpenAI-style `{id, object:"model", deleted:true}`。
- 公共模型删除会返回 400，未登记或跨 DistributedKey lineage 返回 404，避免误删公共模型或其他租户模型。
- `provider-catalog.json` 新增 `openai.models-delete-local-registry` conformance check，并把原 `models_delete` 未暴露声明改为上游 Owner role passthrough 未完成边界。
- `PublicDocsBundleService` 与 `docs/openapi/public-openapi.json` 已公开 `/v1/models`、`/v1/models/{model}` get/delete，并写明 delete 只处理 gateway registry。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.OpenAiFineTunedModelDeletionServiceTests"
```

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.OpenAiFineTunedModelDeletionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

结果：均通过。

## 遗留边界

- 不调用真实 OpenAI 上游 `DELETE /v1/models/{model}`；需要后续在 `TASK-20260514-021` 下继续拆分 owner-role passthrough 与真实 smoke。
- Fine-tuning events/checkpoints 后续已由 `TASK-20260516-013` 闭环；pause/resume 仍归属 `TASK-20260514-022`。
