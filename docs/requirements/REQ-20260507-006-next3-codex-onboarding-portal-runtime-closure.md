# REQ-20260507-006 第四批最高优先级任务闭环设计

状态：Done  
日期：2026-05-07  
关联任务：

- [TASK-20260507-005 Codex 接入向导、Client Instance 与 Deep Link UI 闭环](../../tasks/done/TASK-20260507-005-codex-onboarding-client-instance-deeplink-ui.md)
- [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](../../tasks/done/TASK-20260507-010-community-portal-codex-self-service-surface.md)
- [TASK-20260507-003 Codex 账号池热切换、负载均衡与失败恢复 UI](../../tasks/done/TASK-20260507-003-codex-account-pool-hot-switch-failover-ui.md)

## 背景

上一批已完成真实 Codex `auth.json` 长期测试账号、Admin Console 角色工作台和 `Codex 运营` 导航分组。当前剩余队列中优先级最高的三项集中在 Codex 端到端可用路径：管理员要能把账号池、访问 Key、Client Instance 和 Deep Link 串成一个向导；社区用户要能在 Portal 里看到自己的 Codex 接入状态、Key、订阅和用量；运营管理员要能在账号池维度执行热切换、隔离、恢复和 smoke。

## 优先级选择

1. `TASK-20260507-005`：P1-09，Codex 接入主路径，是管理员把系统能力交付给 CLI/插件用户的第一入口。
2. `TASK-20260507-010`：P1-10，社区用户主路径，需要把后台实体隐藏成用户可理解的接入状态、Key、usage 和下一步。
3. `TASK-20260507-003`：P2-11，管理员运营效率，负责账号池故障恢复、热切换、候选解释和批量操作可信性。

## 目标

- 在 Admin Console 提供 Codex 接入向导，按步骤串联账号池、访问 Key、Client Instance、Deep Link/Plugin grant 和 smoke。
- 在 Portal 首页提供社区用户的 Codex 接入卡片、个人 usage/余额、Key 状态、订阅状态和失败解释。
- 在账号池详情提供 Codex Runtime 面板，展示候选账号、冻结/冷却/失败计数、最近用量、预览和恢复操作。
- 保持所有 secret 安全边界：长期 secret 不进 URL、不进 localStorage、不在日志或文档中输出；Deep Link 默认只承载一次性 grant 或 masked 信息。
- 继续使用现有 API 和数据模型，必要时只补最小聚合/恢复 API，避免绕过现有权限、账号池绑定和 client family 校验。

## 非目标

- 不读取或修改用户本机 Codex CLI 配置文件。
- 不让 Portal 暴露上游官方账号、账号池内部候选或全局路由策略。
- 不在本批实现完整生产级负载均衡算法；本批聚焦可见性、手动热切换、失败恢复和 smoke。
- 不把 masked key 当作可用 secret 传入 Deep Link；完整 secret 仍只能来自创建/轮换的一次性窗口或明确输入。

## 详细设计

### TASK-005 Admin Codex 接入向导

- 新增 `CodexOnboardingPage`，挂载到 `/console/accounts/connect/codex`，保留普通 OAuth provider 的泛化授权页。
- 向导步骤：
  1. 选择或创建 Codex 账号池，优先展示 `CODEX_OAUTH` 且允许 `CODEX` client family 的池。
  2. 导入或复用官方账号：入口指向账号池导入 auth.json 或已有账号池详情，不直接暴露真实 token。
  3. 创建访问 Key：默认 `allowedProtocols=["openai","responses"]`、`allowedClientFamilies=["CODEX"]`、`requireClientFamilyMatch=true`，创建后绑定账号池并启用。
  4. 注册 Client Instance：填写 instance alias、workspace hint、plugin name/version、deep link scheme。
  5. 发行一次性 Deep Link/Plugin grant：优先使用本次新建 key 的 fullKey 或 one-time export token；已有 key 需要管理员明确提供一次性 secret export token，不回显长期 secret。
  6. smoke/排障：展示 curl、config.toml、requestId 排查入口和失败下一步。
- 向导状态需要显示 READY、SKIPPED、BLOCKED、FAILED，支持部分完成后继续。

### TASK-010 Portal Codex 自助接入

- Portal 首页新增 `CodexAccessCard`，根据用户 Key、订阅、余额和最近 ledger 推导状态：未接入、待验证、可用、额度不足、Key 停用、授权过期。
- 展示用户可理解字段：endpoint、masked key、Key 名称、订阅状态、Token 余额、最近使用、smoke 状态和下一步动作。
- Portal 只链接到 `/portal/keys`、`/portal/subscriptions`、`/portal/redeem`，不展示账号池 ID、上游账号 ID、provider secret 或路由候选。
- smoke 失败文案归类为：额度不足、Key 禁用、授权过期、模型不可用、联系管理员。

### TASK-003 Codex Runtime 与失败恢复

- 在账号池详情页新增 `Codex Runtime` 区块，仅对 `CODEX_OAUTH` 或允许 `CODEX` client family 的账号池强调展示。
- Runtime 摘要包含 active pool、候选账号数、可路由账号、冻结账号、冷却账号、失败账号、最近选中/最近错误。
- 候选列表展示每个账号的健康、冻结、cooldown、refresh status、failure count、quota、成功率、缓存收益和最近使用。
- 操作：
  - `隔离账号`：调用现有 `/admin/accounts/{id}/freeze?frozen=true`。
  - `恢复账号`：调用 `/admin/accounts/{id}/runtime-reset`，清理 frozen、healthy、lastError、refreshFailureCount、cooldown/nextRefreshAfter。
  - `刷新 quota`：调用现有 `/admin/accounts/{id}/official/quota-refresh`。
  - `responses smoke`：调用现有 `/admin/accounts/{id}/official/codex/responses-smoke`，默认 dry-run。
- 所有 UI 操作显示影响范围和下一步，不把安全策略冻结误判成普通可恢复。

## 验收标准

- Admin 可从 `/console/accounts/connect/codex` 看到完整接入步骤，完成资源选择/创建、Client Instance 注册和一次性授权预览。
- Portal 首页能在不泄漏内部实体的前提下展示 Codex 接入状态、Key、订阅、余额、usage 和下一步。
- 账号池详情页能展示 Codex Runtime 候选、失败恢复和 smoke 操作，失败项可独立恢复，不影响其他候选。
- 前端测试覆盖接入向导 happy path、Portal 未接入/可用/额度不足/Key 停用、Runtime 冻结/恢复/smoke。
- 后端测试覆盖账号 runtime reset，确保冻结、冷却、错误和失败计数被清理，同时保留安全字段和加密 secret。
- 浏览器验证覆盖 Admin 向导、Portal 首页、账号池 Runtime 三个路由；优先使用 Browser 插件，若仍失败再记录原因。

## 风险

- 现有工作区改动很多，本批必须只在相关页面、API 和任务文档内前进，不回退其他文件。
- 向导需要处理新建 key 后才有完整 secret 的事实；复用旧 key 只能生成 masked onboarding pack 或要求一次性 export token。
- Portal 聚合当前主要基于已有用户 Key、订阅和余额数据，真实 per-user usage 聚合若后端尚未提供，需要后续专项增强。

## 实施记录

- 2026-05-07：按剩余优先级选择 `TASK-005`、`TASK-010`、`TASK-003` 作为第四批闭环目标。
- 2026-05-07：新增 Admin Codex 接入向导 `/console/accounts/connect/codex`，串联账号池、访问 Key、Client Instance、一次性 Deep Link/Plugin grant 和 onboarding pack smoke。
- 2026-05-07：Portal 首页新增社区 Codex 接入卡片，仅展示个人 Key、API Base、模型、订阅/余额、最近使用和下一步入口，不暴露后台治理实体。
- 2026-05-07：账号池详情新增 Codex Runtime 面板，支持候选摘要、冻结/恢复、runtime reset、quota refresh 和 dry-run responses smoke。
- 2026-05-07：后端新增 `/admin/accounts/{id}/runtime-reset`，只清理运行态失败字段，不改密文 secret。

## 验证记录

- `web`: `bun run typecheck`
- `web`: `bun run test -- src/features/accounts/codex-onboarding-page.test.tsx src/features/accounts/account-pool-detail-page.test.tsx src/features/portal/portal-home-page.test.tsx`
- `web`: `bun run build`
- `backend`: `./gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests"`
- `browser-use`: Node 22.22 环境下成功启动 in-app browser，打开 `http://127.0.0.1:5173/console/accounts/connect/codex` 后按未登录状态重定向到 `/login?redirect=%2Fconsole%2Faccounts%2Fconnect%2Fcodex`；未注入真实凭据。

## 遗留与后续建议

- Portal 当前 Codex 状态基于用户 Key、订阅、余额和 ledger 推导；更细的 per-user request usage、失败原因聚合可后续接入 request log 用户维度统计。
- Codex Runtime 本批完成手动热切换和失败恢复面；权重编辑、候选 preview 和 system event 审计可作为后续增强。
