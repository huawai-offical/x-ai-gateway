# TASK-20260501-022 Passkey/TOTP/验证码/邮箱验证

状态：Done  
优先级：High  
来源：TASK-20260501-009 后续拆分  
关联任务：[TASK-20260501-009](../done/TASK-20260501-009-security-system.md)  
关联需求：[REQ-20260501-006](../../docs/requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)

## 背景

当前安全体系已完成 SSRF、敏感词和社交 OAuth 骨架，但二次验证与注册治理仍未闭环。

## 目标

补齐用户账号安全和注册治理的本地可验证基础。

## 范围

- Passkey / WebAuthn。
- TOTP 二次验证。
- 注册验证码、邮箱验证。
- 邀请码 / 白名单注册策略。
- 管理端安全策略配置。

## 非目标

- 本轮不实现 Passkey/WebAuthn 浏览器 ceremony。
- 本轮不发送真实邮件。

## 详细设计

- 用户表增加 `emailVerifiedAt`、`totpSecretCiphertext`、`totpEnabled`、`totpVerifiedAt`。
- 新增 Portal 安全服务：
  - CAPTCHA challenge 使用本地内存 TTL store。
  - 邮箱验证 code 使用本地内存 TTL store，并在确认后写入用户。
  - TOTP secret 使用 AES/GCM 加密后写入用户。
- `PortalRegisterRequest` 支持可选 CAPTCHA challenge。
- `PortalLoginRequest` 支持可选 TOTP code；当用户已启用 TOTP 时必须校验。

## 风险

- 本地内存 challenge 在多实例部署中不可共享。
- 邮箱验证 code 本轮返回给调用方，仅用于本地开发和测试。

## 验收标准

- 用户可完成 CAPTCHA、邮箱验证、TOTP setup/enable/disable。
- TOTP 启用后登录会委托安全服务校验验证码。
- Passkey/WebAuthn、邀请/白名单与安全审计作为后续独立任务继续推进。

## 实现结果

- `GatewayUserEntity` 已增加邮箱验证与 TOTP 状态字段，并通过 Liquibase `db.changelog-0043-portal-security-mfa.yaml` 迁移。
- 新增 `PortalSecurityService`，提供 CAPTCHA、邮箱验证码、TOTP secret 加密存储、setup URI、启用、禁用和登录校验。
- `PortalAuthService` 已接入注册 CAPTCHA 与登录 TOTP 校验；`PortalAuthController` 已暴露本地安全 API。

## 测试/验证情况

- 通过 `PortalSecurityServiceTests` 覆盖 CAPTCHA、邮箱验证、TOTP setup/enable/disable 和登录校验。
- 通过 `PortalAuthServiceTests` 覆盖注册 CAPTCHA 与登录 TOTP 的委托链路。
- 通过目标 Gradle 回归测试。

## 遗留问题

- 暂未实现 Passkey/WebAuthn 浏览器 ceremony。
- 暂未发送真实邮件，邮箱验证码仍为本地开发响应。
- 暂未实现邀请/白名单注册策略和安全审计流水。

## 后续建议

- 新增 `TASK-20260501-026` 继续推进 Passkey/WebAuthn、注册策略与审计闭环。
