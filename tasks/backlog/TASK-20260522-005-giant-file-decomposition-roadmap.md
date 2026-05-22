# TASK-20260522-005 超大文件分批组件化与服务拆分

状态：Backlog  
优先级：High  
上游来源：[REQ-20260522-003](../../docs/requirements/REQ-20260522-003-toast-feedback-and-component-splitting.md)

## 任务类型

父任务

## 背景

本轮已完成调试工作台第一阶段组件化拆分，但前后端仍存在多个超过 1000 行的文件。继续堆叠会让 UI 变更、API 调整和测试维护成本持续上升，需要按风险与边界分批拆分。

## 目标

- 给剩余超大文件建立分批拆分顺序。
- 每个拆分批次具备独立验证边界。
- 前端页面优先拆展示组件、表单向导、数据 hooks。
- 后端服务优先拆纯 mapper、builder、adapter、executor 子服务。

## 非目标

- 不在一个 PR 或一个任务里拆完所有文件。
- 不借拆分改变 API 行为。
- 不重写 baseline 历史迁移策略。

## 输入

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`
- `web/src/features/accounts/account-group-detail-page.tsx`
- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/accounts/account-groups-page.tsx`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionService.java`
- `web/src/features/ops/governance-page.tsx`
- `web/src/features/models/models-page.tsx`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/file/GatewayFileService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OfficialAccountAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/observability/GatewayRequestLifecycleService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`

## 输出

- 分批拆分子任务。
- 每批拆分后的行数、职责边界和验证命令。
- 不改变业务行为的重构提交。

## 影响范围

前端账号、凭证、模型、治理、工作台页面；后端异步资源、聊天执行、资源执行、文件服务、凭证管理、观测生命周期。

## 依赖

- 当前测试和类型检查可作为重构安全网。
- 大页面仍需补充局部组件测试。

## 风险

- 后端核心服务拆分可能影响路由、资源生命周期和观测记录。
- 前端巨型页面中表单状态、详情抽屉和 mutation 混在一起，拆分需要小步验证。

## 验收标准

- [ ] P0 文件均拆出至少一个独立模块或组件。
- [ ] 单文件行数持续下降，新增文件职责单一。
- [ ] 每批有对应定向测试或编译验证。
- [ ] 不引入 API 契约变更。

## 测试边界

- 前端批次：`npm run typecheck`、对应 vitest。
- 后端批次：`.\gradlew.bat compileJava compileTestJava`、对应服务定向测试。
- 高风险执行链路补充回归测试。

## 当前状态

待拆分。
