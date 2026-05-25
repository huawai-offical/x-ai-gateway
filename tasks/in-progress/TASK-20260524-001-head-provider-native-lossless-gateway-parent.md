# TASK-20260524-001 头部自有模型厂商 Native 与无损翻译网关总控父任务

状态：In Progress  
优先级：Critical  
类型：父任务  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

用户明确 x-ai-gateway 的总体目标是：客户端可使用 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等头部自研大模型厂商 API/profile 接入网关，网关负责 native 转发、路由和可证明无损的资源属性互转。当前产品定义只承诺头部自研模型厂商 native API / provider-specific native profile 与可证明无损翻译；项目不再追求长尾 provider 广度，也不保留 Dify 等非自有模型编排平台作为默认核心厂商，xAI 继续保留在支持范围。进入支持范围的厂商必须具备 native API 或 provider-specific native profile；不可对应、不可映射、不可无损或非 native 能力必须直接失败，不能用 emulation、degraded 成功、local fake、模拟返回、metadata/header 标记或模糊提示让客户端误以为可用。

## 目标

- 建立头部自有模型厂商支持清单和清理清单。
- 收敛 provider catalog、默认导入、厂商管理 UI 与能力矩阵。
- 建立 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等头部厂商 native API/profile adapter 要求。
- 建立跨协议资源属性无损翻译矩阵。
- 建立不可对应能力直接失败的统一错误语义。
- 固定 `/responses/compact` 无 native 等价时的 `unsupported` / `native_compaction_required` 语义。
- 回写 public docs、OpenAPI、SDK 示例、smoke 和任务状态。

## 非目标

- 不支持所有 OpenAI-compatible 聚合器、workflow 编排平台或非自有模型厂商；Dify 等是非目标，只能删除或明确标为非目标。
- 不追求官方 API 全量覆盖。
- 不用 lossy translation、local emulation、degraded 返回、local fake、模拟返回、metadata/header 标记替代失败。
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
- OpenAI/Anthropic/Gemini/MiMo/DeepSeek/xAI native API/profile adapter 和 resource mapper。
- route selection、capability matrix、conformance matrix、public docs、OpenAPI snapshot。
- `tasks/backlog/`、`tasks/in-progress/`、`tasks/done/` 与 `tasks/index.md` 中本需求相关任务 spec 和状态口径。

## 依赖

- 当前厂商管理中心、协议入口、凭证绑定和账号分组运行时。
- 真实 smoke key 和可控成本窗口。
- 既有 `TASK-20260523-013/014/015/016` 厂商领域模型与 UI 任务。

## 风险

- 清理 provider preset 可能影响已有凭证和测试 fixture。
- 无损翻译边界如果不严格，会继续产生 fake success。
- native adapter 工作量大，需要按厂商和资源族拆分推进。
- 历史任务或观测字段中残留 emulated/degraded 术语，若不标清执行层硬失败语义，会让下游继续误判能力可用。

## 验收标准

- 核心支持厂商清单明确，非核心 provider 不再进入默认核心 preset。
- 支持范围内厂商 API 均有 native API/profile 能力路径或明确阻断任务。
- 跨协议资源属性只允许可证明无损翻译；不可对应、不可映射、不可无损、非 native 能力直接 hard-fail。
- `/responses/compact` 无 native 等价时必须返回 `unsupported` / `native_compaction_required`，不得返回本地模拟 compact 成功。
- 任何阻断场景不得返回 emulation/degraded/local fake/模拟成功对象，也不得仅用 metadata/header 标记提醒下游。
- 文档、OpenAPI、catalog、UI 与测试边界一致。
- 子任务状态真实更新，未完成项保留在 backlog。

## 测试边界

- 父任务以任务治理和事实源一致性为主。
- 具体测试由子任务定义，包括 catalog loader tests、adapter tests、translation matrix tests、public OpenAPI snapshot tests 和 smoke harness。

## 关联任务

- [TASK-20260524-001-01](../done/TASK-20260524-001-01-provider-catalog-core-vendor-prune.md)
- [TASK-20260524-001-02](../done/TASK-20260524-001-02-native-adapter-minimum-contract.md)
- [TASK-20260524-001-03](../done/TASK-20260524-001-03-lossless-translation-matrix.md)
- [TASK-20260524-001-04](../done/TASK-20260524-001-04-unsupported-capability-hard-fail.md)
- [TASK-20260524-001-05](../done/TASK-20260524-001-05-docs-openapi-smoke-alignment.md)
- [TASK-20260524-001-06](../done/TASK-20260524-001-06-provider-specific-runtime-profile-split.md)
- [TASK-20260524-001-07](TASK-20260524-001-07-native-executor-smoke-for-embed-rerank-providers.md)
- [TASK-20260524-001-08](../done/TASK-20260524-001-08-degraded-capability-layer-isolation.md)

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
- 2026-05-24：补充 native adapter 当前实现审计：OpenAI/Anthropic/Gemini 已有真实 runtime adapter；MiMo、DeepSeek、xAI、Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity 仍主要是 provider-specific OpenAI-compatible contract，runtime 身份仍共用 `OPENAI_COMPATIBLE`；Cohere/Jina 有 native contract 但 executor/smoke 证据未闭环。已拆分 `001-06`、`001-07`、`001-08` 到 backlog，承接 provider-specific runtime profile、embed/rerank native executor 与 degraded 能力层隔离。
- 2026-05-24：`TASK-20260524-001-05` 已完成并归档：SDK 示例、functional service matrix、smoke harness、record/replay fixture、verifier 和真实 certification 输出已统一到核心 provider/native/provider-specific 口径；MiMo 使用 `XIAOMI_MIMO_*` provider-specific protocol，非 MiMo compatible alias 不再冒充 MiMo。
- 2026-05-24：`TASK-20260524-001-06` 进入实施。首批采用 runtime profile/descriptor 方式拆分 provider-specific 身份，不直接扩大数据库枚举；目标是让 MiMo、DeepSeek、xAI 等具名 OpenAI-compatible 厂商在 smoke、能力判断和观测里不再表现为默认 generic `OPENAI_COMPATIBLE`。
- 2026-05-24：`TASK-20260524-001-06` 已完成并归档。新增 `ProviderRuntimeProfile`，MiMo/DeepSeek/xAI 可由 `UpstreamSiteKind`、vendor/baseUrl 推断运行时身份；route affinity、Distributed Key provider 白名单、interop plan debug、route decision candidate summary、metrics、functional smoke/record-replay fixture 与非持久化 migration 记录已补 runtime provider 维度，不再作为 generic OpenAI-compatible 证据。
- 2026-05-24：文档口径同步已由 subagent 完成并回收。新增 `REP-20260524-003`，并同步 `docs/index.md`、公开兼容文档、coverage matrix、media executor、provider smoke/pricing 与长尾 web search 文档，把当前网关定义收口为头部自有模型厂商 native/profile、lossless-only 与 hard-fail。
- 2026-05-24：`TASK-20260524-001-08` 进入实施，聚焦把历史 degraded 能力层从真实执行成功条件中隔离，确保 `ALLOW_LOSSY` / `ALLOW_EMULATED` 不能绕过 Lossless Translation Matrix 或 native-required 阻断。
- 2026-05-24：任务文件已按最新产品口径同步：网关只承诺头部自研大模型厂商 native API/profile 与可证明无损互转；Dify 等非自有模型厂商删除或标为非目标；不可映射、不可无损或非 native 能力必须 hard-fail，不做模拟、降级返回、local fake 或 metadata/header 成功标记。
- 2026-05-24：`TASK-20260524-001-08` 已完成并归档。执行层已隔离 `ALLOW_LOSSY` / `ALLOW_EMULATED` 与成功判定，catalog 旧 hint 已迁移，错误规则旧降级默认语义改为阻断，Responses object、file lifecycle、media resource、tool streaming 等高风险路径已通过 targeted 回归。父任务仍保持 In Progress，剩余为 `001-02`、`001-03`、`001-04` 与 `001-07`。
- 2026-05-24：当时剩余未 Done 子任务为 4 个：`001-02` native adapter 最小契约、`001-03` 无损翻译矩阵、`001-04` 假成功清理、`001-07` Cohere/Jina native executor 与 smoke。
- 2026-05-24：`TASK-20260524-001-07` 已从 backlog 移入 in-progress，当前切片聚焦 Cohere/Jina resource-level native executor、capability truth 与 smoke/record-replay PASS/FAIL/UNSUPPORTED 分类，不改 `001-03/04/06` 任务文件。
- 2026-05-24：`TASK-20260524-001-07` 最小代码闭环已完成但保持 In Progress：Cohere/Jina resource-level native executor、capability truth 分离、functional smoke dry-run/record-replay verifier 与 focused tests 已通过；当时剩余为真实 key live smoke 和 fixture 样本固化。
- 2026-05-24：`TASK-20260524-001-04` 已完成并归档。Responses compact/input_tokens/file_search、resource blocked plan、media native route required、Realtime current-down 文档与公开事实源已完成 hard-fail / native-required 收口；当时父任务剩余未 Done 子任务为 3 个：`001-02`、`001-03`、`001-07`。
- 2026-05-24：`TASK-20260524-001-02` 已完成并归档。native adapter 最小契约、provider catalog 事实源、Admin/Public 透出、provider-specific OpenAI-compatible smoke 协议/path adapter 与 contract drift 验证已闭环；当时父任务剩余未 Done 子任务为 2 个：`001-03` 无损翻译矩阵 mapper/smoke/docs 尾项、`001-07` Cohere/Jina live smoke 与 fixture 样本。
- 2026-05-24：`TASK-20260524-001-03` 已完成并归档。Lossless Translation Matrix、blocked plan、runtime 前 hard-fail、mapper negative tests、smoke PASS/FAIL/UNSUPPORTED 分类、public docs/OpenAPI 和 conformance 验证已闭环；父任务当前剩余未 Done 子任务为 1 个：`001-07` Cohere/Jina live smoke 与 fixture 样本。
- 2026-05-24：`TASK-20260524-001-07` 已补齐本地可证明部分：Cohere/Jina 独立 record/replay sample fixture、Cohere/Jina native PASS/FAIL/UNSUPPORTED verifier、非 embed/rerank family 成功禁入、Cohere embed/rerank 官方响应结构 evidence 与定向回归均已通过。当前环境未发现 `COHERE` / `JINA` 相关环境变量名，真实 key live smoke 仍不可执行；父任务剩余未 Done 子任务仍为 1 个：`001-07` 的真实 key live smoke。
- 2026-05-24：`TASK-20260524-001-07` 已增加真实 key live gate 测试入口。`FunctionalProviderSmokeLiveGateTests` 使用双环境变量 gate 和 provider key 触发真实 Cohere/Jina native live smoke；当前无 gate/key 环境下测试报告为 `tests=2 skipped=2 failures=0 errors=0`，只能证明不会伪成功，不能证明 live smoke 已完成。父任务剩余未 Done 子任务仍为 1 个：`001-07` 的真实 key live smoke。
