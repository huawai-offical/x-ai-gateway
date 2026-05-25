# TASK-20260524-001-08 Degraded 能力层与无损翻译矩阵隔离

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md) / [REP-20260524-002](../../docs/reports/REP-20260524-002-lossless-translation-matrix.md)

## 背景

REQ-20260524-001 明确跨协议资源属性只允许无损翻译，不允许 `LOSSY`、`EMULATED`、local fake、模拟返回或 header/metadata 标记伪装成功。当前网关定义只承诺头部自研模型厂商 native API / provider-specific native profile 与可证明无损翻译；不可对应、不可映射、不可无损或非 native 能力必须直接失败。当前已新增 `LosslessTranslationMatrixService` 并接入执行计划，但历史能力层仍使用 `InteropCapabilityLevel.EMULATED` / `LOSSY` 表示产品支持等级、观测状态或旧兼容展示。

## 目标

- 明确区分两层语义：
  - 能力展示/观测层可以保留 degraded 状态用于历史兼容和诊断。
  - 跨协议资源属性执行层只能接受 Lossless Translation Matrix 的 `LOSSLESS`，否则硬失败。
- 清理会把 `EMULATED` / `LOSSY` 误用为资源翻译成功条件的路径。
- 为旧 `responses.emulated`、`allow_emulated`、`ALLOW_LOSSY` 等入口建立新的阻断或迁移说明。
- 确保 `/responses/compact` 无 native 等价时继续走 `unsupported` / `native_compaction_required`，不能被 degraded 能力层放行。

## 非目标

- 不一次性删除所有历史 done 文档中的旧实现记录。
- 不移除 observability 中历史 degraded 字段。
- 不把所有旧 controller 行为一次性重构完；本任务按风险分批推进。

## 输入

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/LosslessTranslationMatrixService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/ExecutionSupportMatrixService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/site/UpstreamSitePolicyService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/CanonicalRenderCapabilitySupport.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/TranslationExplainService.java`

## 输出

- degraded 能力层与无损翻译矩阵的边界说明和测试。
- 高风险 `EMULATED` / `LOSSY` 执行入口改为 `BLOCKED` 或 native-required。
- `ALLOW_LOSSY` / `ALLOW_EMULATED` 只能作为阻断或历史展示信号，不能成为执行计划成功条件。
- public docs / OpenAPI 不再将 degraded 状态描述成可用翻译成功。

## 影响范围

- Interop plan、capability truth、translation explain、resource execution、public docs、observability。

## 依赖

- `TASK-20260524-001-03` 无损翻译矩阵。
- `TASK-20260524-001-04` 不可对应能力直接失败与假成功清理。

## 风险

- 过早删除 degraded enum 会破坏历史观测数据和 UI 展示。
- 如果只在 docs 改口径而不在执行计划阻断，客户端仍可能收到伪成功。

## 验收标准

- 跨协议资源属性执行路径不再以 `ALLOW_LOSSY` 或 `ALLOW_EMULATED` 作为成功条件。
- 旧 degraded 状态只出现在观测、展示或历史兼容语境，且不能绕过 `LosslessTranslationMatrixService`。
- 不可映射、不可无损或非 native 能力必须 hard-fail，不得通过 emulation、degraded、local fake、模拟返回或 metadata/header 标记伪装成功。
- 至少覆盖 Responses object、file lifecycle、media resource、tool streaming 四类高风险路径。

## 测试边界

- `TranslationExecutionPlanCompiler` negative tests。
- `GatewayResourceExecutionService` blocked plan tests。
- `TranslationExplainService` / interop plan tests。
- docs/OpenAPI snapshot smoke。

## 关联文档

- [REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)
- [REP-20260524-002](../../docs/reports/REP-20260524-002-lossless-translation-matrix.md)

## 关联任务

- 父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)
- 前置任务：[TASK-20260524-001-03](TASK-20260524-001-03-lossless-translation-matrix.md)
- 前置任务：[TASK-20260524-001-04](../in-progress/TASK-20260524-001-04-unsupported-capability-hard-fail.md)

## 当前状态

- 2026-05-24：由主线审计拆分后已提升为 In Progress。当前矩阵已经阻断部分资源执行路径，但旧 degraded 能力层仍需系统隔离。
- 2026-05-24：进入实施，先隔离 `ALLOW_LOSSY` / `ALLOW_EMULATED` 与 `EMULATED` / `LOSSY` 在执行计划中的成功判定，保留 degraded enum 在观测、报告和历史兼容展示中的用途。
- 2026-05-24：当前进展为已开始隔离 `ALLOW_LOSSY` / `ALLOW_EMULATED` 与 blocked plan 守门；待主线验证确认 Responses object、file lifecycle、media resource、tool streaming 均不能绕过 `LosslessTranslationMatrixService` 后，才可判断是否进入 Done。
- 2026-05-24：主线完成第一轮隔离实现。`GatewayDegradationPolicy` 不再放行 `EMULATED` / `LOSSY`；preview、interop plan、translation explain、admin/resource execution 和 chat execution 默认按 `STRICT` / blocked plan 守门；`GatewayChatExecutionService` 与 `GatewayResourceExecutionService` 在解析凭证和调用 runtime/executor 前拒绝 `BLOCKED` plan，且不触发 fallback、credential cooldown 或上游健康污染。
- 2026-05-24：catalog 事实源旧命名已迁移。默认 `provider-catalog.json` 与 `ProviderCatalogLoader` fallback 不再产出 `responses.emulated`，改为 `responses.native` 或 `responses.lossless-translation`；MiMo `responsesCompatibility.mode` 改为 `lossless_to_chat_completions`，运行时不再接受旧 `emulate_with_chat_completions` 模式作为成功开关。错误规则 `DOWNGRADE` 默认输出也改为阻断语义 `BLOCKED_BY_DEGRADATION_RULE`。
- 2026-05-24：完成并归档。已通过定向回归与关键词审计；`responses.emulated`、`emulate_with_chat_completions`、`DOWNGRADED_BY_RULE`、`以 emulated 执行`、`以 lossy 执行`、`opaque marker emulation` 不再出现在当前代码事实源、默认 catalog、公开 OpenAPI 测试或本任务相关现行文档中，除“已迁移”说明外不再作为可执行语义。
- 2026-05-24：已通过定向回归：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ErrorRuleAdminControllerTests"
```
