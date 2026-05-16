# TASK-20260508-006 Codex 批量恢复审计事件追踪

状态：Done  
优先级：High  
排期：P0-03  
来源：User Request / REQ-20260508-002  
关联需求：[REQ-20260508-002 Codex 导入去重、可信前端与审计追踪闭环](../../docs/requirements/REQ-20260508-002-codex-import-dedupe-audit-closure.md)

## 背景

Codex Runtime 批量恢复已经写入系统事件，但管理员需要从批量操作结果直接追踪审计事件，并在系统事件页精准过滤同类操作。

## 目标

- 系统事件 API 支持 `eventType`、`entityType`、`entityRef` 过滤。
- 系统事件页面读取 URL query 并传递过滤条件。
- 批量恢复结果弹窗提供审计事件跳转。
- 补充后端和前端测试。

## 范围

- `OpsTimelineAdminController`
- `OpsTimelineService`
- `web/src/features/ops/system-events-page.tsx`
- `web/src/features/accounts/account-pool-detail-page.tsx`
- 对应测试

## 非目标

- 不改变系统事件表结构。
- 不增加分页或服务端复杂查询优化。

## 验收标准

- `GET /admin/ops/system-events?eventType=CODEX_RUNTIME_BATCH_RECOVERY&entityRef=account-pool:5` 返回匹配事件。
- 前端 URL query 能初始化过滤条件。
- 批量恢复弹窗中可跳转到过滤后的系统事件页。

## 实现记录

- `GET /admin/ops/system-events` 新增 `eventType`、`entityType`、`entityRef` 查询参数。
- `OpsTimelineService.listEvents` 增加事件类型和对象过滤，保留原有 severity/source/from/to 能力。
- Codex 批量恢复系统事件写入统一对象标识：`entityType=ACCOUNT_POOL`、`entityRef=account-pool:{id}`。
- 系统事件页面从 URL query 初始化过滤条件；批量恢复弹窗新增审计事件跳转。

## 测试/验证

- `OpsTimelineServiceTests` 覆盖 `eventType/entityRef` 过滤。
- `system-events-page.test.tsx` 覆盖 URL query 初始化和 API 参数传递。
- 浏览器联调确认从账号池 5 的批量预检弹窗跳转到过滤后的系统事件页，并能看到对应 Codex Runtime 批量恢复事件。

## 遗留问题

- 历史旧事件仍可能使用旧 `entityRef`，新事件已统一格式。
