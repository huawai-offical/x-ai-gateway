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
- 存量凭证没有 protocol endpoint 时需要兼容已有 site profile；后续可做迁移任务逐步补全。
- 多协议 endpoint 的 conversation profile 需要持续沉淀真实厂商差异，本轮先落结构和默认值。

## 当前状态

In Progress
