# TASK-20260524-001-07 Cohere / Jina Native Executor 与 Smoke 闭环

状态：In Progress  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-001](TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md) / [REP-20260524-001](../../docs/reports/REP-20260524-001-native-adapter-minimum-contract.md)

## 背景

Cohere 与 Jina 在默认核心 provider catalog 中以 `cohere_native`、`jina_native` 声明 embed/rerank native contract。但当前运行时 `ProviderType` 没有独立的 Cohere/Jina executor 身份，能力判断中也容易把 OpenAI-compatible chat/resource 假设外推到非 chat 厂商。按当前网关定义，在 native executor / smoke 证据闭环前，相关能力只能明确 hard-fail 或 unsupported，不能用 generic OpenAI-compatible、emulation、degraded 或模拟返回证明可用。

## 目标

- 为 Cohere / Jina 建立最小 native executor 或明确的 resource executor 接入点。
- 将 embed/rerank native smoke 与 provider catalog contract 对齐。
- 确保 Cohere/Jina 不被误认为支持 chat lifecycle、Files/Uploads 或 OpenAI-style resource 全家桶。

## 非目标

- 不把 Cohere/Jina 扩展为通用 chat provider。
- 不承诺 Cohere/Jina 官方 API 全量覆盖。
- 不用 OpenAI-compatible 通道伪装 native rerank/embed 成功。

## 输入

- `src/main/resources/provider-catalog.json`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/ExecutionSupportMatrixService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthService.java`
- 现有 embeddings / rerank resource executor。

## 输出

- Cohere/Jina native executor 或显式 adapter boundary。
- record/replay 或 dry-run smoke fixture。
- capability truth 中 embed/rerank 与 chat/resource lifecycle 分离。

## 影响范围

- resource execution、capability matrix、provider catalog、smoke harness、public docs。

## 依赖

- `TASK-20260524-001-02` native adapter 最小契约。
- `TASK-20260524-001-06` provider-specific runtime profile 拆分可作为后续增强，但不阻塞本任务先建立 resource-level executor。

## 风险

- 如果只写 catalog contract，没有 executor/smoke 证据，下游会误以为 native 能力已可用。
- embed/rerank 返回结构不同，不能使用 ad hoc 字符串转换。

## 验收标准

- Cohere/Jina 的 embed/rerank native 能力有明确执行路径或明确硬失败。
- smoke harness 能给出 PASS/FAIL/UNSUPPORTED，而不是 generic OpenAI-compatible 通过。
- docs 与 capability matrix 不宣称 Cohere/Jina 支持 chat/files/uploads。
- 未闭环前，Cohere/Jina 相关非 native、不可映射或不可无损路径保持 hard-fail / unsupported。

## 测试边界

- resource executor 单元测试。
- smoke dry-run / record replay fixture verifier。
- capability truth negative tests。

## 关联文档

- [REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)
- [REP-20260524-001](../../docs/reports/REP-20260524-001-native-adapter-minimum-contract.md)

## 关联任务

- 父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)
- 相关任务：[TASK-20260524-001-02](../done/TASK-20260524-001-02-native-adapter-minimum-contract.md)

## 当前状态

- 2026-05-24：由 native adapter 现状审计拆分进入 backlog。当前 catalog contract 已存在，executor/smoke 证据仍未闭环。
- 2026-05-24：保持 Backlog，不凭空标 Done；原因是 Cohere/Jina embed/rerank native executor、record/replay 或 dry-run smoke 仍未提供可验证证据，当前只能依赖 hard-fail / unsupported 边界避免下游误判。
- 2026-05-24：进入实施。第一步只处理 Cohere/Jina resource-level 最小闭环：审计并收紧 capability truth，补 Cohere `/v2/embed`、`/v2/rerank` 与 Jina `/v1/embeddings`、`/v1/rerank` native executor；smoke harness / record-replay verifier 只接受 `COHERE_NATIVE`、`JINA_NATIVE` 的 `EMBEDDINGS` / `RERANK` 家族，并对 chat/files/uploads 返回 `UNSUPPORTED`，不借用 generic OpenAI-compatible 或 MiMo chat fixture 证明成功。
- 2026-05-24：最小代码闭环已完成但任务保持 In Progress。已新增 Cohere/Jina resource-level native executor，Cohere `/v1/embeddings` 转 `/v2/embed`、`/v1/rerank` 转 `/v2/rerank`，Jina `/v1/embeddings` 与 `/v1/rerank` 走 native endpoint；Cohere `compatibility/v1` baseUrl 明确 hard-fail。
- 2026-05-24：capability truth 与 execution matrix 已收紧：Cohere/Jina 不再继承 generic OpenAI-compatible chat/tools/files/uploads 成功假设，embed/rerank 保持 native；generic embeddings executor 不再接管 Cohere/Jina。
- 2026-05-24：functional provider smoke 与 record/replay verifier 已补 `COHERE_NATIVE` / `JINA_NATIVE` 分类，默认 family 仅为 `EMBEDDINGS` / `RERANK`，chat/files/uploads family 返回 `UNSUPPORTED` 或被 verifier 拒绝；`Bearer ***` 作为已脱敏 header 被 verifier 接受，未脱敏 token 仍会失败。
- 2026-05-24：record/replay fixture 样本已固化为独立 `COHERE_NATIVE` 与 `JINA_NATIVE` 样本文件，均覆盖 `PASS`、`FAIL`、`UNSUPPORTED` 分类；verifier 已进一步收紧，Cohere/Jina 非 `EMBEDDINGS` / `RERANK` family 只能记录 `UNSUPPORTED`，不能把 chat/files/uploads 标成成功。
- 2026-05-24：smoke evidence 与 executor 结构断言已补齐。Cohere embed PASS 证据必须包含 `embeddings.float` 与 `meta.billed_units.input_tokens`，Cohere rerank PASS 证据必须包含 `results[].relevance_score` 与 `meta.billed_units.search_units`；Jina embeddings/rerank PASS 证据必须能证明 embedding data 或 rerank result。当前环境未发现 `COHERE` / `JINA` 相关环境变量名，真实 key live smoke 仍不可执行，不能归档为 Done。
- 2026-05-24：新增真实 key live gate 测试 `FunctionalProviderSmokeLiveGateTests`。该测试默认不联网，只有同时设置 `XAI_GATEWAY_FUNCTIONAL_PROVIDER_LIVE_SMOKE=true`、`XAI_GATEWAY_ALLOW_BILLABLE_SMOKE=true` 和 `COHERE_API_KEY` / `XAI_GATEWAY_COHERE_API_KEY`、`JINA_API_KEY` / `XAI_GATEWAY_JINA_API_KEY` 时才访问真实 Cohere/Jina；live 成功后必须生成可被 `FunctionalProviderSmokeRecordReplayFixtureVerifier` 接受的 record/replay fixture。当前无 gate/key 环境下命令通过但 JUnit 报告为 `tests=2 skipped=2 failures=0 errors=0`，不计入任务完成。
- 2026-05-24：focused tests 已通过：
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.EmbedRerankNativeGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.EmbeddingsGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"`
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.EmbedRerankNativeGatewayResourceExecutorTests"`
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests"`
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests"`
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.EmbedRerankNativeGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.EmbeddingsGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"`
  - `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeLiveGateTests"`：当前无 live gate / key，测试报告为 skipped，不作为 live smoke 完成证据。

## 实施切片

- [x] 资源执行：Cohere/Jina embeddings/rerank native executor；非 embed/rerank 路径 hard-fail / unsupported。
- [x] 能力真相：Cohere/Jina embed/rerank 与 chat/files/uploads/resource lifecycle 分离。
- [x] Smoke / fixture：dry-run、record/replay verifier 能区分 `PASS`、`FAIL`、`UNSUPPORTED`，并拒绝把 Cohere/Jina 归入 chat/files/uploads provider。
- [x] Fixture 样本：固化 Cohere/Jina 独立 sample fixture，样本 evidence 与 smoke client 实际输出字段一致。
- [x] 结构证据：补 Cohere embed/rerank 官方响应结构断言和 smoke evidence 校验，避免只凭 HTTP 200 宣称成功。
- [x] Live gate：新增真实 key live smoke 的显式双 gate 测试入口；无 key 时只能 skipped，不产生成功证据。
- [x] 验证：补 focused tests，运行 executor、capability truth、smoke verifier/client 定向测试。

## 剩余项

- 使用真实 Cohere/Jina key 执行受控 live smoke；当前环境未发现 `COHERE` / `JINA` 相关环境变量名，本轮只能保留为外部前置条件，不能用 dry-run、record/replay、skipped gate 或模拟结果替代。
- 等真实 key live smoke 补齐后，再评估是否将本任务移动到 `tasks/done/`。
