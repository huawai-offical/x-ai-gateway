# Ops/Maintenance/Release 演练证据

状态：Active
创建日期：2026-05-06
关联需求：[REQ-20260506-001 第七批高优先级任务闭环设计](requirements/REQ-20260506-001-seventh-priority-task-closure-design.md)
关联任务：[TASK-20260505-004 Ops/Maintenance/Release 真实演练证据补齐](../tasks/done/TASK-20260505-004-ops-maintenance-release-real-drill-evidence.md)

## 背景

X-263 代码态审计指出 Ops/Maintenance/Release 链路已有服务和状态机，但需要补充可重放证据，避免只停留在 dry-run 或页面字段层。

## 演练范围

- `MaintenanceRunService` 覆盖 `PRECHECK`、`BACKUP`、`RESTORE_DRY_RUN`、`UPGRADE_CHECK`、`ROLLBACK_PLAN`。
- dry-run 动作生成 `detailJson` 与 `artifactChecksum`，不写真实 artifact。
- 真实 `BACKUP` 必须传入 `confirm=true`，并在 `gateway.storage.fileRoot/maintenance-runs/` 下写入 JSON artifact。
- `PlatformChangePlanService` 覆盖升级失败后基于 rollback playbook 自动创建并执行 `ROLLBACK` plan。
- `MaintenanceRunsPage` 展示 run type、状态、dry-run/confirmed 模式、checksum、artifact path 与 detail JSON。

## 本地复现

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.MaintenanceRunServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PlatformChangePlanServiceTests"
```

## 验收证据

- `MaintenanceRunServiceTests.shouldCreateDryRunEvidenceForMaintenanceRunTypes` 验证 dry-run 状态、checksum、detailJson 与审计事件。
- `MaintenanceRunServiceTests.shouldRequireConfirmForRealBackupAndCreateArtifactWhenConfirmed` 验证真实 `BACKUP` 的 confirm gate、artifact 路径、checksum 和审计记录。
- `PlatformChangePlanServiceTests.shouldAutoRollbackFailedUpgradeWithPlaybook` 验证失败升级发布 `UPGRADE_FAILED`，自动创建回滚 plan，标记 playbook triggered，并发布 `UPGRADE_ROLLED_BACK`。

## 边界

- 当前演练使用本地测试夹具与 mock service，不直接接入生产部署系统。
- `RESTORE_DRY_RUN` 仍保持 dry-run 语义，不执行真实恢复。
- 真实备份 artifact 只保存本地 JSON 证据，不包含生产私密数据。
