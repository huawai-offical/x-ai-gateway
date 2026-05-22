# TASK-20260522-011 厂商管理中心补齐

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260522-007-vendor-management-center.md`

用户确认厂商层需要作为上游凭证、账号组和分发 Key 的上游配置中心，目前前端独立 UI 未实现，旧 `provider-sites` 路由被重定向，后端 Service 中已有部分管理能力未暴露到 Controller。

## 目标

- 暴露 ProviderSite 管理 API。
- 新增控制台“厂商管理”入口。
- 实现厂商/API 入口列表、厂商分组、预设导入、创建/编辑、删除、能力刷新。
- 实现 API 入口详情页和 capability matrix 页面。
- 补齐 targeted tests 与验证记录。

## 非目标

- 不新增独立 vendor 表。
- 不恢复旧 Provider 参考差距页面。
- 不执行真实外部厂商调用。
- 不处理上游凭证创建流程的厂商默认值联动；该项可在后续任务中继续增强。

## 上游来源

- `docs/requirements/REQ-20260522-007-vendor-management-center.md`
- 用户目标：“把厂商管理中心补齐了”

## 输入

- `ProviderSiteAdminService`
- `ProviderSiteAdminController`
- `web/src/features/provider-sites/types.ts`
- 控制台路由与导航
- 现有 PageSection、InfoGrid、Dialog、Tabs、PaginatedRows 等组件

## 输出

- 完整 ProviderSite 管理 API。
- 厂商管理中心前端页面。
- API 入口详情与能力矩阵页面。
- 前后端 targeted tests。
- 文档索引和任务索引回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/ProviderSiteAdminController.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/api/ProviderSiteAdminControllerTests.java`
- `web/src/app/router.tsx`
- `web/src/app/navigation.ts`
- `web/src/app/route-surfaces.ts`
- `web/src/features/provider-sites/*`
- `docs/index.md`
- `tasks/index.md`

## 依赖

- 现有 `ProviderSiteAdminService`
- 现有 `ProviderSiteRegistryService` 预设机制
- 现有 `UpstreamSiteKind` 与 capability snapshot
- 现有前端 API client 与组件库

## 风险

- 旧路由之前被设计为退役入口，本任务需要同步修正文档口径。
- 前端页面需要覆盖多种后端字段，类型遗漏会导致 typecheck 失败。
- 删除和刷新操作依赖后端状态，测试需 mock 成本地可验证路径。

## 验收标准

- [x] 后端 ProviderSite 管理 API 补齐并有 controller 测试。
- [x] `/console/provider-sites` 不再重定向，显示厂商管理中心。
- [x] `/console/provider-sites/:id` 显示 API 入口详情。
- [x] `/console/capability-matrix` 显示能力矩阵。
- [x] 侧边栏与 route meta 可导航到厂商管理。
- [x] 前端 targeted tests 覆盖列表、详情和矩阵页面。
- [x] 文档和任务状态回写完成。

## 测试边界

- 后端：ProviderSiteAdminController targeted tests。
- 前端：provider-sites targeted tests、typecheck。
- 不执行真实外部 API 调用。

## 关联文档

- `docs/requirements/REQ-20260522-007-vendor-management-center.md`

## 关联任务

- `tasks/done/TASK-20260522-009-protocol-suite-authorization-migration.md`
- `tasks/done/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 当前状态

Done

## 实施记录

- 已补齐 `ProviderSiteAdminController` 管理接口：详情、创建、更新、删除、单入口刷新、批量刷新、预设列表、预设详情、预设导入、能力矩阵。
- 已新增控制台“厂商管理”导航入口，并将 `/console/provider-sites`、`/console/provider-sites/:id`、`/console/capability-matrix` 接入现役页面。
- 已新增厂商管理中心列表页，按厂商/API 入口展示状态、协议入口、模型数、绑定凭证数，支持创建、编辑、删除、预设导入和能力刷新。
- 已新增 API 入口详情页，展示调用策略、conversation profile、模型能力、surface 能力和 feature 解析。
- 已新增 capability matrix 页面，展示入口级功能支持矩阵。
- 已同步前端 `ProviderSite`、`ProviderSitePreset`、`ProviderSiteDraft` 类型，补充 `vendorCode/vendorName`、`conversationProfile` 和 `PERPLEXITY` 入口。
- 已修正文档口径：旧“站点档案”以“厂商管理 / API 入口”重新进入现役控制台。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava test --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`：通过。
- `bun run typecheck`：通过。
- `bun run test -- provider-sites-page provider-site-detail-page capability-matrix-page`：通过，3 个测试文件 4 个用例。
