# TASK-20260522-018 上游凭证多协议入口绑定

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-017-credential-multi-protocol-endpoint-binding.md`

厂商协议入口已经可以表达 MiMo、DeepSeek 等厂商的 OpenAI-compatible 与 Anthropic-compatible 双入口，但创建上游凭证时仍只能选择单个入口。用户提出“厂商允许的协议入口要不要允许多选”，本任务将 API Key 凭证创建扩展为多入口展开，同时修正运行时候选对协议入口事实源的读取。

## 目标

- API Key 凭证创建支持多个 `protocolEndpointId`。
- 同一个 secret 按多个协议入口展开为多条上游凭证。
- 前端创建表单支持多选厂商协议入口，编辑仍保持单入口。
- 批量 secret 导入支持按多入口展开。
- 运行时候选优先使用协议入口 provider type、site kind 和 Base URL。

## 非目标

- 不恢复凭证级 Base URL 手工输入。
- 不兼容旧 `allowedProtocols`。
- 不做单条凭证跨入口批量编辑。
- 不执行真实外部 API 调用。

## 上游来源

- `docs/requirements/REQ-20260522-017-credential-multi-protocol-endpoint-binding.md`
- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`
- 用户问题：“厂商允许的协议入口，要不要允许多选呢？”

## 输入

- `CredentialRequest`
- `CredentialAdminService`
- `CredentialAdminController`
- `ProviderProtocolEndpointRepository`
- `ModelCatalogQueryService`
- `web/src/features/credentials/*`

## 输出

- 多入口创建 API 与服务逻辑。
- 前端上游凭证创建多选协议入口。
- 批量创建数量与错误提示更新。
- 候选执行面协议入口事实源修正。
- 后端与前端测试补充。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/test/java/com/prodigalgal/xaigateway/admin/`
- `web/src/features/credentials/`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 厂商多协议入口结构已落地。
- 凭证已支持绑定 `protocolEndpointId`。
- 存量凭证协议入口回填已完成。

## 风险

- 多入口创建需要避免部分成功。
- 前端批量导入需要按展开后的凭证数量统计。
- 运行时候选改用入口元数据时不能破坏历史凭证兜底。

## 验收标准

- [ ] 后端支持一次请求创建多个协议入口凭证。
- [ ] 重复检测包含 `protocolEndpointId`，不会误挡同一 Base URL 下不同入口。
- [ ] 前端创建 API Key 凭证时协议入口可多选。
- [ ] 批量 secret 导入按 `secret * endpoint` 展开创建。
- [ ] 模型候选优先使用协议入口元数据。
- [ ] 后端和前端验证通过。

## 测试边界

- 后端：`CredentialAdminServiceTests`、`CredentialAdminControllerTests`、`ModelCatalogQueryService` 相关测试。
- 前端：`credentials-page` 测试与 `bun run typecheck`。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-017-credential-multi-protocol-endpoint-binding.md`
- `docs/requirements/REQ-20260522-013-provider-protocol-endpoints.md`

## 关联任务

- `tasks/done/TASK-20260522-014-provider-protocol-endpoints.md`
- `tasks/done/TASK-20260522-015-protocol-endpoint-conversation-profile-runtime.md`
- `tasks/done/TASK-20260522-016-credential-protocol-endpoint-backfill.md`

## 当前状态

Done

## 实现结果

- 后端新增 `/admin/credentials/multi-endpoint`，支持一个 API Key secret 绑定多个 `protocolEndpointIds`。
- `CredentialAdminService` 将同一个 secret 按协议入口展开保存为多条凭证，每条凭证独立绑定 `protocolEndpointId`。
- 创建和更新路径的重复检测包含 `protocolEndpointId`，并保留无入口历史凭证的兜底检查。
- `ModelCatalogQueryService` 构建候选时优先使用 `ProviderProtocolEndpointEntity` 的 provider type、site kind、auth/path/error 和 Base URL。
- 前端新增凭证流程的“厂商协议入口”改为多选，批量导入按多入口展开，编辑流程继续单入口。
- 补充 `CredentialAdminServiceTests`、`CredentialAdminControllerTests`、`ModelCatalogQueryServiceTests` 与凭证页前端测试。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryServiceTests"`
- `bun run typecheck`
- `bun run test -- credentials-page`
- `bun run test -- credentials-page provider-site-detail-page provider-sites-page`

## 遗留边界

- 不包含真实外部 API smoke。
- 不包含跨多入口批量编辑既有凭证。
- 不包含对所有 runtime executor 的重写，本轮只修正候选事实源。
