# TASK-20260524-002-02 Portal 社交 OAuth 后台配置

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-002](TASK-20260524-002-admin-oauth-removal-portal-social-oauth-config-parent.md)  
上游来源：[REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 背景

Portal 社交 OAuth 需要 Google、GitHub、QQ、WeChat、Meta、X 等第三方开发者应用配置。当前配置主要来自环境变量和硬编码 provider 列表，管理员无法在后台明确开启/关闭社交 OAuth 或单个 provider。

## 目标

- 在系统参数 API 中增加 `socialOAuth` 配置。
- 支持全局启用开关、provider 启用开关、clientId、clientSecret 配置。
- `clientSecret` 加密存储，响应不回显明文。
- Portal provider list 和 start 按配置过滤。

## 非目标

- 不执行真实线上 OAuth smoke。
- 不重构 OAuth provider client 的全部 endpoint 配置。
- 不把社交 OAuth 配置用于上游模型厂商 credential。

## 输入

- `SystemSettingsAdminService`
- `SystemSettingsRequest`
- `SystemSettingsResponse`
- `PortalSocialOAuthService`
- `GoogleSocialOAuthProfileClient`
- `GitHubSocialOAuthProfileClient`
- `GenericOAuth2SocialOAuthProfileClient`
- `system-settings-page.tsx`

## 输出

- 后台可配置 Portal 社交 OAuth。
- Portal 社交 OAuth 运行时遵守后台配置。
- 对应测试。

## 影响范围

- System settings API。
- Portal social OAuth runtime。
- 系统参数页面。
- smoke 文档。

## 依赖

- `CredentialCryptoService`。
- `system_setting` JSON 存储。

## 风险

- 密钥明文回显风险。
- 未配置 provider 仍展示登录按钮的误导风险。

## 验收标准

- `GET /admin/settings` 返回全局和 provider social OAuth 配置，secret 只返回 configured 标记。
- `PUT /admin/settings` 可设置、保留和清除 provider secret。
- 全局关闭或 provider 关闭时，Portal provider list 不返回对应 provider。
- provider 缺少必需 clientId/clientSecret 时不能 start。
- 前端系统参数页可保存相关配置。

## 测试边界

- 后端 service/controller 单元测试。
- PortalSocialOAuthService 定向测试。
- SystemSettingsPage 前端测试。

## 关联文档

- [REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 当前状态

- 2026-05-24：已接入 `socialOAuth` 系统参数、secret 加密存储、Portal provider list/start/callback runtime 过滤和系统参数页配置入口。

## 验证记录

- `.\gradlew.bat compileJava`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.SystemSettingsAdminControllerTests"`
- `bun run typecheck`（工作目录：`web/`）
