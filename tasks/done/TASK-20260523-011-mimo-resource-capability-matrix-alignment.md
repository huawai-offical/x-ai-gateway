# TASK-20260523-011 MiMo 资源能力矩阵与 OpenAI-compatible 实现对齐

状态：Done  
优先级：High  
父任务：TASK-20260523-011  
上游来源：[REQ-20260523-010](../../docs/requirements/REQ-20260523-010-mimo-resource-capability-matrix-alignment.md)

## 背景

用户反馈小米 MiMo 入口能力矩阵仍把 audio/image/files/uploads 显示为 blocked。排查发现公开入口渲染层已识别路径，但站点能力真相层依赖旧 snapshot，导致已实现的 OpenAI-style passthrough/orchestration 入口没有反映到矩阵。

## 目标

- 对齐 MiMo/OpenAI-compatible generic 的能力矩阵与当前已实现资源入口。
- 修正刷新模型/刷新能力后的 snapshot 写入结果。
- 用测试冻结行为，避免后续再次退回旧 blocked 状态。

## 非目标

- 不做 admin、tuning、batch、eval 等管理/训练类 API。
- 不做小米 MiMo billable live smoke。
- 不承诺 MiMo 上游一定支持所有 passthrough 资源；真实失败仍由运行时反馈。

## 输入

- 用户提供的小米 MiMo 入口能力矩阵 blocked 明细。
- 现有 `SiteCapabilityTruthService`、`ExecutionSupportMatrixService`、`UpstreamSitePolicyService`、`ProviderSiteRegistryService`。
- 已实现的 OpenAI-style audio/images/files/uploads resource executor。

## 输出

- 更新后的 policy/catalog/矩阵真相源代码。
- 更新后的后端单元测试。
- 回写需求文档与任务状态。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/site/UpstreamSitePolicyService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthService.java`
- `src/main/resources/provider-catalog.json`
- `src/test/java/...` 相关矩阵与 registry 测试
- `docs/functional-service-api-coverage-matrix.md`

## 依赖

- OpenAI-compatible resource executor 已存在。
- 站点能力 snapshot 仍由 `ProviderSiteRegistryService.refreshCapabilities` 写入。

## 风险

- OpenAI-compatible passthrough 能力与真实上游能力不完全等价。
- 旧数据库快照需要刷新后才会在运行实例中更新。

## 验收标准

- MiMo/OpenAI-compatible generic 的 audio/image/file/upload 能力矩阵不再误报 blocked。
- 相关测试通过。
- 文档记录矩阵语义和上游真实失败边界。

## 测试边界

- 聚焦运行能力真相源和站点管理测试。
- 不执行真实上游请求。

## 关联文档

- [REQ-20260523-010](../../docs/requirements/REQ-20260523-010-mimo-resource-capability-matrix-alignment.md)

## 子任务

- [x] 子任务 1：修正 OpenAI-compatible generic policy 与 MiMo catalog tags。
- [x] 子任务 2：补充 MiMo/OpenAI-compatible 能力矩阵回归测试。
- [x] 子任务 3：回写文档、验证并更新任务状态。

## 实现结果

- `UpstreamSitePolicyService` 已将 `OPENAI_COMPATIBLE_GENERIC` 与具名兼容厂商分开建模，使 MiMo 这类 generic compatible 站点可以在 snapshot 中声明 gateway 已支持的 OpenAI-style media/resource 能力。
- `provider-catalog.json` 已补齐 MiMo 和 generic preset 的 `audio/images/moderation/files/uploads` 标签，并在 MiMo unsupported notes 中说明 passthrough 与 gateway orchestration 边界。
- `ProviderSiteRegistryServiceTests`、`ProviderSiteAdminServiceTests`、`SiteCapabilityTruthServiceTests` 已补充回归。
- `docs/functional-service-api-coverage-matrix.md` 已补充矩阵语义说明。

## 验证记录

通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests"
```
