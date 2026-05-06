# TASK-20260505-004 Ops/Maintenance/Release 真实演练证据补齐

状态：Done
优先级：High
来源：X-263 代码态审计
关联报告：[REP-20260505 X-263 代码态审计](../../docs/reports/REP-20260505-x263-code-state-audit.md)
关联任务：[TASK-20260505-003](../done/TASK-20260505-003-linear-x263-second-gap-overview.md)
关联推进需求：[REQ-20260506-001](../../docs/requirements/REQ-20260506-001-seventh-priority-task-closure-design.md)

## 背景

当前已有维护运行、变更计划、升级、回滚、备份和审计服务，但 X-263 审计发现“真实执行证据”仍不足，容易停留在状态机和 dry-run 层。

## 目标

补齐可重放的 ops 演练证据，使维护、升级、回滚和恢复链路具备真实 artifact、日志、审计和失败回滚样例。

## 范围

- 维护运行 `BACKUP`、`RESTORE_DRY_RUN`、`UPGRADE_CHECK`、`ROLLBACK_PLAN` 的演练脚本或测试夹具。
- 生成或校验本地 artifact 路径、checksum、审计事件和状态迁移。
- 前端页面展示演练结果的最小可验证字段。

## 非目标

- 不直接接入生产环境升级系统。
- 不在仓库内保存真实备份文件或私有部署凭证。

## 风险

- 本地 artifact 与真实部署环境仍存在差异，需要明确标记 mock、dry-run 与真实执行边界。

## 验收标准

- 至少一条升级失败自动回滚路径有可重放测试。
- 维护运行 artifact、审计事件、状态迁移均有测试覆盖。
- 文档记录如何在本地复现演练。

## 本批推进记录

- 2026-05-06：进入第七批高优先级任务闭环，目标是补齐 maintenance run 覆盖、升级失败自动回滚测试和复现文档。

## 实现结果

- 扩展 `MaintenanceRunServiceTests`，覆盖 `PRECHECK`、`RESTORE_DRY_RUN`、`UPGRADE_CHECK`、`ROLLBACK_PLAN` dry-run 证据。
- 强化真实 `BACKUP` 测试，验证 `confirm=true`、artifact 文件存在、checksum 和审计记录。
- 扩展 `PlatformChangePlanServiceTests`，覆盖升级失败后自动创建并执行 rollback plan、标记 rollback playbook triggered、发布升级失败与已回滚事件。
- 确认前端 `MaintenanceRunsPage` 已展示 run type、状态、dry-run/confirmed 模式、checksum、artifact path 与 detail JSON 等最小演练字段。
- 新增本地复现文档：[Ops/Maintenance/Release 演练证据](../../docs/operations-drill-evidence.md)。

## 测试/验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.MaintenanceRunServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PlatformChangePlanServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OllamaGatewayChatRuntimeTests"
```

结果：通过。

## 遗留问题

- 当前自动回滚演练使用 mock rollout service，不代表真实部署系统已经接入。
- 真实恢复、真实升级窗口和生产 artifact 管理仍建议在后续生产部署任务中继续强化。

## 后续建议

- 与 `TASK-20260501-010` 生产部署与升级体系联动，补充真实部署 provider 或运维脚本的端到端 smoke。
