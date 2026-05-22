# TASK-20260521-006-03 清理后 Liquibase Baseline 重建

## 任务类型

子任务 / 依赖前两项

## 背景

父任务：`tasks/in-progress/TASK-20260521-006-redundant-api-eradication-and-baseline-refresh.md`

在冗余接口与实体清理完成后，需要按最新 schema 重建 baseline，供用户清库后直接初始化；其中 `vector` / `file` 相关主线支撑表应明确保留，不随控制台下线一并删掉。

## 目标

- 基于清理后的真实 schema 重建 `db.changelog-0001-baseline.yaml`
- 确保 `db.changelog-master.yaml` 只引用新 baseline
- 确认 baseline 保留 `vector stores`、`files` 与 Responses `file_search` 依赖表
- 写明清库后启动验证前置说明

## 当前状态

进行中（已完成校准确认：`db.changelog-master.yaml` 仍只引用 `db.changelog-0001-baseline.yaml`，且 baseline 保留 `gateway_file`、`gateway_async_resource`、`site_capability_snapshot`、`cost_model` 等当前主线支撑表）
