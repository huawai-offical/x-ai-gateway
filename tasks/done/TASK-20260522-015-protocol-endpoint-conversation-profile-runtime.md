# TASK-20260522-015 协议入口对话兼容画像下发运行时

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-014-protocol-endpoint-conversation-profile-runtime.md`

厂商协议入口已经可以独立配置 conversation profile，但当前运行时主要从站点档案和凭证 metadata 中读取画像。为了让 endpoint 级配置真正参与 Responses/Chat/Messages、reasoning_content、stream/tool 等请求细节处理，需要在凭证绑定协议入口时，把入口画像同步到凭证 metadata 的 `conversationProfile`。

## 目标

- 凭证创建时把协议入口 conversation profile 写入 credential metadata。
- 凭证更新或切换协议入口时刷新 credential metadata 中的 conversation profile。
- 用户手工传入的 `credentialMetadata.conversationProfile` 优先覆盖入口默认画像。
- 增加后端回归测试。

## 非目标

- 不重写运行时 executor。
- 不做真实外部 API 调用。
- 不迁移历史凭证。
- 不删除站点级 conversation profile。

## 上游来源

- `docs/requirements/REQ-20260522-014-protocol-endpoint-conversation-profile-runtime.md`
- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`

## 输入

- `ProviderProtocolEndpointEntity.conversationProfileJson`
- `CredentialRequest.credentialMetadata`
- `CredentialAdminService`
- `OpenAiNativeGatewayChatRuntime` 已有 metadata 读取逻辑

## 输出

- 凭证保存后的 `credentialMetadataJson.conversationProfile`
- 后端单测覆盖入口画像合并和用户覆盖优先级
- 文档与任务索引回写

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 厂商多协议入口结构已落地。
- 凭证已可绑定 `protocolEndpointId`。

## 风险

- 本轮只做对象级浅合并，不做嵌套递归合并。
- 存量凭证只有在重新编辑保存或后续迁移后才会获得 endpoint 画像。

## 验收标准

- [x] 新建凭证时，入口 `conversationProfileJson` 写入凭证 metadata。
- [x] 用户 metadata 中已有 `conversationProfile` 字段时，用户字段优先。
- [x] 更新凭证切换入口时，metadata 中的画像同步更新。
- [x] 后端定向测试通过。

## 测试边界

- 后端：`CredentialAdminServiceTests`。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-014-protocol-endpoint-conversation-profile-runtime.md`

## 关联任务

- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`

## 当前状态

Done

## 实现结果

- `CredentialEndpointBinding` 增加入口 conversation profile。
- `CredentialAdminService` 写入凭证 metadata 时合并 endpoint conversation profile 到 `conversationProfile`。
- 用户传入的 `credentialMetadata.conversationProfile` 字段拥有最高优先级。
- 新增创建与更新路径回归测试。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DefaultResourceBootstrapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`

## 遗留边界

- 不包含真实外部 API 调用。
- 存量凭证批量回填由 `TASK-20260522-016` 承接并完成。
- 不包含 runtime executor 动态切换协议入口。
