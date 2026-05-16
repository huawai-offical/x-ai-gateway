# TASK-20260513-004 客户门户完整度补齐

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260513-001](../done/TASK-20260513-001-reference-translation-admin-portal-audit.md)  
上游来源：[REQ-20260513-002](../../docs/requirements/REQ-20260513-002-high-priority-gap-closure-implementation.md)、[REQ-20260513-001](../../docs/requirements/REQ-20260513-001-reference-translation-admin-portal-audit.md)、[REP-20260513](../../docs/reports/REP-20260513-reference-translation-admin-portal-audit.md)

## 背景

客户门户后端 API 已覆盖登录、注册、订阅、Key、兑换、余额、订单、profile、安全能力、用量 summary、channel status 等，但前端门户目前主要包含概览、订阅、访问密钥、兑换与余额、公告详情。客户自助体验还没有完全闭环。

## 目标

- 增加客户安全中心：profile、邮箱验证、Passkey、TOTP、OAuth identities 解绑。
- 增加用量明细页：按时间、模型、Key、状态、成本查看并支持导出。
- 增加服务状态页：展示 channel/provider 可用性、维护状态、降级说明。
- 增加订单详情页：充值订单、支付状态、发票/退款信息展示。
- 强化 Codex 接入体验：一键复制配置、Deep Link、客户端配置下载、诊断反馈。
- 保持门户 UI 与后台 Console 明确区隔。

## 非目标

- 不在本任务内实现真实支付通道接入。
- 不在本任务内重构 Admin Console。
- 不在本任务内新增公开营销站点。

## 输入

- `web/src/features/portal/`
- `web/src/app/router.tsx`
- `src/main/java/**/PortalAuthController.java`
- 现有 Portal API client。

## 输出

- 新增 Portal 安全中心、用量、服务状态、订单详情页面。
- Portal 导航更新。
- 缺失 API client 补齐。
- 前端测试和浏览器 smoke 验证记录。

## 影响范围

- Portal routes。
- Portal shell。
- Portal API client。
- 客户自助流程。

## 依赖

- 现有 Portal session。
- 现有 profile、usage summary、channel status、orders、安全相关后端 API。

## 风险

- 安全中心涉及凭证与 MFA 状态，需要避免误操作。
- 用量导出需要控制数据范围和权限。
- 服务状态展示不能泄漏后台敏感 provider secret。

## 验收标准

- 客户可以在门户内完成个人资料与安全设置查看/操作。
- 客户可以查看自己的用量、订单、Key、订阅和兑换记录。
- 客户可以查看服务状态与 Codex 接入配置。
- Portal 与 Admin Console 在路由、导航、视觉层级上清晰区分。

## 测试边界

- Portal route smoke。
- API client 单测或集成测试。
- 浏览器手工验证登录后主要页面可访问。

## 实施结果

- Portal API client 新增 profile、security status、passkeys、email verification、TOTP、OAuth identities、usage summary、channel status 等函数。
- Portal 路由新增 `/portal/security`、`/portal/usage`、`/portal/status`、`/portal/orders`。
- Portal 导航新增安全中心、用量、服务状态、订单入口。
- 新增 `PortalSecurityPage`，覆盖 profile、安全状态、邮箱验证、TOTP、Passkey、社交身份。
- 新增 `PortalUsagePage`，展示请求数、Token、近期用量并支持 CSV 导出。
- 新增 `PortalStatusPage`，展示客户可见的 provider/channel 可用性。
- 新增 `PortalOrdersPage`，展示订单、支付、退款和对账状态，并保留创建订单入口。
- Vite dev proxy 新增 `/portal` API 代理，并对 HTML 导航做 bypass，解决 Portal 前端路由与 API 代理冲突。

## 验证记录

- `bun run typecheck`
- `bun run lint`
- `bun run test -- src/app/navigation.test.ts src/app/layout.test.tsx src/features/portal/portal-home-page.test.tsx`
- Browser smoke：`/portal/security`、`/portal/usage`、`/portal/status`、`/portal/orders` 均可访问，页面不出现 Admin Console 文案。

## 当前状态

Done。
