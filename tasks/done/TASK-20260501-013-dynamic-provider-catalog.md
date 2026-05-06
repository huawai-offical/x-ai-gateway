# TASK-20260501-013 动态 Provider Catalog 与 Conformance Loader

状态：Done  
优先级：High  
来源：本地拆分  
关联任务：[TASK-20260501-001](../done/TASK-20260501-001-provider-registry-2.md)  
关联设计：[REQ-20260501-003](../../docs/requirements/REQ-20260501-003-second-priority-task-closure-design.md)

## 背景

Provider Registry 2.0 已完成静态 preset catalog 与幂等导入，但长期需要支持动态 provider catalog、版本管理、conformance 检查和成本 metadata 更新。

## 本轮目标

将 provider preset 从硬编码列表推进到 classpath JSON catalog loader，并将 catalog 版本、来源、deprecated 和 conformance 信息暴露给 API。

## 本轮范围

- 新增本地 `provider-catalog.json`。
- 新增 catalog loader 与 fallback。
- preset response 返回 catalog metadata。
- 导入逻辑兼容现有 `preset:{code}` 行为。

## 非目标

- 不接远程 marketplace。
- 不在此任务中实现具体非 Chat provider executor。

## 验收标准

- catalog 可加载并与现有 preset API 兼容。
- 至少 3 个 provider 有 conformance metadata。
- catalog 加载失败时有 fallback。

## 实现记录

- 新增 `src/main/resources/provider-catalog.json`，本地 catalog 包含 6 个 provider preset，并为 OpenAI、Azure OpenAI、DeepSeek、OpenRouter、Anthropic、Gemini 提供 conformance metadata。
- 新增 `ProviderCatalogLoader`，优先读取 classpath catalog，文件缺失、为空或解析失败时退回内置 fallback。
- 新增 `ProviderCatalogSnapshot`、`ProviderPresetDefinition`，`ProviderSiteRegistryService` 改为通过 loader 读取 preset。
- `ProviderSitePresetResponse` 增加 `catalogVersion`、`catalogSource`、`deprecated`、`conformanceChecks`，现有导入仍保持 `preset:{code}` 幂等行为。

## 测试/验证

- 通过：`ProviderSiteRegistryServiceTests`
- 通过综合命令：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GovernanceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.GovernanceAdminControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.GatewayPublicResourceControllersTests"`

## 遗留问题

- 远程 catalog marketplace、签名校验、版本增量更新仍需后续任务承接。
