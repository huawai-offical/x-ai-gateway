# TASK-20260521-013 全局表格分页

状态：Done  
优先级：Critical  
上游来源：[REQ-20260521-013](../../docs/requirements/REQ-20260521-013-global-table-pagination.md)

## 任务类型

父任务

## 背景

用户要求所有表格都具备分页能力，默认每页 50 条且可更改。当前仓库中表格分布于控制台、门户、运维、请求日志、网络治理、用户域和公开文档页面，多数直接渲染完整数组。

## 目标

- 建立统一分页组件和分页 hook。
- 将现役列表表格接入分页，默认每页 50 条。
- 支持用户切换每页条数，并提供上一页/下一页控制。
- 保持现有表格列、详情入口、操作按钮和筛选条件不变。

## 非目标

- 不改后端 API 为服务端分页。
- 不引入第三方表格库。
- 不做排序、列配置或导出功能。

## 输入

- `web/src/components/app/`
- `web/src/features/**`
- 现有表格页面测试

## 输出

- 通用表格分页组件与 hook。
- 已接入分页的表格页面。
- 测试与文档回写。
- 全量列表型 `<table>` 的分页接入、验证和文档回写。

## 影响范围

前端所有列表表格的渲染数量、分页控件、每页条数选择和测试断言。

## 依赖

- 现有 React/Vite 前端。
- 现有 UI Button/Input/Select 组件。

## 风险

- 表格数量多，可能出现遗漏。
- 某些详情弹窗内的短列表分页后需要保持紧凑。
- 每页切换和数据变化需要避免空页。

## 验收标准

- [x] 通用分页默认每页 50 条。
- [x] 每页条数可更改。
- [x] 现役列表表格均接入分页。
- [x] 分页不会破坏已有详情、编辑、删除等表格操作。
- [x] 前端类型检查与定向测试通过。

## 测试边界

- `npm run typecheck`
- 表格相关定向 vitest。
- 必要时浏览器检查分页控件可见性。

## 当前状态

已完成全量表格分页接入、类型检查、定向测试和结构扫描。

## 全量实现结果

- 新增 `web/src/components/app/table-pagination.tsx`，统一提供 `useTablePagination` 和 `PaginatedRows`。
- 新增 `web/src/components/app/table-pagination.test.tsx`，覆盖默认 50 条、翻页和每页条数切换。
- 账号、上游凭证、模型、网络、运维、请求日志、资源、集成、上游缓存、门户、公开文档和用户域页面的列表型 `<table>` 均已接入分页。
- 默认每页 50 条，用户可切换 25、50、100、200 条。
- 对 `query.data` 直传项补充空数组或显式类型归一化，避免分页泛型推断为 `unknown[]`。
- 保留 CSV 导出、筛选、详情弹窗、编辑、删除、手工执行等原有交互语义。

## 全量验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/components/app/table-pagination.test.tsx src/features/accounts/account-groups-page.test.tsx src/features/accounts/account-group-detail-page.test.tsx src/features/credentials/credentials-page.test.tsx src/features/request-logs/request-logs-page.test.tsx src/features/network/proxies-page.test.tsx src/features/network/tls-profiles-page.test.tsx src/features/network/probes-page.test.tsx src/features/models/models-page.test.tsx src/features/resources/resources-page.test.tsx src/features/upstream-cache/upstream-cache-page.test.tsx src/features/ops/ops-alerts-page.test.tsx src/features/ops/ops-page.test.tsx src/features/ops/ops-probes-page.test.tsx src/features/ops/system-events-page.test.tsx src/features/operations/windows-page.test.tsx src/features/integrations/runbooks-page.test.tsx src/features/portal/portal-home-page.test.tsx src/features/public/public-pages.test.tsx src/features/user-domain/plans-page.test.tsx src/features/user-domain/subscriptions-page.test.tsx src/features/user-domain/users-page.test.tsx`：22 个测试文件、42 个测试通过。
- 2026-05-22 追记：网络代理拨测已按 [REQ-20260522-002](../../docs/requirements/REQ-20260522-002-network-proxy-probe-retirement.md) 下线，上述历史验证命令中的 `src/features/network/probes-page.test.tsx` 和 `src/features/ops/ops-probes-page.test.tsx` 不再作为现役测试入口。
- AST 扫描：33 个包含 `<table>` 的 TSX 文件中，每个 `<table>` 均位于 `PaginatedRows` 祖先节点内。
- `rg -n "items=\{[^}]+\.data\}" web/src/features -g "*.tsx"`：无未归一化 `query.data` 直传项。
- `ops-page` 测试输出 Recharts 在 jsdom 中的 0 宽高警告，不影响断言结果。

## 本轮子任务

### TASK-20260521-013-01 门户与用户域指定页面表格分页接入

状态：Done  
上游来源：[REQ-20260521-013](../../docs/requirements/REQ-20260521-013-global-table-pagination.md)

#### 背景

已新增通用组件 `web/src/components/app/table-pagination.tsx`，导出 `PaginatedRows`，默认每页 50 条并支持切换每页条数。用户要求在指定的门户、公开文档和用户域页面中把所有列表型 `<table>` 接入该组件。

#### 目标

- 在指定 13 个页面中为所有列表型 `<table>` 外层接入 `PaginatedRows`。
- 默认不传 `pageSize`，沿用组件默认 50 条。
- 保持现有列、按钮、空态、样式不变。
- 只把 `tbody` 中原数组 `.map(...)` 的数据源替换为 `pageItems`。

#### 非目标

- 不修改通用分页组件行为。
- 不修改后端接口和服务端分页契约。
- 不处理本轮指定范围以外的页面。
- 不回退或整理其他人已经存在的工作区改动。

#### 输入

- `web/src/components/app/table-pagination.tsx`
- 用户指定的 13 个页面文件

#### 输出

- 指定页面中列表型表格接入 `PaginatedRows`。
- 验证命令结果回写到本任务。

#### 影响范围

仅影响指定页面的数据行可见范围和分页控件展示；筛选、详情、编辑、删除、导出等逻辑保持原有数据源。

#### 依赖

- `PaginatedRows` 已存在并可从 `@/components/app/table-pagination` 引入。

#### 风险

- 页面中存在弹窗内表格和小型文档表，需要按“所有表格”规则接入，但避免给非列表说明块强行改造。
- 并行工作区改动较多，必须限制写入范围。

#### 验收标准

- 指定页面所有列表型表格均使用 `PaginatedRows`。
- 默认每页 50 条，不额外传入 pageSize。
- 原空态仍由原数组长度判断。
- 定向类型检查可运行并记录结果。

#### 测试边界

- 优先运行 `cd web; bun run typecheck`。
- 如类型检查因既有工作区问题失败，记录失败摘要并区分本轮改动。

#### 关联文档

- [REQ-20260521-013](../../docs/requirements/REQ-20260521-013-global-table-pagination.md)

#### 关联任务

- 父任务：[TASK-20260521-013](./TASK-20260521-013-global-table-pagination.md)

#### 实现结果

- `web/src/features/portal/portal-home-page.tsx`：订阅、访问密钥、充值订单、余额流水表接入分页。
- `web/src/features/portal/portal-keys-page.tsx`：访问密钥表接入分页。
- `web/src/features/portal/portal-orders-page.tsx`：充值订单表接入分页。
- `web/src/features/portal/portal-redeem-page.tsx`：余额流水表接入分页。
- `web/src/features/portal/portal-subscriptions-page.tsx`：订阅表接入分页。
- `web/src/features/portal/portal-usage-page.tsx`：用量明细表接入分页，CSV 导出仍使用完整 `recentUsage`。
- `web/src/features/public/public-docs-page.tsx`：协议兼容表接入分页；卡片网格未改造为表格。
- `web/src/features/user-domain/access-groups-page.tsx`：访问组表接入分页。
- `web/src/features/user-domain/announcements-page.tsx`：公告表接入分页。
- `web/src/features/user-domain/plans-page.tsx`：套餐表接入分页。
- `web/src/features/user-domain/promo-codes-page.tsx`：活动表和弹窗内兑换码表接入分页。
- `web/src/features/user-domain/subscriptions-page.tsx`：订阅关系表接入分页。
- `web/src/features/user-domain/users-page.tsx`：用户清单表接入分页。

#### 验证结果

- `rg -n "<table"` 覆盖本轮 13 个文件，确认 17 张 `<table>` 均已在 `PaginatedRows` 分支中渲染。
- `npm run typecheck`：通过。
- 全量定向测试命令：22 个测试文件、42 个测试通过。
- 失败过的分页泛型推断问题已通过对 `query.data` 入口做空数组或显式类型归一化解决。
