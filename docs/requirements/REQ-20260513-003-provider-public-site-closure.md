# REQ-20260513-003 Provider 差距与 Public Site 闭环实施批次

状态：Done  
日期：2026-05-13  
关联报告：

- [REP-20260513 参考项目、翻译能力、后台与门户完整度复核](../reports/REP-20260513-reference-translation-admin-portal-audit.md)

关联任务：

- [TASK-20260513-005 Provider/Media/价格同步参考差距补齐](../../tasks/done/TASK-20260513-005-provider-media-pricing-reference-gap-closure.md)
- [TASK-20260513-006 Public Site、Docs、Pricing、Status 客户入口](../../tasks/done/TASK-20260513-006-public-site-docs-pricing-status-surface.md)

## 背景

上一批已闭环翻译支持矩阵、Admin 菜单精简和 Portal 客户自助页。剩余缺口集中在两个面：一是对齐 `new-api` 的 Provider/Media/价格产品化事实源，二是让未登录客户访问 `/` 时进入公开站点，而不是直接进入 Admin Console。

## 目标

- 在后台提供 Provider/Media/价格参考差距视图，明确当前支持、兼容支持、待补齐和不支持。
- 为价格同步建立可展示的 source、状态、更新时间、失败原因和 smoke 分级。
- 新增 Public Site、Docs、Pricing、Status 页面，作为客户访客入口。
- 保持 Admin Console、Portal、Public Site 三个产品面边界清晰。

## 非目标

- 不在本批次内实现所有 Provider 专属 adapter。
- 不使用生产 secret 或真实付费 key。
- 不实现完整 CMS 或真实在线支付。
- 不删除已有 Console/Portal 路由。

## 方案

1. 后端在 Provider Site 能力域新增参考差距/价格状态 API，数据先来自 catalog 与静态参考项目映射，后续可接真实同步器。
2. 前端 Provider/Capability 页面增加 Provider 参考差距、媒体能力和价格状态展示。
3. 新建 Public Site 目录，提供 Home、Docs、Pricing、Status 页面。
4. Router 根路径 `/` 指向 Public Home，保留 `/console`、`/portal` 作为明确入口。

## 详细设计

### Provider 差距与价格状态

- 新增 `GET /admin/provider-reference-gap`，返回三类事实：
  - `providers`：以 `new-api` channel 清单为参考，按 `SUPPORTED`、`COMPATIBLE`、`MISSING` 标记当前 catalog 覆盖状态。
  - `mediaCapabilities`：按 audio、image、video、music、realtime、rerank、web_search 等媒体/非 Chat 能力标记支持等级和治理边界。
  - `pricingSync`：按高价值 provider 标记价格来源、同步策略、最近验证时间、失败分级和是否需要真实 key。
- 当前实现不读取 secret，不触发真实外网 smoke；真实 smoke 通过既有可选环境变量和测试 harness 执行。
- 前端新增 Console 页面 `/console/provider-reference-gap`，作为 `能力矩阵` 的旁路视图，不替代站点快照矩阵。

### Public Site

- `/`：访客公开首页，聚合接入路径、协议覆盖、Portal 与 Console 入口。
- `/docs`：公开文档入口，优先读取 `/public/docs/compatibility`；后端不可用时展示安全 fallback。
- `/pricing`：展示套餐、额度、计费边界和“以后台套餐为准”的说明，不声称实时官方 provider 价格。
- `/status`：只展示公开服务状态、维护窗口和客户可见摘要，不暴露 provider secret、账号池和内部路由事件。
- Vite 增加 `/public` API proxy，且 HTML 请求不代理，保证前端路由可直达。

## 风险

- 参考项目 channel 清单和当前 catalog 口径不同，必须明确支持等级，不能误标。
- Public Status 不能泄露内部 provider secret、账号池、路由细节。
- Pricing 页面必须声明为当前套餐/示例，不把未同步价格包装成实时官方价格。

## 验收标准

- `TASK-20260513-005` 与 `TASK-20260513-006` 状态、实现结果、验证记录回写完成。
- 后端或前端测试覆盖 Provider 差距与 Public 页面路由。
- `bun run typecheck`、`bun run lint` 通过。
- 浏览器 smoke 覆盖 Public Home、Docs、Pricing、Status。

## 实现结果

- 后端新增 `GET /admin/provider-reference-gap`，输出参考项目 provider/channel 覆盖、媒体能力和价格同步状态。
- 前端 Console 新增 `/console/provider-reference-gap` 与“路由 / 参考差距”菜单项。
- Public Site 新增 `/`、`/docs`、`/pricing`、`/status`，根路径不再跳 Console。
- `AdminAuthProvider` 从全局主动拉取控制台会话改为公开页 idle、进入受保护 Console 路由时再恢复会话，避免访客页产生 admin auth 请求。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderReferenceGapAdminControllerTests"`：通过。
- `bunx vitest run src\app\public-router.test.tsx src\app\operations-router.test.tsx src\features\provider-sites\provider-reference-gap-page.test.tsx src\features\public\public-pages.test.tsx src\app\route-surfaces.test.ts src\app\navigation.test.ts`：通过。
- `bun run typecheck`：通过。
- `bun run lint`：通过。
- Browser smoke：`http://127.0.0.1:5174/`、`/docs`、`/pricing`、`/status` 在桌面与 390x844 移动视口均可渲染目标文本。

## 遗留问题

- 价格同步目前是展示合同与策略矩阵，真实定时同步器仍可后续单独拆任务。
- Public Docs 在未启动后端时展示安全 fallback；联调真实后端时应走 `/public/docs/compatibility`。
- React Router lazy route 仍有既有 `HydrateFallback` warning，可后续统一加 fallback 消除。
