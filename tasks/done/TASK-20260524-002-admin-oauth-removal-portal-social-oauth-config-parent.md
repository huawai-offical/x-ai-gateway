# TASK-20260524-002 删除 Admin 厂商 OAuth 连接并配置化 Portal 社交 OAuth

状态：Done  
优先级：Critical  
类型：父任务  
上游来源：[REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 背景

Admin 侧厂商 OAuth 连接当前只有授权跳转和伪 token 入库，不具备真实上游厂商 API credential 能力。Portal 社交 OAuth 属于用户登录能力，应保留，但需要后台配置启用开关和第三方应用信息，避免不可用 provider 出现在登录入口。

## 目标

- 下线 Admin 侧厂商 OAuth 连接入口。
- 保留 Portal 社交 OAuth。
- 将 Portal 社交 OAuth 的启用状态和 provider clientId/clientSecret 配置纳入后台系统参数。
- 保证不可用 provider 不出现在 Portal provider list，也不能 start。
- 支持已注册 Portal 用户后续绑定社交 OAuth。
- 支持管理员控制注册渠道，并新增邀请码注册渠道。

## 非目标

- 不删除 auth.json 导入能力。
- 不删除历史账号类型枚举。
- 不实现厂商上游 OAuth token exchange。
- 不删除 Portal 社交 OAuth。

## 输入

- [REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)
- Admin OAuth controller/service。
- System settings API 和页面。
- Portal social OAuth service 和 profile clients。

## 输出

- 四个子任务的实现和验证结果。
- 文档、任务和测试状态回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- `src/main/java/com/prodigalgal/xaigateway/portal/application/`
- `src/main/java/com/prodigalgal/xaigateway/portal/api/`
- `web/src/features/accounts/`
- `web/src/features/portal/`
- `web/src/features/settings/`
- `tasks/index.md` 与 `docs/index.md`

## 依赖

- `system_setting` 表。
- `CredentialCryptoService`。
- 现有 Portal social OAuth provider client。

## 风险

- 历史 `OAuth` 命名已经混用上游账号和社交登录，改动时需要避免误删 Portal social OAuth。
- 旧 UI 测试可能依赖“发起 OAuth 连接”按钮，需要同步改成 auth.json 导入口径。
- 注册渠道默认值需要保持兼容，避免现有部署突然关闭邮箱密码注册。
- 社交 OAuth 绑定必须防止外部身份跨用户绑定。

## 验收标准

- 子任务 `002-01`、`002-02`、`002-03` 和 `002-04` 均完成。
- 后端和前端定向测试通过。
- 文档说明 Admin 厂商 OAuth 已下线，Portal 社交 OAuth 改由后台配置，并记录注册渠道治理口径。

## 测试边界

- 父任务只记录整体状态。
- 具体测试由子任务定义。

## 关联任务

- [TASK-20260524-002-01](TASK-20260524-002-01-remove-admin-upstream-oauth-connection.md)
- [TASK-20260524-002-02](TASK-20260524-002-02-portal-social-oauth-admin-config.md)
- [TASK-20260524-002-03](TASK-20260524-002-03-portal-social-oauth-account-binding.md)
- [TASK-20260524-002-04](TASK-20260524-002-04-portal-registration-channel-policy.md)

## 当前状态

- 2026-05-24：创建父任务，准备实施两个子任务。
- 2026-05-24：按新增需求扩展为四个子任务，纳入社交 OAuth 绑定、注册渠道控制和邀请码渠道。
- 2026-05-24：四个子任务已完成，定向后端测试和前端类型检查通过。

## 验证记录

- `.\gradlew.bat compileJava`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.SystemSettingsAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.SecurityPolicyServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.AdminAuthControllerTests"`
- `bun run typecheck`（工作目录：`web/`）
