# TASK-20260524-003-02 Portal 注册邀请码核销

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-003](TASK-20260524-003-portal-invitation-code-system-parent.md)  
上游来源：[REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)

## 背景

注册路径当前只校验策略内存白名单，不能持久核销邀请码。需要在用户创建成功时写入邀请码使用记录并递增使用次数。

## 目标

- 注册策略不再依赖 `inviteCodes` 集合判断真实邀请码。
- 邮箱注册带邀请码或策略要求邀请码时，先校验渠道，再在用户创建后核销邀请码。
- `inviteCodesConfigured` 来自持久化邀请码库存。
- 社交 OAuth 首次建号遇到强制邀请码时硬失败。

## 非目标

- 不在社交 OAuth callback 中接收邀请码。
- 不改变已有邮箱域名限制。
- 不改变 Portal 登录态语义。

## 输入

- `PortalAuthService.register`
- `PortalSecurityService`
- `PortalSocialOAuthService`

## 输出

- Portal 注册真实核销邀请码。

## 影响范围

- Portal 注册接口、注册策略接口和相关测试。

## 依赖

- TASK-20260524-003-01 的邀请码服务。

## 风险

- 事务顺序错误会导致用户创建和邀请码核销不一致。
- 旧测试构造器可能需要保持兼容。

## 验收标准

- 有效邀请码注册成功后写入 usage 并增加 usedCount。
- 无效邀请码注册失败且不创建用户。
- 策略强制邀请码时无邀请码注册失败。
- 社交 OAuth 首次建号在强制邀请码时失败。

## 测试边界

- 更新 `PortalAuthServiceTests`、`PortalSecurityServiceTests`、`PortalSocialOAuthServiceTests`。

## 当前状态

- 2026-05-24：待实施。
- 2026-05-24：已完成 Portal 邮箱注册邀请码核销、注册策略库存状态改造和社交 OAuth 强制邀请码硬失败。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
