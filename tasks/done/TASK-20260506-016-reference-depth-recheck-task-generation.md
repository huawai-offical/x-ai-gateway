# TASK-20260506-016 参考项目功能深度复核与任务再生成

状态：Done  
优先级：High  
来源：User Request  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)  
关联报告：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)

## 背景

用户要求再次深度对比参考项目，重新评估 `x-ai-gateway` 的功能完善度和功能丰富度，并把缺失或不完善项转换为后续 task。此前多项对标增强任务已经归档 Done，本轮需要避免重复已有任务，同时把“骨架已完成但产品闭环仍不足”的内容拆出来。

## 目标

- 深度复核五个参考项目与当前项目的能力差距。
- 区分已完成、基本实现、部分实现、未实现和不建议照搬。
- 将需要后续处理的缺口拆为 backlog task。
- 更新本地文档、报告与任务索引。

## 范围

- `D:/WorkSpace/Project/ai/参考/new-api-main`
- `D:/WorkSpace/Project/ai/参考/sub2api-main`
- `D:/WorkSpace/Project/ai/参考/cc-switch-main`
- `D:/WorkSpace/Project/ai/参考/cockpit-tools-main`
- `D:/WorkSpace/Project/ai/参考/cli_proxy-master`
- 当前仓库 `src/`、`web/`、`docs/`、`tasks/`

## 非目标

- 不实现新功能代码。
- 不同步线上 Linear/Notion。
- 不修改无关历史改动。

## 验收标准

- 生成新的深度复核报告。
- 生成后续 backlog task。
- 任务索引和文档索引已回写。
- 本任务完成后移动到 `tasks/done/`。

## 实现记录

已完成五个参考项目再次深度复核，并生成 [REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)。

本轮新增 9 个后续 backlog task：

- [TASK-20260506-017 Provider 真实凭证 Smoke 与价格同步自动化](../backlog/TASK-20260506-017-provider-smoke-pricing-sync.md)
- [TASK-20260506-018 支付定时对账、订阅发票与跨币种结算](../backlog/TASK-20260506-018-payment-scheduled-reconcile-invoice-currency.md)
- [TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../backlog/TASK-20260506-019-realtime-pool-media-adapters.md)
- [TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI](../backlog/TASK-20260506-020-cloud-request-filter-audit-ui.md)
- [TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](../backlog/TASK-20260506-021-ai-ide-account-import-quota-refresh.md)
- [TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](../backlog/TASK-20260506-022-client-instance-plugin-deeplink.md)
- [TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../backlog/TASK-20260506-023-openapi-sdk-frontend-i18n.md)
- [TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](../backlog/TASK-20260506-024-linux-systemd-data-migration.md)
- [TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../backlog/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md)

## 测试/验证

- 已复核当前仓库后端包、协议入口、Provider Catalog、前端功能目录、docs/tasks 索引和已归档任务遗留项。
- 已复核五个参考项目 README、目录结构和关键能力模块。
- 已将新增报告加入 `docs/index.md`。
- 已将本轮分析任务和新增 backlog 加入 `tasks/index.md`。

## 遗留问题

- 本轮仅做分析与任务拆分，不进入代码实现。
- 工作区存在大量历史未提交改动，本轮没有清理无关文件。
