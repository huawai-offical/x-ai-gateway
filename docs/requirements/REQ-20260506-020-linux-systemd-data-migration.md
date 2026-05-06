# REQ-20260506-020 Linux/systemd 部署、数据管理与迁移兼容

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](../../tasks/done/TASK-20260506-024-linux-systemd-data-migration.md)

## 背景

仓库已有 Windows PowerShell 的 install/upgrade/rollback 脚本和 Docker Compose 方向的部署材料，但 Linux 私有化部署、systemd unit、备份恢复和 One API/Sub2API 迁移 dry-run 仍缺闭环。

## 目标

- 建立 Linux/systemd 一键安装、升级、回滚、健康检查流程。
- 提供数据导出、备份、恢复、校验和 migration dry-run 脚本。
- 给出 One API/Sub2API 用户、key、channel/provider、余额/用量映射方案和样例。

## 范围

- `scripts/linux/install.sh`、`upgrade.sh`、`rollback.sh`、`x-ai-gateway.env.example`。
- `deploy/systemd/x-ai-gateway.service` 和 ops smoke 脚本。
- 数据管理脚本与样例映射报告。
- Linux 部署和迁移文档。

## 非目标

- 不破坏现有 Windows PowerShell 脚本。
- 不迁移真实生产数据。
- 不承诺无损迁移第三方扩展字段。

## 验收标准

- Linux 脚本支持 `--dry-run` 并能通过 shell 语法检查。
- systemd unit 与环境变量模板完整。
- 数据迁移 dry-run 能输出映射和失败报告。
- 文档、任务状态和验证结果完成回写。

## 实现结果

- 新增 `deploy/systemd/x-ai-gateway.service`，提供 Linux systemd unit 模板。
- 新增 `scripts/linux/x-ai-gateway.env.example`、`scripts/linux/install.sh`、`scripts/linux/upgrade.sh`、`scripts/linux/rollback.sh`，覆盖安装、升级、回滚、环境变量和 `--dry-run` 流程。
- 新增 `scripts/data-management.mjs`，支持 One API/Sub2API 迁移 dry-run 与导出模板。
- 新增 `docs/migrations/samples/one-api-export.sample.json`、`docs/migrations/samples/sub2api-export.sample.json`，提供可重复验证样例。
- 新增 [linux-systemd-data-migration](../linux-systemd-data-migration.md)，沉淀部署、回滚和迁移映射说明。

## 测试/验证

- `node scripts\data-management.mjs migrate --source one-api --input docs\migrations\samples\one-api-export.sample.json --dry-run`
- `node scripts\data-management.mjs migrate --source sub2api --input docs\migrations\samples\sub2api-export.sample.json --dry-run`
- `bash -n scripts/linux/install.sh`
- `bash -n scripts/linux/upgrade.sh`
- `bash -n scripts/linux/rollback.sh`
- `node scripts\data-management.mjs export-template --help`

## 遗留问题

- 本轮未连接真实生产数据库执行迁移，只提供 dry-run、映射输出和样例数据。
- systemd 脚本已做语法和参数闭环，真实 Linux 主机安装仍需要目标机器、用户、目录和 artifact 路径配合。
