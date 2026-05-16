# REQ-20260506-012 参考项目功能深度复核与任务再生成

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-016 参考项目功能深度复核与任务再生成](../../tasks/done/TASK-20260506-016-reference-depth-recheck-task-generation.md)

关联报告：

- [REP-20260506 参考项目功能深度再复核](../reports/REP-20260506-reference-feature-depth-recheck.md)

## 背景

此前已完成对 `new-api-main`、`sub2api-main`、`cc-switch-main`、`cockpit-tools-main`、`cli_proxy-master` 的多轮对标，并把多项高优差距归档为 Done。用户再次要求深度对比参考项目，重点关注功能完善度和功能丰富度，并把缺失或不完善项转为后续 task。

## 目标

- 重新对比 `x-ai-gateway` 与 `D:/WorkSpace/Project/ai/参考` 下参考项目的功能面。
- 以“是否具备可生产使用闭环”为主，而不只按代码入口是否存在判断。
- 识别已经完成但仍不完善的能力、尚未实现的能力，以及不建议短期照搬的能力。
- 将需要后续处理的缺口拆为本地 backlog task，并更新任务索引。

## 范围

- 当前仓库代码、文档、任务索引与已归档任务状态。
- 五个参考项目的 README、目录结构和关键功能模块。
- 功能维度包括 Provider 生态、协议转换、计费支付、账号配额、CLI/AI IDE 接入、请求过滤、观测、运维、i18n/OpenAPI、迁移兼容和客户端工具链。

## 非目标

- 本轮不进入产品功能代码实现。
- 不调用线上 Notion、Linear 或其他 SaaS。
- 不清理当前工作区中与本轮分析无关的历史未提交改动。
- 不把桌面工具、本地代理或本机 profile 接管作为默认产品方向。

## 方案

1. 创建本地需求、任务和报告骨架。
2. 复核当前项目能力面与已完成任务，确认哪些缺口已经闭环、哪些只是骨架闭环。
3. 复核五个参考项目的功能丰富度，按可迁移价值重新归类。
4. 输出新的能力矩阵和差距分级。
5. 将缺失或不完善项拆分为后续 backlog task，并回写索引。

## 风险

- 当前工作区存在大量历史未提交改动，分析必须避免误把未提交但已存在的能力当作未实现。
- 参考项目定位不同，桌面工具类能力不能直接映射为服务端网关必做功能。
- 仅靠目录和 README 容易低估实现细节，因此需要结合当前仓库代码入口、任务归档记录和报告证据判断。

## 验收标准

- 新增或更新深度复核报告，覆盖五个参考项目。
- 报告明确功能完善度、功能丰富度、当前状态、差距和建议任务。
- 缺失或不完善项已转为本地 task，并在后续批次完成后归档到 `tasks/done/`。
- `docs/index.md` 与 `tasks/index.md` 已更新。
- 本轮分析任务归档到 `tasks/done/`，需求文档记录验收结果。

## 实现结果

已完成五个参考项目再次深度复核，并生成 [REP-20260506 参考项目功能深度再复核](../reports/REP-20260506-reference-feature-depth-recheck.md)。

本轮新增的 9 个后续 task 现均已完成并归档：

- [TASK-20260506-017 Provider 真实凭证 Smoke 与价格同步自动化](../../tasks/done/TASK-20260506-017-provider-smoke-pricing-sync.md)
- [TASK-20260506-018 支付定时对账、订阅发票与跨币种结算](../../tasks/done/TASK-20260506-018-payment-scheduled-reconcile-invoice-currency.md)
- [TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../../tasks/done/TASK-20260506-019-realtime-pool-media-adapters.md)
- [TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI](../../tasks/done/TASK-20260506-020-cloud-request-filter-audit-ui.md)
- [TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](../../tasks/done/TASK-20260506-021-ai-ide-account-import-quota-refresh.md)
- [TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](../../tasks/done/TASK-20260506-022-client-instance-plugin-deeplink.md)
- [TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../../tasks/done/TASK-20260506-023-openapi-sdk-frontend-i18n.md)
- [TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](../../tasks/done/TASK-20260506-024-linux-systemd-data-migration.md)
- [TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../../tasks/done/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md)

## 测试/验证

- 已复核当前仓库后端包、协议入口、Provider Catalog、前端功能目录、docs/tasks 索引和已归档任务遗留项。
- 已复核五个参考项目 README、目录结构和关键能力模块。
- 已更新 `docs/index.md` 与 `tasks/index.md`。

## 遗留问题

- 本轮仅做分析与任务拆分，不进入代码实现。
- 当前工作区存在大量历史未提交改动，本轮没有清理无关文件。
