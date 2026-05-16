# REQ-20260507-005 第三批最高优先级任务闭环设计

状态：Done  
日期：2026-05-07  
关联任务：

- [TASK-20260507-016 Codex 真实 auth.json 长期测试账号入库与详测](../../tasks/done/TASK-20260507-016-codex-real-auth-db-long-term-test.md)
- [TASK-20260507-006 管理端 UI 信息架构与角色化工作台重整](../../tasks/done/TASK-20260507-006-admin-ui-information-architecture-workbench.md)
- [TASK-20260507-012 Admin Console 角色化工作台与导航体系](../../tasks/done/TASK-20260507-012-admin-console-role-workbench-navigation.md)

## 背景

上一批已闭环 Codex 反代协议地基、Portal/Admin 边界、权限隔离、真实账号适配与 Console 命名空间迁移。当前优先级队列中仍有一项 P0 真实 Codex 测试账号入库任务处于 In Progress，后续两个最高优先级任务都集中在后台管理员界面的信息架构和角色化工作台。

本批目标是先建立可长期复用的真实 Codex 测试凭证基线，再把后台 Console 从“实体页面集合”收敛为“角色和任务驱动”的运营入口，支撑后续账号池热切换、实时观测和社区自助接入继续落地。

## 优先级选择

1. `TASK-20260507-016`：P0-05，真实 `auth.json` 入库与详测是 Codex 长期回归的前置基线。
2. `TASK-20260507-006`：P1-07，管理端信息架构决定后台用户能否快速找到接入、账号池、排障和账务能力。
3. `TASK-20260507-012`：P1-08，角色化工作台和导航是信息架构的代码落点，需要和 `TASK-006` 合并设计、分层验收。

## 目标

- 将真实 Codex `auth.json` 通过显式、安全、幂等的导入器写入数据库，用于长期测试。
- 增补 Codex 入库、脱敏、幂等和 dry-run smoke 的更详尽测试。
- 重整 Admin Console 的首屏信息架构，按管理员角色和高频任务组织入口。
- 重整 Console 导航分组、面包屑和移动侧栏，保留现有高级页面可访问性。
- 将批量操作设计为可信、可预检、可审计、可恢复的流程，避免管理员执行批量动作时陷入黑箱。

## 非目标

- 不把真实 token、refresh token、id token 或 `auth.json` 原文写入仓库、任务、文档或日志。
- 不默认触发真实联网 Codex smoke，避免误消耗测试额度。
- 不移除现有高级管理页面，不做大规模权限模型重写。
- 不把后台 Console 改成营销式首页，仍保持运营工具的信息密度和可扫描性。

## 方案

### Codex 长期测试账号入库

- 新增显式 runner，仅当 `gateway.codex-test.import-auth-json-path` 存在时读取本机 `auth.json`。
- 通过现有 `CodexAuthJsonParser` 提取账号 ID、token presence、过期时间和安全 fingerprint。
- 自动创建或复用 `codex-long-term-test` 账号池，providerType 为 `CODEX_OAUTH`，allowedClientFamilies 为 `CODEX`。
- 使用稳定身份指纹幂等复用账号，重复导入只更新 ciphertext、metadata 和刷新快照。
- 重复判定采用分层策略：OpenID/JWT subject、email、官方 account ID 为强信号；access/refresh token fingerprint 仅作为弱信号，不能单独合并账号。
- Codex App API 真实测试默认走 ChatGPT 登录态链路：`POST /backend-api/codex/responses` 用于 smoke，`GET /backend-api/wham/usage` 用于保活/额度探测；标准 OpenAI `/v1/responses` 仅作为 API key 模式显式覆盖。
- smoke 与保活只记录脱敏证据：HTTP 状态、requestId、耗时、failureType、planType、窗口存在性、remaining percentage，不保存 token、email、subject 或原始 usage body。
- 真实 smoke 默认采用参考项目的 `gpt-5.4@low` 探测语义：请求模型写 `gpt-5.4`，reasoning 使用 `low`；`gpt-5-codex` 不再作为 ChatGPT 登录态唯一默认模型，避免把模型不适配误判为账号不可用。
- 控制台输出只允许账号 ID、账号池 ID、状态、route eligibility、routeBlockReason 和 fingerprint。

### Admin 信息架构

- Console 首页按角色视角组织：接入管理员、运营管理员、排障管理员、财务/计费管理员、系统管理员。
- 首屏优先放置任务入口：接入 Codex、导入官方账号、管理账号池、运行 smoke、排查失败请求、查看 usage、处理告警。
- 页面内容保持运维工具密度：摘要指标、异常列表、待处理动作和下一步链接，而不是大面积 hero。
- 对高风险术语建立可读标签：distributed key 显示为访问 Key，client instance 显示为客户端实例，workspace hint 显示为工作区标识。

### Admin 导航与工作台

- 导航分组调整为：总览、接入与账号、Codex 运营、路由与策略、观测与排障、计费与用户、部署与系统、集成。
- 任务卡跳转到具体页面或带查询参数的筛选入口，避免只跳泛列表。
- 增加全局搜索/command palette 的入口壳，先覆盖本地页面、Key、账号池、请求 ID、客户端实例等导航意图。
- 移动端保留可展开侧栏和可返回路径，避免 Console 命名空间迁移后入口断裂。

### 批量操作可信性与容错性

- 批量操作入口必须展示影响范围、筛选条件、预计影响数量、不可操作项数量和风险提示。
- 执行前提供 dry-run/preflight 结果，逐项给出 `READY`、`SKIPPED`、`BLOCKED`、`RISKY` 等状态。
- 执行中记录 batchId、操作者、参数快照、目标 ID 列表摘要、成功/失败/跳过数量和每项错误原因。
- 执行结果允许部分成功，失败项可导出或再次重试，不因单项失败吞掉整体进度。
- 高风险批量动作需要二次确认，确认文案必须绑定目标范围，例如账号池、Key、用户或请求筛选条件。
- UI 上区分“批量预检”“批量执行中”“部分失败可重试”“已完成可审计”四类状态。

## 验收标准

- `TASK-016`：显式导入命令可执行，重复导入不产生重复账号，输出和持久化 metadata 不泄漏真实凭证。
- `TASK-006`：Console 首屏能按角色和任务进入 Codex 接入、账号池运营、失败请求排查、usage/账务、系统告警。
- `TASK-012`：新导航分组可扫描，现有页面仍可访问，任务卡和搜索入口有测试覆盖。
- 批量操作相关入口至少具备可信批量操作设计壳：preflight 摘要、执行状态、部分失败提示、重试入口和审计线索。
- 后端测试和前端测试通过；若浏览器验证受本地 Node 版本限制，需要记录阻断原因。

## 风险

- 真实凭证进入数据库后需要按敏感数据管理，备份和日志必须避免泄漏。
- Console 信息架构调整可能影响既有测试选择器和路由断言，需要兼容旧路由。
- `TASK-006` 与 `TASK-012` 范围重叠，本批按“设计归 `TASK-006`、代码落点和测试归 `TASK-012`”拆分归档。

## 实施记录

- 2026-05-07：选择当前最高优先级三项，建立本批闭环需求和验收标准。
- 2026-05-07：补充批量操作可信性与容错性约束，要求 Admin 批量动作具备预检、审计、部分失败恢复和重试能力。
- 2026-05-07：补充账号重复判定约束，避免同一 Codex 账号重复登录后因 `auth.json` 变化产生重复长期测试账号。
- 2026-05-07：根据用户纠偏补充 Codex App API 适配要求，明确真实 `auth.json` 不走标准 API key `/v1/responses`，而走 Codex CLI/App 对应的 responses 与 `wham/usage` 链路。
- 2026-05-07：参考项目对照后补充模型探测策略：Codex App API smoke 默认按 `gpt-5.4@low` 执行，模型不支持类 4xx 作为可恢复配置问题，不影响账号幂等与健康判定。
- 2026-05-07：`TASK-016` 已闭环：真实 `auth.json` 幂等入库，保活 200，responses smoke 200；本批剩余重点转入 `TASK-006` 与 `TASK-012` 的 Admin Console 角色化 UI/UX 闭环。
- 2026-05-07：参考项目对照后完成 Admin Console 信息架构收口：普通账号/Key/代理归入 `接入与账号`，Codex App API、Live Session、Native 兼容和调试工作台归入 `Codex 运营`。
- 2026-05-07：完成角色化工作台、批量操作可信面板、command palette、导航分组和 breadcrumb 统一，`/console` 默认进入角色工作台。
- 2026-05-07：`TASK-006` 与 `TASK-012` 已闭环，第三批最高优先级三项全部完成。

## 实现结果

- Codex 真实长期测试账号：真实 `auth.json` 已幂等写入数据库长期测试账号，Codex App API responses smoke 和 `wham/usage` 保活均返回 200；输出仅保留账号 ID、账号池 ID、fingerprint、requestId、responseId 等脱敏证据。
- Admin 首屏：新增角色化运营工作台，覆盖接入管理员、运营管理员、排障管理员、财务/计费管理员、系统管理员五个视角。
- Admin 导航：侧栏与 breadcrumb 统一为 `总览`、`接入与账号`、`Codex 运营`、`路由与策略`、`观测与排障`、`计费与用户`、`部署与系统`、`集成`。
- 批量可信性：首屏新增批量操作可信面板，明确 preflight、跳过、阻断、失败可重试和审计线索，为后续真实批量执行提供稳定承载面。
- 快速定位：新增 command palette，可按页面、账号池、请求 ID、客户端实例意图跳转，降低管理员跨页面排障成本。

## 验证记录

- 后端：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexAuthJsonParserTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexLongTermTestImportServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexResponsesSmokeHttpClientTests"`：通过。
- 真实 Codex：`.\gradlew.bat bootRun --args="--spring.profiles.active=local --gateway.codex-test.import-auth-json-path=C:/Users/zzp84/Desktop/auth.json --gateway.codex-test.import-only=true --gateway.codex-test.live-smoke=true"`：通过，`accountId=2`，`poolId=5`，保活 200，responses smoke 200。
- 前端测试：`bun run test -- src/app/navigation.test.ts src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/app/route-surfaces.test.ts`：通过，19 个测试通过。
- 前端类型与构建：`bun run typecheck` 通过，`bun run build` 通过。
- 浏览器验证：Browser 插件因本机 Node runtime `v22.20.0` 低于 `v22.22.0` 被阻断；已用 Playwright MCP 验证桌面 1440x980 与移动 390x844。页面身份、非空渲染、无 framework overlay、角色工作台、批量可信面板、command palette 筛 `request`、跳转请求日志并展示 Codex 请求行均通过。

## 遗留风险与后续建议

- 开发期仍有 1 条 Vite/React Router `HydrateFallback` warning，不影响生产构建；后续可在全局路由层补 `HydrateFallback` 进一步消噪。
- 本批已完成批量操作可信设计壳，真实批量执行的事务边界、失败项导出和重试 API 可在 `TASK-20260507-003` 或后续批量执行专项继续深化。
- 社区用户自助界面仍在 `TASK-20260507-010`，后台管理员界面已先行收口。
