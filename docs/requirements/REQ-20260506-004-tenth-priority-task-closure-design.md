# REQ-20260506-004 第十批任务闭环设计

状态：Done  
创建日期：2026-05-06  
关联任务：

- [TASK-20260501-007 客户端接入包](../../tasks/done/TASK-20260501-007-client-onboarding-pack.md)
- [TASK-20260501-010 生产部署与升级体系](../../tasks/done/TASK-20260501-010-production-deployment-upgrade.md)
- [TASK-20260501-011 监控与账务 rollup](../../tasks/done/TASK-20260501-011-monitoring-billing-rollup.md)

## 背景

按 `tasks/index.md` 中所有未完成任务排序，当前剩余最高优先级均为 Medium。按任务索引顺序，本批选择 `TASK-20260501-007`、`TASK-20260501-010`、`TASK-20260501-011`。三项分别补齐客户端接入体验、生产部署升级入口、长期监控与账务聚合，能够让已有网关能力从“可用”继续推进到“可接入、可部署、可运营”。

## 目标

- 为 Distributed Key 增加面向 Codex、Claude Code、Gemini CLI、OpenCode/OpenClaw 的接入包，包含配置片段、Deep Link、MCP、Prompts、Skills 与故障排查。
- 为生产部署与升级体系增加可机器读取的部署清单、升级预检、回滚说明与脚本入口，补齐私有化部署交付面。
- 为监控与账务 rollup 增加日/周/月维度聚合、渠道健康摘要、账务流水汇总与导出入口。

## 范围

### 客户端接入包

- 扩展 `DistributedKeyAdminService` 的 client config export，新增 onboarding pack 聚合响应。
- 支持至少 Codex、Claude Code、Gemini CLI 三类 CLI 配置，并覆盖 OpenCode/OpenClaw 兼容提示。
- 输出 `xag://` Deep Link、HTTPS import link、curl smoke、MCP server、Prompts、Skills、env 与故障排查片段。
- 继续遵守完整 secret 只在创建/轮换后一次性消费的规则，普通导出只返回 masked secret 占位。

### 生产部署与升级体系

- 在现有 `PlatformOperationsService` 基础上增加部署清单与升级预检响应。
- 清单覆盖 Docker Compose、`.env.example`、install/upgrade/rollback 脚本、健康检查、卷挂载、数据库迁移、Redis、日志路径。
- 预检结果需要明确阻断项、建议项、升级/回滚命令与文档路径。
- 本轮不直接执行真实 Docker 或数据库 migration，只提供可验证的管理端 manifest 与文档。

### 监控与账务 rollup

- 在观测与支付已有 repository 基础上增加聚合服务。
- 支持 day/week/month 粒度窗口，并按 provider、model、distributedKey 汇总 usage 与请求健康。
- 汇总 paid orders、token credits、balance ledger delta、失败请求、平均延迟和异常摘要。
- 提供 JSON 响应与 CSV 文本导出，避免首版引入外部 BI。

## 非目标

- 不在本轮实现桌面客户端或直接修改用户本机 CLI 配置文件。
- 不在本轮引入云平台专属一键模板。
- 不在本轮删除或归档历史明细数据。
- 不在本轮实现完整前端页面，仅补后端接口、文档、测试和本地任务闭环。

## 风险

- CLI 客户端配置格式会随外部项目变化，本轮以可编辑片段与 Deep Link 元数据为主，后续仍需真实客户端 smoke。
- 生产部署预检只能覆盖仓库与配置层面的确定性检查，真实环境的端口、权限、镜像仓库仍需上线演练。
- Rollup 首版基于明细查询即时聚合，长周期大数据量场景后续应补持久化 rollup 表与定时任务。

## 验收标准

- 三个任务均移动到 `tasks/done/`，并回写实现结果、验证情况、遗留问题和后续建议。
- 客户端接入包至少覆盖 Codex、Claude Code、Gemini CLI 的配置片段、Deep Link、MCP、Prompts、Skills 与 smoke 示例。
- 部署清单与升级预检接口可返回明确的阻断项、建议项、命令和文档路径。
- 监控与账务 rollup 可按 day/week/month 生成聚合结果并导出 CSV。
- 目标单元测试通过，文档索引与任务索引同步更新。

## 实现结果

- `TASK-20260501-007`：新增 Distributed Key onboarding pack 接口，输出 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw、curl smoke 配置片段，补齐 Deep Link、MCP、Prompts、Skills、故障排查，并扩展 `GatewayClientFamily` 归一化。
- `TASK-20260501-010`：新增部署 manifest 和 preflight 接口，补齐 Dockerfile、Docker Compose、`.env.example`、install/upgrade/rollback PowerShell 脚本和生产部署升级文档。
- `TASK-20260501-011`：新增 `MonitoringBillingRollupService`，支持 day/week/month 即时聚合、provider/model/distributedKey 维度、渠道健康、账务汇总和 CSV 导出。
- 已新增本地说明文档：[client-onboarding-pack](../client-onboarding-pack.md)、[production-deployment-upgrade](../production-deployment-upgrade.md)、[monitoring-billing-rollup](../monitoring-billing-rollup.md)。

## 验证情况

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PlatformOperationsDeploymentServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.MonitoringBillingRollupServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests"
```

验证覆盖：

- 多 CLI 接入包不泄露完整 secret，Deep Link、MCP、troubleshooting 和 client family 片段完整返回。
- OpenCode/OpenClaw 常见别名可归一化。
- 部署 manifest 能发现关键文件，preflight 能返回 Redis runtime store 建议和升级命令。
- Rollup 能聚合请求、usage、支付订单、余额流水、渠道健康和 CSV 导出。

## 遗留问题

- CLI 客户端真实导入行为仍需后续在线 smoke，特别是 Claude Code 和 Gemini CLI 对自定义 base URL 的限制。
- 部署脚本首版以 Docker Compose 为主，云平台模板、镜像签名和蓝绿发布仍可后续增强。
- Rollup 首版为即时聚合，长周期大数据量场景后续应补持久化 rollup 表、定时任务、清理和归档策略。
