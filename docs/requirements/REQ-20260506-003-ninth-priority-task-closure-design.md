# REQ-20260506-003 第九批任务闭环设计

状态：Done  
创建日期：2026-05-06  
关联任务：

- [TASK-20260501-026 Passkey/WebAuthn、注册策略与安全审计](../../tasks/done/TASK-20260501-026-passkey-webauthn-registration-policy-audit.md)
- [TASK-20260501-005 Portal 用户自助增强](../../tasks/done/TASK-20260501-005-portal-self-service.md)
- [TASK-20260501-006 编程类账号身份治理](../../tasks/done/TASK-20260501-006-programming-account-identity.md)

## 背景

按 `tasks/index.md` 中所有未完成任务排序，当前最高优先级是 `TASK-20260501-026`；Medium 任务中按索引顺序后续为 `TASK-20260501-005` 与 `TASK-20260501-006`。这三项都围绕用户、账号、身份、安全与自助运营，可以在同一批内形成“用户安全入口 + Portal 自助查看 + 编程账号治理”的闭环。

## 目标

- 为 Portal 用户补齐 Passkey/WebAuthn challenge、凭证生命周期、注册策略与安全审计最小闭环。
- 为 Portal 增加自助运营摘要，覆盖个人资料、余额、账单订单、用量与渠道状态。
- 为编程类账号增加 Codex/Antigravity/Copilot/Claude Plan 身份治理模型、额度视图与路由可用性判断。

## 范围

### Passkey/WebAuthn、注册策略与安全审计

- 增加 Passkey credential 持久化实体与 repository。
- 增加 registration/assertion challenge、完成注册、完成登录、删除凭证接口。
- 增加注册策略校验，支持邮箱域名白名单、邀请码占位和是否要求邮箱验证。
- 增加安全审计流水，覆盖 Passkey、TOTP、邮箱验证、注册策略拦截。

### Portal 用户自助增强

- 增加 Portal profile/self-service summary。
- 增加用户订单列表、余额摘要、用量摘要与明细。
- 增加可用渠道/模型状态视图，供 Portal 只读查看。

### 编程类账号身份治理

- 扩展编程类账号 provider type：Codex、Antigravity、Copilot、Claude Plan。
- 对 import auth json 中的 identity、quota、client family、adoption decision 做标准化视图。
- 增加管理端 identity summary 与 client family 路由可用性判断。

## 非目标

- 不在本轮替换已有密码登录、TOTP 或社交 OAuth。
- 不接入企业 IdP SSO。
- 不保存 OAuth 明文 token。
- 不实现真实浏览器端 UI 页面，只补后端接口、模型、测试与本地文档。

## 风险

- WebAuthn 完整 attestation 依赖浏览器、安全上下文和 RP ID，本轮以后端 challenge、origin 校验、public key 注册和 assertion 签名校验形成可测试闭环，生产 attestation policy 可后续强化。
- 多实例 challenge store 仍建议迁移到 Redis，本轮先沿用本地内存结构。
- Portal 自助统计依赖请求日志与 usage 归档完整性，历史空数据需要有明确空态。

## 验收标准

- 三个任务均移动到 `tasks/done/`，并回写实现结果、验证情况、遗留问题和后续建议。
- Passkey 注册、登录、删除、注册策略拦截、安全审计有单元测试。
- Portal 自助摘要能返回 profile、余额、订单、usage、渠道状态。
- 编程类账号 identity summary 能展示身份、授权状态、额度窗口和路由可用性。

## 实现结果

- `TASK-20260501-026`：新增 Passkey credential 持久化、registration/assertion challenge、public key 注册、assertion 签名校验、凭证删除、注册策略与安全审计。
- `TASK-20260501-005`：新增 Portal profile、自助摘要、订单、usage summary 和渠道状态接口。
- `TASK-20260501-006`：新增 Codex/Antigravity/Copilot/Claude Plan 编程账号 provider type、refresh adapter、identity summary 与 client family 路由可用性判断。
- 已新增本地说明文档：[passkey-registration-policy-audit](../passkey-registration-policy-audit.md)、[portal-self-service](../portal-self-service.md)、[programming-account-identity](../programming-account-identity.md)。

## 验证情况

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.AccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.OAuthSessionRefreshServiceTests"
```

验证覆盖：

- Passkey 注册、断言登录、删除、注册策略拦截与安全审计。
- Portal profile、余额、订单、usage、订阅、Key 和渠道状态聚合。
- Codex 编程账号 identity summary 与 refresh adapter。

## 遗留问题

- WebAuthn attestation policy、浏览器 UI、RP ID 动态配置和 Redis challenge store 后续可继续增强。
- Portal 自助前端页面仍可后续接入当前后端接口。
- 编程账号真实 provider quota API、identity adoption 审核流和更细粒度风控策略后续可继续增强。
