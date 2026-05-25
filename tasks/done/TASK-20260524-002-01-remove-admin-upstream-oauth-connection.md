# TASK-20260524-002-01 删除 Admin 厂商 OAuth 连接入口

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-002](TASK-20260524-002-admin-oauth-removal-portal-social-oauth-config-parent.md)  
上游来源：[REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 背景

`/admin/oauth/{provider}/start` 和 `/admin/oauth/{provider}/callback` 当前没有真实 token exchange，callback 后把 authorization code 包装成伪 access/refresh token 入库。该功能会误导管理员认为上游厂商 OAuth credential 可用，应删除。

## 目标

- 删除 Admin 厂商 OAuth controller/service 和对应测试。
- 删除前端通用厂商 OAuth 连接页、回调页、路由和详情页按钮。
- 保留 Codex auth.json 导入入口。

## 非目标

- 不删除 Portal social OAuth。
- 不删除 `CODEX_OAUTH` 等历史账号类型枚举。
- 不迁移历史数据库表。

## 输入

- `OAuthConnectionController`
- `OAuthConnectionService`
- `oauth-connect-page.tsx`
- `oauth-callback-page.tsx`
- `account-group-detail-page.tsx`
- router/navigation 元数据

## 输出

- Admin 厂商 OAuth 连接入口不可用。
- 前端不再提供“发起 OAuth 连接”入口。

## 影响范围

- Admin API。
- Account group detail。
- 前端路由元数据和相关测试。

## 依赖

- `TASK-20260524-002` 父任务。

## 风险

- 误删 Codex onboarding/auth.json 导入会影响现有真实账号导入能力。

## 验收标准

- 代码中不再存在 `/admin/oauth` controller 映射。
- Account group detail 不再出现“发起 OAuth 连接”按钮。
- `/accounts/connect/codex` 仍保留。
- 定向后端/前端测试通过。

## 测试边界

- `OAuthConnectionControllerTests` 删除或改为不存在入口。
- Account group detail 相关测试更新。

## 关联文档

- [REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 当前状态

- 2026-05-24：已删除 Admin 厂商 OAuth controller/service/test 和前端通用连接/回调页面；保留 Codex auth.json 导入入口。

## 验证记录

- `.\gradlew.bat compileJava`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.SystemSettingsAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.SecurityPolicyServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.AdminAuthControllerTests"`
- `bun run typecheck`（工作目录：`web/`）
