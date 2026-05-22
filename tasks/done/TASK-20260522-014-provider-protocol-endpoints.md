# TASK-20260522-014 厂商多协议入口与对话兼容画像升级

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-013-provider-protocol-endpoints.md`

用户批准将厂商允许协议升级为多协议入口结构，以支持同一厂商同时暴露 OpenAI-compatible 与 Anthropic-compatible 等不同协议 Base URL。当前站点档案仍偏向“一个 API 入口一个 Base URL”，不足以表达 MiMo、DeepSeek 这类厂商的双协议入口。

## 目标

- 新增协议入口数据模型与管理 API。
- 默认厂商预设导入时生成协议入口。
- API Key 上游凭证优先绑定具体协议入口，并从入口继承 provider type 和 Base URL。
- 前端厂商管理展示协议入口，上游凭证表单改为选择协议入口。
- 补充后端与前端测试。

## 非目标

- 不一次性完成所有 runtime 根据请求协议自动切换 endpoint 的深度改造。
- 不做真实外部 API 调用。
- 不恢复凭证级 Base URL 常规输入。
- 不强制改造 OAuth/auth.json 导入。

## 上游来源

- `docs/requirements/REQ-20260522-013-provider-protocol-endpoints.md`
- 用户目标：“批准这个升级，开始推进项目进度”

## 输入

- `UpstreamSiteProfileEntity`
- `UpstreamCredentialEntity`
- `ProviderSiteAdminService`
- `ProviderSiteRegistryService`
- `CredentialAdminService`
- `web/src/features/provider-sites/*`
- `web/src/features/credentials/*`

## 输出

- 协议入口 schema、entity、repository、API DTO。
- 站点档案 response 带协议入口列表。
- 凭证 request/response 带 `protocolEndpointId`。
- 前端厂商与凭证页面可见协议入口。
- 文档、任务与索引回写。

## 影响范围

- `src/main/resources/db/changelog/changes/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- `web/src/features/provider-sites/`
- `web/src/features/credentials/`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 默认厂商 API 入口引导。
- 厂商管理中心 UI。
- 上游凭证绑定厂商/API 入口。

## 风险

- 当前 runtime 仍依赖 credential 的 provider type/baseUrl；本轮通过 credential 继承协议入口来降低改造风险。
- 存量凭证无 `protocolEndpointId` 时仍需允许读取和编辑，但新创建必须绑定协议入口。
- 协议入口与站点档案字段存在短期重复，后续需要逐步把 Base URL 从站点级下沉到协议入口级。

## 验收标准

- [x] 新增协议入口表和 JPA entity/repository。
- [x] 默认预设导入生成协议入口。
- [x] API Key 凭证可以绑定 `protocolEndpointId` 并继承 provider type/Base URL。
- [x] 厂商管理 UI 展示协议入口。
- [x] 上游凭证 UI 选择协议入口。
- [x] 后端和前端验证通过。

## 测试边界

- 后端：ProviderSiteRegistryService、ProviderSiteAdminController、CredentialAdminService 相关定向测试。
- 前端：provider-sites、credentials 页面测试与 `bun run typecheck`。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-013-provider-protocol-endpoints.md`
- `docs/requirements/REQ-20260522-012-credential-vendor-site-binding.md`
- `docs/requirements/REQ-20260522-007-vendor-management-center.md`

## 关联任务

- `tasks/done/TASK-20260522-013-credential-vendor-site-binding.md`
- `tasks/done/TASK-20260522-012-default-provider-site-bootstrap.md`
- `tasks/done/TASK-20260522-011-vendor-management-center.md`

## 当前状态

Done

## 实现结果

- 新增 `ProviderProtocolEndpointEntity`、`ProviderProtocolEndpointRepository` 与 Liquibase `db.changelog-0004-provider-protocol-endpoints.yaml`。
- 厂商/API 入口 response 带 `protocolEndpoints`，厂商详情页可展示、创建、编辑和删除协议入口。
- 预设导入会创建默认协议入口；MiMo/DeepSeek 自动生成 OpenAI-compatible 与 Anthropic-compatible 双入口。
- `CredentialRequest`、`CredentialResponse` 与上游凭证库存 response 带 `protocolEndpointId`，API Key 凭证创建必须选择具体协议入口。
- 上游凭证 UI 改为选择“厂商协议入口”，并从入口派生 provider type、site kind 与 Base URL。
- 补充 MiMo 双协议入口生成后端回归测试，以及凭证页、厂商详情页前端测试。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`
- `bun run typecheck`
- `bun run test -- credentials-page provider-site-detail-page provider-sites-page`
- 当前本地库 `x_ai_gateway` 已执行 `0004-provider-protocol-endpoints`，并确认 MiMo/DeepSeek 双协议入口落库。

## 遗留边界

- 不包含真实外部 API 调用。
- 不包含 runtime 按请求协议动态切换 endpoint 的深层路由改造。
- 不强制迁移 OAuth/auth.json 账号导入。
- 存量 API Key 凭证回填由 `TASK-20260522-016` 承接并完成。
