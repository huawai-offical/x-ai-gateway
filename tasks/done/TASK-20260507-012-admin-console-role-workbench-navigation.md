# TASK-20260507-012 Admin Console 角色化工作台与导航体系

状态：Done  
优先级：High  
排期：P1-08  
来源：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)

## 背景

当前后台页面覆盖能力很广，但侧边栏更接近后端实体列表。管理员的真实工作通常是接入 Codex、管理账号池、排查失败请求、查看用量成本、处理告警和维护部署。需要将管理端从“页面集合”整理成角色化工作台。

## 目标

- 建立面向角色和任务的 Console 首页。
- 重整导航分组，保留高级能力但降低扫描成本。
- 为 Codex 反代运营提供一组首屏入口：接入、账号池、热切换、实时请求、过滤命中、usage。
- 增加全局搜索或 command palette 的设计入口。

## 范围

- Console dashboard 信息架构。
- 导航分组：接入与账号、Codex 运营、路由与策略、观测与排障、计费与用户、部署与系统、集成。
- 高频任务卡与上下文链接。
- 角色视角：接入管理员、运营管理员、排障管理员、财务/计费管理员、系统管理员。

## 非目标

- 不移除现有高级页面。
- 不做营销式 hero 或低密度展示页。
- 不在本任务内实现所有 Codex 后端能力。

## 详细设计

- Console 首页默认显示任务卡、异常摘要、最近失败请求、账号池健康、成本趋势和待处理告警。
- 每张任务卡跳转到具体向导或带筛选状态的页面，而不是泛泛跳到列表。
- 导航分组使用用户目标命名，内部保留实体页入口。
- 对高风险术语提供短标签和 hover/help，例如 distributed key 显示为访问 Key，client instance 显示为客户端实例。
- 搜索入口支持 key、account、requestId、clientInstance、workspace hint、user email。

## 本批实施设计

- 关联需求：[REQ-20260507-005 第三批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-005-next3-priority-codex-admin-closure-design.md)
- 导航分组调整为：总览、接入与账号、Codex 运营、路由与策略、观测与排障、计费与用户、部署与系统、集成。
- 新增或调整 Console 首页组件，展示角色视角、任务卡、告警摘要和最近失败请求。
- 新增 command palette 入口壳，支持本地页面搜索和关键实体搜索提示。
- 前端测试覆盖导航分组、任务卡跳转、搜索入口和移动侧栏。
- 在 Console 首屏提供批量操作可信度提示区，展示最近批量任务、部分失败数量、可重试动作和审计入口。

## 验收标准

- 管理员首屏能进入接入 Codex、排查失败请求、查看 usage、处理告警四个高频任务。
- 现有页面仍可通过新导航或搜索访问。
- 导航分组数量可扫描，移动端侧栏可正常使用。
- 前端测试覆盖导航渲染、任务卡跳转、breadcrumb 和移动侧栏。

## 风险

- 角色化不能变成权限绕过；不同角色可见性应与后端权限一致。
- 信息架构调整应控制范围，避免和 Codex 反代核心任务产生大冲突。

## 进度记录

- 2026-05-07：进入实现，纳入第三批最高优先级任务闭环。
- 2026-05-07：补充批量过程容错要求，导航和工作台需要给批量预检、执行、失败恢复、审计追踪留下稳定入口。
- 2026-05-07：根据参考项目处理方式，将 Codex App API 相关操作从普通上游管理中拆出，形成 `Codex 运营` 分组。
- 2026-05-07：完成 Console 导航重排、面包屑统一、`/console` 默认工作台跳转和 command palette 入口。
- 2026-05-07：补充导航回归测试，锁定 Codex 接入、Live Session、请求日志、站点档案、维护窗口、用户清单等关键页面的分组归属。

## 实现结果

- 侧栏分组调整为：`总览`、`接入与账号`、`Codex 运营`、`路由与策略`、`观测与排障`、`计费与用户`、`部署与系统`、`集成`。
- route meta 与侧栏一致，详情页和深层页的 breadcrumb 不再混用旧的 `上游接入`、`站点真相`、`策略与操作` 等分组。
- command palette 支持本地页面搜索，并额外提供 `按请求 ID 排查`、`按账号池查看 Codex`、`按客户端实例定位` 三类任务化入口。
- 移动端保持 `打开导航`、搜索和上下文切换入口，390px 视口下首屏元素可见且没有框架 overlay。

## 验证记录

- `bun run test -- src/app/navigation.test.ts src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/app/route-surfaces.test.ts`：通过。
- `bun run typecheck`：通过。
- `bun run build`：通过。
- Playwright MCP 渲染验证：桌面 1440x980 下页面身份、非空渲染、无 framework overlay、command palette 过滤和请求日志跳转均通过；移动 390x844 下角色工作台、批量可信面板、移动导航和搜索按钮均可见。
- 控制台健康：使用 mock WebSocket 和最小 API 夹具后无 app error；保留 1 条 Vite/React Router 开发期 `HydrateFallback` warning，不影响生产构建。

## 验收结论

已闭环。Admin Console 角色化工作台与导航体系已落地，现有页面仍可通过新导航或搜索访问，Codex 运营路径具备清晰入口。
