# TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容

状态：Done  
优先级：Medium  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-020 Linux/systemd 部署、数据管理与迁移兼容](../../docs/requirements/REQ-20260506-020-linux-systemd-data-migration.md)

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

- 新增 `deploy/systemd/x-ai-gateway.service` 和 `scripts/linux/x-ai-gateway.env.example`。
- 新增 `scripts/linux/install.sh`、`scripts/linux/upgrade.sh`、`scripts/linux/rollback.sh`，支持 dry-run、systemd 操作提示、健康检查和版本目录切换。
- 新增 `scripts/data-management.mjs`，提供 One API/Sub2API 迁移 dry-run 和导出模板能力。
- 新增 One API/Sub2API 样例导出文件，覆盖用户、key、provider/channel 和 usage 映射。
- 新增 [linux-systemd-data-migration](../../docs/linux-systemd-data-migration.md)，记录部署、升级、回滚、迁移映射和验收命令。

## 测试/验证

- `node scripts\data-management.mjs migrate --source one-api --input docs\migrations\samples\one-api-export.sample.json --dry-run`
- `node scripts\data-management.mjs migrate --source sub2api --input docs\migrations\samples\sub2api-export.sample.json --dry-run`
- `bash -n scripts/linux/install.sh`
- `bash -n scripts/linux/upgrade.sh`
- `bash -n scripts/linux/rollback.sh`
- `node scripts\data-management.mjs export-template --help`

## 遗留问题

- 未在真实 Linux 主机安装 systemd service；当前完成脚本、模板、文档和语法验证。
- 未迁移真实生产数据；当前提供 dry-run 映射输出和失败报告基础。
