# TASK-20260522-013 上游凭证绑定厂商 API 入口

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-012-credential-vendor-site-binding.md`

用户确认新增上游凭证应该和厂商/API 入口结合，避免每次手动填写 Base URL。当前厂商 API 入口已默认引导，新增凭证应改为选择 API 入口并继承配置。

## 目标

- 后端 API Key 凭证创建要求绑定 `siteProfileId`。
- 后端从 API 入口推导 provider type 和 Base URL。
- 前端新增/编辑 API Key 凭证表单以 API 入口选择为主。
- 验证后端 targeted tests 和前端 typecheck。

## 非目标

- 不修改 OAuth/auth.json 导入行为。
- 不处理凭证级高级 Base URL override。
- 不做真实外部厂商调用。

## 上游来源

- `docs/requirements/REQ-20260522-012-credential-vendor-site-binding.md`
- 用户目标：“新增上游凭证的流程，需不需要和厂商结合进去？”

## 输入

- `CredentialRequest`
- `CredentialAdminService`
- `ProviderSiteAdminService`
- `web/src/features/accounts/account-group-detail-page.tsx`
- 现有凭证与账号分组测试

## 输出

- API Key 凭证绑定 API 入口的后端校验与默认值推导。
- API 入口优先的前端凭证表单。
- 文档、任务和索引回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminServiceTests.java`
- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/credentials/credentials-page.test.tsx`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 默认厂商 API 入口引导。
- `ProviderSiteRegistryService#ensureSiteProfile`
- `UpstreamSiteProfileEntity#baseUrlPattern`

## 风险

- 前端上游凭证页面文件较大，本轮只做表单路径的定向修改。
- 后端如果直接禁止所有无 `siteProfileId` 更新，可能影响旧凭证编辑；本轮需要区分创建和更新。

## 验收标准

- [x] 新增 API Key 凭证必须选择 API 入口。
- [x] 后端从 API 入口继承 Base URL 和 provider type。
- [x] 前端不再把 Base URL 作为常规必填项。
- [x] 相关测试通过。
- [x] 文档和任务状态回写完成。

## 测试边界

- 后端：`CredentialAdminServiceTests` targeted tests。
- 前端：`credentials-page` targeted tests 与 `bun run typecheck`。
- 不执行真实外部 API 调用。

## 实现结果

- `CredentialRequest` 不再把 `providerType/baseUrl` 作为 Bean Validation 必填字段。
- `CredentialAdminService` 创建/更新 API Key 凭证时从厂商/API 入口派生 `providerType/baseUrl`，并阻止无入口、停用入口和无 Base URL 入口。
- `ExecutionBackendPolicyService#providerTypeForSite` 补齐 OpenAI Direct/Azure 到 `OPENAI_DIRECT` 的映射，避免 OpenAI 入口被错误保存为兼容类型。
- 上游凭证页面新增 API 入口查询与选择器，创建/编辑表单均由入口带出 provider type 与 Base URL。
- 凭证页面测试覆盖单条与批量创建时的 `siteProfileId`、派生 provider type 和 Base URL payload。

## 验证结果

- 已通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests"`
- 已通过：`bun run typecheck`
- 已通过：`bun run test -- credentials-page`

## 关联文档

- `docs/requirements/REQ-20260522-012-credential-vendor-site-binding.md`
- `docs/requirements/REQ-20260522-011-default-provider-site-bootstrap.md`
- `docs/requirements/REQ-20260522-007-vendor-management-center.md`

## 关联任务

- `tasks/done/TASK-20260522-012-default-provider-site-bootstrap.md`
- `tasks/done/TASK-20260522-011-vendor-management-center.md`

## 当前状态

Done
