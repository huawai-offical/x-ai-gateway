# TASK-20260524-003-01 邀请码数据模型与 Admin 服务

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-003](TASK-20260524-003-portal-invitation-code-system-parent.md)  
上游来源：[REQ-20260524-003](../../docs/requirements/REQ-20260524-003-portal-invitation-code-system.md)

## 背景

邀请码需要从注册策略白名单升级为持久化资源，Admin 必须能管理库存和查看核销记录。

## 目标

- 新增 `invitation_code` 和 `invitation_code_usage` schema。
- 新增 entity、repository、Admin DTO、service 和 controller。
- 支持列表、批量创建/生成、编辑、删除、使用记录查询。

## 非目标

- 不接入 Portal 注册核销。
- 不做前端页面。
- 不做 CSV 导出。

## 输入

- `RedeemCodeEntity`、`RedeemCodeUsageEntity`
- `PromoCodeAdminService`
- Liquibase changelog。

## 输出

- 后端邀请码库存管理能力。

## 影响范围

- `src/main/resources/db/changelog`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence`
- `src/main/java/com/prodigalgal/xaigateway/admin`

## 依赖

- `GatewayUserEntity`
- Spring Data JPA。

## 风险

- 大小写不一致可能绕过唯一性。
- 删除已使用邀请码会破坏审计。

## 验收标准

- 重复邀请码创建失败。
- 最大使用次数不能小于已用次数。
- 已使用邀请码不能删除。
- 使用记录可按邀请码查询。

## 测试边界

- 新增 Admin service 单元测试。

## 当前状态

- 2026-05-24：待实施。
- 2026-05-24：已完成 `invitation_code`、`invitation_code_usage`、Admin API、Admin service、搜索/状态过滤和审计。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests"`
