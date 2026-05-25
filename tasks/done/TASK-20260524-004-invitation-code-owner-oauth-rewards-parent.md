# TASK-20260524-004 邀请码归属、OAuth 首次注册与奖励赠品

状态：Done  
优先级：High  
类型：父任务  
上游来源：[REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 背景

邀请码系统已具备库存和核销能力，但仍缺少归属人、注册奖励和社交 OAuth 首次注册支持。该父任务承接用户新的产品口径：邀请码需要成为所有首次注册渠道共享的准入和权益配置载体。

## 目标

- 补齐邀请码归属人和奖励额度配置。
- 社交 OAuth 首次建号支持邀请码传递、策略校验和核销。
- 邮箱密码注册与社交 OAuth 注册统一走邀请码核销与奖励发放。
- Admin 和 Portal 前端同步支持新字段和新注册路径。

## 非目标

- 不实现复杂营销活动、套餐绑定、访问组赠品或返佣结算。
- 不实现归属用户搜索选择器。
- 不做真实第三方 OAuth live smoke。

## 输入

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)
- 已完成的 [TASK-20260524-003](TASK-20260524-003-portal-invitation-code-system-parent.md)
- 现有 Portal 社交 OAuth state/session 实现。
- 现有用户余额流水模型。

## 输出

- 后端 schema/entity/service/API 改造。
- Portal OAuth 注册路径改造。
- Admin/Portal 前端改造。
- focused tests 与文档任务回写。

## 影响范围

- 数据库 changelog、邀请码 entity/repository、Admin service/API、Portal security/auth/OAuth service。
- Admin 邀请码页面、Portal 注册页和 OAuth API client。

## 依赖

- `gateway_user` 用户表。
- `gateway_user_balance_ledger` 余额流水表。
- `portal_social_oauth_session.metadata_json` OAuth state metadata。
- React Query 与现有 Portal 登录页模式。

## 风险

- OAuth callback 是异步回跳，如果 start 阶段未持久化邀请码会丢失注册上下文。
- 奖励发放需要幂等边界，否则 callback 重试可能重复加额度。
- 注册策略和核销服务职责边界必须清晰：策略只判断需要邀请码，核销服务判断邀请码真实有效并发放权益。

## 验收标准

- 子任务全部完成并通过对应验证。
- 所有首次注册渠道支持邀请码输入与核销。
- 邀请码归属人与奖励额度可由 Admin 管理。
- 文档和任务状态准确回写。

## 测试边界

- 后端 focused tests：Admin、Redemption、Security、Social OAuth。
- 前端：`bun run typecheck`。
- 不做真实 OAuth live smoke。

## 关联文档

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)
- [REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)

## 关联任务

- [TASK-20260524-004-01](TASK-20260524-004-01-invitation-code-owner-reward-backend.md)
- [TASK-20260524-004-02](TASK-20260524-004-02-social-oauth-invitation-registration.md)
- [TASK-20260524-004-03](TASK-20260524-004-03-invitation-owner-reward-frontend.md)

## 当前状态

- 2026-05-24：已创建父任务，准备实施。
- 2026-05-24：已完成全部子任务，实现邀请码归属人、奖励额度、社交 OAuth 首次注册邀请码核销和前端接入。

## 实现结果

- 新增邀请码归属人和奖励额度 schema、entity、Admin DTO/service 映射。
- 邀请码核销成功后按配置写入余额流水，并在 usage 记录发放额度。
- 社交 OAuth start 保存邀请码，callback 首次创建用户后执行策略校验、邀请码核销和奖励发放。
- 已登录用户绑定社交 OAuth 保持不核销邀请码。
- Admin 邀请码页面和 Portal 注册页已支持新字段和 OAuth 注册邀请码传递。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"`
- `bun run typecheck`（工作目录：`web/`）
