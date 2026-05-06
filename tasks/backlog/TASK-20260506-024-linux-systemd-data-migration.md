# TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容

状态：Backlog  
优先级：Medium  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)

## 背景

当前仓库已有 Docker Compose 和 Windows PowerShell 的 install/upgrade/rollback 脚本。对照 `sub2api-main` 的一键部署、在线升级、回滚和 `datamanagementd`，仍缺 Linux/systemd 私有化部署、数据管理服务和 One API/Sub2API 迁移兼容工具。

## 目标

- 建立 Linux/systemd 一键部署与升级回滚流程。
- 补齐备份、恢复、导入、导出和数据管理 smoke。
- 评估并实现 One API/Sub2API 数据迁移兼容的最小路径。

## 范围

- `install.sh`、`upgrade.sh`、`rollback.sh` 或等价脚本。
- systemd unit、环境变量模板、健康检查和日志路径。
- 数据导入/导出、备份恢复、migration dry-run。
- One API/Sub2API 用户、key、provider/channel、余额/用量的映射方案。

## 非目标

- 不破坏现有 Windows PowerShell 部署脚本。
- 不承诺无损迁移所有第三方扩展字段。
- 不迁移真实生产数据，除非另有明确授权。

## 验收标准

- Linux 一键部署可在干净环境完成预检、启动、健康检查和回滚。
- 数据管理命令支持 dry-run、校验、导出和恢复。
- One API/Sub2API 迁移映射有文档、样例数据和失败报告。
- 部署与迁移 smoke 结果写入本地 docs。

## 实现记录

待处理。

## 测试/验证

待处理。

## 遗留问题

待处理。
