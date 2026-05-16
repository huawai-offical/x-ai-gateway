# TASK-20260507-006 管理端 UI 信息架构与角色化工作台重整

状态：Done  
优先级：High  
排期：P1-07  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)

## 背景

当前前端功能覆盖面很广，但侧边栏按后端实体和治理域展开，概念密度高。对照 `new-api-main`、`sub2api-main` 和桌面类参考项目，成熟产品会围绕用户目标组织入口，例如“接入 Codex CLI”“管理账号池”“排查失败请求”“查看成本与用量”。

## 目标

- 重整管理端信息架构，保留专业能力但降低首次使用成本。
- 建立角色化工作台：接入管理员、运营管理员、排障管理员、财务/计费管理员。
- 把高频任务入口放到首屏，低频治理配置收纳到二级页面。
- 保持现有路由兼容，避免大规模破坏已有测试。

## 详细设计

- 设计新的导航分组：接入与账号、路由与策略、观测与排障、计费与用户、部署与系统。
- Dashboard 增加任务卡：接入 Codex、创建 Key、导入账号、运行 smoke、查看失败请求、处理告警。
- 每个任务卡跳转到具体向导或已筛选页面，而不是只到实体列表。
- 增加全局搜索或 command palette，支持按 key、account、requestId、clientInstance 快速跳转。
- 对术语做用户化映射：将 distributed key、client family、workspace hint 等解释为可读标签。

## 本批实施设计

- 关联需求：[REQ-20260507-005 第三批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-005-next3-priority-codex-admin-closure-design.md)
- 首屏采用角色化工作台：接入管理员、运营管理员、排障管理员、财务/计费管理员、系统管理员。
- 工作台保留高密度摘要：Codex 接入状态、账号池健康、近期失败请求、usage/成本、待处理告警。
- 导航分组和页面标题统一使用用户目标命名，同时保留实体页入口。
- 任务卡必须落到具体路由或带筛选意图的路由参数。
- 批量操作入口必须具备可信操作壳：影响范围、preflight 摘要、风险提示、执行批次 ID、部分失败重试和审计线索。

## 验收标准

- 管理端首屏能引导完成 3 个高频任务：接入 Codex、排查请求、查看用量。
- 导航项数量和层级更容易扫描，现有深层页面仍可访问。
- 关键页面有上下文说明、相关资源链接和下一步动作。
- 前端测试覆盖导航、breadcrumb、移动侧栏和核心任务入口。

## 风险

- 信息架构调整不能丢失现有高级功能入口。
- 不应把运营工具做成营销首页；界面仍应保持高密度、可扫描、适合重复操作。

## 进度记录

- 2026-05-07：进入实现，纳入第三批最高优先级任务闭环。
- 2026-05-07：补充批量操作可信性和容错性要求，后续 UI 重整需把批量动作从黑箱按钮升级为可预检流程。
- 2026-05-07：参考 `cc-switch-main`、`cli_proxy-master`、`cockpit-tools-main` 的 Codex App API 与运营面做法，确认 Codex 相关入口需要从普通 Provider 实体页中拆出独立 `Codex 运营` 视角。
- 2026-05-07：完成 Console 首屏角色化工作台：接入管理员、运营管理员、排障管理员、财务/计费管理员、系统管理员；高频入口覆盖接入 Codex、账号池、失败请求、成本路由和维护窗口。
- 2026-05-07：新增批量操作可信面板，展示 `READY`、`SKIPPED`、`BLOCKED`、`FAILED` 四类状态，并把 preflight、重试、审计线索作为首屏可见能力。
- 2026-05-07：导航和面包屑统一为 `总览`、`接入与账号`、`Codex 运营`、`路由与策略`、`观测与排障`、`计费与用户`、`部署与系统`、`集成`。

## 实现结果

- `web/src/features/dashboard/dashboard-page.tsx`：新增角色化运营工作台、优先动作区和批量操作可信面板，保留原系统请求、缓存收益、趋势和事件看板。
- `web/src/app/navigation.ts`：重排 Console 侧栏与 route meta，Codex 接入、Live Session、调试工作台、Native 兼容归入 `Codex 运营`，普通账号/Key/代理归入 `接入与账号`。
- `web/src/components/app/app-shell.tsx`：新增 command palette 入口，支持按页面、账号池、请求 ID、客户端实例意图快速跳转。
- `web/src/app/router.tsx`：`/console` 默认落到 `/console/dashboard`，让角色工作台成为后台首屏。

## 验证记录

- `bun run test -- src/app/navigation.test.ts src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/app/route-surfaces.test.ts`：通过，19 个测试通过；jsdom 下 Recharts 容器宽高 warning 为测试环境噪声。
- `bun run typecheck`：通过。
- `bun run build`：通过。
- 浏览器验证：`http://127.0.0.1:5173/console/dashboard` 在 1440x980 和 390x844 视口均能渲染角色化工作台、批量可信面板、移动导航和搜索入口；命令面板筛选 `request` 后可跳转到请求日志并展示 Codex 请求行。
- Browser 插件验证路径因本机 Node runtime `v22.20.0` 低于插件要求的 `v22.22.0` 被阻断，已用 Playwright MCP 进行同等渲染验证。

## 验收结论

已闭环。管理端从实体列表式入口收敛为角色和任务驱动的运营首屏，同时保留既有高级页面可访问性；批量操作的可信性和容错性已进入首屏设计壳，后续真实批量执行任务可在该壳上继续深化。
