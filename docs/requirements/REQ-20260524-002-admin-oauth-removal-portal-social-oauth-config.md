# REQ-20260524-002 删除 Admin 厂商 OAuth 连接并配置化 Portal 社交 OAuth

状态：In Progress  
日期：2026-05-24  
关联任务：[TASK-20260524-002](../../tasks/done/TASK-20260524-002-admin-oauth-removal-portal-social-oauth-config-parent.md)

## 背景

当前系统存在两类 OAuth：

- Admin 侧上游账号 OAuth 连接：`/admin/oauth/{provider}/start` 和 `/admin/oauth/{provider}/callback` 用于跳转 OpenAI、Gemini、Claude 等厂商授权页，并在 callback 后写入上游账号。
- Portal 社交 OAuth：`/portal/auth/oauth/*` 用于用户通过 Google、GitHub、QQ、WeChat、Meta、X 登录或绑定身份。

Admin 侧上游厂商 OAuth 连接当前没有完成真实 token exchange、refresh、quota sync 和厂商 API credential 验证，callback 后会把 authorization code 包装成伪 access/refresh token 入库。这类入口会让管理员误以为网关已经支持对应厂商账号直连，实际不可证明可用，违反当前“不可用能力直接失败，不提供模糊成功”的产品口径。

Portal 社交 OAuth 属于用户登录能力，不是上游模型厂商 API credential。该能力可以保留，但第三方 OAuth provider 通常需要系统级 clientId、clientSecret、回调域名和启用开关；这些不应只依赖环境变量或硬编码默认列表，后台必须提供配置位，让管理员明确选择是否开启社交 OAuth 和各 provider。

## 目标

- 删除 Admin 侧“厂商 OAuth 连接”入口，避免继续创建伪上游账号 token。
- 保留 Portal 社交 OAuth 登录/绑定能力。
- 在后台系统参数中增加 Portal 社交 OAuth 全局开关和 provider 配置。
- 社交 OAuth provider 只有在全局开启、provider 开启且必需 clientId/clientSecret 配置完整时才出现在 Portal provider 列表并允许 start/callback。
- `clientSecret` 必须加密存储，后台响应只返回是否已配置，不回显明文。
- 已通过邮箱密码注册的 Portal 用户，后续可以在登录态安全中心绑定社交 OAuth 身份；绑定时不得因为 provider 邮箱不同而创建新用户。
- 管理员可以控制 Portal 允许的注册渠道，至少包含邮箱密码、社交 OAuth 和邀请码。
- 邀请码注册作为显式注册渠道接入；当仅开放邀请码渠道或策略要求邀请码时，注册必须提供有效邀请码。

## 非目标

- 不删除 Portal 社交 OAuth 登录能力。
- 不在本任务中重命名或迁移 `CODEX_OAUTH`、`GEMINI_OAUTH` 等历史账号类型枚举；这些枚举仍被 auth.json 导入、账号池和路由选择复用。
- 不实现 OpenAI、Gemini、Claude 等上游厂商 OAuth token exchange。
- 不把社交 OAuth 的 provider 配置当作上游模型厂商 credential。
- 不在本任务中建设邀请码库存、发放、核销次数、过期时间等完整营销系统；本任务复用现有注册策略中的邀请码集合。

## 输入

- `src/main/java/com/prodigalgal/xaigateway/admin/api/OAuthConnectionController.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OAuthConnectionService.java`
- `src/main/java/com/prodigalgal/xaigateway/portal/application/PortalSocialOAuthService.java`
- `src/main/java/com/prodigalgal/xaigateway/portal/application/*SocialOAuthProfileClient.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/SystemSettingsAdminService.java`
- `web/src/features/settings/system-settings-page.tsx`
- `web/src/features/accounts/*`

## 输出

- Admin 厂商 OAuth 连接后端接口下线。
- 前端删除通用 `/accounts/connect/:provider` 和 `/accounts/callback/:provider` 上游 OAuth 页面入口。
- 系统参数 API 返回和保存 `socialOAuth` 配置。
- 系统参数页面可配置社交 OAuth 全局开关、各 provider 启用状态、clientId、clientSecret。
- Portal 社交 OAuth 运行时从后台配置读取 provider 状态和密钥。
- Portal 安全中心提供已登录用户绑定社交 OAuth 的入口。
- Portal 注册策略支持注册渠道开关和邀请码渠道校验。

## 影响范围

- Admin OAuth controller/service/test。
- Account group detail 的“发起 OAuth 连接”按钮和相关路由。
- System settings API、系统参数页面和测试。
- Portal 社交 OAuth provider list、start、profile client 支持判断和 token exchange。
- Portal 注册、社交 OAuth 新用户创建与登录态绑定路径。
- Portal 安全中心和注册页。
- 文档索引、smoke checklist 和任务状态。

## 依赖

- 现有 `system_setting` JSON 存储。
- 现有 `CredentialCryptoService` 加密能力。
- 现有 `GatewayProperties` 作为环境变量默认值和 endpoint 默认值。

## 风险

- 直接删除 `UpstreamAccountProviderType` 枚举会误伤 auth.json 导入和账号池路由，因此本任务只删除“跳转授权并伪造 token”的连接入口。
- 如果 `clientSecret` 明文回显，会产生安全风险。
- 如果 provider 未配置仍出现在 Portal 登录页，会继续造成不可用按钮。
- 如果登录态绑定流程仍按邮箱匹配或创建新用户，会把同一自然人拆成多个账号。
- 如果注册渠道关闭时仍允许对应路径创建用户，会绕过管理员策略。

## 验收标准

- `/admin/oauth/{provider}/start` 和 `/admin/oauth/{provider}/callback` 不再作为可用入口。
- 前端不再展示“发起厂商 OAuth 连接”按钮，不再提供通用厂商 OAuth 连接页。
- `GET /admin/settings` 返回 `socialOAuth` 配置，secret 只返回 `clientSecretConfigured`。
- `PUT /admin/settings` 可开启/关闭全局社交 OAuth 和单个 provider，并可设置/清除 provider secret。
- `GET /portal/auth/oauth/providers` 只返回后台开启且配置完整的 provider；全局关闭时返回空列表。
- 未开启或未配置的 provider 调用 start/callback 时直接失败，不生成 session 或跳转 URL。
- 已登录 Portal 用户从安全中心发起社交 OAuth 后，callback 会把外部身份绑定到当前用户；若该外部身份已属于其它用户，必须失败。
- 未登录用户通过社交 OAuth 首次创建用户时，必须受 `SOCIAL_OAUTH` 注册渠道开关约束。
- 邮箱密码注册必须受 `PASSWORD` 或 `INVITE_CODE` 注册渠道开关约束；邀请码渠道打开或策略要求邀请码时，无有效邀请码必须失败。
- Admin 注册策略 API 能返回和保存允许的注册渠道。

## 测试边界

- 后端：System settings controller/service、PortalSocialOAuthService、profile client 支持判断。
- 后端：PortalSecurityService 注册渠道策略、PortalAuthService 注册渠道判定、PortalSocialOAuthService 登录态绑定与社交注册渠道判定。
- 前端：SystemSettingsPage 保存社交 OAuth 配置、AccountGroupDetail 不展示厂商 OAuth 连接按钮、Portal 注册页和安全中心绑定入口。
- 不执行真实第三方 OAuth 线上 smoke。

## 当前状态

- 2026-05-24：根据用户确认创建需求，准备拆分任务并实施。
- 2026-05-24：范围扩展为 Portal 身份入口治理，增加已注册用户社交 OAuth 绑定、注册渠道开关和邀请码注册渠道。
- 2026-05-24：已删除 Admin 侧厂商 OAuth 连接入口；已接入 Portal 社交 OAuth 后台配置、运行时过滤、登录态绑定、注册渠道策略、邀请码注册渠道和 Portal 前端入口。

## 实现结果

- Admin 侧 `/admin/oauth/*` controller/service/test 和前端通用厂商 OAuth 连接页已删除，账号分组详情不再展示“发起 OAuth 连接”。
- `GET/PUT /admin/settings` 增加 `socialOAuth` 配置，`clientSecret` 加密存储且只通过 `clientSecretConfigured` 暴露状态。
- Portal 社交 OAuth provider list/start/callback 读取后台配置；未开启、未配置或缺少必需密钥时直接失败。
- 登录态 callback 会把社交 OAuth 外部身份绑定到当前 Portal 用户；外部身份已绑定其它用户时失败。
- 未登录社交 OAuth 首次建号受 `SOCIAL_OAUTH` 注册渠道约束。
- 注册策略增加 `PASSWORD`、`INVITE_CODE`、`SOCIAL_OAUTH` 渠道；邮箱密码注册带邀请码时走 `INVITE_CODE` 渠道。
- Portal 注册页提交邀请码；Portal 安全中心可从可用 provider 发起绑定；系统参数页增加社交 OAuth provider 配置与注册渠道策略。

## 验证记录

- `.\gradlew.bat compileJava`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.api.SystemSettingsAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.SecurityPolicyServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.AdminAuthControllerTests"`
- `bun run typecheck`（工作目录：`web/`）

## 遗留边界

- 完整邀请码库存、有效期、次数、使用记录和核销审计已由 [REQ-20260524-003](REQ-20260524-003-portal-invitation-code-system.md) 承接并落地；本需求仅保留注册渠道策略历史记录。
- 本轮未执行真实第三方 OAuth 线上 smoke。
