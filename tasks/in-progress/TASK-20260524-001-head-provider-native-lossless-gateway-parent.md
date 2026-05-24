# TASK-20260524-001 头部自有模型厂商 Native 与无损翻译网关总控父任务

状态：In Progress  
优先级：Critical  
类型：父任务  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

用户明确 x-ai-gateway 的总体目标是：客户端可使用 OpenAI、Anthropic、Gemini 等头部厂商 API 接入网关，网关负责 native 转发、路由和资源属性无损翻译。项目不再追求长尾 provider 广度，也不保留 Dify 等非自有模型编排平台作为默认核心厂商。进入支持范围的厂商必须具备 native 能力；不可对应能力必须直接失败，不能用降级成功、模糊标记或 local fake 让客户端误以为可用。

## 目标

- 建立头部自有模型厂商支持清单和清理清单。
- 收敛 provider catalog、默认导入、厂商管理 UI 与能力矩阵。
- 建立 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等厂商 native adapter 要求。
- 建立跨协议资源属性无损翻译矩阵。
- 建立不可对应能力直接失败的统一错误语义。
- 回写 public docs、OpenAPI、SDK 示例、smoke 和任务状态。

## 非目标

- 不支持所有 OpenAI-compatible 聚合器和 workflow 编排平台。
- 不追求官方 API 全量覆盖。
- 不用 lossy translation、local emulation 或 header 标记替代失败。
- 不在父任务中直接实现所有具体 adapter。

## 输入

- [REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)
- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`
- `docs/functional-service-api-coverage-matrix.md`
- 现有 provider adapter、protocol endpoint、capability matrix 与 smoke 文档。

## 输出

- 厂商保留/清理任务结果。
- native adapter 与协议入口整改结果。
- 无损翻译矩阵和失败语义。
- public docs/OpenAPI/provider catalog/smoke 更新。

## 影响范围

- provider catalog、provider preset loader、默认入口引导、厂商管理 UI。
- OpenAI/Anthropic/Gemini/MiMo/DeepSeek/xAI adapter 和 resource mapper。
- route selection、capability matrix、conformance matrix、public docs、OpenAPI snapshot。

## 依赖

- 当前厂商管理中心、协议入口、凭证绑定和账号分组运行时。
- 真实 smoke key 和可控成本窗口。
- 既有 `TASK-20260523-013/014/015/016` 厂商领域模型与 UI 任务。

## 风险

- 清理 provider preset 可能影响已有凭证和测试 fixture。
- 无损翻译边界如果不严格，会继续产生 fake success。
- native adapter 工作量大，需要按厂商和资源族拆分推进。

## 验收标准

- 核心支持厂商清单明确，非核心 provider 不再进入默认核心 preset。
- 支持范围内厂商 API 均有 native 能力路径或明确阻断任务。
- 跨协议资源属性只允许无损翻译；不可对应能力直接失败。
- 文档、OpenAPI、catalog、UI 与测试边界一致。
- 子任务状态真实更新，未完成项保留在 backlog。

## 测试边界

- 父任务以任务治理和事实源一致性为主。
- 具体测试由子任务定义，包括 catalog loader tests、adapter tests、translation matrix tests、public OpenAPI snapshot tests 和 smoke harness。

## 关联任务

- [TASK-20260524-001-01](../done/TASK-20260524-001-01-provider-catalog-core-vendor-prune.md)
- [TASK-20260524-001-02](TASK-20260524-001-02-native-adapter-minimum-contract.md)
- [TASK-20260524-001-03](TASK-20260524-001-03-lossless-translation-matrix.md)
- [TASK-20260524-001-04](TASK-20260524-001-04-unsupported-capability-hard-fail.md)
- [TASK-20260524-001-05](TASK-20260524-001-05-docs-openapi-smoke-alignment.md)

## 当前状态

- 2026-05-24：已创建父任务和子任务清单。
- 2026-05-24：进入第一阶段实施，先闭环 `TASK-20260524-001-01` 的默认核心 provider catalog 收敛；native adapter、无损翻译矩阵、不可对应能力失败语义和 OpenAPI/smoke 全量对齐仍由后续子任务承接。
- 2026-05-24：`TASK-20260524-001-01` 已完成并归档到 `tasks/done/`。当前父任务剩余工作为 `001-02` native adapter 最小契约、`001-03` 无损翻译矩阵、`001-04` 不可对应能力失败语义、`001-05` 文档/OpenAPI/smoke 全量对齐。
- 2026-05-24：`TASK-20260524-001-04` 已进入实施，优先处理 `/v1/responses/compact` 非 native route 本地 opaque marker 假成功。
- 2026-05-24：`/v1/responses/compact` 第一阶段已改为 native route required；非 native route 返回 `native_compaction_required`，公开 docs/OpenAPI 和 controller tests 已同步。`001-04` 继续保留进行中，用于后续审计其它 fake success 边界。
- 2026-05-24：`TASK-20260524-001-02` 已进入实施，先把 native adapter 最小契约落成 provider catalog 结构化事实源并接入 preset response/public docs。
- 2026-05-24：`nativeAdapterContract` 已接入 provider catalog、Admin preset response 和 Public docs response；默认核心 16 个 provider preset 已填契约。`001-02` 继续进行中，后续接入 smoke harness 与 adapter contract tests。
- 2026-05-24：`TASK-20260524-001-03` 已进入实施，先建立无损翻译矩阵代码事实源与报告；矩阵只允许 `LOSSLESS`、`NATIVE_REQUIRED`、`UNSUPPORTED`，后续接入 route validation 和 mapper negative tests。
- 2026-05-24：`001-03` 第一阶段矩阵事实源已通过聚焦测试；父任务仍保持进行中，剩余重点是 route validation、mapper negative tests、smoke harness 和 public docs/OpenAPI 全量对齐。
- 2026-05-24：`001-03` 已完成执行计划接入。`TranslationExecutionPlanCompiler` 会把无损矩阵中的 `NATIVE_REQUIRED` / `UNSUPPORTED` 转成 `BLOCKED` plan；conformance baseline 已将 OpenAI surface 到 Gemini native 的图片编辑、图片变体、音频翻译和 OpenAI surface 到 Anthropic file object lifecycle 改为硬失败。父任务仍保持进行中，剩余重点是 mapper negative tests、smoke harness、public docs/OpenAPI 全量矩阵引用和其它 fake success 审计。
- 2026-05-24：`GatewayResourceExecutionService` 已补运行时硬失败防线，确保 `BLOCKED` plan 不会继续进入上游凭证解析或 resource executor；失败会进入 lifecycle failure，并且不会把逻辑阻断误记为上游凭证 cooldown。
- 2026-05-24：`TASK-20260524-001-05` 已进入实施，第一阶段完成 public docs bundle、`docs/public-api-compatibility.md` 与 OpenAPI snapshot 对齐：公开 provider 清单移除 Dify/OpenRouter/Together/Fireworks/SiliconFlow/generic，文档与 OpenAPI 显式写入 Lossless Translation Matrix、`native_route_required`、`unsupported_translation_attribute`、`native_compaction_required` 和 media native-required 失败码。SDK 示例与 smoke harness 范围仍在该子任务后续切片中继续推进。
