# TASK-20260501-026 Passkey/WebAuthn、注册策略与安全审计

状态：Done  
优先级：High  
来源：TASK-20260501-022 后续拆分  
关联任务：[TASK-20260501-022](../done/TASK-20260501-022-passkey-totp-captcha-email-verification.md)  
关联需求：[REQ-20260501-006](../../docs/requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)
关联推进需求：[REQ-20260506-003](../../docs/requirements/REQ-20260506-003-ninth-priority-task-closure-design.md)
关联说明文档：[passkey-registration-policy-audit](../../docs/passkey-registration-policy-audit.md)

## 背景

第五批已完成 CAPTCHA、邮箱验证与 TOTP 后端闭环，但 Passkey/WebAuthn、注册策略和安全审计仍未落地。

## 目标

补齐浏览器侧无密码认证、注册准入策略和安全事件可追溯能力。

## 范围

- WebAuthn registration/assertion challenge 生成与校验。
- 用户 Passkey 凭证生命周期管理。
- 邀请码、白名单、邮箱域名注册策略。
- 登录、TOTP、Passkey、注册策略变更的安全审计流水。
- Portal 前端安全设置页。

## 非目标

- 不替换现有密码登录与 TOTP。
- 不接入企业 IdP SSO。

## 风险

- WebAuthn ceremony 强依赖浏览器、安全上下文与 RP ID 配置。
- 多实例 challenge store 需要 Redis 或等价共享状态。

## 验收标准

- 用户可注册、使用、删除 Passkey。
- 管理端可配置注册准入策略。
- 关键安全动作可查询审计记录。

## 本批推进记录

- 2026-05-06：进入第九批任务闭环，目标是补上后端 Passkey challenge、credential lifecycle、注册策略与安全审计最小可验证闭环。
- 2026-05-06：完成 Passkey credential 持久化、challenge、public key 注册、assertion 签名校验、删除、注册策略与 audit log 记录。

## 实现结果

- 新增 `gateway_user_passkey_credential`、entity、repository 和 Liquibase changelog。
- Portal 支持 Passkey registration start/finish、assertion start/finish、list/delete。
- assertion finish 使用保存 public key 校验 `authenticatorData + SHA256(clientDataJson)` 签名。
- 管理端支持 `GET/PUT /admin/security/registration-policy`。
- 注册策略支持邮箱域名、邀请码和创建 API Key 前邮箱验证要求。
- 安全审计覆盖邮箱验证、TOTP、Passkey、注册策略拦截和 key 创建拦截。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests"
```

覆盖：

- Passkey 注册、断言登录、删除和审计。
- 注册策略域名拦截、邀请码拦截和邮箱验证前禁止创建 API Key。

## 遗留问题

- 完整 attestation policy、浏览器 UI、RP ID 动态配置和 Redis challenge store 尚未在本轮实现。

## 后续建议

- 后续把 Passkey challenge store 接入 Redis，并在 Portal 前端安全设置页接入浏览器 WebAuthn ceremony。
