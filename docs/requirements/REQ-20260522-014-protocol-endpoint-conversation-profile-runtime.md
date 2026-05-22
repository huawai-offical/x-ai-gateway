# REQ-20260522-014 协议入口对话兼容画像下发运行时

## 背景

厂商多协议入口已经落到配置层：一个厂商/API 入口可以拥有 OpenAI-compatible、Anthropic-compatible 等多个协议 endpoint，每个 endpoint 可以配置 conversation profile。当前运行时 `OpenAiNativeGatewayChatRuntime` 已会读取凭证 metadata 中的 `conversationProfile` 并覆盖站点级画像，但协议入口级画像还没有在凭证绑定时同步到这个运行时可读位置，导致 endpoint 上配置的 reasoning_content、targetProtocol、stream/tool 差异只停留在管理配置里。

## 目标

- API Key 凭证绑定协议入口时，把该入口的 `conversationProfileJson` 合并到凭证 metadata 的 `conversationProfile` 字段。
- 合并优先级为：站点级画像 < 协议入口画像 < 用户手工凭证 metadata 中的 `conversationProfile`。
- 保留用户传入的其他 credential metadata 字段，不覆盖 smoke certification、source、备注等非画像字段。
- 创建和编辑凭证都走同一套画像下发逻辑。

## 范围

- `CredentialAdminService` 的凭证创建/更新 metadata 写入逻辑。
- `CredentialEndpointBinding` 携带 endpoint conversation profile。
- `CredentialAdminServiceTests` 增加创建/更新时入口画像合并回归。
- 本地任务与索引回写。

## 非目标

- 不改造所有 runtime executor 的 endpoint 动态选择逻辑。
- 不改变协议入口 CRUD 的字段结构。
- 不执行真实外部厂商 API 调用。
- 不把站点级 conversation profile 删除或迁移。

## 验收标准

- 新建凭证绑定带 conversation profile 的协议入口后，保存出的 credential metadata 包含合并后的 `conversationProfile`。
- 用户请求 metadata 中已有 `conversationProfile` 时，用户字段优先覆盖入口默认值。
- 凭证更新切换协议入口时，会刷新 metadata 中的入口画像。
- 现有后端定向测试通过。

## 风险

- 若用户已手动写入较复杂的嵌套 conversation profile，本轮仅做对象级合并，不做深层递归合并。
- 存量凭证已通过 `REQ-20260522-015` 增加启动期保守回填，无法唯一匹配的凭证仍需人工处理。

## 当前状态

Done

## 实现结果

- `CredentialEndpointBinding` 已携带协议入口 `conversationProfileJson` 解析后的画像对象。
- `CredentialAdminService` 在创建和更新 API Key 凭证时，会把入口画像写入 `credentialMetadata.conversationProfile`。
- 用户在 `CredentialRequest.credentialMetadata.conversationProfile` 中手工传入的字段会覆盖入口默认画像字段，其他 metadata 字段保持不变。
- 新增 `CredentialAdminServiceTests` 覆盖创建时画像合并、用户字段覆盖，以及更新切换协议入口时刷新画像。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`

## 遗留问题与后续建议

- 存量凭证自动回填已由 `REQ-20260522-015` 承接完成。
- 本轮只做对象级浅合并；如果后续画像出现多层嵌套策略，再单独引入递归合并规则。
