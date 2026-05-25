# TASK-20260524-001-03 跨协议资源属性无损翻译矩阵

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

网关目标是让客户端使用 OpenAI、Anthropic、Gemini 等协议接入，再按目标厂商执行。类似资源属性需要具备转换翻译能力，但用户明确不要模糊过渡地带：不能无损翻译、不可映射或没有 native 等价能力时必须直接失败，不通过 emulation、degraded、local fake、模拟返回或 metadata/header 标记让下游误判可用。

## 目标

- 建立 OpenAI、Anthropic、Gemini 之间的 message/content/tool/tool result/stream/usage/file/image/audio 属性矩阵。
- 标出可无损翻译、必须 native、必须失败的能力。
- 将矩阵接入 route semantics、capability matrix 和 request validation。

## 非目标

- 不实现 lossy translation。
- 不用 header、metadata、warning、local fake 或模拟返回表示“已降级但成功”。
- 不处理非核心官方 API 全量 parity。

## 输入

- OpenAI Chat/Responses schema。
- Anthropic Messages schema。
- Gemini generateContent schema。
- 现有 request mapper、response encoder 和 tool mapper。

## 输出

- 跨协议无损翻译矩阵。
- mapper 缺口任务。
- 不可翻译能力的阻断列表。
- 第一阶段代码事实源：`LosslessTranslationMatrixService`、`LosslessTranslationMatrixEntry`、`LosslessTranslationSupport`。
- 执行计划接入口：`TranslationExecutionPlanCompiler` 将 matrix blocker 转成 `BLOCKED` plan。
- 第一阶段报告：[REP-20260524-002](../../docs/reports/REP-20260524-002-lossless-translation-matrix.md)。

## 影响范围

- OpenAI Responses/Chat mapper。
- Anthropic Messages mapper。
- Gemini GenerateContent mapper。
- streaming encoder、tool calling、usage normalizer。

## 依赖

- native adapter 最小契约。
- capability snapshot refresh semantics。

## 风险

- “相似字段”并不代表语义等价，例如 reasoning/thinking、tool streaming、file reference、opaque content。
- 过度翻译会导致客户端误判能力可用。

## 验收标准

- 每个资源属性都有可执行分类。
- 无损翻译路径有测试覆盖。
- 不可映射、不可无损或非 native 路径返回 hard fail，而不是静默丢字段或降级成功。
- `response.compaction` / `/responses/compact` 无 native 等价时只能进入 `UNSUPPORTED` / `native_compaction_required`，不能走本地模拟 compact。

## 测试边界

- mapper 单元测试。
- stream/tool/usage 互转回归。
- negative tests 覆盖不可翻译能力失败。

## 当前状态

- 2026-05-24：进入实施。本轮先建立跨协议资源属性无损翻译矩阵事实源，只允许 `LOSSLESS`、`NATIVE_REQUIRED`、`UNSUPPORTED` 三类结果，不引入 `LOSSY` 或 `EMULATED`。
- 2026-05-24：已新增 `LosslessTranslationMatrixService`、`LosslessTranslationMatrixEntry`、`LosslessTranslationSupport`，覆盖 OpenAI、Responses、Anthropic、Gemini 的基础 message/content/tool/tool result/stream/usage/file/image/audio/web_search 属性分类。
- 2026-05-24：已将 `response.compaction`、`reasoning.encrypted_content`、`content.file.provider_file_id`、`file.object_lifecycle`、`upload.multipart_lifecycle` 等 opaque/native-only 属性标为 native required；未声明属性默认 unsupported。
- 2026-05-24：已新增报告 [REP-20260524-002](../../docs/reports/REP-20260524-002-lossless-translation-matrix.md)，记录第一阶段矩阵和失败优先规则。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests"`。
- 2026-05-24：已将矩阵接入 `TranslationExecutionPlanCompiler`。跨协议请求命中 `NATIVE_REQUIRED` 或 `UNSUPPORTED` 时会进入 `ExecutionKind.BLOCKED`、`SupportStatus.BLOCKED`、`degradationLevel=UNSUPPORTED`；同协议 native route 不触发翻译校验。
- 2026-05-24：已补 `TranslationExecutionPlanCompilerLosslessMatrixTests` 和请求体属性 negative tests，覆盖 provider file id 阻断、纯文本无损通过、非会话资源 prompt 不误判为 `content.text`。
- 2026-05-24：已同步 conformance baseline 和 site fixture：OpenAI surface 到 Gemini native 的图片编辑、图片变体、音频翻译，以及 OpenAI surface 到 Anthropic file object create/get/content/delete 均改为 `BLOCKED`；本地 file list 和 Google native 同协议资源仍保持原能力。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanServiceTests"`。
- 2026-05-24：已把 blocked plan 执行拦截接入 `GatewayResourceExecutionService` 的 JSON、binary、multipart 资源执行入口；planner 阻断会在解析上游凭证和调用 executor 前失败，并记录 lifecycle failure，不触发 credential cooldown。
- 2026-05-24：已新增 `GatewayResourceExecutionServiceTests.shouldRejectBlockedPlanBeforeCredentialAndExecutorUse`，验证 blocked plan 不会使用上游凭证、不调用 executor、不把逻辑阻断计入候选 cooldown。
- 2026-05-24：本任务继续保持进行中，后续仍需把 mapper negative tests、public docs/OpenAPI 矩阵引用和 smoke 分类进一步补齐。
- 2026-05-24：审计发现旧能力层仍存在 `InteropCapabilityLevel.EMULATED` / `LOSSY` 与 `ALLOW_LOSSY` 语义。它们不能作为跨协议资源属性翻译成功条件，已拆分并完成 [TASK-20260524-001-08](../done/TASK-20260524-001-08-degraded-capability-layer-isolation.md)，隔离 degraded 展示/观测层与无损翻译执行层。
- 2026-05-24：`001-08` 已开始把 `ALLOW_LOSSY` / `ALLOW_EMULATED` 与 blocked plan 守门分离；本任务仍保持 In Progress，待主线验证确认矩阵阻断、mapper negative tests、smoke 分类和 public docs/OpenAPI 引用一致后再评估归档。
- 2026-05-24：补充 service 层 mapper negative tests：`GatewayChatExecutionServiceTests` 新增 OpenAI `input_file.file_id -> Anthropic` 与 Gemini `fileData.fileId -> OpenAI` 的真实 mapper + 真实 `TranslationExecutionPlanCompiler` 组合负例，验证 `content.file.provider_file_id` 会在 runtime、credential resolver 和 cooldown 前以 `native_route_required` 阻断，不能被跨协议翻译为成功。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests"`。
- 2026-05-24：核心矩阵执行链验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"`。
- 2026-05-24：补充 smoke fixture/verifier 分类证据。`functional-provider-smoke-record-replay-fixture.sample.json` 已固定为 official `XIAOMI_MIMO` / `XIAOMI_MIMO_OPENAI_COMPATIBLE` 样本并同时覆盖 `PASS=1`、`FAIL=1`、`UNSUPPORTED=1`、`BUDGET_BLOCKED=1`；`FunctionalProviderSmokeRecordReplayFixtureVerifier` 已加入禁入 provider/protocol、provider/protocol 配对、requestPreview 范围一致性和分类证据校验，防止 generic `OPENAI_COMPATIBLE`、Dify/OpenRouter/Together/Fireworks/SiliconFlow 进入 official smoke fixture。
- 2026-05-24：smoke fixture/verifier 验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests"`。
- 2026-05-24：conformance 收口发现并修复 `cohere-openai-chat` 旧期望：Cohere/Jina 只作为 native embed/rerank provider，不再把 OpenAI chat surface 作为成功证据；fixture 已改为 `BLOCKED` / `UNSUPPORTED`，但模型仍可作为 embedding-capable provider 可见。
- 2026-05-24：`001-03` 合并验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`。
- 2026-05-24：归档。无损翻译矩阵、执行计划阻断、runtime 前 hard-fail、mapper negative tests、smoke PASS/FAIL/UNSUPPORTED 分类、public docs/OpenAPI 引用与 conformance 主线验证均已闭环；剩余 Cohere/Jina 真实 key live smoke 与 fixture 样本固化继续由 [TASK-20260524-001-07](../in-progress/TASK-20260524-001-07-native-executor-smoke-for-embed-rerank-providers.md) 承接。
