# REQ-20260522-013 厂商多协议入口与对话兼容画像升级

## 背景

厂商/API 入口已经成为“厂商 -> 上游凭证 -> 账号组 -> 分发 Key”的主配置路径。用户批准将厂商允许协议从单入口单协议扩展为多协议入口：同一厂商可能同时提供 OpenAI-compatible 与 Anthropic-compatible API，例如 MiMo 和 DeepSeek 分别存在 `/v1` 与 `/anthropic` 两类 Base URL。若只在站点档案上做协议多选，会把不同协议的路径、鉴权、流式事件和请求/响应转换混在一个 Base URL 上，无法准确支撑 Codex `/responses`、OpenAI Chat、Anthropic Messages 和 reasoning_content 等差异。

## 目标

- 在厂商/API 入口下新增“协议入口”结构，支持一个站点档案拥有多个协议簇 endpoint。
- 每个协议入口独立保存 protocol suite、Base URL、provider type、site kind、鉴权策略、路径策略、错误结构、stream transport 和 conversation profile。
- 默认预设导入时自动生成至少一个协议入口；MiMo、DeepSeek 等多协议厂商可以生成 OpenAI-compatible 与 Anthropic-compatible 两个入口。
- API Key 上游凭证绑定到具体协议入口，并从协议入口派生 provider type 与 Base URL。
- 厂商管理 UI 展示并管理协议入口，新增/编辑上游凭证时选择具体协议入口。
- 对话兼容画像作为协议入口的转译策略输入，服务于 Responses/Chat/Messages、reasoning_content、tools、stream 和 usage 等差异化处理。

## 范围

- 后端新增协议入口 entity、repository、request/response、管理 API 和 Liquibase changeset。
- 后端默认 provider preset 导入时生成协议入口。
- 后端上游凭证创建/更新从 `siteProfileId` 升级为优先绑定 `protocolEndpointId`，保留 `siteProfileId` 用于站点归属。
- 前端厂商管理中心展示协议入口清单，并提供基本新增/编辑/删除入口能力。
- 前端上游凭证创建/编辑从选择站点档案改为选择具体协议入口。
- 文档、任务与索引回写。

## 非目标

- 不一次性完成所有 runtime 路由根据请求协议自动切换 endpoint 的深度改造。
- 不在本轮执行真实外部厂商调用。
- 不回退到凭证级手填 Base URL 的主流程。
- 不把 OAuth/auth.json 账号导入强制改为协议入口绑定。

## 验收标准

- 数据库存在协议入口表，并与 `upstream_site_profile`、`upstream_credential` 建立关联。
- 默认厂商 API 入口导入后，会自动生成对应协议入口。
- API Key 凭证保存可以选择具体协议入口，并从入口继承 provider type 与 Base URL。
- MiMo/DeepSeek 这类厂商可以表达 OpenAI-compatible 与 Anthropic-compatible 两种协议入口。
- 厂商管理 UI 可以看到协议入口列表；上游凭证 UI 可以选择协议入口。
- 后端定向测试、前端 typecheck 与相关前端测试通过。

## 风险

- 现有运行时仍以 credential 的 provider type/baseUrl 作为主要事实源，本轮先通过凭证继承协议入口来推进，不强行改全链路。
- 存量凭证没有 protocol endpoint 时需要兼容已有 site profile；已通过 `REQ-20260522-015` 增加启动期保守回填。
- 多协议 endpoint 的 conversation profile 需要持续沉淀真实厂商差异，本轮先落结构和默认值。

## 当前状态

Done

## 实现结果

- 新增 `provider_protocol_endpoint` 厂商协议入口表，并让 `upstream_credential` 记录 `protocol_endpoint_id`，凭证唯一约束同步纳入协议入口维度。
- 新增协议入口 entity、repository、request/response DTO 与 `/admin/provider-sites/{id}/protocol-endpoints` 管理 API，支持列表、新增、编辑和删除。
- 默认厂商预设导入会自动生成协议入口；MiMo 与 DeepSeek 会生成 OpenAI-compatible 和 Anthropic-compatible 两个入口。
- API Key 上游凭证创建必须绑定具体协议入口，并从入口继承 `providerType`、`siteKind` 和 `baseUrl`；存量凭证读取与编辑仍保留站点档案兜底。
- 厂商管理详情页展示协议入口、Base URL、协议簇、绑定凭证数和入口级 conversation profile，并提供入口维护弹窗。
- 上游凭证创建/编辑流程改为选择“厂商协议入口”，Base URL 改为由入口派生，避免用户重复手填厂商 URL。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`
- `bun run typecheck`
- `bun run test -- credentials-page provider-site-detail-page provider-sites-page`
- 当前本地库 `x_ai_gateway` 已执行 Liquibase `0004-provider-protocol-endpoints`，并确认存在 `provider_protocol_endpoint` 表与 `upstream_credential.protocol_endpoint_id` 字段。
- 当前本地库已确认默认导入 MiMo/DeepSeek 双协议入口：`xiaomi_mimo.openai_compatible`、`xiaomi_mimo.anthropic_compatible`、`deepseek.openai_compatible`、`deepseek.anthropic_compatible`。

## 遗留问题与后续建议

- 本轮没有执行真实外部厂商调用；MiMo 与 DeepSeek 的真实双协议 smoke 可在后续接入测试窗口执行。
- runtime 深层路由仍主要使用凭证上的 provider type 与 Base URL；本轮通过凭证继承协议入口先把配置事实源串起来，后续可继续推进“按入口协议动态选择执行器”。
- 存量凭证保守回填已在 `REQ-20260522-015` 完成；无法唯一匹配的历史凭证仍需人工选择协议入口。
