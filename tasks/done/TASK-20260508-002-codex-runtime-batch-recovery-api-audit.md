# TASK-20260508-002 Codex Runtime 批量恢复执行 API、容错与系统事件审计

状态：Done  
优先级：High  
排期：P0-02  
来源：[REQ-20260508-001 Codex 观测后端化、批量恢复与联调 Smoke 闭环](../../docs/requirements/REQ-20260508-001-codex-observability-batch-api-smoke-closure.md)

## 背景

上一批只有前端 dry-run 预检。为了保证批量操作可信，需要后端统一判定 safe/blocked/alreadyReady，并提供真实执行 API，记录系统事件，保证 partial success 可解释。

## 目标

- 新增账号池级 batch recovery preflight 和 execute API。
- 后端统一阻断权限、策略、安全、禁用类账号。
- 执行 safe 账号时容错处理，单账号失败不影响后续账号。
- 将结果写入系统事件审计，detail JSON 脱敏。
- 前端 Runtime 面板使用后端预检/执行结果。

## 验收标准

- dry-run 返回 safe/blocked/alreadyReady/failed 结构化结果。
- execute 只处理 safe 项，blocked 不执行。
- 任一账号恢复失败时响应包含 failed 项，整批继续。
- 系统事件中记录统计、blocked/failed 摘要和 dryRun/execute 模式。
- 后端测试覆盖 blocked、partial success、审计脱敏。

## 实施记录

- 新增 `CodexRuntimeBatchRecoveryRequest`、`CodexRuntimeBatchRecoveryResponse`、`CodexRuntimeBatchRecoveryItemResponse`。
- 新增账号池级预检与执行接口：`/codex-runtime/batch-recovery-preflight`、`/codex-runtime/batch-recovery`。
- 后端统一判定 `safe`、`blocked`、`alreadyReady`；权限、策略、安全、禁用、未授权类错误默认 blocked。
- 执行模式仅恢复 safe 项：清除 frozen、unhealthy、lastError、refreshFailure、cooldown、nextRefreshAfter，并保留 secret 不变。
- 批量流程逐项 try/catch，响应保留 `EXECUTED`、`FAILED`、`SKIPPED`，并写入 `CODEX_RUNTIME_BATCH_RECOVERY` 系统事件。
- 前端账号池 Runtime 弹窗展示后端预检/执行结果、审计事件和执行状态，safe=0 时禁用真实执行按钮。

## 验证记录

- 后端：`AccountPoolAdminServiceTests.shouldPreflightAndExecuteCodexRuntimeBatchRecoverySafely` 覆盖 preflight、execute、blocked skip、safe reset、系统事件。
- 前端：`account-pool-detail-page.test.tsx` 覆盖后端预检数据、blocked 候选、脱敏错误、执行按钮和执行结果。
- 联调：真实后端 `/console/account-pools/5` 预检返回 1 个 alreadyReady、`auditEventId=2`，执行按钮因 safe=0 禁用，未修改真实账号运行态。
