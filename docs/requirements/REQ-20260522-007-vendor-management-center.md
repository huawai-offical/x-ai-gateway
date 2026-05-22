# REQ-20260522-007 厂商管理中心

## 背景

当前后端已经存在 `upstream_site_profile`、`ProviderSiteAdminService`、预设导入、能力刷新和 capability matrix 等厂商/API 入口基础能力，但控制台 `/provider-sites`、`/capability-matrix` 等入口被重定向到模型目录，`web/src/features/provider-sites/` 只剩类型与调试页。用户确认需要把厂商层串入“厂商 -> 上游凭证 -> 账号组 -> 分发 Key”的主路径，因此需要恢复并补齐厂商管理中心。

## 目标

- 控制台新增现役“厂商管理”入口，按厂商聚合 API 入口。
- 管理 API 入口的创建、编辑、删除、启停、预设导入和能力刷新。
- 展示厂商编码、厂商名称、协议入口、协议簇相关入口能力、鉴权/路径/错误策略、对话兼容画像、模型能力和绑定凭证数。
- 支持 API 入口详情页查看能力矩阵、模型能力、surface 能力和 conversation profile。
- 恢复 capability matrix 页面作为厂商中心的矩阵视图，不恢复旧“Provider 参考差距”等退役页面。
- 后端暴露 `ProviderSiteAdminService` 中已有管理动作，前端不再只读 `/admin/provider-sites`。

## 范围

- 后端 `ProviderSiteAdminController` 管理接口补齐。
- 前端路由、导航、route meta 和 `provider-sites` 页面补齐。
- 前端类型定义同步 vendor 字段、conversation profile、preset 字段和 draft 字段。
- Provider site 页面与详情页 targeted tests。
- 文档与任务状态回写。

## 非目标

- 不新增独立 `vendor` 数据表；本轮以现有 `vendorCode/vendorName + site profile` 形成 UI 层厂商聚合。
- 不做真实 MiMo/DeepSeek 外部调用；能力刷新仍复用现有后端逻辑。
- 不恢复已退役的 `provider-reference-gap`、`native-compatibility` 独立控制台入口。
- 不重命名后端实体 `UpstreamSiteProfileEntity`；UI 文案使用“厂商/API 入口”降低理解成本。

## 验收标准

- `/console/provider-sites` 可作为厂商管理中心访问，不再重定向到模型目录。
- 侧边栏“接入与模型”包含“厂商管理”入口。
- 管理中心可查看厂商分组、API 入口列表、预设列表，并可创建、编辑、删除、导入预设、刷新能力。
- `/console/provider-sites/:id` 可查看单个 API 入口详情、模型能力和 surface/feature 状态。
- `/console/capability-matrix` 可查看 API 入口能力矩阵。
- 后端 ProviderSite 管理 API 的 CRUD、preset import、refresh 入口有 controller 测试覆盖。
- 前端 typecheck 与 provider-sites targeted tests 通过；若存在既有无关失败需明确标注。

## 风险

- 当前工作区已有未提交改动，本需求只触碰厂商管理中心相关文件，避免覆盖其他任务成果。
- 预设导入和能力刷新可能触发较多后端逻辑，前端应以明确按钮触发，不自动执行外部调用。
- 删除 API 入口会被绑定凭证阻止，前端需要展示后端错误，不做强行删除。

## 当前状态

Done

## 实施结果

- `ProviderSiteAdminController` 已补齐厂商/API 入口管理 API：列表、详情、创建、更新、删除、单入口刷新、批量刷新、预设列表、预设详情、预设导入和 capability matrix。
- 控制台新增“厂商管理”导航入口，`/console/provider-sites`、`/console/provider-sites/:id`、`/console/capability-matrix` 已接入现役页面，不再重定向到模型目录。
- 厂商管理中心支持按厂商聚合 API 入口，展示厂商编码、厂商名称、协议入口、健康状态、模型数、绑定凭证数，并支持创建、编辑、删除、预设导入和能力刷新。
- API 入口详情页展示调用策略、Base URL 匹配、凭证要求、conversation profile、模型能力、surface 能力和 feature 解析。
- capability matrix 页面展示入口级能力矩阵，覆盖 Responses、Embeddings、Audio、Images、Moderation、Files、Uploads 等主线能力。
- 前端类型已补充 `vendorCode/vendorName`、`conversationProfile`、预设 conversation/model policy 字段和 `PERPLEXITY` 入口。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava test --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`：通过。
- `bun run typecheck`：通过。
- `bun run test -- provider-sites-page provider-site-detail-page capability-matrix-page`：通过，3 个测试文件 4 个用例。
