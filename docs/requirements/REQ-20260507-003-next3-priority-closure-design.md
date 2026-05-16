# REQ-20260507-003 第二批最高优先级任务闭环设计

状态：Done  
日期：2026-05-07  
关联任务：

- [TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke](../../tasks/done/TASK-20260507-001-codex-official-account-real-adapter-smoke.md)
- [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](../../tasks/done/TASK-20260507-014-portal-console-ux-acceptance-system.md)
- [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](../../tasks/done/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md)

## 背景

上一批任务已经完成 Codex CLI 请求保真、Portal/Admin 身份边界和 API 权限隔离。本批继续按当前优先级推进：先补 Codex 官方账号真实凭证适配与 smoke 边界，再补 Portal/Console UI/UX 验收体系，最后把后台管理端主路径迁移到 `/console/*`，为后续角色化工作台和自助接入界面提供稳定基础。

用户提供了真实 Codex `auth.json` 测试文件。该文件属于敏感凭证，只允许用于本机只读结构校验或显式 smoke 输入；不得把 token、refresh token、session secret 或完整 JSON 写入日志、任务、需求文档、测试快照、提交内容或最终交付说明。

## 目标

- 闭环 Codex 官方账号 `auth.json` 解析、刷新结果建模和 `/v1/responses` smoke harness。
- 建立 Portal/Console 的 UI/UX 验收矩阵，让后续页面级硬化有可执行标准。
- 将 Admin Console 主命名空间迁移到 `/console/*`，保留旧后台路径 redirect。
- 完成实现、自动化验证和本地任务归档。

## 范围

- 后端：Codex 官方账号凭证解析、脱敏摘要、刷新/smoke contract、失败冷却与可解释结果。
- 前端：UI/UX 验收矩阵、核心页面 viewport 覆盖、状态和高风险输入规则的可测试事实源。
- 前端路由：`/console/*` 管理端路由、旧路径 redirect、登录入口区分和路由测试。
- 文档任务：本需求、三项任务、索引、验证结果和遗留风险回写。

## 非目标

- 不把真实 `auth.json` 内容纳入仓库。
- 不擅自对真实官方服务发起消耗额度的联网 smoke；真实 smoke 只提供显式 harness 和安全边界。
- 不在本批次重做整站视觉或 Admin Console 全量信息架构。
- 不改变 Admin API namespace，也不改变 Community Portal 的 `/portal/*` 路由。

## 详细设计

### 1. Codex 官方账号真实适配与 smoke

- 新增 Codex `auth.json` parser，识别常见 token、账户、过期时间和计划字段，输出脱敏摘要与 token presence。
- parser 输出只允许包含字段存在性、过期时间、账号标识和短 fingerprint，不暴露原始 secret。
- 在官方账号服务中为 `CODEX`/`CODEX_OAUTH` 建立 adapter contract：刷新成功写入 plan、quota、resetAt、nextRefreshAfter；失败写入错误分类、cooldownUntil 和 route block reason。
- 提供可选 smoke harness，用显式传入的 `auth.json` 路径执行 `/v1/responses` 最小请求前置校验；默认测试使用 fixture，不触碰真实凭证。

### 2. Portal/Console UI/UX 验收体系

- 建立页面清单、状态清单、viewport 清单、高风险输入规则和 destructive 操作规则。
- 用测试保证核心页面至少覆盖桌面与移动两个 viewport，并要求 loading、empty、loaded、error、permission denied 等状态具备可解释定义。
- Codex 接入链路和管理端高风险字段不得只依赖手写裸 ID，必须有 picker、masked secret、copy button、inline help 或校验型 field array 的落地路径。
- 产出可复用验收事实源，供 `TASK-20260507-007` 页面级硬化继续引用。

### 3. Admin Console 命名空间迁移

- 新增 `/console` layout route，继续复用 `RequireAdminAuth` 和 `AppLayout`。
- 根路径和旧管理端路径通过 redirect map 跳转到 `/console/*`。
- `AppLayout`、导航、breadcrumb 和测试 helper 输出 `/console/*` 管理路径。
- 保持 `/login` 为管理员登录，`/portal/login` 为社区登录；`/portal/*` 不受迁移影响。

## 风险

- 真实 Codex 官方协议可能变化，因此真实 adapter 必须隔离在 Codex 官方账号层。
- `auth.json` 为真实凭证，任何调试输出都必须掩码或只输出结构信息。
- 路由迁移可能牵动大量测试和导航链接，需要优先覆盖代表路径并保留旧路径兼容。
- UI/UX 验收矩阵若过度追求一次性页面重构，会放大改动面；本批先落事实源和核心规则。

## 验收标准

- 三个任务均从 `tasks/in-progress/` 移动到 `tasks/done/`，任务内回写实现结果与验证结果。
- 后端测试覆盖 Codex `auth.json` parser、脱敏、安全失败路径和 smoke harness contract。
- 前端测试覆盖 UI/UX 验收矩阵与 `/console/*` 路由迁移。
- 真实 `auth.json` 不出现在仓库差异、日志和文档正文中。
- `.\gradlew.bat test` 与 `bun run test` 或对应子集验证通过，若存在既有噪音需明确记录。

## 实施记录

- 2026-05-07：创建需求，确认本批三项最高优先级任务和凭证安全边界。
- 2026-05-07：完成 Codex `auth.json` parser、官方账号 dry-run smoke endpoint、UI/UX 验收矩阵、`/console/*` 路由迁移和旧路径 redirect。
- 2026-05-07：验证通过 `.\gradlew.bat test`、`bun run typecheck`、`bun run test`。in-app Browser 渲染验证因 Node 运行时 `22.20.0` 低于 Browser 插件要求 `22.22.0` 被阻断；Vite 服务已启动在 `http://127.0.0.1:5173` 供人工查看。

## 实现结果

- `CodexAuthJsonParser` 支持真实 Codex `auth.json` 的 `tokens.access_token`、`tokens.refresh_token`、`tokens.account_id` 和 `OPENAI_API_KEY` fallback 解析，并输出脱敏摘要。
- `OfficialAccountAdminService` 支持从 raw `auth.json` metadata 提取 Codex token，保存时递归脱敏，并提供 `/admin/accounts/{id}/official/codex/responses-smoke` dry-run smoke。
- 前端管理端主入口迁移到 `/console/*`，旧根管理路径通过 redirect 保持兼容，Portal `/portal/*` 不受影响。
- `ux-acceptance.ts` 建立 Portal/Console 核心页面、viewport、状态、空态、错误态、高风险输入和破坏性操作验收矩阵。

## 验证结果

- 后端：`.\gradlew.bat test` 通过。
- 前端类型检查：`bun run typecheck` 通过。
- 前端测试：`bun run test` 通过，51 个测试文件、110 个测试通过。
- 敏感信息扫描：本轮新增/修改文件未发现 `AIzaSy`、`ya29.`、超长 token、真实 OpenAI/Codex key 形态。

## 遗留问题

- 真实联网 `/v1/responses` smoke 仍需在受控环境显式执行，当前实现闭环的是 dry-run 前置校验、脱敏请求预览和路由可用性判断。
- Browser 插件渲染验证需要本机 Node 升级到 `>=22.22.0` 后再跑。
