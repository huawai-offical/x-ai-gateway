# TASK-20260522-016 存量凭证协议入口保守回填

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-015-credential-protocol-endpoint-backfill.md`

厂商多协议入口和入口级 conversation profile 已经落地，但历史 API Key 凭证可能没有 `protocolEndpointId`。为了让“厂商 -> 协议入口 -> 上游凭证 -> 账号组 -> 分发 Key”链路在已有数据上也尽量完整，需要在启动默认资源引导阶段做确定性回填。

## 目标

- 查询缺少 `protocolEndpointId` 的未删除凭证。
- 在同一 `siteProfileId` 下按 `providerType + baseUrl` 唯一匹配协议入口。
- 唯一匹配时回填 `protocolEndpointId` 并合并入口 conversation profile。
- 无匹配或多匹配时跳过，不修改凭证。

## 非目标

- 不做真实 API 调用。
- 不改动已经绑定协议入口的凭证。
- 不做跨站点猜测。
- 不处理 OAuth/auth.json 账号类凭证。

## 上游来源

- `docs/requirements/REQ-20260522-015-credential-protocol-endpoint-backfill.md`
- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`
- `tasks/done/TASK-20260522-015-protocol-endpoint-conversation-profile-runtime.md`

## 输入

- `UpstreamCredentialEntity.siteProfileId/providerType/baseUrl/protocolEndpointId`
- `ProviderProtocolEndpointEntity.siteProfileId/providerType/baseUrl/conversationProfileJson`
- 默认资源引导流程

## 输出

- 回填后的 `UpstreamCredentialEntity.protocolEndpointId`
- 回填后的 `credentialMetadataJson.conversationProfile`
- 后端回归测试与文档索引

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/UpstreamCredentialRepository.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/DefaultResourceBootstrapService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteRegistryServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/DefaultResourceBootstrapServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 默认厂商 API 入口引导。
- 协议入口表与仓库。
- 凭证 metadata 合并入口画像逻辑。

## 风险

- Base URL 尾斜杠和大小写差异需要归一化，避免漏回填。
- 不能在歧义情况下自动绑定，避免错误连接双协议厂商。

## 验收标准

- [x] 唯一匹配 endpoint 的历史凭证会回填 `protocolEndpointId`。
- [x] 回填时会合并 endpoint conversation profile。
- [x] 无匹配或歧义时跳过。
- [x] 默认资源引导会调用回填方法。
- [x] 后端定向测试通过。

## 测试边界

- 后端：`ProviderSiteRegistryServiceTests`、`DefaultResourceBootstrapServiceTests`。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-015-credential-protocol-endpoint-backfill.md`

## 关联任务

- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`
- `tasks/done/TASK-20260522-015-protocol-endpoint-conversation-profile-runtime.md`

## 当前状态

Done

## 实现结果

- 新增缺失 `protocolEndpointId` 的凭证查询。
- 新增启动期保守回填逻辑，仅在同站点、provider type、Base URL 唯一匹配时绑定 endpoint。
- 回填时同步 endpoint conversation profile 到 credential metadata。
- 默认资源引导导入 provider presets 后会执行回填。
- 补充唯一匹配、歧义跳过与 bootstrap 调用测试。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`
- `bun run typecheck`
- `bun run test -- credentials-page provider-site-detail-page provider-sites-page`
- 当前本地库 `x_ai_gateway` 已执行 `0004-provider-protocol-endpoints`，确认存在 `provider_protocol_endpoint` 与 `upstream_credential.protocol_endpoint_id`。
- 当前本地库中 MiMo 凭证 `id=8`、`id=9` 已回填到 `xiaomi_mimo.openai_compatible` 协议入口。

## 遗留边界

- 不包含真实外部 API 调用。
- 不包含无法唯一匹配凭证的自动绑定。
- 不包含 OAuth/auth.json 账号类凭证回填。
