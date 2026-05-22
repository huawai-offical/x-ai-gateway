# REQ-20260522-017 上游凭证多协议入口绑定

## 背景

厂商/API 入口已经支持多个协议入口，例如 MiMo 与 DeepSeek 同时提供 OpenAI-compatible 与 Anthropic-compatible Base URL。当前上游凭证创建流程仍只能选择一个 `protocolEndpointId`，导致同一个真实 API Key 若要同时服务 OpenAI 与 Anthropic 协议，需要用户重复创建多条凭证并重复填写同一个 secret。这个体验容易出错，也会让“厂商允许多协议入口”的能力停在配置层，未贯穿到凭证创建主流程。

## 目标

- API Key 上游凭证创建支持一次选择多个厂商协议入口。
- 后端按所选协议入口把同一个 secret 展开为多条 `upstream_credential` 记录，每条记录绑定一个 `protocolEndpointId`，并从对应入口继承 provider type、Base URL、site kind 和 conversation profile。
- 前端新增凭证流程把“厂商协议入口”改为可多选；编辑单条凭证时仍保持单入口编辑，因为一条凭证记录只代表一个执行入口。
- 批量导入多个 secret 时，支持 `secret 数量 * 协议入口数量` 的展开创建。
- 运行时候选展开时，优先读取凭证绑定的协议入口元数据，避免 Anthropic-compatible 凭证仍被站点档案的 OpenAI-compatible 字段误识别。

## 范围

- `CredentialRequest` 增加多协议入口输入。
- `CredentialAdminService` 增加多入口创建逻辑，并把重复检测收敛到 `fingerprint + provider + baseUrl + siteProfile + protocolEndpointId`。
- `CredentialAdminController` 增加多入口创建 API。
- `ModelCatalogQueryService` 在构建候选时优先使用 `ProviderProtocolEndpointEntity` 的协议入口元数据。
- 上游凭证前端创建表单、批量导入逻辑和相关测试。
- 本地文档、任务和索引回写。

## 非目标

- 不恢复凭证级手填 Base URL 作为主流程。
- 不回退或兼容旧 `allowedProtocols` 字段。
- 不把单条凭证编辑改成跨多入口批量编辑。
- 不在本轮执行真实外部厂商 API 调用。
- 不重写所有 runtime executor，只修正候选事实源，保证后续执行器能拿到正确协议入口事实。

## 验收标准

- 创建 API Key 凭证时可以选择多个协议入口。
- 同一个 secret 绑定两个协议入口时，数据库生成两条凭证记录，分别绑定不同 `protocolEndpointId`。
- MiMo/DeepSeek 这类双协议厂商可以通过一次创建同时生成 OpenAI-compatible 与 Anthropic-compatible 上游凭证。
- 运行时模型候选使用协议入口的 provider type、site kind 和 Base URL。
- 后端定向测试、前端 typecheck 与凭证页测试通过。

## 风险

- 一次请求展开多条凭证后，前端成功提示需要准确表达实际创建数量。
- 如果某个入口已存在相同 fingerprint 凭证，本轮保持事务性失败，避免半成功造成难以理解的状态。
- 候选展开改用协议入口元数据后，需要保留无 `protocolEndpointId` 的历史凭证兜底路径。

## 当前状态

Done

## 实现结果

- `CredentialRequest` 增加 `protocolEndpointIds`，新增 `/admin/credentials/multi-endpoint`，API Key 创建可一次选择多个厂商协议入口。
- `CredentialAdminService` 会按每个协议入口展开创建一条 `UpstreamCredentialEntity`，并从入口继承 provider type、Base URL、site kind 和 conversation profile。
- 多入口创建时自动在凭证名后追加入口显示名，方便区分同一个 secret 展开的多条凭证。
- 凭证重复检测已纳入 `protocolEndpointId`，避免同 Base URL 不同协议入口被误判为重复。
- `ModelCatalogQueryService` 在候选展开时优先读取凭证绑定的协议入口元数据，无入口的历史凭证继续使用站点档案兜底。
- 上游凭证创建 UI 的厂商协议入口改为多选；编辑单条凭证仍保持单入口编辑。
- 批量 secret 导入会按 `secret 数量 * 协议入口数量` 展开创建，并按返回数量统计成功条数。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests"`
- `bun run typecheck`
- `bun run test -- credentials-page`
- `bun run test -- credentials-page provider-site-detail-page provider-sites-page`

## 遗留问题与后续建议

- 本轮未执行真实外部厂商 API 调用；MiMo/DeepSeek 双入口真实 smoke 可在后续测试窗口执行。
- 单条凭证编辑仍只编辑一个协议入口，这是当前数据模型的刻意边界；跨入口批量修改可作为后续增强单独设计。
