# REQ-20260522-012 上游凭证绑定厂商 API 入口

## 背景

厂商/API 入口已经成为“厂商 -> 上游凭证 -> 账号组 -> 分发 Key”的主配置路径，且默认厂商 API 入口会在启动时自动导入。当前新增 API Key 上游凭证时仍以 `providerType + baseUrl` 为主要输入，用户需要重复填写 Base URL，且容易绕过厂商级 conversation profile、请求策略、错误策略和模型映射。

## 目标

- API Key 上游凭证创建时必须选择一个厂商/API 入口。
- 常规创建流程由 API 入口自动带出 provider type 与 Base URL，用户只需要填写凭证名称、Secret、账号组和可选治理字段。
- Base URL、provider type 保留为派生字段和详情展示，不再作为常规输入项。
- 后端禁止未绑定 API 入口的 API Key 凭证创建，避免继续生成游离凭证。
- 保留 OAuth/auth.json 账号类流程的既有行为，不把本轮范围扩散到官方账号导入。

## 范围

- 后端 `CredentialAdminService` 创建/更新 API Key 凭证时的 `siteProfileId` 校验和默认 Base URL 推导。
- 前端账号分组详情中的 API Key 凭证弹窗：从填写 provider/baseUrl 改为选择 API 入口。
- 前端类型、测试和文案同步。
- 本地任务与索引回写。

## 非目标

- 不自动创建真实 API key。
- 不改变账号分组、分发 Key、模型策略和协议簇授权的数据结构。
- 不处理 OAuth/auth.json 账号导入中的 `siteProfileId` 可选字段。
- 不删除 API 入口管理中的自定义入口能力。

## 验收标准

- 新增 API Key 凭证必须提交 `siteProfileId`。
- 选择 API 入口后，前端自动隐藏常规 Base URL 输入，只展示入口继承信息。
- 后端能从 API 入口 `baseUrlPattern` 推导凭证 `baseUrl`。
- 未配置 Base URL 的 API 入口会阻止创建凭证并返回明确错误。
- 既有测试更新后通过，前端 typecheck 通过。

## 风险

- 存量游离凭证仍可能存在，本轮不做数据迁移或强行修复。
- 某些自定义厂商可能需要凭证级 Base URL 覆盖，后续可放入高级覆盖流程；本轮先把常规流程收紧到 API 入口。
- API 入口下线或删除会影响新凭证创建，但不会改动已有凭证。

## 实现结果

- 后端 API Key 凭证创建必须绑定厂商/API 入口；未提交 `siteProfileId`、入口停用或入口缺少 Base URL 时会阻止保存。
- 后端保存凭证时以 API 入口为事实源，从 `UpstreamSiteProfileEntity#siteKind` 派生 `providerType`，从 `baseUrlPattern` 派生凭证 `baseUrl`。
- 前端新增/编辑上游凭证表单改为选择“厂商/API 入口”，常规 Base URL 改为只读继承展示。
- 批量 API Key 导入复用同一个入口，提交 payload 会携带 `siteProfileId` 以及派生的 `providerType/baseUrl`。
- OAuth/auth.json 导入流程保持原有行为。

## 验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`
- `bun run typecheck`
- `bun run test -- credentials-page`

## 遗留问题

- 存量游离凭证未做数据迁移；如需彻底收口，可另开迁移任务把历史凭证归入明确 API 入口。
- 自定义厂商的凭证级 Base URL override 本轮未提供，后续如要支持应放入高级入口配置，而不是恢复常规凭证字段。

## 当前状态

Done
