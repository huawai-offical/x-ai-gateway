# REQ-20260522-015 存量凭证协议入口保守回填

## 背景

厂商多协议入口升级后，新建 API Key 凭证已经要求绑定 `protocolEndpointId`，并由协议入口派生 provider type、Base URL 和 conversation profile。存量凭证可能仍只有 `siteProfileId`、`providerType` 和 `baseUrl`，没有绑定具体协议入口。若不回填，管理 UI 与后续运行时策略会出现“新凭证完整、旧凭证半断链”的状态。

## 目标

- 启动默认资源引导时，对缺少 `protocolEndpointId` 的历史 API Key 凭证做保守回填。
- 仅当同一站点下按 `providerType + baseUrl` 能唯一匹配到一个协议入口时才回填。
- 回填时同步入口 conversation profile 到凭证 metadata，使旧凭证也能进入运行时可读路径。
- 对无法唯一匹配的凭证保持原样，不做猜测绑定。

## 范围

- `UpstreamCredentialRepository` 增加查询缺失协议入口的活跃/未删除凭证。
- `ProviderSiteRegistryService` 或默认引导链路增加保守回填方法。
- `DefaultResourceBootstrapService` 在默认 preset 导入后触发回填。
- 单元测试覆盖唯一匹配、歧义跳过与 metadata 画像同步。

## 非目标

- 不做破坏性数据迁移。
- 不修改已绑定 `protocolEndpointId` 的凭证。
- 不尝试跨站点或跨 baseUrl 猜测。
- 不执行真实外部 API 调用。

## 验收标准

- 存量凭证缺少 `protocolEndpointId` 且唯一匹配 endpoint 时会自动绑定。
- 回填后的凭证 metadata 包含 endpoint conversation profile。
- 多 endpoint 歧义或无法匹配时不回填。
- 默认资源引导会触发回填。
- 后端定向测试通过。

## 风险

- 历史凭证的 Base URL 可能与 endpoint Base URL 存在尾斜杠差异，需要做轻量归一化。
- 若历史数据本身错误，本轮不会强行修复，只保持跳过并等待人工处理。

## 当前状态

Done

## 实现结果

- `UpstreamCredentialRepository` 新增缺失 `protocolEndpointId` 的未删除凭证查询。
- `ProviderSiteRegistryService#backfillCredentialProtocolEndpoints()` 会按 `siteProfileId + providerType + normalizedBaseUrl` 唯一匹配协议入口并回填。
- 回填时会把 endpoint conversation profile 合并进凭证 metadata 的 `conversationProfile`，保留历史 metadata 中用户字段优先级。
- `DefaultResourceBootstrapService` 在默认厂商 preset 导入后触发回填，并在日志中输出回填数量。
- 新增 registry 与 bootstrap 单测覆盖唯一匹配、歧义跳过和默认引导调用。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`
- `bun run typecheck`
- `bun run test -- credentials-page provider-site-detail-page provider-sites-page`
- 当前本地库 `x_ai_gateway` 已确认 `provider_protocol_endpoint` 表和 `upstream_credential.protocol_endpoint_id` 字段存在，`databasechangelog` 已记录 `0004-provider-protocol-endpoints`。
- 当前本地库已确认 MiMo 与 DeepSeek 双协议入口均已落库并启用。
- 当前本地库中 MiMo 凭证 `id=8`、`id=9` 已回填到 `xiaomi_mimo.openai_compatible` 协议入口。

## 遗留问题与后续建议

- 无法唯一匹配的历史凭证保持原样，等待人工选择协议入口或后续提供迁移报告。
- 本轮不处理 OAuth/auth.json 账号类凭证。
