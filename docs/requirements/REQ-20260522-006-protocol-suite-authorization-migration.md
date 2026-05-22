# REQ-20260522-006 分发 Key 协议簇授权迁移

## 背景

创建分发 Key 时原字段 `allowedProtocols` 表达的是入口协议或 endpoint 面，例如 `openai`、`responses`、`anthropic`、`gemini`。随着新版 Codex 只支持 `/v1/responses`、部分上游只兼容 `/v1/chat/completions`，以及 MiMo、DeepSeek 等厂商在 OpenAI-compatible 语义下存在不同请求细节，单纯按入口协议授权会把“客户端入口”和“上游厂商协议簇”混在一起。

用户明确要求不保留旧字段兼容，不接受在代码中长期保留 `allowedProtocols` 与新字段双轨逻辑；本需求直接迁移为 `allowedProtocolSuites`，把授权维度提升到厂商协议簇。

## 目标

- 将分发 Key 与访问组上的 `allowedProtocols` 统一迁移为 `allowedProtocolSuites`。
- 让 `allowedProtocolSuites` 表达厂商协议簇，例如 `openai.native`、`deepseek.openai_compatible`、`xiaomi_mimo.openai_compatible`。
- 路由和模型目录查询按候选站点的 `siteKind` 映射协议簇后执行授权判断。
- 保留 `supportedProtocols` 在站点、模型、账号分组层面的能力语义，用于表达入口协议或模型能力，不与授权字段混淆。
- 前端创建 Key、Key 详情、访问组管理、门户自助创建统一展示和提交协议簇。

## 范围

- 后端 Entity、DTO、Admin Service、Portal Service、DistributedKey 查询视图、访问组权益解析。
- 路由选择和模型目录查询中的协议授权判断。
- Liquibase 迁移：直接重命名列并迁移存量 JSON 值，不保留旧列。
- 前端 Key、访问组、门户、Codex onboarding 与类型定义中的字段命名和展示。
- 相关后端单元测试与前端类型/测试同步。

## 非目标

- 不保留 `allowedProtocols` 请求字段、响应字段、Java getter/setter 或前端 DTO 类型。
- 不把站点/模型的 `supportedProtocols` 改名；它仍表示 capability，不表示授权策略。
- 不在本任务内完成 `/v1/responses` 到 `/v1/chat/completions` 的协议转换细节。
- 不清理用户已经录入的真实 MiMo、DeepSeek key；迁移脚本只做字段和 JSON 值转换。

## 协议簇定义

首批协议簇以 `vendor_or_family.transport_family` 命名：

- `openai.native`
- `openai_compatible.generic`
- `azure_openai.openai_compatible`
- `deepseek.openai_compatible`
- `xiaomi_mimo.openai_compatible`
- `qwen.openai_compatible`
- `moonshot.openai_compatible`
- `siliconflow.openai_compatible`
- `volcengine.openai_compatible`
- `minimax.openai_compatible`
- `dify.openai_compatible`
- `grok.openai_compatible`
- `mistral.openai_compatible`
- `cohere.openai_compatible`
- `jina.openai_compatible`
- `together.openai_compatible`
- `fireworks.openai_compatible`
- `openrouter.openai_compatible`
- `perplexity.openai_compatible`
- `anthropic.native`
- `gemini.native`
- `vertex_ai.gemini_native`
- `ollama.native`

空 `allowedProtocolSuites` 表示不限制协议簇，仍需通过模型、provider type、账号分组绑定和候选健康等后续过滤。

## 迁移策略

- 数据库列直接从 `allowed_protocols_json` 重命名为 `allowed_protocol_suites_json`。
- Java、API 和前端字段直接改名为 `allowedProtocolSuites`，不保留旧字段兼容。
- 存量 `openai` 或 `responses` 值迁移为 OpenAI native 与常见 OpenAI-compatible 厂商协议簇集合，避免已有 OpenAI-compatible 账号在迁移后被误拦截。
- 存量 `anthropic`、`anthropic_native` 迁移为 `anthropic.native`。
- 存量 `gemini`、`google_native` 迁移为 `gemini.native`。
- 存量 `ollama`、`ollama_native` 迁移为 `ollama.native`。

## 风险

- 字段直接改名会让仍提交旧 `allowedProtocols` 的客户端失败，这是预期行为，用于避免兼容债。
- 存量 `openai/responses` 无法准确区分具体厂商，只能迁移为较宽的 OpenAI-compatible 协议簇集合；后续仍由 provider type、模型白名单、账号分组绑定和站点能力继续收缩。
- 前端存在多个新增页面引用该字段，需要通过类型检查和 targeted tests 收口。

## 验收标准

- 后端不再存在业务字段 `allowedProtocols`、`allowed_protocols_json`。
- Admin 与 Portal 创建/更新 Key 时只接受 `allowedProtocolSuites`。
- 路由和模型目录按候选 `siteKind` 转换协议簇后判断 key 是否允许。
- Key 页面不再出现 `responses` 作为“允许协议”选项，而展示厂商协议簇。
- Liquibase 包含直接迁移列和值的 changeset。
- `compileJava`、相关后端测试、前端类型检查或 targeted tests 通过；若存在既有无关失败，需要明确标注。

## 当前状态

Done

## 实施结果

- 新增 `ProtocolSuite`，集中维护厂商协议簇 code、`UpstreamSiteKind` 映射和 MiMo/DeepSeek 等 `vendorCode` 特例。
- `DistributedKeyEntity`、`AccessGroupEntity`、Admin/Portal DTO 与服务统一迁移到 `allowedProtocolSuites`。
- `ModelCatalogQueryService` 与 `GatewayRouteSelectionService` 在候选层按 `vendorCode + siteKind` 推导协议簇并过滤；入口协议仍由 `supportedProtocols`、canonical surface 和 interop policy 判断。
- `CatalogCandidateView` 补充 `vendorCode`，用于区分 MiMo 这类挂在 OpenAI-compatible generic site kind 下的具体厂商。
- 前端 Key、Key 详情、Portal Key、Codex onboarding、用户域访问组页面改为展示/提交厂商协议簇，不再把 `responses` 当作 Key 授权选项。
- 新增 `db.changelog-0003-protocol-suite-authorization.yaml`，在不修改已执行 baseline 的前提下重命名列并迁移旧 JSON 值。历史 baseline 中的 `allowed_protocols_json` 只作为迁移前状态存在，不代表继续保留业务字段或旧 API 兼容。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`：通过。
- `.\gradlew.bat compileJava compileTestJava test --tests "com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AccessGroupAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"`：通过。
- `bun run typecheck`：通过。
- `bun run test -- keys-page key-detail-page portal-home-page portal-keys-page codex-onboarding-page account-group-detail-page access-groups-page`：通过，实际匹配并执行 4 个测试文件 13 个用例。

## 遗留说明

- `.\gradlew.bat test` 全量仍存在与本任务非同源的失败：官方账号导入测试缺少账号分组 mock、资源/endpoint conformance baseline 与现有实现漂移、缺少 `removed-object-lifecycle` fixture、部分 OpenAI Direct dry-run 资源族数量基线不一致。协议簇相关聚焦测试已通过；这些失败建议另立 conformance/test-baseline 清理任务处理。
