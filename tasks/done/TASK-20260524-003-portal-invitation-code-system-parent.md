# TASK-20260524-003 Portal 完整邀请码系统

状态：Done  
优先级：High  
类型：父任务  
上游来源：[REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)

## 背景

Portal 注册已经有邀请码渠道开关，但邀请码库存仍是内存策略白名单。该父任务承接完整邀请码系统建设，目标是把邀请码变成持久化、可管理、可核销、可审计的注册准入资源。

## 目标

- 建立邀请码持久化模型和核销记录。
- 接入 Portal 注册路径并保证校验失败硬失败。
- 提供 Admin 管理 API 与前端页面。
- 更新注册策略语义和本地文档任务状态。

## 非目标

- 不合并兑换码系统。
- 不接入余额奖励或套餐权益。
- 不实现社交 OAuth 注册前邀请码暂存流程。
- 不做 CSV 导出和复杂报表。

## 输入

- [REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)
- 现有 `PortalSecurityService` 注册渠道策略。
- 现有 `PromoCodeAdminService` 批量码管理模式。

## 输出

- 邀请码数据模型、Admin API、Portal 注册核销和 Admin 前端页面。
- 单元测试、前端 typecheck 和文档回写。

## 影响范围

- 后端持久化、Admin API、Portal auth service。
- Admin 前端导航和用户域页面。
- 注册策略请求/响应和相关测试。

## 依赖

- Spring Data JPA、Liquibase。
- 现有 Gateway user 与 Portal session。
- 现有 React Query、Dialog、Table pagination 组件。

## 风险

- 并发核销可能导致次数超用。
- 邀请码库存与旧策略白名单共存会造成语义混乱。
- 已使用邀请码删除会破坏审计链路。

## 验收标准

- 子任务全部完成并通过对应验证。
- 注册路径真实核销邀请码并写入记录。
- Admin 能管理邀请码库存。
- 文档和任务状态准确回写。

## 测试边界

- 后端 focused tests：Admin 邀请码服务、Portal 注册核销、注册策略响应。
- 前端：`bun run typecheck`。
- 不做真实第三方 OAuth smoke。

## 关联文档

- [REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)

## 关联任务

- [TASK-20260524-003-01](TASK-20260524-003-01-invitation-code-data-service.md)
- [TASK-20260524-003-02](TASK-20260524-003-02-portal-registration-invitation-redemption.md)
- [TASK-20260524-003-03](TASK-20260524-003-03-admin-invitation-code-ui.md)

## 当前状态

- 2026-05-24：已创建父任务，准备实施。
- 2026-05-24：已完成全部子任务，实现持久化邀请码库存、核销记录、Admin 管理、Portal 注册核销和文档回写。

## 实现结果

- 新增邀请码 schema、entity、repository、Admin API、Admin 页面和 Portal 核销服务。
- 注册策略不再保存邀请码白名单，邀请码真实有效性由持久化库存和核销服务判断。
- 系统参数页只保留注册渠道策略与邀请码库存状态。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `bun run typecheck`（工作目录：`web/`）
