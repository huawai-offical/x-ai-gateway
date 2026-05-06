# Passkey/WebAuthn、注册策略与安全审计

关联需求：[REQ-20260506-003 第九批任务闭环设计](requirements/REQ-20260506-003-ninth-priority-task-closure-design.md)  
关联任务：[TASK-20260501-026 Passkey/WebAuthn、注册策略与安全审计](../tasks/done/TASK-20260501-026-passkey-webauthn-registration-policy-audit.md)

## 实现范围

- 新增 `gateway_user_passkey_credential` 持久化表、JPA entity 与 repository。
- Portal 支持 Passkey registration challenge、registration finish、assertion challenge、assertion finish 和删除凭证。
- registration finish 校验 `clientDataJson.type`、challenge、origin，并保存 credential public key。
- assertion finish 校验 `clientDataJson`、credential allow list，并使用保存的 public key 校验 `authenticatorData + SHA256(clientDataJson)` 签名。
- 新增管理端注册策略接口：`GET/PUT /admin/security/registration-policy`。
- 注册策略支持邮箱域名白名单、邀请码要求和“创建 API Key 前必须完成邮箱验证”。
- 安全审计写入 `audit_log`，覆盖邮箱验证、TOTP、Passkey、注册策略拦截与 key 创建拦截。

## 关键接口

- `POST /portal/auth/security/passkeys/registration/start`
- `POST /portal/auth/security/passkeys/registration/finish`
- `POST /portal/auth/passkeys/assertion/start`
- `POST /portal/auth/passkeys/assertion/finish`
- `DELETE /portal/auth/security/passkeys/{id}`
- `GET /portal/auth/security/passkeys`

## 边界

- 本轮实现后端 WebAuthn challenge、origin、public key 与 assertion 签名闭环。
- 完整 attestation policy、浏览器 UI、RP ID 动态配置和 Redis challenge store 后续可继续增强。

## 验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests"
```

覆盖：

- Passkey 注册、断言登录、删除和审计。
- 注册策略域名拦截、邀请码拦截和邮箱验证前禁止创建 API Key。
